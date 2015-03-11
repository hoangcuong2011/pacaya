#!/usr/bin/env python

import sys
import os
import getopt
import math
import tempfile
import stat
import shlex
import subprocess
from subprocess import Popen
from optparse import OptionParser
from pypipeline.util import get_new_file, sweep_mult, fancify_cmd, frange
from pypipeline.util import head_sentences
import platform
from glob import glob
from pypipeline.experiment_runner import ExpParamsRunner, get_subset
from pypipeline import experiment_runner
from pypipeline import pipeline
import re
import random
from pypipeline.pipeline import write_script, RootStage, Stage
from pypipeline.stages import get_oome_stages, StagePath
import multiprocessing
from experiments.exp_util import *
from experiments.path_defs import *
from experiments.param_defs import *
from experiments.srl_stages import ScrapeSrl, SrlExpParams, GobbleMemory, AnnoPipelineRunner

# ---------------------------- Experiments Creator Class ----------------------------------

def pruned_parsers(parsers):
    return [x + SrlExpParams(pruneByModel=True,
                             tagger_parser=x.get("tagger_parser")+"-pr") 
            for x in parsers]
    
class SrlExpParamsRunner(ExpParamsRunner):
    
    # Class variables
    known_exps = (  "dp-conllx",
                    "dp-cll",
                    "dp-conllx-tmp",
                    "dp-conllx-tune",
                    "dp-pruning",
                    "gobble-memory",
                    "dp-aware-tune",
                    "dp-aware-langs",
                    "dp-aware-en",
                    "dp-aware",
                    "dp-aware-small",
                    "dp-erma",
                    "dp-erma-tune",
                    "dp-init-model", 
                    "dp-opt-avg",  
                    "dp-opt", 
                    "dp-conll07",                
                    "dp-en",
                    "dp-logadd",
                    "dp-agiga2",
                    )
    
    def __init__(self, options):
        if options.expname not in SrlExpParamsRunner.known_exps:
            sys.stderr.write("Unknown experiment setting.\n")
            parser.print_help()
            sys.exit()
        name = options.expname if not options.fast else "fast_" + options.expname 
        ExpParamsRunner.__init__(self, name, options.queue, print_to_console=True, dry_run=options.dry_run)
        self.root_dir = os.path.abspath(get_root_dir())
        self.fast = options.fast
        self.expname = options.expname
        self.hprof = options.hprof   
        self.big_machine = (multiprocessing.cpu_count() > 2)
        self.prm_defs = ParamDefinitions(options) 

    def get_experiments(self):
        # ------------------------ PARAMETERS --------------------------
        
        g, l, p = self.prm_defs.get_param_groups_and_lists_and_paths()
        
        g.defaults += g.feat_mcdonald
        g.defaults += g.adagrad_comid
        g.defaults.update(featureSelection=False, useGoldSyntax=True, 
                          adaGradEta=0.05, featureHashMod=20000000, sgdNumPasses=5, l2variance=10000,
                          #adaGradInitialSumSquares=0.1, adaGradConstantAddend=0,
                          sgdAutoSelecFreq=5, sgdAutoSelectLr=True, pruneByDist=True,
                          useLogAddTable=False, acl14DepFeats=False, normalizeMessages=True,
                          algebra="LOG_SIGN",
                          includeUnsupportedFeatures=True,
                          singleRoot=False,
                          inference="BP")
        g.defaults.set_incl_name("pruneByModel", False)
        g.defaults.set_incl_name("pruneModel", False)
        g.defaults.set_incl_name("prune_model_path", False)
        g.defaults.set_incl_name("siblingFactors", False)
        g.defaults.set_incl_name("headBigramFactors", False)
        g.defaults.set_incl_name("grandparentFactors", False)
        g.defaults.set_incl_name("dpSkipPunctuation", False)
        g.defaults.set_incl_name("reduceTags", False)
        g.defaults.set_incl_name("l2variance", False)
        g.defaults.set_incl_name("basicOnly", False)
        g.defaults.set_incl_name("useMstFeats", False)
        g.defaults.set_incl_name("useCarerrasFeats", False)
        g.defaults.set_incl_name("useCoarseTags", False)
        g.defaults.set_incl_arg("group", False)
        g.defaults.set_incl_arg("datasource", False)
        g.defaults.set_incl_arg("prune_model_path", False)
        g.defaults.remove("printModel")
                
        # Parsers
        g.first_order = SrlExpParams(useProjDepTreeFactor=True, linkVarType="PREDICTED", predAts="DEP_TREE",
                                   removeAts="DEPREL", tagger_parser="1st", pruneByModel=False,
                                   bpUpdateOrder="SEQUENTIAL", bpSchedule="TREE_LIKE", bpMaxIterations=1)
        g.second_order = g.first_order + SrlExpParams(grandparentFactors=True, 
                                                      siblingFactors=True, 
                                                      #headBigramFactors=True, 
                                                      tagger_parser="2nd-gra-asib-hb", 
                                                      bpMaxIterations=5, 
                                                      useMseForValue=True)
        g.second_grand_asib = g.first_order + SrlExpParams(grandparentFactors=True, 
                                                      siblingFactors=True, 
                                                      #headBigramFactors=False, 
                                                      tagger_parser="2nd-gra-asib", 
                                                      bpMaxIterations=5, 
                                                      useMseForValue=True)
        g.second_grand = g.second_order + SrlExpParams(grandparentFactors=True, 
                                                       siblingFactors=False,
                                                       #headBigramFactors=False,  
                                                       tagger_parser="2nd-gra")
        g.second_grand_exact = g.second_grand + SrlExpParams(inference="DP")
        g.second_asib = g.second_order + SrlExpParams(grandparentFactors=False, 
                                                     siblingFactors=True,
                                                     #headBigramFactors=False,  
                                                     tagger_parser="2nd-asib")
        g.second_hb = g.second_order + SrlExpParams(grandparentFactors=False, 
                                                     siblingFactors=False,
                                                     #headBigramFactors=True,  
                                                     tagger_parser="2nd-hb")
        #g.unpruned_parsers = [g.first_order, g.second_order, g.second_grand_asib, g.second_asib, g.second_grand_exact, g.second_grand, g.second_hb]
        g.unpruned_parsers = [g.first_order, g.second_grand_asib, g.second_asib, g.second_grand_exact, g.second_grand]
        g.pruned_parsers = pruned_parsers(g.unpruned_parsers)
        g.parsers = g.pruned_parsers + g.unpruned_parsers
        
        # Trainers
        g.erma_dp       = SrlExpParams(trainer="ERMA", dpLoss="DP_DECODE_LOSS", dpStartTemp=0.1, dpEndTemp=0.0001, dpUseLogScale=False, dpAnnealMse=True, trainProjectivize=False)
        g.erma_dp_nomse = SrlExpParams(trainer="ERMA", dpLoss="DP_DECODE_LOSS", dpStartTemp=0.1, dpEndTemp=0.0001, dpUseLogScale=False, dpAnnealMse=False, trainProjectivize=False)
        g.erma_mse      = SrlExpParams(trainer="ERMA", dpLoss="MSE", trainProjectivize=False)
        g.erma_er       = SrlExpParams(trainer="ERMA", dpLoss="EXPECTED_RECALL", trainProjectivize=False)
        g.cll           = SrlExpParams(trainer="CLL", trainProjectivize=True)
        
        if self.fast:
            models_dir = os.path.join(self.root_dir, "exp", "models", "fast-dp-pruning-workaround")
        else:
            models_dir = os.path.join(self.root_dir, "exp", "models", "dp-pruning-workaround")
        
        # Feature sets
        g.turbo_feats = SrlExpParams(feature_set="turbo", useMstFeats=False, useCarerrasFeats=False, useCoarseTags=False)
        g.turbo_coarse_feats = g.turbo_feats + SrlExpParams(feature_set="turbo-coarse", useCoarseTags=True)
        g.mst_car_feats = SrlExpParams(feature_set="mst-car", useMstFeats=True, useCarerrasFeats=True, useCoarseTags=True)
        g.basic_car_feats = SrlExpParams(feature_set="basic-car", useMstFeats=True, useCarerrasFeats=True, useCoarseTags=True, basicOnly=True)
        
        g.defaults += g.mst_car_feats
        
        # Language specific parameters
        p.cx_langs_with_phead = ["bg", "en", "de", "es"]

        # This is a map from language to number of sentences.
        l2var_map = {"ar" : 1500, "zh" : 57000, "cs" : 72700, "da" : 5200, "nl" : 13300,
                     "de" : 39200, "ja" : 17000, "pt" : 9100, "sl" : 1500, "es" : 3300,
                     "sv" : 11000, "tr" : 5000, "bg" : 12800, "en" : 40000, "en-st" : 40000}

        for lang_short in p.cx_lang_short_names:
            gl = g.langs[lang_short]
            pl = p.langs[lang_short]
            gl.cx_data = SrlExpParams(train=pl.cx_train, trainType="CONLL_X", devType="CONLL_X",
                                      test=pl.cx_test, testType="CONLL_X", datasource="CoNLL-X",
                                      language=lang_short, l2variance=l2var_map[lang_short],
                                      prune_model_path=os.path.join(models_dir, "1st_cx_"+lang_short, "model.binary.gz"))      
            if lang_short.startswith("en"):
                gl.cx_data += SrlExpParams(dev=pl.cx_dev, reduceTags=p.tag_map_en_ptb,
                                           dpSkipPunctuation=True, useGoldSyntax=False,
                                           useMorphologicalFeats=False, useLemmaFeats=False)
            else:
                gl.cx_data += SrlExpParams(propTrainAsDev=0.10, reduceTags=p.cx_tag_maps[lang_short]) 
                
        # This is a map from language to number of sentences.
        # ["ar", "eu", "ca", "zh", "cs", "en", "el", "hu", "it", "tr"]
        c07_l2var_map = {"ar" : 2900, "eu" : 3200, "ca" : 15000, "zh" : 57000, "cs" : 25400, 
                         "en" : 18600, "el" : 2700, "hu" : 6000, "it" : 3100, "tr" : 5600}
        for lang_short in p.c07_lang_short_names:
            gl = g.langs[lang_short]
            pl = p.langs[lang_short]
            gl.c07_data = SrlExpParams(train=pl.c07_train, trainType="CONLL_X", devType="CONLL_X",
                                      test=pl.c07_test, testType="CONLL_X", datasource="CoNLL-2007",
                                      propTrainAsDev=0.10,
                                      language=lang_short, l2variance=c07_l2var_map[lang_short],
                                      reduceTags=p.c07_tag_maps[lang_short],
                                      prune_model_path=os.path.join(models_dir, "1st_c07_"+lang_short, "model.binary.gz"))
                    
        # Ordered from smallest to largest.
        cx_lang_subset = p.cx_lang_short_names #["tr", "sl", "ja", "da", "nl", "bg", "sv", "es", "en", "en-st"] 
        c07_lang_subset = p.c07_lang_short_names #["eu", "zh", "el", "hu", "it"] 
        
        # ------------------------ EXPERIMENTS --------------------------
                
        if self.expname is None:
            raise Exception("expname must be specified")
        
        elif self.expname == "dp-opt":
            # Examines optimizer / annotator speed up with different numbers of threads / batch sizes.
            exps = []
            g.defaults += g.cll
            g.defaults.update(trainMaxSentenceLength=20, devMaxSentenceLength=20, 
                              testMaxSentenceLength=20, work_mem_megs=5000,
                              regularizer="NONE", l2variance=16000)
            g.defaults.remove("printModel")
            g.defaults.remove("modelOut")
            g.defaults.set_incl_name("threads", True)
            lang_short = "en"
            gl = g.langs[lang_short]
            for optimizer in [g.adagrad_comid]:
                for sgdEarlyStopping in [True]:
                    for threads in [1, 2, 7, 15]:
                        for sgdBatchSizeScale in [1, 2, 4, 8]:
                            exp = g.defaults + gl.cx_data + g.first_order + optimizer
                            exp.update(sgdEarlyStopping=sgdEarlyStopping, threads=threads, 
                                       sgdBatchSize=threads*sgdBatchSizeScale)
                            exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-logadd":
            # Checks whether using a logAddTable adversely affects accuracy.
            exps = []
            g.defaults += g.cll
            g.defaults.update(trainMaxSentenceLength=20, devMaxSentenceLength=20, 
                              testMaxSentenceLength=20, work_mem_megs=5000,
                              l2variance=16000, sgdBatchSize=1, threads=1)
            g.defaults.remove("printModel")
            g.defaults.remove("modelOut")
            lang_short = "en"
            gl = g.langs[lang_short]
            for algebra in ["LOG", "LOG_SIGN", "REAL"]:
                for useLogAddTable in [True, False]:
                    exp = g.defaults + gl.cx_data + g.first_order 
                    exp.update(useLogAddTable=useLogAddTable, algebra=algebra)
                    exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-opt-avg":
            # Compares learning with and without parameter averaging.
            exps = []
            g.defaults += g.cll
            g.defaults.update(trainMaxSentenceLength=20, devMaxSentenceLength=20, 
                              testMaxSentenceLength=20, work_mem_megs=5000)
            g.defaults.remove("printModel")
            g.defaults.remove("modelOut")
            lang_short = "en"
            gl = g.langs[lang_short]
            for optimizer in [g.asgd, g.fobos, g.sgd, g.adagrad]:
                for sgdAveraging in [True, False]:
                    for regularizer in ["NONE", "L2"]:
                        exp = g.defaults + gl.cx_data + g.first_order + optimizer
                        exp.update(sgdAveraging=sgdAveraging, regularizer=regularizer)
                        exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-init-model":
            # Compares training of an ERMA 2nd order model, initializing the model
            # to either zeroes or to the 1st order pruning model.
            exps = []
            g.defaults += g.erma_dp
            g.defaults.update(bpMaxIterations=10, trainMaxSentenceLength=20, devMaxSentenceLength=20, 
                              testMaxSentenceLength=20, work_mem_megs=5000, regularizer="NONE")
            g.defaults.remove("printModel")
            lang_short = "en"
            gl = g.langs[lang_short]
            
            # Train a first-order pruning model.
            prune_exp = g.defaults + gl.cx_data + g.first_order
            exps.append(prune_exp)
            pruneModel = StagePath(prune_exp, "model.binary.gz")     
            # Train the pruned (e.g. second-order) models.
            g.defaults.remove("modelOut") # Speedup.
            exp1 = g.defaults + gl.cx_data + g.second_order + SrlExpParams(pruneModel=pruneModel, modelIn=pruneModel, group="init1st")
            exp0 = g.defaults + gl.cx_data + g.second_order + SrlExpParams(pruneModel=pruneModel, group="init0")
            exp1.add_prereq(prune_exp)
            exp0.add_prereq(prune_exp)
            exps.append(exp1)
            exps.append(exp0)
            
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-cll":
            '''Conditional log-likelihood training of 1st and 2nd-order models for parsing.
                Includes hyperparameter tuning.'''
            exps = []
            languages = ["en"]
            
            g.defaults += g.cll
            # Speedups
            g.defaults.update(trainMaxSentenceLength=20,
                              sgdNumPasses=10,
                              work_mem_megs=5.*1000,
                              bpMaxIterations=10)
            g.defaults.remove("printModel")
            
            # Train a first-order pruning model for each language
            prune_exps = {}
            for lang_short in languages:
                gl = g.langs[lang_short]
                data = gl.cx_data
                data.update(propTrainAsDev=0)
                exp = g.defaults + data + g.first_order
                prune_exps[lang_short] = exp
                exps.append(exp)
                        
            # Train the pruned (e.g. second-order) models.
            g.defaults.remove("modelOut") # Speedup.
            for lang_short in languages:
                gl = g.langs[lang_short]
                for parser in pruned_parsers([g.first_order, g.second_order]):
                    data = gl.cx_data
                    data.update(pruneModel=StagePath(prune_exps[lang_short], "model.binary.gz"))             
                    exp = g.defaults + data + parser
                    exp.add_prereq(prune_exps[lang_short])
                    exps.append(exp)

            return self._get_pipeline_from_exps(exps)
            
        elif self.expname == "dp-aware-tune":
            '''Tuning parameters of the DP_DECODE_LOSS function.'''
            root = RootStage()
            
            # Get the datasets.
            datasets = []
            for lang_short in ["tr", "sl"]:
                gl = g.langs[lang_short]
                datasets.append(gl.cx_data)
                
            # Train the second order models.
            for data in datasets:
                for trainer in [g.erma_mse]: #, #g.cll]:
                    for parser in pruned_parsers([g.first_order]): #, g.second_grand_asib]):
                        if parser.get("tagger_parser").startswith("1st"):
                            bpMaxIterations = 1
                        else:
                            bpMaxIterations = 4
                        data.update(pruneModel=data.get("prune_model_path"),
                                    propTrainAsDev=0.1) # DEV DATA.
                        data.remove("test") # NO TEST DATA, we're just tuning.
                        exp = g.defaults + data + parser + trainer + SrlExpParams(bpMaxIterations=bpMaxIterations)
                        exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                        exp.add_prereq(root)
                        if trainer != g.cll:
                            # TUNE PARAMETERS HERE:
                            pairs = []
                            for s in [0.1, 0.01, 0.001]:
                                for e in [0.1, 0.01, 0.001, 0.0001, 0.00001]:
                                    if s >= e:
                                        pairs.append((s,e))
                            for dpStartTemp, dpEndTemp in pairs:
                                for dpUseLogScale in [True, False]:
                                    exp2 = g.defaults + data + parser + g.erma_dp_nomse + SrlExpParams(bpMaxIterations=bpMaxIterations)
                                    exp2.update(dpStartTemp=dpStartTemp, dpEndTemp=dpEndTemp, dpUseLogScale=dpUseLogScale)
                                    exp2.update(modelIn=StagePath(exp, "model.binary.gz"))
                                    exp2 += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp2))
                                    exp2.add_prereq(exp)
                                    exp2.remove("modelOut") # Speedup.
                        else:
                            exp.remove("modelOut") # Speedup.
                            
            if self.fast: root.dependents[0].dependents = root.dependents[0].dependents[:2]
            scrape = ScrapeSrl(csv_file="results.csv", tsv_file="results.data")
            scrape.add_prereqs(pipeline.dfs_stages(root))
            return root
        
        elif self.expname == "dp-aware-langs":
            '''Comparison of CLL and ERMA training with varying models and iterations.'''
            root = RootStage()

            # Get the datasets.
            datasets = []
            for lang_short in cx_lang_subset:
                if lang_short == "en": continue          # SKIP PTB-YM since we already have the results.
                gl = g.langs[lang_short]
                datasets.append(gl.cx_data)
            for lang_short in c07_lang_subset:
                gl = g.langs[lang_short]
                datasets.append(gl.c07_data)

            for data in datasets:
                data.update(pruneModel=data.get("prune_model_path"),
                            propTrainAsDev=0.1) # USING DEV DATA.

            # Train the second order models.
            for data in datasets:
                for trainer in [g.erma_mse, g.cll]:
                    for parser in pruned_parsers([g.first_order, g.second_grand_asib]):
                        for bpMaxIterations in [1, 2, 4, 8]:
                            # if parser.get("tagger_parser").startswith("1st"):
                            #     bpMaxIterations = 1
                            # else:
                            #     bpMaxIterations = 4
                            if parser.get("inference") == "DP" and (trainer != g.cll or bpMaxIterations != 1):
                                continue
                            if parser.get("tagger_parser").startswith("1st") and bpMaxIterations != 1:
                                continue
                            exp = g.defaults + data + parser + trainer + SrlExpParams(bpMaxIterations=bpMaxIterations)
                            exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                            exp.add_prereq(root)

                            exp2 = g.defaults + data + parser + g.erma_dp_nomse + SrlExpParams(bpMaxIterations=bpMaxIterations)
                            exp2.update(modelIn=StagePath(exp, "model.binary.gz"))
                            exp2 += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp2))
                            exp2.add_prereq(exp)
                            exp2.remove("modelOut") # Speedup.
                            if trainer == g.cll: exp2.update(group="initCLL")
                            else: exp2.update(group="initMSE")
                            
            if self.fast: root.dependents[0].dependents = root.dependents[0].dependents[:2]
            scrape = ScrapeSrl(csv_file="results.csv", tsv_file="results.data")
            scrape.add_prereqs(pipeline.dfs_stages(root))
            return root
        
        elif self.expname == "dp-aware-en":
            '''Comparison of CLL and ERMA training with varying models and iterations.'''
            root = RootStage()
            
            # Get the datasets.
            datasets = []
            for lang_short in ["en"]:
                gl = g.langs[lang_short]
                datasets.append(gl.cx_data)
                ## Trying the pruning model from develop branch.
                #models_dir = os.path.join(self.root_dir, "exp", "models", "dp-pruning-workaround")
                #gl.cx_data.update(prune_model_path=os.path.join(models_dir, "1st_cx_"+lang_short, "model.binary.gz"))
            for data in datasets:
                data.update(pruneModel=data.get("prune_model_path"),
                            propTrainAsDev=0.0)  # TODO: Set to zero for final experiments.

            # Train the second order models.
            for data in datasets:
                for bpMaxIterations in [1, 2, 3, 4, 5, 6, 7, 8]:
                    for trainer in [g.erma_mse, g.cll]:
                        for parser in g.pruned_parsers:
                            if parser.get("inference") == "DP" and (trainer != g.cll or bpMaxIterations != 1):
                                continue
                            if parser.get("tagger_parser").startswith("1st") and bpMaxIterations != 1:
                                continue
                            exp = g.defaults + data + parser + trainer + SrlExpParams(bpMaxIterations=bpMaxIterations)
                            exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                            exp.add_prereq(root)
                            
                            exp2 = g.defaults + data + parser + g.erma_dp_nomse + SrlExpParams(bpMaxIterations=bpMaxIterations)
                            exp2.update(modelIn=StagePath(exp, "model.binary.gz"))
                            exp2 += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp2))
                            exp2.add_prereq(exp)
                            exp2.remove("modelOut") # Speedup.
                            if trainer == g.cll: exp2.update(group="initCLL")
                            else: exp2.update(group="initMSE")

                            #if parser in [g.second_order, g.second_grand, g.second_asib]:
                            #    get_oome_stages(exp) # These are auto-added as dependents.
                            
            if self.fast: root.dependents[0].dependents = root.dependents[0].dependents[:2]
            scrape = ScrapeSrl(csv_file="results.csv", tsv_file="results.data")
            scrape.add_prereqs(pipeline.dfs_stages(root))
            return root
            
        elif self.expname == "dp-aware":
            '''Comparison of CLL and ERMA training with varying models and iterations.'''
            root = RootStage()
            languages = ["en"] #["es", "bg", "en"]

            # Train a first-order pruning model for each language
            prune_exps = {}
            for lang_short in languages:
                gl = g.langs[lang_short]
                pl = p.langs[lang_short]
                data = gl.cx_data
                data.update(propTrainAsDev=0) # TODO: Set to zero for final experiments.
                exp = g.defaults + data + g.first_order + g.basic_car_feats
                exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                prune_exps[lang_short] = exp
                root.add_dependent(exp)
                        
            # Train the second order models.
            for lang_short in languages:
                for bpMaxIterations in [1, 2, 3, 4]:
                    for trainer in [g.erma_mse, g.cll]:
                        gl = g.langs[lang_short]
                        pl = p.langs[lang_short]
                        for parser in g.pruned_parsers:
                            if parser.get("inference") == "DP" and (trainer != g.cll or bpMaxIterations != 1):
                                continue
                            if parser.get("tagger_parser").startswith("1st") and bpMaxIterations != 1:
                                continue
                            data = gl.cx_data
                            data.update(pruneModel=StagePath(prune_exps[lang_short], "model.binary.gz"),
                                        propTrainAsDev=0.0)  # TODO: Set to zero for final experiments.
                            exp = g.defaults + data + parser + trainer + SrlExpParams(bpMaxIterations=bpMaxIterations)
                            exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                            exp.add_prereq(prune_exps[lang_short])
                            if parser in [g.second_order, g.second_grand, g.second_asib]:
                                get_oome_stages(exp) # These are auto-added as dependents.
                            if trainer != g.cll:
                                if parser in [g.second_order, g.second_grand, g.second_asib]:
                                    raise Exception("Unable to specify which experiment directory will contain the model.")
                                exp2 = g.defaults + data + parser + g.erma_dp_nomse + SrlExpParams(bpMaxIterations=bpMaxIterations)
                                exp2.update(modelIn=StagePath(exp, "model.binary.gz"))
                                exp2 += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp2))
                                exp2.add_prereq(prune_exps[lang_short])
                                exp2.add_prereq(exp)
                                exp2.remove("modelOut") # Speedup.
                            else:
                                exp.remove("modelOut") # Speedup.
                            
            if self.fast: root.dependents[0].dependents = root.dependents[0].dependents[:2]
            scrape = ScrapeSrl(csv_file="results.csv", tsv_file="results.data")
            scrape.add_prereqs(pipeline.dfs_stages(root))
            return root
                
        elif self.expname == "dp-aware-small":
            '''Comparison of CLL and ERMA training with varying models and iterations.
                Here we use a small dataset and no pruning.'''
            exps = []
            overrides = SrlExpParams(trainMaxNumSentences=1000,
                              trainMaxSentenceLength=10,
                              pruneByDist=False,
                              pruneByModel=False,
                              propTrainAsDev=0.5,
                              bpUpdateOrder="SEQUENTIAL", 
                              bpSchedule="TREE_LIKE",
                              useMseForValue=True,
                              featureHashMod=10000)
            for l2variance in [500, 1000, 5000, 10000, 50000, 100000]:
                for trainer in [g.erma_mse, g.cll]:
                    for bpMaxIterations in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]:
                        for lang_short in ['en']: #["bg", "es", "en"]:
                            gl = g.langs[lang_short]
                            pl = p.langs[lang_short]
                            for parser in [g.first_order, g.second_order, g.second_asib, g.second_grand]:
                                data = gl.cx_data
                                data.remove("test")
                                data.remove("testType")
                                data.remove("dev")
                                exp = g.defaults + data + parser + trainer + overrides 
                                exp.update(bpMaxIterations=bpMaxIterations,
                                           l2variance=l2variance)
                                exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                                exps.append(exp)
            return self._get_pipeline_from_exps(exps)
         
        elif self.expname == "dp-erma":
            '''Comparison of CLL and ERMA training with varying models and iterations.'''
            exps = []
            g.defaults += g.erma_mse #TODO: Change to DEP_PARSE_DECODE_LOSS
            g.defaults.set_incl_name("l2variance", False)
            # Train a first-order pruning model for each language
            prune_exps = {}
            languages = p.cx_lang_short_names
            for lang_short in languages:
                # Include the full first order model, just for comparison with prior work.
                for feats in [g.feat_mcdonald_basic, g.feat_mcdonald]:
                    gl = g.langs[lang_short]
                    pl = p.langs[lang_short]
                    data = gl.cx_data
                    data.update(propTrainAsDev=0,
                                trainUseCoNLLXPhead=False) # TODO: Set to zero for final experiments.
                    exp = g.defaults + data + g.first_order + feats
                    exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                    prune_exps[lang_short] = exp
                    exps.append(exp)
            # Train the other models for each language
            parser = g.second_order 
            parser += SrlExpParams(pruneByModel=True,
                                   tagger_parser=g.second_order.get("tagger_parser")+"-pr")
            for lang_short in languages:
                gl = g.langs[lang_short]
                pl = p.langs[lang_short]
                data = gl.cx_data
                data.update(pruneModel=StagePath(prune_exps[lang_short], "model.binary.gz"),
                            propTrainAsDev=0,
                            trainUseCoNLLXPhead=False)  # TODO: Set to zero for final experiments.
                exp = g.defaults + data + parser
                exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                if parser in [g.second_order, g.second_grand, g.second_asib]:
                    exps += get_oome_stages(exp)
                else:
                    exps.append(exp)
                exp.add_prereq(prune_exps[lang_short])
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-erma-tune":
            '''Tuning optimization for gradients computed by ERMA.'''
            exps = []
            #g.defaults.update(trainMaxNumSentences=)
            g.defaults.set_incl_name("l2variance", False)
            # With a short number of sentences prune_byDist is causing trouble.
            g.defaults.update(pruneByDist=False) # TODO: Consider changing this.
            # Train a first-order pruning model for each language
            languages = p.cx_lang_short_names
            for trainMaxNumSentences in [100, 1000, 10000]:
                for optimizer in [g.sgd, g.adagrad, g.lbfgs]:
                    for trainer in [g.erma_dp, g.erma_mse, g.cll, g.erma_er]:
                        for lang_short in ["bg"]:
                            gl = g.langs[lang_short]
                            pl = p.langs[lang_short]
                            data = gl.cx_data
                            data.update(trainMaxNumSentences=trainMaxNumSentences)
                            exp = g.defaults + data + g.first_order + g.feat_mcdonald_basic + optimizer + trainer
                            exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))                
                            exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-en":
            '''English-only experiments with first order parsers.'''
            exps = []
            g.defaults += g.cll + g.first_order
            # Note: "ar" has a PHEAD column, but it includes multiple roots per sentence.
            for data in [g.langs['en'].c07_data, g.langs['en'].cx_data, g.langs['en-st'].cx_data]:
                for pruneByDist in [True, False]:
                    data.update(propTrainAsDev=0)  # TODO: Set to zero for final experiments.
                    exp = g.defaults + data
                    exp.update(pruneByDist=pruneByDist, work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                    exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-pruning":            
            '''Trains the pruning models for the CoNLL-X and CoNLL-2007 languages.'''
            exps = []
            g.defaults.update(featureHashMod=10000000) # Speedup
            
            datasets = []
            for lang_short in cx_lang_subset:
                gl = g.langs[lang_short]
                datasets.append(gl.cx_data)
            for lang_short in c07_lang_subset:
                gl = g.langs[lang_short]
                datasets.append(gl.c07_data)
                
            for feats in [g.basic_car_feats]: #, g.turbo_feats, g.mst_car_feats, g.turbo_coarse_feats]:                
                for data in datasets:
                    data.update(propTrainAsDev=0) # TODO: Set to zero for final experiments.
                    exp = g.defaults + data + g.first_order + g.cll + feats
                    exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                    if feats == g.basic_car_feats:
                        exp.update(modelOut=data.get("prune_model_path"))
                        d = os.path.dirname(data.get("prune_model_path"))
                        if not os.path.exists(d):
                            print "Making directory:",d
                            os.makedirs(d) 
                        if os.path.exists(exp.get("modelOut")):
                            # Don't retrain the pruning models if we already have them.
                            continue
                    exps.append(exp)
            return self._get_pipeline_from_exps(exps, 25)
        
        elif self.expname == "dp-conll07":
            '''CoNLL-2007 experiments.'''
            exps = []
            g.defaults += g.cll
            # Note: "ar" has a PHEAD column, but it includes multiple roots per sentence.
            for lang_short in p.c07_lang_short_names:
                gl = g.langs[lang_short]
                pl = p.langs[lang_short]
                for parser in g.unpruned_parsers:
                    data = gl.c07_data
                    data.update(propTrainAsDev=0)  # TODO: Set to zero for final experiments.
                    exp = g.defaults + data + parser
                    exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                    if parser in [g.second_order, g.second_grand, g.second_asib]:
                        exps += get_oome_stages(exp)
                    else:
                        exps.append(exp)
            exps = [x for x in exps if x.get("language") == "en"]
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-conllx":
            '''CoNLL-X experiments.'''
            exps = []
            g.defaults += g.cll
            # Note: "ar" has a PHEAD column, but it includes multiple roots per sentence.
            for lang_short in p.cx_lang_short_names:
                gl = g.langs[lang_short]
                pl = p.langs[lang_short]
                for parser in g.pruned_parsers:
                    data = gl.cx_data
                    data.update(pruneModel=data.get("prune_model_path"),
                                propTrainAsDev=0)  # TODO: Set to zero for final experiments.
                    exp = g.defaults + data + parser
                    exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                    if parser in [g.second_order, g.second_grand, g.second_asib]:
                        exps += get_oome_stages(exp)
                    else:
                        exps.append(exp)
            exps = [x for x in exps if x.get("language").startswith("en")]
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-conllx-tune":
            '''Tuning for CoNLL-X experiments.'''
            exps = []
            g.defaults.update(seed=123456789) # NOTE THE FIXED SEED
            for lang_short in ["es", "bg"]:
                gl = g.langs[lang_short]      
                pl = p.langs[lang_short]      
                for parser in [g.second_order, g.first_order]:
                    for adaGradEta in [ 0.05, 0.01, 0.1, 0.001, 1.0]:
                        for l2variance in [10000, 1000, 100000, 100,]:
                            for sgdNumPasses in [3, 5]:
                                hyper = SrlExpParams(sgdNumPasses=sgdNumPasses, adaGradEta=adaGradEta, 
                                                     l2variance=l2variance)
                                exp = g.defaults + gl.cx_data + parser + hyper
                                exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                                exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-conllx-tmp":
            '''Temporary CoNLL-X experiment setup (currently testing why we can't overfit train).'''
            exps = []
            g.defaults += g.feat_mcdonald #tpl_narad 
            g.defaults.update(sgdNumPasses=2, sgdAutoSelectLr=False)
            if not self.big_machine:
                g.defaults.update(maxEntriesInMemory=1, sgdBatchSize=2)
            for trainMaxNumSentences in [100, 500, 1000, 2000, 9999999]:
                for lang_short in ["bg"]: #, "es"]:
                    gl = g.langs[lang_short]
                    pl = p.langs[lang_short]
                    for parser in g.parsers:
                        data = gl.cx_data
                        data.update(l2variance=l2var_map[lang_short],
                                    pruneModel=gl.pruneModel,
                                    trainMaxNumSentences=trainMaxNumSentences)
                        data.remove("test")
                        exp = g.defaults + data + parser
                        exp += SrlExpParams(work_mem_megs=self.prm_defs.get_srl_work_mem_megs(exp))
                        exps.append(exp)
            return self._get_pipeline_from_exps(exps)
        
        elif self.expname == "dp-agiga2":
            '''Trains an English-only model for making predictions on for Annotated Gigaword 2.0'''
            root = RootStage()
            g.defaults += g.cll + g.first_order
            g.defaults.update(pruneByDist=True, work_mem_megs=self.prm_defs.get_srl_work_mem_megs(g.defaults))
            train = g.defaults + g.langs['en'].cx_data
            comm = glob(p.concrete380 + "/*")[0]
            comm_name = os.path.basename(comm)
            if self.fast: # Enable for quick local run.
                print "WARN: USING SETTINGS FOR A QUICK LOCAL RUN."
                train.update(pruneByDist=False, trainMaxNumSentences=3, devMaxNumSentences=3,
                             trainMaxSentenceLength=7, devMaxSentenceLength=7,  
                             featureHashMod=1000, sgdNumPasses=2)
            train.update(pipeOut="pipe.binary.gz")
            train.remove("test")
            train.remove("testType")
            train.remove("testPredOut") 
            #train.update(test=comm, testType="CONCRETE", group=comm_name, testPredOut=comm_name, evalTest=False)
            root.add_dependent(train)
            apr_defaults = AnnoPipelineRunner(pipeIn=StagePath(train, train.get("pipeOut")), predAts="DEP_TREE")        
            apr_defaults.set_incl_arg("group", False)
            apr_defaults.set_incl_name("pipeIn", False)
            apr_defaults.set_incl_name("test", False)
            apr_defaults.set_incl_name("testPredOut", False)
            for comm in glob(p.concrete380 + "/*"):
                comm_name = os.path.basename(comm)
                exp = apr_defaults + AnnoPipelineRunner(test=comm, testType="CONCRETE", group=comm_name, testPredOut=comm_name)
                train.add_dependent(exp)
            pl = p.langs['en']
            for c09 in [pl.pos_gold_train, pl.pos_gold_dev, pl.pos_gold_eval]:
                c09_name = os.path.basename(c09)
                exp = apr_defaults + AnnoPipelineRunner(test=c09, testType="CONLL_2009", group=c09_name, testPredOut=c09_name)
                if self.fast: exp.update(testMaxNumSentences=3, testMaxSentenceLength=7)
                train.add_dependent(exp)                
            return root
            
        elif self.expname == "gobble-memory":
            exps = []
            stage = GobbleMemory(megsToGobble = 300, work_mem_megs=100)
            stage.set_incl_arg("work_mem_megs", False)
            exps += get_oome_stages(stage)
            return self._get_pipeline_from_exps(exps)
        
        else:
            raise Exception("Unknown expname: " + str(self.expname))
    
    def _get_pipeline_from_exps(self, exps, num_for_fast=4):
        if self.fast and len(exps) > num_for_fast: exps = exps[:num_for_fast]
        root = RootStage()            
        root.add_dependents(exps)    
        scrape = ScrapeSrl(csv_file="results.csv", tsv_file="results.data")
        scrape.add_prereqs(root.dependents)
        return root
    
    def update_stages_for_qsub(self, root_stage):
        ''' Makes sure that the stage object specifies reasonable values for the 
            qsub parameters given its experimental parameters.
        '''
        for stage in self.get_stages_as_list(root_stage):
            # First make sure that the "fast" setting is actually fast.
            if isinstance(stage, SrlExpParams) and self.fast:
                self.make_stage_fast(stage)
            if isinstance(stage, SrlExpParams) and not self.big_machine and not self.dry_run:
                stage.update(work_mem_megs=1100, threads=1) 
            if isinstance(stage, experiment_runner.ExpParams):
                self.update_qsub_fields(stage)
            if self.hprof:
                if isinstance(stage, experiment_runner.JavaExpParams):
                    stage.hprof = self.hprof
        return root_stage
    
    def update_qsub_fields(self, stage):
        # Update the thread count
        threads = stage.get("threads")
        if threads != None: 
            # Add an extra thread just as a precaution.
            stage.threads = threads + 1
        work_mem_megs = stage.get("work_mem_megs")
        if work_mem_megs != None:
            stage.work_mem_megs = work_mem_megs
        # Update the runtime
        timeoutSeconds = stage.get("timeoutSeconds")
        if timeoutSeconds != None:
            stage.minutes = (timeoutSeconds / 60.0)
            # Add some extra time in case some other part of the experiment
            # (e.g. evaluation) takes excessively long.
            stage.minutes = (stage.minutes * 2.0) + 10
    
    def make_stage_fast(self, stage):       
        ''' Makes the stage run in a very short period of time (under 5 seconds).
        ''' 
        stage.update(maxLbfgsIterations=3,
                     trainMaxSentenceLength=7,
                     trainMaxNumSentences=3,
                     devMaxSentenceLength=11,
                     devMaxNumSentences=3,
                     testMaxSentenceLength=7,
                     testMaxNumSentences=3,
                     work_mem_megs=2000,
                     timeoutSeconds=20)
        if (stage.get("featureHashMod") > 1):
            stage.update(featureHashMod=1000)

        # Uncomment next line for multiple threads on a fast run: 
        # stage.update(threads=2)

if __name__ == "__main__":
    usage = "%prog "

    parser = OptionParser(usage=usage)
    parser.add_option('-q', '--queue', help="Which SGE queue to use")
    parser.add_option('-f', '--fast', action="store_true", help="Run a fast version")
    parser.add_option('-e', '--expname',  help="Experiment name. [" + ", ".join(SrlExpParamsRunner.known_exps) + "]")
    parser.add_option(      '--hprof',  help="What type of profiling to use [cpu, heap]")
    parser.add_option('-n', '--dry_run',  action="store_true", help="Whether to just do a dry run.")
    (options, args) = parser.parse_args(sys.argv)

    if len(args) != 1:
        parser.print_help()
        sys.exit(1)
    
    runner = SrlExpParamsRunner(options)
    root_stage = runner.get_experiments()
    root_stage = runner.update_stages_for_qsub(root_stage)
    runner.run_pipeline(root_stage)


