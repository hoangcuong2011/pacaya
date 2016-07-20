package edu.jhu.pacaya.gm.model;

import java.io.Serializable;
import java.util.HashMap;


/**
 * A configuration of (i.e. assignment to) a set of variables. 
 * 
 * @author mgormley
 *
 */
// TODO: Maybe rename this to VarsAssignment.
public class VarConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    // TODO: Internally, we should store an int[] not a HashMap.
    private HashMap<Var,Integer> config;
    private VarSet vars;
    
    /** Constructs an empty variable configuration. */
    public VarConfig() {
        config = new HashMap<Var,Integer>();
        vars = new VarSet();
    }

    /** Constructs a configuration from an array of variables and corresponding integer assignments */
    public VarConfig(Var[] vars, int... assignments) {
        this();
        assert vars.length == assignments.length;
        for (int i = 0; i < vars.length; i++) {
            put(vars[i], assignments[i]);
        }
    }
    
    /** Constructs a variable configuration by adding each of the configs in order. */
    public VarConfig(VarConfig... configs) {
        this();
        for (VarConfig other : configs) {
            put(other);
        }
    }

    /**
     * Gets the index of this configuration for the variable set it represents.
     * 
     * This is used to provide a unique index for each setting of the the
     * variables in a VarSet.
     */
    public int getConfigIndex() {
        return getConfigIndexOfSubset(vars);
    }

    /**
     * Gets the index of this configuration for the given variable set.
     * 
     * This is used to provide a unique index for each setting of the the
     * variables in a VarSet.
     */
    public int getConfigIndexOfSubset(VarSet vars) {
        int configIndex = 0;
        int numStatesProd = 1;
        for (int v=vars.size()-1; v >= 0; v--) {
            Var var = vars.get(v);
            int state = config.get(var);
            configIndex += state * numStatesProd;
            numStatesProd *= var.getNumStates();
            if (numStatesProd <= 0) {
                throw new IllegalStateException("Integer overflow when computing config index -- this can occur if trying to compute the index of a high arity factor: " + numStatesProd);
            }
        }
        return configIndex;
    }

    /** Sets all variable assignments in other. */
    public void put(VarConfig other) { 
        this.config.putAll(other.config);
        this.vars.addAll(other.vars);
    }
    
    /** Sets the state value to stateName for the given variable, adding it if necessary. */
    public void put(Var var, String stateName) {
        int state = var.getState(stateName);
        if (state == -1) {
            throw new IllegalArgumentException("Unknown state name " + stateName + " for var " + var);
        }
        put(var, state);
    }
    
    /** Sets the state value to state for the given variable, adding it if necessary. */
    public void put(Var var, int state) {
        if (state < 0 || state >= var.getNumStates()) {
            throw new IllegalArgumentException("Invalid state idx " + state + " for var " + var);
        }
        config.put(var, state);
        vars.add(var);
    }
    
    public boolean contains(Var var) {
        return config.containsKey(var);
    }

    /** Gets the state name (in this config) for a given variable. */
    public String getStateName(Var var) {
        return var.getStateNames().get(config.get(var));
    }
    
    /** Gets the state (in this config) for a given variable. */
    public int getState(Var var) {
        Integer state = config.get(var);
        if (state == null) {
            throw new RuntimeException("VarConfig does not contain var: " + var);
        }
        return state;
    }
    
    /** Gets the state (in this config) for a given variable if it exists, or the default otherwise. */
    public int getState(Var var, int defaultState) {
        Integer state = config.get(var);
        if (state == null) {
            return defaultState;
        } else {
            return state;
        }
    }

    /** Gets the variable set. */
    public VarSet getVars() {
        return vars;
    }

    /** Gets the number of variables in this configuration. */
    public int size() {
        return vars.size();
    }
    
    /** Gets a new variable configuration that contains only a subset of the variables. */
    public VarConfig getSubset(VarSet subsetVars) {
        if (!vars.isSuperset(subsetVars)) {
            throw new IllegalStateException("This config does not contain all the given variables.");
        }
        return getIntersection(subsetVars);
    }

    /** Gets a new variable configuration that keeps only variables in otherVars. */
    public VarConfig getIntersection(Iterable<Var> otherVars) {
        VarConfig subset = new VarConfig();
        for (Var v : otherVars) {
            Integer state = config.get(v);
            if (state != null) {
                subset.put(v, state);
            }
        }
        return subset;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((config == null) ? 0 : config.hashCode());
        result = prime * result + ((vars == null) ? 0 : vars.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VarConfig other = (VarConfig) obj;
        if (config == null) {
            if (other.config != null)
                return false;
        }
        if (vars == null) {
            if (other.vars != null)
                return false;
        }
        
        if (!config.equals(other.config))
            return false;
        if (!vars.equals(other.vars))
            return false;
        return true;
    }

    @Deprecated
    public String getStringName() {
        StringBuilder configSb = new StringBuilder();
        int i=0;
        for (Var v : vars) {
            if (i > 0) {
                configSb.append(",");
            }
            configSb.append(v.getName());
            configSb.append("=");
            if (v.getStateNames() != null) {
                configSb.append(getStateName(v));
            } else {
                configSb.append(getState(v));
            }
            i++;
        }
        return configSb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder configSb = new StringBuilder();
        int i=0;
        for (Var v : vars) {
            if (i > 0) {
                configSb.append(", ");
            }
            configSb.append(v.getName());
            configSb.append("=");
            if (v.getStateNames() != null) {
                configSb.append(getStateName(v));
            } else {
                configSb.append("id:");
                configSb.append(getState(v));
            }
            i++;
        }
        return "VarConfig [config=[" + configSb.toString() + "], vars=" + vars + "]";
    }
        
}
