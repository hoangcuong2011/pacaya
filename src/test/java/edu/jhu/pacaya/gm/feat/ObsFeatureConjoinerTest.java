package edu.jhu.pacaya.gm.feat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.jhu.pacaya.gm.data.FgExampleMemoryStore;
import edu.jhu.pacaya.gm.data.LFgExample;
import edu.jhu.pacaya.gm.data.LabeledFgExample;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner.ObsFeatureConjoinerPrm;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.gm.model.VarSet;
import edu.jhu.pacaya.util.FeatureNames;
import edu.jhu.pacaya.util.collections.QLists;

public class ObsFeatureConjoinerTest {

    @Test
    public void testNumParams() {
        FactorTemplateList fts = getFtl();
        ObsFeatureConjoinerPrm prm = new ObsFeatureConjoinerPrm();
        prm.featCountCutoff = 0;
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(prm, fts);
        ofc.init(null);
        assertEquals((3*2)*2 + 2*1, ofc.getNumParams());
    }

    @Test
    public void testExcludeUnsupportedFeatures1() {
        boolean useLat = false;
        boolean includeUnsupportedFeatures = true;
        checkNumParams(useLat, includeUnsupportedFeatures, 20);        
    }
    
    @Test
    public void testExcludeUnsupportedFeatures2() {
        boolean useLat = false;
        boolean includeUnsupportedFeatures = false;
        // 6 bias features, and 4 other features.
        checkNumParams(useLat, includeUnsupportedFeatures, 6+4);
    }
    
    @Test
    public void testExcludeUnsupportedFeaturesWithLatentVars1() {
        boolean useLat = true;
        boolean includeUnsupportedFeatures = true;
        checkNumParams(useLat, includeUnsupportedFeatures, 20);        
    }
    
    @Test
    public void testExcludeUnsupportedFeaturesWithLatentVars2() {
        boolean useLat = true;
        boolean includeUnsupportedFeatures = false;
        // 6 bias features, and 6 other features.
        checkNumParams(useLat, includeUnsupportedFeatures, 6+6);
    }

    private void checkNumParams(boolean useLat, boolean includeUnsupportedFeatures,
            int expectedNumParams) {
        FactorTemplateList fts = getFtl(useLat);
        ObsFeatureConjoinerPrm prm = new ObsFeatureConjoinerPrm();
        prm.featCountCutoff = includeUnsupportedFeatures ? 0 : 1;
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(prm, fts);
        
        FgExampleMemoryStore data = new FgExampleMemoryStore();
        data.add(getExForFts("1a", "2a", ofc, fts, useLat));
        data.add(getExForFts("1a", "2c", ofc, fts, useLat));
        data.add(getExForFts("1b", "2b", ofc, fts, useLat));
        data.add(getExForFts("1b", "2c", ofc, fts, useLat));

        ofc.init(data);
        
        System.out.println("\n"+ofc);
        assertEquals(expectedNumParams, ofc.getNumParams());
    }
    
    public static class MockFeatureExtractor implements ObsFeatureExtractor {

        protected FactorTemplateList fts;

        public MockFeatureExtractor(FactorTemplateList fts) {
            this.fts = fts;
        }

        @Override
        public FeatureVector calcObsFeatureVector(ObsFeExpFamFactor factor) {
            FeatureVector fv = new FeatureVector();
            FeatureNames alphabet = fts.getTemplate(factor).getAlphabet();

            int featIdx = alphabet.lookupIndex("BIAS_FEATURE", true);
            alphabet.setIsBias(featIdx);
            fv.set(featIdx, 1.0);
            featIdx = alphabet.lookupIndex("feat2a");
            fv.set(featIdx, 1.0);
            
            return fv;
        }
    }
    
    private LFgExample getExForFts(String state1, String state2, ObsFeatureConjoiner ofc, FactorTemplateList fts, boolean useLat) {
        Var v1 = new Var(VarType.PREDICTED, 2, "1", QLists.getList("1a", "1b"));
        Var v2 = new Var(useLat ? VarType.LATENT : VarType.PREDICTED, 3, "2", QLists.getList("2a", "2b", "2c"));
        FactorGraph fg = new FactorGraph();
        MockFeatureExtractor obsFe = new MockFeatureExtractor(fts);
        fg.addFactor(new ObsFeExpFamFactor(new VarSet(v1, v2), "key2", ofc, obsFe));
        
        VarConfig vc = new VarConfig();
        vc.put(v1, state1);
        vc.put(v2, state2);
        
        return new LabeledFgExample(fg, vc, fts);
    }

    public static FactorTemplateList getFtl() {
        return getFtl(false);
    }
    
    public static FactorTemplateList getFtl(boolean useLat) {
        FactorTemplateList fts = new FactorTemplateList();
        Var v1 = new Var(VarType.PREDICTED, 2, "1", QLists.getList("1a", "1b"));
        Var v2 = new Var(useLat ? VarType.LATENT : VarType.PREDICTED, 3, "2", QLists.getList("2a", "2b", "2c"));
        {
            FeatureNames alphabet = new FeatureNames();
            alphabet.lookupIndex("feat1");
            fts.add(new FactorTemplate(new VarSet(v1), alphabet, "key1"));
        }
        {
            FeatureNames alphabet = new FeatureNames();
            alphabet.lookupIndex("feat2a");
            alphabet.lookupIndex("feat2b");
            fts.add(new FactorTemplate(new VarSet(v1, v2), alphabet, "key2"));
        }
        return fts;
    }
        
}
