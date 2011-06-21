#!/usr/bin/python

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
from experiments.core.pipeline import ExperimentRunner
from experiments.core.util import get_new_file, sweep_mult, fancify_cmd
from experiments.core.util import head_sentences
import platform
from glob import glob
from experiments.core.experiment_runner import ExpParamsRunner
from experiments.core import experiment_runner

def get_root_dir():
    scripts_dir =  os.path.abspath(sys.path[0])
    root_dir =  os.path.dirname(os.path.dirname(scripts_dir))
    print "Using root_dir: " + root_dir
    return root_dir;

def get_dev_data(data_dir, file_prefix, name=None):
    return get_some_data(data_dir, file_prefix, name, "dev")

def get_test_data(data_dir, file_prefix, name=None):
    return get_some_data(data_dir, file_prefix, name, "test")

def get_some_data(data_dir, file_prefix, name, test_suffix): 
    data = DPExpParams()
    if name == None:
        name = file_prefix.replace("/", "-")
    data.set("dataset", name,True,False)
    data.set("train","%s/%s" % (data_dir, file_prefix),False,True)
    #data.set("dev","%s/%s.%s" % (data_dir, file_prefix, test_suffix),False,True)
    return data

class DPExpParams(experiment_runner.JavaExpParams):
    
    def __init__(self, **keywords):
        experiment_runner.ExpParams.__init__(self,keywords)
        
    def get_name_key_order(self):
        key_order = []
        initial_keys = self.get_initial_keys()
        all_keys = sorted(self.params.keys())
        for key in initial_keys:
            if key in all_keys:
                key_order.append(key)
        for key in all_keys:
            if key not in initial_keys:
                key_order.append(key)
        return key_order
    
    def get_initial_keys(self):
        return "dataSet model k s".split()
    
    def get_instance(self):
        return DPExpParams()
    
    def create_experiment_script(self, exp_dir, eprunner):
        script = ""
        script += "export CLASSPATH=%s/classes:%s/lib/*\n" % (eprunner.root_dir, eprunner.root_dir)
        cmd = "java -cp $CLASSPATH " + self.get_java_args(eprunner) + " edu.jhu.hltcoe.PipelineRunner  %s \n" % (self.get_args())
        script += fancify_cmd(cmd)
        return script
    
    def get_java_args(self, eprunner):
        # Allot the available memory to java and the ILP solver
        java_mem = eprunner.work_mem_megs * 0.5
        ilp_mem = eprunner.work_mem_megs - java_mem
        # Subtract off some overhead for CPLEX
        ilp_mem -= 128
        self.update(ilpWorkMemMegs=ilp_mem)
        
        # Create the JVM args
        java_args = self._get_java_args(java_mem)  
        if self.get("ilpSolver") == "cplex":  
            mac_jlp = "/Users/mgormley/installed/ILOG/CPLEX_Studio_AcademicResearch122/cplex/bin/x86-64_darwin9_gcc4.0"
            coe_jlp = "/home/hltcoe/mgormley/installed/ILOG/CPLEX_Studio_AcademicResearch122/cplex/bin/x86-64_sles10_4.1"
            if os.path.exists(mac_jlp): jlp = mac_jlp
            elif os.path.exists(coe_jlp): jlp = coe_jlp
            else: raise Exception("Could not find java.library.path for CPLEX")
            java_args += " -Djava.library.path=%s " % (jlp)
        return java_args

class DepParseExpParamsRunner(ExpParamsRunner):
    
    def __init__(self, options):
        ExpParamsRunner.__init__(self, "dp", options.queue)
        self.root_dir = os.path.abspath(get_root_dir())
        self.fast = options.fast
        self.expname = options.expname
        if options.test:
            self.get_data = get_test_data
        else:
            self.get_data = get_dev_data
            
    def get_experiments(self):
        all = DPExpParams()
        all.set("expname", self.expname, False, False)
        all.update(threads=self.threads)
        all.update(formulation="deptree-flow-nonproj",
                   parser="ilp-corpus",
                   model="dmv",
                   algorithm="viterbi")
        all.update(ilpSolver="cplex")
        
        ilpCorpus = DPExpParams(parser="ilp-corpus")
        ilpSentence = DPExpParams(parser="ilp-sentence")
        ilpDeltas = DPExpParams(parser="ilp-deltas")
        
        dgFixedInterval = DPExpParams(deltaGenerator="fixed-interval",interval=0.01,numPerSide=2)
        dgFactor = DPExpParams(deltaGenerator="factor",factor=1.1,numPerSide=2)
        
        if self.fast:       all.update(iterations=1,
                                       maxSentenceLength=7,
                                       maxNumSentences=2)
        else:               all.update(iterations=10)
        
        # Data sets
        data_dir = os.path.join(self.root_dir, "data")
        wsj_00 = self.get_data(data_dir, "treebank_3/wsj/00")
        wsj_full = self.get_data(data_dir, "treebank_3/wsj")
            
        if self.fast:       datasets = [wsj_00]
        else:               datasets = [wsj_full]
        
        experiments = []
        if self.expname == "formulations":
            for dataset in datasets:
                formulations = ["deptree-dp-proj", "deptree-explicit-proj", "deptree-flow-nonproj", "deptree-flow-proj", "deptree-multiflow-nonproj", "deptree-multiflow-proj" ]
                for formulation in formulations:
                    ilpform = DPExpParams(formulation=formulation)
                    experiments.append(all + dataset + ilpform)
        elif self.expname == "corpus-size":
            if not self.fast:
                all.update(iterations=1)
            for parser in ["ilp-corpus","ilp-sentence"]:
                par = DPExpParams(parser=parser)
                for dataset in datasets:
                    for maxSentenceLength in [7,10,20]:
                        msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                        # Limiting to max  7 words/sent there are 2394 sentences
                        # Limiting to max 10 words/sent there are 5040 sentences
                        # Limiting to max 20 words/sent there are 20570 sentences
                        if (maxSentenceLength == 7):
                            mns_list = range(200,2200,200)
                        elif (maxSentenceLength == 10):
                            mns_list = range(100,5040,100)
                        else: # for 20
                            mns_list = range(1000,20570,1000)
                        for maxNumSentences in mns_list:
                            mns = DPExpParams(maxNumSentences=maxNumSentences)
                            experiments.append(all + dataset + msl + par + mns)
        elif self.expname == "deltas":
            if not self.fast:
                all.update(iterations=1)
            for dataset in datasets:
                msl = DPExpParams(maxSentenceLength=5)
                mns_list = range(10,100,10)
                for maxNumSentences in mns_list:
                    mns = DPExpParams(maxNumSentences=maxNumSentences)
                    experiments.append(all + dataset + msl + mns + DPExpParams(parser="ilp-corpus"))
                    for dataGen in [dgFixedInterval, dgFactor]:
                        experiments.append(all + dataset + msl + mns + DPExpParams(parser="ilp-deltas") + dataGen)
        else:
            raise Exception("Unknown expname: " + self.expname)
                
        return experiments

    def create_post_processing_script(self, top_dir, exp_tuples):
        #return "python %s/scripts/experiments/pp_scrape_expout.py %s" % (self.tagging_dir, top_dir)
        return None;

  
if __name__ == "__main__":
    usage = "%s " % (sys.argv[0])

    parser = OptionParser(usage=usage)
    parser.add_option('-q', '--queue', action="store_true", help="Which SGE queue to use")
    parser.add_option('-f', '--fast', action="store_true", help="Run a fast version")
    parser.add_option('--test', action="store_true", help="Use test data")
    parser.add_option('--expname',  help="Experiment name")
    (options, args) = parser.parse_args(sys.argv)

    if len(args) != 1:
        print usage
        sys.exit(1)
    
    runner = DepParseExpParamsRunner(options)
    experiments = runner.get_experiments()
    runner.run_experiments(experiments)

