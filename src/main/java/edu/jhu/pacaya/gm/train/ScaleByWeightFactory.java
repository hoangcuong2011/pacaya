package edu.jhu.pacaya.gm.train;

import edu.jhu.pacaya.autodiff.Module;
import edu.jhu.pacaya.autodiff.Tensor;
import edu.jhu.pacaya.autodiff.TopoOrder;
import edu.jhu.pacaya.autodiff.tensor.ScalarMultiply;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.FgModelIdentity;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.util.collections.QLists;
import edu.jhu.pacaya.util.semiring.Algebra;

public class ScaleByWeightFactory implements MtFactory {

    private MtFactory mtFac;
    
    public ScaleByWeightFactory(MtFactory mtFac) {
        this.mtFac = mtFac;
    }
    
    @Override
    public Module<Tensor> getInstance(FgModelIdentity mid, FactorGraph fg, VarConfig goldConfig, double weight,
            int curIter, int maxIter) {
        Module<Tensor> mt = mtFac.getInstance(mid, fg, goldConfig, 1.0, curIter, maxIter);
        Algebra s = mt.getAlgebra();
        ScalarMultiply scale = new ScalarMultiply(mt, s.fromReal(weight));
        return new TopoOrder<Tensor>(QLists.getList(mid), scale, "ScaledByWeight");
    }

}
