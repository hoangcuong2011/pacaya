package edu.jhu.pacaya.gm.model.globalfac;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.jhu.pacaya.gm.inf.BfsMpSchedule;
import edu.jhu.pacaya.gm.inf.BruteForceInferencer;
import edu.jhu.pacaya.gm.inf.BeliefPropagation;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpScheduleType;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpUpdateOrder;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BeliefPropagationPrm;
import edu.jhu.pacaya.gm.inf.BeliefPropagationTest;
import edu.jhu.pacaya.gm.inf.FgInferencer;
import edu.jhu.pacaya.gm.model.ExplicitFactor;
import edu.jhu.pacaya.gm.model.Factor;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.gm.model.VarSet;
import edu.jhu.pacaya.gm.model.VarTensor;
import edu.jhu.pacaya.util.collections.QLists;
import edu.jhu.pacaya.util.semiring.Algebra;
import edu.jhu.pacaya.util.semiring.LogSemiring;
import edu.jhu.pacaya.util.semiring.RealAlgebra;
import edu.jhu.pacaya.util.semiring.SplitAlgebra;
import edu.jhu.prim.Primitives;
import edu.jhu.prim.arrays.DoubleArrays;
import edu.jhu.prim.util.math.FastMath;

public class ProjDepTreeFactorTest {

    @Test
    public void testHasParentPerToken() {
        int n = 10;
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        // Create vc for left branching tree.
        VarConfig vc = new VarConfig();
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i == j) { continue; }
                vc.put(treeFac.getLinkVar(i, j), (i == j - 1) ? LinkVar.TRUE : LinkVar.FALSE);
            }
        }
        assertTrue(ProjDepTreeFactor.hasOneParentPerToken(n, vc));
        // Add two parents for token 3.
        vc.put(treeFac.getLinkVar(3, 6), LinkVar.TRUE);
        assertFalse(ProjDepTreeFactor.hasOneParentPerToken(n, vc));
        // No parents for token 3.
        vc.put(treeFac.getLinkVar(3, 6), LinkVar.FALSE);
        vc.put(treeFac.getLinkVar(3, 4), LinkVar.FALSE);
        assertFalse(ProjDepTreeFactor.hasOneParentPerToken(n, vc));
    }
    
    @Test
    public void testGetParents() {
        int n = 6;
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        // Create vc for left branching tree.
        VarConfig vc = new VarConfig();
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i == j) { continue; }
                vc.put(treeFac.getLinkVar(i, j), (i == j - 1) ? LinkVar.TRUE : LinkVar.FALSE);
            }
        }
        assertArrayEquals(new int[]{-1, 0, 1, 2, 3, 4}, ProjDepTreeFactor.getParents(n, vc));
    }
    
    @Test
    public void testGetScore() {
        // For n >= 5, we will hit an integer overflow.
        int n = 4;
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        // Create vc for left branching tree.
        VarConfig vc = new VarConfig();
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i == j) { continue; }
                vc.put(treeFac.getLinkVar(i, j), (i == j - 1) ? LinkVar.TRUE : LinkVar.FALSE);
            }
        }
        int vcid1 = vc.getConfigIndex();
        VarConfig vc2 = treeFac.getVars().getVarConfig(vcid1);
        int vcid2 = vc2.getConfigIndex();
        assertEquals(vcid1, vcid2);
        assertEquals(vc.getVars(), vc2.getVars());
        assertEquals(vc, vc2);

        treeFac.updateFromModel(null);
        assertEquals(0.0, treeFac.getLogUnormalizedScore(vc.getConfigIndex()), 1e-13);
        // Add two parents for token 3.
        vc.put(treeFac.getLinkVar(1, 3), LinkVar.TRUE);
        assertEquals(Double.NEGATIVE_INFINITY, treeFac.getLogUnormalizedScore(vc.getConfigIndex()), 1e-13);
        // No parents for token 3.
        vc.put(treeFac.getLinkVar(1, 3), LinkVar.FALSE);
        vc.put(treeFac.getLinkVar(1, 2), LinkVar.FALSE);
        assertEquals(Double.NEGATIVE_INFINITY, treeFac.getLogUnormalizedScore(vc.getConfigIndex()), 1e-13);
    }
    
    @Test
    public void testPartitionFunctionWithoutUnaryFactorsProb() {
        partitionFunctionWithoutUnaryFactors(RealAlgebra.getInstance());       
    }
    
    @Test
    public void testPartitionFunctionWithoutUnaryFactorsLogProb() {
        partitionFunctionWithoutUnaryFactors(LogSemiring.getInstance());
    }
    
    public void partitionFunctionWithoutUnaryFactors(Algebra s) {
        assertEquals(1, getNumberOfTreesByBruteForce(1, s), 1e-13);
        assertEquals(2, getNumberOfTreesByBruteForce(2, s), 1e-13);
        assertEquals(7, getNumberOfTreesByBruteForce(3, s), 1e-13);
        //Slow: assertEquals(30, getNumberOfTreesByBruteForce(4, s), 1e-13);
        
        assertEquals(1, getNumberOfTreesByBp(1, s), 1e-13);
        assertEquals(2, getNumberOfTreesByBp(2, s), 1e-13);
        assertEquals(7, getNumberOfTreesByBp(3, s), 1e-13);
        assertEquals(30, getNumberOfTreesByBp(4, s), 1e-13); // TODO: is this correct
        assertEquals(143, getNumberOfTreesByBp(5, s), 1e-13); 
        assertEquals(728, getNumberOfTreesByBp(6, s), 1e-13);
        
        assertEquals(1, getNumberOfTreesByLoopyBp(1, s), 1e-13);
        assertEquals(2, getNumberOfTreesByLoopyBp(2, s), 1e-13);
        assertEquals(7, getNumberOfTreesByLoopyBp(3, s), 1e-13);
        assertEquals(30, getNumberOfTreesByLoopyBp(4, s), 1e-13); 
        assertEquals(143, getNumberOfTreesByLoopyBp(5, s), 1e-10); 
        assertEquals(728, getNumberOfTreesByLoopyBp(6, s), 1e-10);
    }

    private double getNumberOfTreesByBruteForce(int n, Algebra s) {
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        treeFac.updateFromModel(null);
        FactorGraph fg = new FactorGraph();
        fg.addFactor(treeFac);
        
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        return bf.getPartition();
    }    

    private double getNumberOfTreesByBp(int n, Algebra s) {
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        treeFac.updateFromModel(null);
        FactorGraph fg = new FactorGraph();
        fg.addFactor(treeFac);
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.normalizeMessages = false;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();
        return bp.getPartition();
    }
    

    private double getNumberOfTreesByLoopyBp(int n, Algebra s) {
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        treeFac.updateFromModel(null);
        FactorGraph fg = new FactorGraph();
        fg.addFactor(treeFac);
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.normalizeMessages = true;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();
        return bp.getPartition();
    }
    
    @Test
    public void testMarginalsAndPartitionFunction() {
        Algebra s;
        s = RealAlgebra.getInstance();        
        inferAndCheckMarginalsAndPartitionFunction(s, false, false);        
        inferAndCheckMarginalsAndPartitionFunction(s, true, false);        
        inferAndCheckMarginalsAndPartitionFunction(s, true, true); 
        s = LogSemiring.getInstance();        
        inferAndCheckMarginalsAndPartitionFunction(s, false, false);        
        inferAndCheckMarginalsAndPartitionFunction(s, true, false);        
        inferAndCheckMarginalsAndPartitionFunction(s, true, true); 
    }

    public static class FgAndLinks {
        public FactorGraph fg;
        public LinkVar[] rootVars;
        public LinkVar[][] childVars;
        public int n;
        public FgAndLinks(FactorGraph fg, LinkVar[] rootVars, LinkVar[][] childVars, int n) {
            this.fg = fg;
            this.rootVars = rootVars;
            this.childVars = childVars;
            this.n = n;
        }        
    }
        
    private void inferAndCheckMarginalsAndPartitionFunction(Algebra s, boolean normalizeMessages, boolean useBetheFreeEnergy) {
        FgAndLinks fgl = getFgl();
        FactorGraph fg = fgl.fg;
        LinkVar[] rootVars = fgl.rootVars;
        LinkVar[][] childVars = fgl.childVars;
        int n = fgl.n;
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();        
        prm.s = s;
        if (useBetheFreeEnergy) {
            prm.maxIterations = 20;
            prm.updateOrder = BpUpdateOrder.PARALLEL;
        } else {
            prm.maxIterations = 1;
            prm.schedule = BpScheduleType.TREE_LIKE;
            prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        }
        prm.normalizeMessages = normalizeMessages;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();
        
        // Print schedule:
        BfsMpSchedule schedule = new BfsMpSchedule(fg);
        
        System.out.println();
        for (Object edge : schedule.getOrder()) {
            System.out.println(edge.toString());
        }
        System.out.println();
        
        // Print marginals
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i != j) {
                    System.out.format("%d %d: %.2f\n", i, j, getExpectedCount(bp, rootVars, childVars, i, j));
                }
            }
        }

        // Check expected counts.
        double Z = 45+28+20+84+162+216+96;
        assertEquals((28+84)/Z, getExpectedCount(bp, rootVars, childVars, 1, 2), 1e-3);
        assertEquals((45+162+216)/Z, getExpectedCount(bp, rootVars, childVars, 2, 1), 1e-3);
        assertEquals((28+20+96)/Z, getExpectedCount(bp, rootVars, childVars, 0, 1), 1e-3);
        assertEquals((96+216)/Z, getExpectedCount(bp, rootVars, childVars, 2, 0), 1e-3);  
        
        assertEquals((45+28+20)/Z, getExpectedCount(bp, rootVars, childVars, -1, 0), 1e-13);
        assertEquals((162+216+96)/Z, getExpectedCount(bp, rootVars, childVars, -1, 2), 1e-3);

        // Check partition function.
        double[] trees = new double[] {45, 28, 20, 84, 162, 216, 96};
        double expectedRbar = 0;
        for (int t=0; t<trees.length; t++) {
            expectedRbar += trees[t] * FastMath.log(trees[t]);
        }
        System.out.println("expectedRbar: " + expectedRbar);
        assertEquals(45+28+20+84+162+216+96, bp.getPartition(), 1e-3);
        
        // Run brute force inference and compare.
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagationTest.assertEqualMarginals(fg, bf, bp, 1e-10);
    }

    @Test
    public void testMarginalsAndPartitionWithAdditionalVariable() {
        testPartitionWithAdditionalVariableHelper(RealAlgebra.getInstance(), false);
        testPartitionWithAdditionalVariableHelper(LogSemiring.getInstance(), false);
        testPartitionWithAdditionalVariableHelper(LogSemiring.getInstance(), true);
        testPartitionWithAdditionalVariableHelper(RealAlgebra.getInstance(), true);
    }
    
    public void testPartitionWithAdditionalVariableHelper(Algebra s, boolean normalizeMessages) {
        double[] root = new double[] {1, 2}; 
        double[][] child = new double[][]{ {0, 3}, {4, 0} };   
        
        FgAndLinks fgl = getFgl(root, child);
        FactorGraph fg = fgl.fg;
        LinkVar[] rootVars = fgl.rootVars;
        LinkVar[][] childVars = fgl.childVars;
        int n = fgl.n;
                
        // Add an extra variable over which we will marginalize.        
        Var roleVar = new Var(VarType.PREDICTED, 2, "Role_0_1", QLists.getList("arg0", "_"));
        ExplicitFactor roleFac = new ExplicitFactor(new VarSet(roleVar, childVars[0][1]));
        roleFac.setValue(0, 2);
        roleFac.setValue(1, 5);
        roleFac.setValue(2, 3);
        roleFac.setValue(3, 7);
        System.out.println(roleFac);
        roleFac.convertRealToLog();
        fg.addFactor(roleFac);
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.normalizeMessages = normalizeMessages;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();
        
        // Print schedule:
        BfsMpSchedule schedule = new BfsMpSchedule(fg);
        
        System.out.println();
        for (Object edge : schedule.getOrder()) {
            System.out.println(edge.toString());
        }
        System.out.println();
        
        // Print marginals
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i != j) {
                    System.out.format("%d %d: %.2f\n", i, j, getExpectedCount(bp, rootVars, childVars, i, j));
                }
            }
        }

        double Z = 3*3 + 3*7 + 8*2 + 8*5;

        // Check partition function.
        assertEquals(Z, bp.getPartition(), 1e-3);
        // Check expected counts.
        assertEquals((3*3 + 3*7)/Z, getExpectedCount(bp, rootVars, childVars, -1, 0), 1e-3);
        assertEquals((8*2 + 8*5)/Z, getExpectedCount(bp, rootVars, childVars, 1, 0), 1e-3);

        // Run brute force inference and compare.
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagationTest.assertEqualMarginals(fg, bf, bp, 1e-10);
    }

    @Test
    public void testPartitionWithAllOnesAndLatentRoleVar() {
        Algebra s = RealAlgebra.getInstance();        

        double[] root = new double[] {1, 1}; 
        double[][] child = new double[][]{ {1, 1}, {1, 1} };

        FgAndLinks fgl = getFgl(root, child);
        FactorGraph fg = fgl.fg;
        LinkVar[] rootVars = fgl.rootVars;
        LinkVar[][] childVars = fgl.childVars;
        int n = fgl.n;
        
        Var roleVar = new Var(VarType.PREDICTED, 2, "Role_1_0", QLists.getList("arg0", "_"));
                
        // Add an extra variable over which we will marginalize.        
        ExplicitFactor roleLinkFac = new ExplicitFactor(new VarSet(childVars[1][0], roleVar));
        roleLinkFac.setValue(0, 1);
        roleLinkFac.setValue(1, 1);
        roleLinkFac.setValue(2, 1);
        roleLinkFac.setValue(3, 1);
        System.out.println(roleLinkFac);
        roleLinkFac.convertRealToLog();
        fg.addFactor(roleLinkFac);
        ExplicitFactor roleFac = new ExplicitFactor(new VarSet(roleVar));
        roleFac.fill(1.0);
        roleFac.convertRealToLog();
        fg.addFactor(roleFac);
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.normalizeMessages = false;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();        
        
        // Print marginals
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i != j) {
                    System.out.format("%d %d: %.2f\n", i, j, getExpectedCount(bp, rootVars, childVars, i, j));
                }
            }
        }
        
        // Print schedule:
        BfsMpSchedule schedule = new BfsMpSchedule(fg);        
        System.out.println();
        for (Object edge : schedule.getOrder()) {
            System.out.println(edge.toString());
        }
        System.out.println();
        
        // Print factors
        for (Factor f : fg.getFactors()) {
            System.out.println(f);
        }
        
        double Z = 4;
        // Check partition function.
        assertEquals(Z, bp.getPartition(), 1e-3);

        // Check expected counts.
        System.out.println(getExpectedCount(bp, rootVars, childVars, -1, 0));
        assertEquals(2/Z, getExpectedCount(bp, rootVars, childVars, -1, 0), 1e-3);
        assertEquals(2/Z, getExpectedCount(bp, rootVars, childVars, 1, 0), 1e-3);

        // Run brute force inference and compare.
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagationTest.assertEqualMarginals(fg, bf, bp);
    }
    
    @Test
    public void testMarginalsAndPartitionWithAllOnes() {
        Algebra s = RealAlgebra.getInstance();        

        double[] root = new double[] {1, 1}; 
        double[][] child = new double[][]{ {1, 1}, {1, 1} };

        FgAndLinks fgl = getFgl(root, child);
        FactorGraph fg = fgl.fg;
        LinkVar[] rootVars = fgl.rootVars;
        LinkVar[][] childVars = fgl.childVars;
        int n = fgl.n;
                
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.maxIterations = 1;
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.normalizeMessages = true;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();        
        
        // Print marginals
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i != j) {
                    System.out.format("VarMarg: %d %d: %.2f\n", i, j, getExpectedCount(bp, rootVars, childVars, i, j));
                }
            }
        }
                
        // Print factors
        for (Factor f : fg.getFactors()) {
            System.out.println(bp.getMarginals(f));
            //System.out.println(f);
            //System.out.println();
        }
        
        double Z = 2;
        // Check partition function.
        assertEquals(Z,  bp.getPartition(), 1e-3);

        // Check expected counts.
        System.out.println(getExpectedCount(bp, rootVars, childVars, -1, 0));
        assertEquals(1/Z, getExpectedCount(bp, rootVars, childVars, -1, 0), 1e-3);
        assertEquals(1/Z, getExpectedCount(bp, rootVars, childVars, 1, 0), 1e-3);

        // Run brute force inference and compare.
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        BeliefPropagationTest.assertEqualMarginals(fg, bf, bp);
    }

    // This test fails because of a known floating point precision limitation of the ProjDepTreeFactor.
    // Currently, the values in get2WordSentFactorGraph() are scaled to avoid the floating point error.
    @Test
    public void testBpCompareMessagesWithExplicitTreeFactor() {
        compareBpMessagesWithExplicitTreeFactor(RealAlgebra.getInstance(), true, false);
        compareBpMessagesWithExplicitTreeFactor(RealAlgebra.getInstance(), true, true);
        compareBpMessagesWithExplicitTreeFactor(LogSemiring.getInstance(), true, false);
        compareBpMessagesWithExplicitTreeFactor(LogSemiring.getInstance(), true, true);
    }

    public void compareBpMessagesWithExplicitTreeFactor(Algebra s, boolean normalizeMessages, boolean makeLoopy) {
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.s = s;
        prm.updateOrder = BpUpdateOrder.PARALLEL;
        prm.maxIterations = 3;
        prm.normalizeMessages = normalizeMessages;
        
        FactorGraph fgExpl = get2WordSentFactorGraph(true, makeLoopy);
        BeliefPropagation bpExpl = new BeliefPropagation(fgExpl, prm);
        bpExpl.run();
        //printMessages(fgExpl, bpExpl);
        
        FactorGraph fgDp = get2WordSentFactorGraph(false, makeLoopy);
        BeliefPropagation bpDp = new BeliefPropagation(fgDp, prm);
        bpDp.run();
        //printMessages(fgDp, bpDp);
        
        System.out.println("Messages");
        assertEqualMessages(fgExpl, bpExpl.getMessages(), bpDp.getMessages());
        System.out.println("Partition: " + bpExpl.getPartition());
        System.out.println("Partition: " + bpDp.getPartition());
        assertEquals(bpExpl.getLogPartition(), bpDp.getLogPartition(), 1e-10);
    }
    
    @Test
    public void testErmaCompareMessagesWithExplicitTreeFactor() {
        compareErmaMessagesWithExplicitTreeFactor(RealAlgebra.getInstance(), true, false);
        compareErmaMessagesWithExplicitTreeFactor(RealAlgebra.getInstance(), true, true);
    }

    public void compareErmaMessagesWithExplicitTreeFactor(Algebra s, boolean normalizeMessages, boolean makeLoopy) {
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.s = s;
        prm.updateOrder = BpUpdateOrder.PARALLEL;
        prm.maxIterations = 3;
        prm.normalizeMessages = normalizeMessages;
        
        FactorGraph fgExpl = get2WordSentFactorGraph(true, makeLoopy);
        BeliefPropagation bpExpl = new BeliefPropagation(fgExpl, prm);
        bpExpl.forward();
        //printMessages(fgExpl, bpExpl);
        
        FactorGraph fgDp = get2WordSentFactorGraph(false, makeLoopy);
        BeliefPropagation bpDp = new BeliefPropagation(fgDp, prm);
        bpDp.forward();
        //printMessages(fgDp, bpDp);
        
        System.out.println("Messages");
        assertEqualMessages(fgExpl, bpExpl.getMessages(), bpDp.getMessages());
        System.out.println("Beliefs");
        assertEqualVarTensors(bpExpl.getOutput().varBeliefs, bpDp.getOutput().varBeliefs);
        assertEqualVarTensors(bpExpl.getOutput().facBeliefs, bpDp.getOutput().facBeliefs);
        System.out.println("Partition: " + bpExpl.getPartition());
        System.out.println("Partition: " + bpDp.getPartition());
        assertEquals(bpExpl.getLogPartition(), bpDp.getLogPartition(), 1e-10);
        
        for (int v=0; v<fgDp.getNumVars(); v++) {
            LinkVar link = (LinkVar) fgDp.getVar(v);
            double adj = 0.0;
            if ((link.getParent() == -1 && link.getChild() == 1) || 
                    (link.getParent() == 1 && link.getChild() == 0)) {
                adj = 1.0;
            }
            bpExpl.getOutputAdj().varBeliefs[v].setValue(LinkVar.TRUE, adj);
            bpDp.getOutputAdj().varBeliefs[v].setValue(LinkVar.TRUE, adj);
        }
        bpExpl.backward();
        bpDp.backward();
        System.out.println("Adjoints");
        assertEqualMessages(fgExpl, bpExpl.getMessagesAdj(), bpDp.getMessagesAdj());
        assertEqualVarTensors(bpExpl.getPotentialsAdj(), bpDp.getPotentialsAdj());
    }

    private void assertEqualMessages(FactorGraph fgExpl, VarTensor[] msgsExpl, VarTensor[] msgsDp) {
        for (int i=0; i<msgsExpl.length; i++) {
            VarTensor msgExpl = msgsExpl[i];
            VarTensor msgDp = msgsDp[i];
            String edge = fgExpl.edgeToString(i);
            assertEqualMessages(msgExpl, msgDp, edge);
        }
    }

    private void assertEqualMessages(VarTensor msgExpl, VarTensor msgDp, String edge) {
        assertEquals(msgExpl.size(), msgDp.size());
        for (int c=0; c<msgExpl.size(); c++) {
            if (msgDp.getValue(c) == Double.NEGATIVE_INFINITY //&& msgExpl.getValue(c) < -30
                    || msgExpl.getValue(c) == Double.NEGATIVE_INFINITY ) {//&& msgDp.getValue(c) < -30) {
                //continue;
            }

            if (!Primitives.equals(msgExpl.getValue(c), msgDp.getValue(c), 1e-13)) {
                System.out.println("NOT EQUAL:");
                System.out.println(edge);
                System.out.println(msgExpl);
                System.out.println(msgDp);
            } 
            assertEquals(msgExpl.getValue(c), msgDp.getValue(c), 1e-13);
        }
        // TODO: This doesn't work because the vars aren't the same: assertTrue(msgExpl.equals(msgDp, 1e-5));
    }

    private void assertEqualVarTensors(VarTensor[] msgsExpl, VarTensor[] msgsDp) {
        for (int i=0; i<msgsExpl.length; i++) {
            if (msgsExpl[i] == null || msgsDp[i] == null) {
                // Don't compare the potentials for the projective dependency tree factor.
                continue;
            }
            VarTensor msgExpl = msgsExpl[i];
            VarTensor msgDp = msgsDp[i];
            assertEquals(msgExpl.size(), msgDp.size());
            for (int c=0; c<msgExpl.size(); c++) {
                if (msgDp.getValue(c) == Double.NEGATIVE_INFINITY //&& msgExpl.getValue(c) < -30
                        || msgExpl.getValue(c) == Double.NEGATIVE_INFINITY ) {//&& msgDp.getValue(c) < -30) {
                    //continue;
                }

                if (!Primitives.equals(msgExpl.getValue(c), msgDp.getValue(c), 1e-13)) {
                    System.out.println("NOT EQUAL:");
                    System.out.println(msgExpl);
                    System.out.println(msgDp);
                } 
                assertEquals(msgExpl.getValue(c), msgDp.getValue(c), 1e-13);
            }
            // TODO: This doesn't work because the vars aren't the same: assertTrue(msgExpl.equals(msgDp, 1e-5));
        }
    }

    private void printMessages(FactorGraph fg, BeliefPropagation bp) {
        System.out.println("Messages");
        printMessages(fg, bp.getMessages());
        System.out.println("Partition: " + bp.getPartition());
    }


    private void printMessages(FactorGraph fg, VarTensor[] msgs) {
        for (int i=0; i<fg.getNumEdges(); i++) {            
            String edge = fg.edgeToString(i);
            System.out.println(edge);
            System.out.println(msgs[i]);
            System.out.println("Log odds: " + (msgs[i].getValue(1) - msgs[i].getValue(0)));
        }
    }

    // This test used to fail when the number of iterations was too low. But
    // passes with sufficient iterations. It seems loopy BP might even
    // oscillate.
    //
    @Test
    public void testComparePartitionWithBruteForce() {
        // Below, we check both the case of an explicit tree factor and the ProjDepTreeFactor class.
        // 
        // Check that we can correctly compute the partition in the non-loopy setting.
        comparePartitionWithBruteForce(LogSemiring.getInstance(), true, true, false, false);
        comparePartitionWithBruteForce(LogSemiring.getInstance(), true, false, false, false);
        // Check that we can correctly compute the partition in the loopy setting.
        comparePartitionWithBruteForce(LogSemiring.getInstance(), true, true, true, false);
        comparePartitionWithBruteForce(LogSemiring.getInstance(), true, false, true, false);
    }
    
    @Test
    public void testComparePartitionWithBruteForceInfiniteEdgeWeight() {
        // Check the case of a negative infinity edge weight
        comparePartitionWithBruteForce(LogSemiring.getInstance(), true, false, false, true);
    }

    public void comparePartitionWithBruteForce(Algebra s, boolean normalizeMessages, boolean useExplicitTreeFactor, boolean makeLoopy, boolean negInfEdgeWeight) {
        FactorGraph fg = get2WordSentFactorGraph(useExplicitTreeFactor, makeLoopy, negInfEdgeWeight);
        
        System.out.println(fg.getFactors());
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.s = s;
        prm.updateOrder = BpUpdateOrder.PARALLEL;
        prm.maxIterations = 20;
        prm.normalizeMessages = normalizeMessages;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);
        bp.run();

        printMessages(fg, bp);
        
        printBeliefs(fg, bp);
        
        // Run brute force inference and compare.
        BruteForceInferencer bf = new BruteForceInferencer(fg, s);
        bf.run();
        printBeliefs(fg, bf);

        assertEquals(bf.getLogPartition(), bp.getLogPartition(), 1e-1);
        //ErmaBpForwardTest.assertEqualMarginals(fg, bf, bp, 1e-10);
    }
    
    @Test
    public void testBackwardPass2WordGlobalFactor() {
        Algebra s = RealAlgebra.getInstance();        
        double[] root = new double[]{ 1.0, 1.0 };
        double[][] child = new double[][]{ { 0.0, 1.0 }, { 1.0, 0.0 } };
        testBackwardPassGlobalFactor(s, root, child);
    }
    
    @Test
    public void testBackwardPass2WordGlobalFactorWithPruning() {
        Algebra s = RealAlgebra.getInstance();        
        double[] root = new double[]{ 1.0, 1.0 };
        // We prune the edge from 1 --> 0.
        double[][] child = new double[][]{ { 0.0, 1.0 }, { 0.0, 0.0 } };
        testBackwardPassGlobalFactor(s, root, child);
    }

    private void testBackwardPassGlobalFactor(Algebra s, double[] root, double[][] child) {
        FgAndLinks fgl = ProjDepTreeFactorTest.getFgl(root, child);
        FactorGraph fg = fgl.fg;
        LinkVar[] rootVars = fgl.rootVars;
        LinkVar[][] childVars = fgl.childVars;
        
        BeliefPropagationPrm prm = new BeliefPropagationPrm();
        prm.s = s;
        prm.schedule = BpScheduleType.TREE_LIKE;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.maxIterations = 2;
        prm.normalizeMessages = true;
        BeliefPropagation bp = new BeliefPropagation(fg, prm);

        bp.forward();
        bp.getOutputAdj().fill(1.0);
        bp.backward();

        System.out.println("Messages:");
        printMessages(fg, bp.getMessages());
        System.out.println("\nMessage Adjoints:");
        printMessages(fg, bp.getMessagesAdj());
        System.out.println("\nPotential Adjoints:");
        for (VarTensor adj : bp.getPotentialsAdj()) {
            if (adj != null) {
                System.out.println(adj);
                assertTrue(!adj.containsNaN());
            }
        }
    }

    public static FactorGraph get2WordSentFactorGraph(boolean useExplicitTreeFactor, boolean makeLoopy) {
        return get2WordSentFactorGraph(useExplicitTreeFactor, makeLoopy, false);
    }
    
    public static FactorGraph get2WordSentFactorGraph(boolean useExplicitTreeFactor, boolean makeLoopy, boolean negInfEdgeWeight) {
        return get2WordSentFgAndLinks(useExplicitTreeFactor, makeLoopy, negInfEdgeWeight).fg;
    }
    
    public static FgAndLinks get2WordSentFgAndLinks(boolean useExplicitTreeFactor, boolean makeLoopy, boolean negInfEdgeWeight) {
        // These are the log values, not the exp.
        double[] root = new double[] {8.571183, 89.720164}; 
        double[][] child = new double[][]{ {0, 145.842585}, {23.451215, 0} };
        // TODO: These scaling factors are added to avoid the floating point error in some of the
        // tests above. This should really have multiple tests with and without the floating point
        // error.
        DoubleArrays.scale(root, .1);
        DoubleArrays.scale(child, .1);
        
        // For random values:
        //        Prng.seed(14423444);
        //        root = DoubleArrays.getLog(ModuleTestUtils.getAbsZeroOneGaussian(2).toNativeArray());
        //        child[0] = DoubleArrays.getLog(ModuleTestUtils.getAbsZeroOneGaussian(2).toNativeArray());
        //        child[1] = DoubleArrays.getLog(ModuleTestUtils.getAbsZeroOneGaussian(2).toNativeArray());
        
        if (negInfEdgeWeight) {
            child[0][1] = Double.NEGATIVE_INFINITY;
        }
        
        // Create an edge factored dependency tree factor graph.
        //FactorGraph fg = getEdgeFactoredDepTreeFactorGraph(root, child);
        FactorGraph fg = new FactorGraph();
        int n = root.length;
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        treeFac.updateFromModel(null);
        LinkVar[] rootVars = treeFac.getRootVars();
        LinkVar[][] childVars = treeFac.getChildVars();
        
        // Add unary factors to each edge.
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i != j) {
                    ExplicitFactor f;
                    if (i == -1) {
                        f = new ExplicitFactor(new VarSet(rootVars[j]));
                        f.setValue(LinkVar.TRUE, root[j]);
                        f.setValue(LinkVar.FALSE, 0.0);
                    } else {
                        f = new ExplicitFactor(new VarSet(childVars[i][j]));
                        f.setValue(LinkVar.TRUE, child[i][j]);
                        f.setValue(LinkVar.FALSE, 0.0);
                    }
                    //f.scale(0.01);
                    fg.addFactor(f);
                }
            }
        }
        
        if (makeLoopy) {
            ExplicitFactor f = new ExplicitFactor(new VarSet(rootVars[0], rootVars[1]));
            f.setValue(3, -DoubleArrays.sum(root));
            fg.addFactor(f);
            //f.scale(0.01);
        }
        
        if (useExplicitTreeFactor) {
            ExplicitFactor f = new ExplicitFactor(new VarSet(rootVars[0], rootVars[1], childVars[0][1], childVars[1][0]));
            f.fill(Double.NEGATIVE_INFINITY);
            VarConfig vc = new VarConfig();
            vc.put(rootVars[0], LinkVar.TRUE);
            vc.put(rootVars[1], LinkVar.FALSE);
            vc.put(childVars[0][1], LinkVar.TRUE);
            vc.put(childVars[1][0], LinkVar.FALSE);
            f.setValue(vc.getConfigIndex(), 0.0);
            vc = new VarConfig();
            vc.put(rootVars[0], LinkVar.FALSE);
            vc.put(rootVars[1], LinkVar.TRUE);
            vc.put(childVars[0][1], LinkVar.FALSE);
            vc.put(childVars[1][0], LinkVar.TRUE);
            f.setValue(vc.getConfigIndex(), 0.0);
            fg.addFactor(f);
        } else {
            fg.addFactor(treeFac);
        }
        return new FgAndLinks(fg, rootVars, childVars, 2);
    }

    private void printBeliefs(FactorGraph fg, FgInferencer bp) {
        // Print marginals
        System.out.println("Var marginals: ");
        for (Var v : fg.getVars()) {
            System.out.println(bp.getMarginals(v));
        }                
        // Print factors
        System.out.println("Factor marginals: ");
        for (Factor f : fg.getFactors()) {
            System.out.println(bp.getMarginals(f));
        }
        System.out.println("Partition: " + bp.getPartition());
    }
    
    private double getExpectedCount(BeliefPropagation bp, LinkVar[] rootVars, LinkVar[][] childVars, int i, int j) {
        VarTensor marg;
        if (i == -1) {
            marg = bp.getMarginals(rootVars[j]);
        } else {
            marg = bp.getMarginals(childVars[i][j]);
        }        
        return marg.getValue(LinkVar.TRUE);
    }

    public static FgAndLinks getFgl() {
        double[] root = new double[] {1, 2, 3}; 
        double[][] child = new double[][]{ {0, 4, 5}, {6, 0, 7}, {8, 9, 0} };        
        return getFgl(root, child);
    }

    public static FgAndLinks getFgl(double[] root, double[][] child) {
        // Create an edge factored dependency tree factor graph.
        //FactorGraph fg = getEdgeFactoredDepTreeFactorGraph(root, child);
        FactorGraph fg = new FactorGraph();
        int n = root.length;
        ProjDepTreeFactor treeFac = new ProjDepTreeFactor(n, VarType.PREDICTED);
        treeFac.updateFromModel(null);

        LinkVar[] rootVars = treeFac.getRootVars();
        LinkVar[][] childVars = treeFac.getChildVars();
        
        // Add unary factors to each edge.
        for (int i=-1; i<n; i++) {
            for (int j=0; j<n; j++) {
                if (i != j) {
                    ExplicitFactor f;
                    if (i == -1) {
                        f = new ExplicitFactor(new VarSet(rootVars[j]));
                        f.setValue(LinkVar.TRUE, root[j]);
                        f.setValue(LinkVar.FALSE, 1.0);
                    } else {
                        f = new ExplicitFactor(new VarSet(childVars[i][j]));
                        f.setValue(LinkVar.TRUE, child[i][j]);
                        f.setValue(LinkVar.FALSE, 1.0);
                    }
                    f.convertRealToLog();
                    fg.addFactor(f);
                }
            }
        }
        
        // Add this at the end, just to exercise the BFS schedule a bit more.
        fg.addFactor(treeFac);
        
        return new FgAndLinks(fg, rootVars, childVars, n);
    }
    
    @Test
    public void testGlobalFactorComputationSameAsExplicitFactor() {
        checkGlobalFactorComputationSameAsExplicitFactor(RealAlgebra.getInstance());
        checkGlobalFactorComputationSameAsExplicitFactor(SplitAlgebra.getInstance());
        checkGlobalFactorComputationSameAsExplicitFactor(LogSemiring.getInstance());
    }

    private void checkGlobalFactorComputationSameAsExplicitFactor(Algebra s) {
        ProjDepTreeFactor ptree = new ProjDepTreeFactor(3, VarType.PREDICTED);        
        GlobalExplicitFactor ef = new GlobalExplicitFactor(new ExplicitGlobalFactor(ptree));
        
        // Create messages that simulate being clamped to a specific tree.
        VarSet vars = ptree.getVars();
        VarTensor[] inMsgs = new VarTensor[vars.size()];
        for (int v=0; v<inMsgs.length; v++) {
            LinkVar var = (LinkVar)vars.get(v);
            inMsgs[v] = new VarTensor(s, new VarSet(var));
            if ((var.getParent() == -1 && var.getChild() == 1) ||
                    (var.getParent() == 1 && var.getChild() == 0) ||
                    (var.getParent() == 1 && var.getChild() == 2)) {
                inMsgs[v].setValue(LinkVar.FALSE, s.fromReal(0.0));
                inMsgs[v].setValue(LinkVar.TRUE, s.fromReal(1+v));
            } else {
                inMsgs[v].setValue(LinkVar.FALSE, s.fromReal(1+v));
                inMsgs[v].setValue(LinkVar.TRUE, s.fromReal(0.0));
            }
        }
        
        // Compute the messages by dynamic programming.
        VarTensor[] outMsgs1 = new VarTensor[vars.size()];
        for (int v=0; v<outMsgs1.length; v++) {
            outMsgs1[v] = new VarTensor(s, new VarSet(vars.get(v)));
        }
        ptree.createMessages(inMsgs, outMsgs1);
        
        // Compute the messages by explicit enumeration of the factor.
        VarTensor[] outMsgs2 = new VarTensor[vars.size()];
        for (int v=0; v<outMsgs2.length; v++) {
            outMsgs2[v] = new VarTensor(s, new VarSet(vars.get(v)));
        }
        ef.createMessages(inMsgs, outMsgs2);
        
        for (int v=0; v<outMsgs2.length; v++) {
            assertEqualMessages(outMsgs2[v], outMsgs1[v], ""+vars.get(v));
        }
        
        assertEquals(ef.getExpectedLogBelief(inMsgs), ptree.getExpectedLogBelief(inMsgs), 1e-13);
    }
    
}
