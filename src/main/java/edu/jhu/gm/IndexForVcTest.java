package edu.jhu.gm;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import edu.jhu.gm.Var.VarType;

public class IndexForVcTest {


    @Test
    public void testGetConfigArray1() {
        Var v0 = getVar(0, 2);
        Var v1 = getVar(1, 3);
        Var v2 = getVar(2, 5);

        VarSet vars1 = new VarSet();
        vars1.add(v0);
        vars1.add(v1);
        vars1.add(v2);

        VarConfig vc2 = new VarConfig();
        vc2.put(v2, 2);
        
        int[] configs = IndexForVc.getConfigArr(vars1, vc2);
        System.out.println(Arrays.toString(configs));
        assertEquals(2*3, configs.length);
        Assert.assertArrayEquals(new int[]{12, 13, 14, 15, 16, 17}, configs);
    }
    
    @Test
    public void testGetConfigArray2() {
        Var v0 = getVar(0, 2);
        Var v1 = getVar(1, 3);
        Var v2 = getVar(2, 5);

        VarSet vars1 = new VarSet();
        vars1.add(v0);
        vars1.add(v1);
        vars1.add(v2);

        VarConfig vc2 = new VarConfig();
        vc2.put(v1, 0);
        
        System.out.println(new DenseFactor(vars1));
        
        // TODO: we can't loop over a particular configuration of vars1, only the config in which each (non-vars2) variable has state 0.
        int[] configs = IndexForVc.getConfigArr(vars1, vc2);
        System.out.println(Arrays.toString(configs));
        assertEquals(2*5, configs.length);
        Assert.assertArrayEquals(new int[]{0, 1, 6, 7, 12, 13, 18, 19, 24, 25}, configs);
    }
    
    @Test
    public void testGetConfigArray3() {
        Var v0 = getVar(0, 2);
        Var v1 = getVar(1, 3);
        Var v2 = getVar(2, 5);

        VarSet vars1 = new VarSet();
        vars1.add(v0);
        vars1.add(v1);
        vars1.add(v2);

        VarConfig vc2 = new VarConfig();
        vc2.put(v1, 1);
        
        System.out.println(new DenseFactor(vars1));
        
        // TODO: we can't loop over a particular configuration of vars1, only the config in which each (non-vars2) variable has state 0.
        int[] configs = IndexForVc.getConfigArr(vars1, vc2);
        System.out.println(Arrays.toString(configs));
        assertEquals(2*5, configs.length);
        Assert.assertArrayEquals(new int[]{2, 3, 8, 9, 14, 15, 20, 21, 26, 27}, configs);
    }
    
    @Test
    public void testGetConfigArray4() {
        Var v0 = getVar(0, 2);
        Var v1 = getVar(1, 3);
        Var v2 = getVar(2, 5);

        VarSet vars1 = new VarSet();
        vars1.add(v0);
        vars1.add(v1);
        vars1.add(v2);

        VarConfig vc2 = new VarConfig();
        vc2.put(v0, 1);
        vc2.put(v2, 4);
        
        System.out.println(new DenseFactor(vars1));
        
        // TODO: we can't loop over a particular configuration of vars1, only the config in which each (non-vars2) variable has state 0.
        int[] configs = IndexForVc.getConfigArr(vars1, vc2);
        System.out.println(Arrays.toString(configs));
        assertEquals(3, configs.length);
        Assert.assertArrayEquals(new int[]{25, 27, 29}, configs);
    }

    public static Var getVar(int id, int numStates) {
        ArrayList<String> stateNames = new ArrayList<String>();
        for (int i=0; i<numStates; i++) {
            stateNames.add("state" + i);
        }
        return new Var(VarType.OBSERVED, numStates, "var"+id, stateNames);
    }
}
