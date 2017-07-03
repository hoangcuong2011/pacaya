package edu.jhu.pacaya.gm.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.util.collections.SmallSet;
import edu.jhu.prim.iter.IntIter;
import edu.jhu.prim.list.IntArrayList;

/**
 * A subset of the variables.
 * 
 * Implementation Note: Internally, we use a simple ArrayList to store the
 * variables. This was inspired by libDAI which makes an analogous design
 * choice for the representation of a variable set which we expect to be
 * quite small.
 * 
 * @author mgormley
 */
// TODO: Move all the VarConfig related methods to VarConfig.
public class VarSet extends SmallSet<Var> {

    private static final long serialVersionUID = 1L;

    public VarSet() {
        super();
    }
    
    /** Copy constructor. */
    public VarSet(VarSet vars) {
        super(vars);
    }
    
    /** Constructs a variable set containing only this variable. */
    public VarSet(Var var) {
        super(1);
        add(var);
    }

    /** Constructs a new variable set which is the union of vars1 and vars2. */
    @SuppressWarnings("unchecked")
    public VarSet(VarSet vars1, VarSet vars2) {
        super(vars1, vars2);
    }

    /** Constucts a variable set containing all the given vars. */
    public VarSet(Var... vars) {
        super(vars.length);
        addAll(Arrays.asList(vars));
    }

    /**
     * Gets an iterator over the configurations of the variables for this
     * variable set, such that the c'th configuration in the iterator will
     * correspond to the variable setting where all the values of the
     * variables in this set take on the same values as the c'th
     * configuration of the variables in vars, and all the other variables
     * take on the zero state.
     * 
     * This is a trick from libDAI for efficiently computing factor
     * products.
     * 
     * @param vars The variable set to which the iterator should be aligned.
     * @return The iterator.
     */
    public IntIter getConfigIter(VarSet vars) {
        return new IndexFor(this, vars);
    }
    
    /**
     * Gets an array version of the configuration iterator.
     * @see edu.jhu.pacaya.gm.model.VarSet#getConfigIter
     */
    public int[] getConfigArr(VarSet vars) {        
        IntArrayList a = new IntArrayList(vars.calcNumConfigs());
        IntIter iter = getConfigIter(vars);
        while (iter.hasNext()) {
            a.add(iter.next());
        }
        return a.toNativeArray();
    }

    /**
     * Gets the number of possible configurations for this set of variables.
     */
    // TODO: ensure that this isn't called within any for-loops.
    public int calcNumConfigs() {
        if (this.size() == 0) {
            return 0;
        }
        int numConfigs = 1;
        for (Var var : this) {
            numConfigs *= var.getNumStates();
            if (numConfigs <= 0) {
                throw new IllegalStateException("Integer overflow. This can occur if computing the number of configs for factor of high arity.");
            }
        }
        return numConfigs;
    }
    
    /**
     * Gets the variable configuration corresponding to the given configuration index.
     * @param configIndex The config index.
     * @return The variable configuration.
     */
    public VarConfig getVarConfig(int configIndex) {
        // Configuration as an array of ints, one for each variable.
        int i;
        int[] states = getVarConfigAsArray(configIndex);
        
        VarConfig config = new VarConfig();
        i=0;
        for (Var var : this) {
            config.put(var, states[i++]);
        }
        return config;
    }

    /**
     * Gets the variable configuration corresponding to the given configuration
     * index as an array.
     * 
     * @param configIndex
     *            The config index.
     * @return The variable configuration as an array of ints, where states[i]
     *         is the state of the i'th variable in this variable set.
     * @see edu.jhu.pacaya.gm.model.VarSet#getVarConfig
     */
    public int[] getVarConfigAsArray(int configIndex) {
        int[] states = new int[this.size()];
        getVarConfigAsArray(configIndex, states);
        return states;
    }
    
    /**
     * this is the "no-allocation" version of the non-void method of the same name.
     */
    public void getVarConfigAsArray(int configIndex, int[] putInto) {
        if(putInto.length != this.size())
            throw new IllegalArgumentException();
        int i = putInto.length - 1;
        for (int v=this.size()-1; v >= 0; v--) {
            Var var = this.get(v);
            putInto[i--] = configIndex % var.getNumStates();
            configIndex /= var.getNumStates();
        }
    }

    /**
     * Returns an array holding the number of values the corresponding variables can take on
     */
    public int[] getDims() {
        int[] dims = new int[size()];
        for (int i = 0; i < size(); i++) {
            dims[i] = get(i).getNumStates();
        }
        return dims;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VarSet [");
        sb.append("size=");
        sb.append(size());
        sb.append(", list=[");
        int i=0;
        for (Var v : this) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(v.getName());
            i++;
        }
        sb.append("]]");
        return sb.toString();
    }

    public VarSet getVarsOfType(VarType type) {
        return getVarsOfType(this, type);
    }
    
    /** Gets the subset of vars with the specified type. */
    public static List<Var> getVarsOfType(List<Var> vars, VarType type) {
        ArrayList<Var> subset = new ArrayList<Var>();
        for (Var v : vars) {
            if (v.getType() == type) {
                subset.add(v);
            }
        }
        return subset;      
    }

    /** Gets the subset of vars with the specified type. */
    public static VarSet getVarsOfType(VarSet vars, VarType type) {
        VarSet subset = new VarSet();
        for (Var v : vars) {
            if (v.getType() == type) {
                subset.add(v);
            }
        }
        return subset;      
    }
            
    public boolean hasVarsOfType(VarType type) {
        for (Var v : this) {
            if (v.getType() == type) {
                return true;
            }
        }
        return false;
    }
    
}