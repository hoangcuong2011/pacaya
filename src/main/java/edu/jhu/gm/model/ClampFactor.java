package edu.jhu.gm.model;

import edu.jhu.util.semiring.LogSemiring;
import edu.jhu.util.semiring.RealSemiring;
import edu.jhu.util.semiring.Semiring;

public class ClampFactor extends ExplicitFactor implements Factor {

    private static final long serialVersionUID = 1L;
    
    private int state;

    public ClampFactor(Var v, int state) {
        super(new VarSet(v));
        this.state = state;
        this.set(Double.NaN);
    }

    public void updateFromModel(FgModel model, boolean logDomain) {
        Semiring s = logDomain ? new LogSemiring() : new RealSemiring();
        this.set(s.zero());
        this.setValue(state, s.one());
    }

}