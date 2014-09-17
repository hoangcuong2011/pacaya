package edu.jhu.nlp.features;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.CorpusStatistics.CorpusStatisticsPrm;
import edu.jhu.nlp.data.conll.CoNLL09FileReader;
import edu.jhu.nlp.data.conll.CoNLL09Sentence;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.features.TemplateLanguage.FeatTemplate;
import edu.jhu.nlp.tag.BrownClusterTagger;
import edu.jhu.nlp.tag.BrownClusterTagger.BrownClusterTaggerPrm;
import edu.jhu.prim.tuple.Pair;
import edu.jhu.prim.tuple.Triple;
import edu.jhu.prim.tuple.Tuple;
import edu.jhu.prim.util.math.FastMath;
import edu.jhu.util.FeatureNames;
import edu.jhu.util.Timer;
import edu.jhu.util.hash.MurmurHash3;

/**
 * Class for comparing methods of representing features. Allows for testing of
 * OOM errors, speed up extraction/hashing/lookup.
 * 
 * @author mgormley
 */
public class FeatureCreationSpeedTest {

    //@Test
    public void testSpeedOfFeatureCreation() throws UnsupportedEncodingException, FileNotFoundException {
        // Params
        final int numExamples = 50000;
        final int numTemplates = 1;
        final int numRounds = 2;

        //InputStream inputStream = this.getClass().getResourceAsStream(CoNLL09ReadWriteTest.conll2009Example);
        InputStream inputStream = new FileInputStream("./data/conll2009/LDC2012T04/data/CoNLL2009-ST-English/CoNLL2009-ST-English-train.txt");
        CoNLL09FileReader cr = new CoNLL09FileReader(inputStream);
        List<CoNLL09Sentence> conllSents = cr.readSents(numExamples);
        for (CoNLL09Sentence s : conllSents) {
            s.intern();
        }
        AnnoSentenceCollection sents = CoNLL09Sentence.toAnno(conllSents, false);
        
        // Run
        System.out.println("Num sents: " + sents.size());
        //testFeatExtract(numTemplates, sents, new PairExtractor());
        //testFeatExtract(numTemplates, sents, new TupleExtractor());
        //testFeatExtract(numTemplates, sents, new StringExtractor());
        
        testFeatExtract(numRounds, numTemplates, sents, 3, false);
        
//        testFeatExtract(numRounds, numTemplates, sents, 0, true);
//        testFeatExtract(numRounds, numTemplates, sents, 1, true);
//        testFeatExtract(numRounds, numTemplates, sents, 2, true);
    }
    
    private void testFeatExtract(int numRounds, final int numTemplates, AnnoSentenceCollection sents, final int opt, final boolean lookup) {
        Timer timer = new Timer();
        Timer lookupTimer = new Timer();
        Timer hashTimer = new Timer();
        Timer extTimer = new Timer();
        long hashSum = 0;
        
        int numPairs = 0;
        for (int round = 0; round<numRounds; round++) {
            numPairs = 0;
            timer = new Timer();
            timer.start();
            lookupTimer = new Timer();
            hashTimer = new Timer();
            extTimer = new Timer();
            
            FeatureNames alphabet = new FeatureNames();
            try {
                for (int i=0; i<sents.size(); i++) {
                    AnnoSentence sent = sents.get(i);
                    Pair[] preds = null;
                    Pair[] args = null;
                    if (opt == 0) {
                        extTimer.start();
                        preds = new Pair[sents.size()];
                        args = new Pair[sents.size()];
                        for (int pa=0; pa<sent.size(); pa++) {
                            String pWord = sent.getWord(pa);
                            String aWord = sent.getWord(pa);
                            preds[pa] = new Pair("pred", pWord);
                            args[pa] = new Pair("arg", pWord);
                        }
                        extTimer.stop();
                    }
                    
                    for (int pred=0; pred<sent.size(); pred++) {
                        for (int arg=0; arg<sent.size(); arg++) {
                            numPairs++;
                            for (int t=0; t<numTemplates; t++) {
                                String pWord = sent.getWord(pred);
                                String aWord = sent.getWord(arg);
                                
                                extTimer.start();
                                Object featName;
                                if (opt == 0) {
                                    featName = new Triple(t, preds[pred], args[arg]);
                                } else if (opt == 1) {
                                    featName = new Tuple(t,"pred", pWord, "arg", aWord);
                                } else if (opt == 2) {
                                    featName = t + "_pred_" + pWord + "_arg_" + aWord;
                                } else if (opt == 3) {
                                    String data = t + "_pred_" + pWord + "_arg_" + aWord;
                                    featName = FastMath.mod(MurmurHash3.murmurhash3_x86_32(data, 0, data.length(), 123456789), 200000);
                                } else {
                                    featName = null;
                                }
                                //hashSum += featName.hashCode();
                                extTimer.stop();
                                
                                hashTimer.start();
                                hashSum += featName.hashCode();
                                hashTimer.stop();
                                
                                if (lookup) {
                                    lookupTimer.start();
                                    alphabet.lookupIndex(featName);
                                    lookupTimer.stop();
                                }
                            }
                        }
                    }
                    if (i % 1000 == 0) {
                        System.out.println("alphabet.size() = " + alphabet.size());
                    }
                }
                timer.stop();
                System.out.println("Num features: " + alphabet.size());
            } catch (OutOfMemoryError e) {
                System.out.println("Num features at OOM: " + alphabet.size());
                break;
            }
        }
        System.out.println("Num pairs: " + numPairs);
        System.out.println("Hash sum: " + hashSum);
        System.out.println("Time to extract: " + extTimer.totMs());
        System.out.println("Time to hash: " + hashTimer.totMs());
        System.out.println("Time to lookup: " + lookupTimer.totMs());
        System.out.println("Time total            : " + timer.totMs());
    }
    
    //@Test
    public void testSpeedOfFeatureCreation2() throws IOException {
        // Params
        final int numExamples = 10;
        final int numRounds = 1;

        //InputStream inputStream = this.getClass().getResourceAsStream(CoNLL09ReadWriteTest.conll2009Example);
        InputStream inputStream = new FileInputStream("./data/conll2009/LDC2012T04/data/CoNLL2009-ST-English/CoNLL2009-ST-English-train.txt");
        CoNLL09FileReader cr = new CoNLL09FileReader(inputStream);
        List<CoNLL09Sentence> conllSents = cr.readSents(numExamples);
        for (CoNLL09Sentence s : conllSents) {
            s.intern();
        }
        AnnoSentenceCollection sents = CoNLL09Sentence.toAnno(conllSents, false);
        
        // Add Brown clusters
        BrownClusterTagger bct = new BrownClusterTagger(new BrownClusterTaggerPrm());
        bct.read(new File("./data/bc_out_1000/full.txt_en_1000/bc/paths"));
        bct.annotate(sents);
        
        // Run
        System.out.println("Num sents: " + sents.size());
        
        //List<FeatTemplate> tpls = TemplateSets.getBjorkelundArgUnigramFeatureTemplates();
        //List<FeatTemplate> tpls = TemplateSets.getFromResource(TemplateSets.kooHybridDepFeatsResource);
        List<FeatTemplate> tpls = TemplateSets.getCoarseUnigramSet1();
        System.out.println("Num tpls: " + tpls.size());

        testFeatExtract2(numRounds, tpls, sents, "en", 3, true);
        System.exit(0);
        
        for (int t=0; t<tpls.size(); t++) {
            System.out.println(tpls.get(t));
            testFeatExtract2(numRounds, tpls.subList(t, t+1), sents, "en", 3, true);
        }
    }
    
    private void testFeatExtract2(int numRounds, List<FeatTemplate> tpls, AnnoSentenceCollection sents, String language, final int opt, final boolean lookup) {
        Timer timer = new Timer();
        Timer lookupTimer = new Timer();
        Timer hashTimer = new Timer();
        Timer extTimer = new Timer();
        long hashSum = 0;
        
        CorpusStatisticsPrm prm = new CorpusStatisticsPrm();
        prm.language = language;
        CorpusStatistics cs = new CorpusStatistics(prm);

        int numPairs = 0;
        for (int round = 0; round<numRounds; round++) {
            numPairs = 0;
            timer = new Timer();
            timer.start();
            lookupTimer = new Timer();
            hashTimer = new Timer();
            extTimer = new Timer();
            
            FeatureNames alphabet = new FeatureNames();
            try {
                for (int i=0; i<sents.size(); i++) {
                    AnnoSentence sent = sents.get(i);
                    TemplateFeatureExtractor ext = new TemplateFeatureExtractor(sent, cs);
                    
                    for (int pred=0; pred<sent.size(); pred++) {
                        for (int arg=0; arg<sent.size(); arg++) {
                            extTimer.start();
                            List<String> feats = new ArrayList<String>();
                            ext.addFeatures(tpls, LocalObservations.newPidxCidx(pred, arg), feats);
                            extTimer.stop();
                            
                            numPairs++;
                            for (int t=0; t<feats.size(); t++) {                                

                                extTimer.start();
                                Object featName = feats.get(t);
                                extTimer.stop();
                                
                                hashTimer.start();
                                if (opt == 3) {
                                    String data = (String) featName;
                                    featName = FastMath.mod(MurmurHash3.murmurhash3_x86_32(data, 0, data.length(), 123456789), 200000);
                                }
                                hashSum += featName.hashCode();
                                hashTimer.stop();
                                
                                if (lookup) {
                                    lookupTimer.start();
                                    alphabet.lookupIndex(featName);
                                    lookupTimer.stop();
                                }
                            }
                        }
                    }
                    if (i % 1000 == 0) {
                        System.out.println("alphabet.size() = " + alphabet.size());
                    }
                }
                timer.stop();
                System.out.println("Num features: " + alphabet.size());
            } catch (OutOfMemoryError e) {
                System.out.println("Num features at OOM: " + alphabet.size());
                break;
            }
        }
        System.out.println("Num pairs: " + numPairs);
        System.out.println("Hash sum: " + hashSum);
        System.out.println("Time to extract: " + extTimer.totMs());
        System.out.println("Time to hash: " + hashTimer.totMs());
        System.out.println("Time to lookup: " + lookupTimer.totMs());
        System.out.println("Time total            : " + timer.totMs());
        System.out.println("Seconds per sentence: " + timer.totSec() / sents.size());
        System.out.println("Sentences per second: " + sents.size() / timer.totSec());
    }
    

    // ---------- Unused Legacy Classes --------------
    private interface Extractor {
        Object extract(int t, String pWord, String aWord);
    }
    private static class PairExtractor implements Extractor {
        public Object extract(int t, String pWord, String aWord) {
            return new Pair(t, new Pair(new Pair("pred", pWord), new Pair("arg", aWord)));
        }
    }
    private static class TupleExtractor implements Extractor {
        public Object extract(int t, String pWord, String aWord) {
            return new Tuple(t,"pred", pWord, "arg", aWord);
        }
    }
    private static class StringExtractor implements Extractor {
        public Object extract(int t, String pWord, String aWord) {
            return t + "_pred_" + pWord + "_arg_" + aWord;
        }
    }
    // -----------------------------------------------------
    
    public static void main(String[] args) throws IOException {
        new FeatureCreationSpeedTest().testSpeedOfFeatureCreation2();
    }

}