package edu.jhu.optimize;

import java.util.Arrays;

import org.apache.log4j.Logger;

import edu.jhu.util.Prng;
import edu.jhu.util.Utilities;

/**
 * Stochastic gradient descent with minibatches.
 * 
 * We use the learning rate from "Introduction to Stochastic Search and
 * Optimization (James Spall). See eq. (4.14) pg. 113 and the constants
 * suggested on page pg. 164.
 * 
 * @author mgormley
 */
public class AdaGrad extends SGD {

    /** Options for this optimizer. */
    public static class AdaGradPrm {
        /** The scaling parameter for the learning rate. */
        public double eta = 0.1;
        public SGDPrm sgdPrm = new SGDPrm();
    }
    
    private static final Logger log = Logger.getLogger(AdaGrad.class);

    private AdaGradPrm prm;
    private double[] gradSumSquares;
    
    /**
     * Constructs an SGD optimizer.
     */
    public AdaGrad(AdaGradPrm prm) {
        super(prm.sgdPrm);
        this.prm = prm;
    }

    /**
     * Initializes all the parameters for optimization.
     */
    protected void init(BatchFunction function) {
        super.init(function);
        gradSumSquares = new double[function.getNumDimensions()];
    }

    /** A tie-in for subclasses such as AdaGrad. */
    protected void takeNoteOfGradient(double[] gradient) {
        super.takeNoteOfGradient(gradient);
        for (int i=0; i<gradient.length; i++) {
            gradSumSquares[i] += gradient[i] * gradient[i];
            assert !Double.isNaN(gradSumSquares[i]);
        }
    }
    
    /**
     * Updates the learning rate for the next iteration.
     * @param iterCount The current iteration.
     * @param i The index of the current model parameter. 
     */
    protected double getLearningRate(int iterCount, int i) {
        if (gradSumSquares[i] < 0) {
            throw new RuntimeException("Gradient sum of squares entry is < 0: " + gradSumSquares[i]);
        }
        double learningRate = prm.eta / Math.sqrt(gradSumSquares[i]);
        assert !Double.isNaN(learningRate);
        if (learningRate == Double.POSITIVE_INFINITY) {
            if (gradSumSquares[i] != 0.0) {
                log.warn("Gradient was non-zero but learningRate hit positive infinity: " + gradSumSquares[i]);
            }
            // Just return a large value. The gradient is probably just 0.0;
            return 2e200;
        }
        return learningRate;
    }
}