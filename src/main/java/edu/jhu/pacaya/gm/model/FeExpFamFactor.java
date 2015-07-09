package edu.jhu.pacaya.gm.model;

import edu.jhu.pacaya.gm.feat.FeatureExtractor;
import edu.jhu.pacaya.gm.feat.FeatureVector;

/**
 * An exponential family factor which takes a FeatureExtractor at construction time and 
 * uses it to extract features.
 * 
 * @author mgormley
 */
public class FeExpFamFactor extends ExpFamFactor {

    private static final long serialVersionUID = 1L;
    private FeatureExtractor fe;
    
    public FeExpFamFactor(VarSet vars, FeatureExtractor fe) {
        super(vars);
        this.fe = fe;
    }

    @Override
    public FeatureVector getFeatures(int configId) {
        return fe.calcFeatureVector(this, configId);
    }
    
}