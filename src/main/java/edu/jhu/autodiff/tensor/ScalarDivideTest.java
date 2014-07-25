package edu.jhu.autodiff.tensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.jhu.autodiff.AbstractModuleTest;
import edu.jhu.autodiff.Module;
import edu.jhu.autodiff.Tensor;
import edu.jhu.autodiff.TensorIdentity;
import edu.jhu.autodiff.AbstractModuleTest.Tensor2Factory;
import edu.jhu.autodiff.TensorUtils;
import edu.jhu.util.semiring.Algebra;
import edu.jhu.util.semiring.RealAlgebra;

public class ScalarDivideTest {

    private Algebra s = new RealAlgebra();

    @Test
    public void testSimple() {
        Tensor t1 = TensorUtils.getVectorFromValues(s, 2, 3, 5);
        Tensor t2 = TensorUtils.getVectorFromValues(s, 4, 6, 7);
        
        Tensor expOut = TensorUtils.getVectorFromValues(s, 2./6., 3./6., 5./6.);
        Tensor expT1Adj = TensorUtils.getVectorFromValues(s, 2.2/6., 2.2/6., 2.2/6.);
        Tensor expT2Adj = TensorUtils.getVectorFromValues(s, 0.0, 
                2.2*2/(-6*6) + 2.2*3/(-6*6) + 2.2*5/(-6*6), 
                0.0);
        
        Tensor2Factory fact = new Tensor2Factory() {
            public Module<Tensor> getModule(Module<Tensor> m1, Module<Tensor> m2) {
                return new ScalarDivide(m1, m2, 1);
            }
        };
        
        AbstractModuleTest.evalTensor2(t1, expT1Adj, t2, expT2Adj, fact, expOut, 2.2);
    }
    
    // TODO: This test has the same functionality as the one above for the RealSemiring only.
    @Test
    public void testForwardAndBackward() {
        Tensor t1 = TensorUtils.getVectorFromValues(s, 2, 3, 5);
        Tensor t2 = TensorUtils.getVectorFromValues(s, 4, 6, 7);
        TensorIdentity id1 = new TensorIdentity(t1);
        TensorIdentity id2 = new TensorIdentity(t2);
        ScalarDivide ea = new ScalarDivide(id1, id2, 1);

        Tensor out = ea.forward();
        assertEquals(2./6, out.getValue(0), 1e-13);
        assertEquals(3./6, out.getValue(1), 1e-13);
        assertEquals(5./6, out.getValue(2), 1e-13);
        assertTrue(out == ea.getOutput());

        // Set the adjoint of the sum to be 1.
        ea.getOutputAdj().fill(2.2);
        ea.backward();
        
        assertEquals(2.2/6, id1.getOutputAdj().getValue(0), 1e-13);
        assertEquals(2.2/6, id1.getOutputAdj().getValue(1), 1e-13);
        assertEquals(2.2/6, id1.getOutputAdj().getValue(2), 1e-13);
        
        assertEquals(0, id2.getOutputAdj().getValue(0), 1e-13);
        assertEquals(2.2*2/(-6*6) + 2.2*3/(-6*6) + 2.2*5/(-6*6), id2.getOutputAdj().getValue(1), 1e-13);
        assertEquals(0, id2.getOutputAdj().getValue(2), 1e-13);
    }

    @Test
    public void testGradByFiniteDiffsAllSemirings() {
        Tensor2Factory fact = new Tensor2Factory() {
            public Module<Tensor> getModule(Module<Tensor> m1, Module<Tensor> m2) {
                return new ScalarDivide(m1, m2, 1);
            }
        };
        AbstractModuleTest.evalTensor2ByFiniteDiffs(fact);
    }
    
}