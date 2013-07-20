package edu.jhu.srl;

import java.util.List;
import java.util.Set;

import edu.jhu.data.conll.CoNLL09Sentence;
import edu.jhu.gm.DenseFactor;
import edu.jhu.gm.FactorGraph;
import edu.jhu.gm.ProjDepTreeFactor;
import edu.jhu.gm.ProjDepTreeFactor.LinkVar;
import edu.jhu.gm.Var;
import edu.jhu.gm.Var.VarType;
import edu.jhu.gm.VarSet;

/**
 * A factor graph for SRL.
 * 
 * @author mmitchell
 * @author mgormley
 */
public class SrlFactorGraph extends FactorGraph {

    /**
     * Parameters for the SrlFactorGraph.
     * @author mgormley
     */
    public static class SrlFactorGraphPrm {
        
        /** The structure of the Role variables. */
        public RoleStructure roleStructure = RoleStructure.ALL_PAIRS;
        
        /**
         * Whether the Role variables (if any) that correspond to predicates not
         * marked with a "Y" should be latent, as opposed to predicted
         * variables.
         */
        public boolean makeUnknownPredRolesLatent = true;
        
        /** The type of the link variables. */
        public VarType linkVarType = VarType.LATENT;
        
        /**
         * Whether to include a global factor which constrains the Link
         * variables to form a projective dependency tree.
         */
        public boolean useProjDepTreeFactor = false;
        
        /** Whether to predict predicate sense. */
        public boolean predictSense = true;
    }

    public enum RoleStructure {
        /** Defines Role variables each of the "known" predicates with all possible arguments. */
        PREDS_GIVEN,
        /** The N**2 model. */
        ALL_PAIRS,
    }
    
    public enum SrlFactorTemplate {
        LINK_ROLE,
        ROLE_UNARY,
        LINK_UNARY,
    }
    
    /**
     * An SRL factor, which includes its type (i.e. template).
     * @author mgormley
     */
    public static class SrlFactor extends DenseFactor {

        private SrlFactorTemplate template;

        public SrlFactor(VarSet vars, SrlFactorTemplate template) {
            super(vars);
            this.template = template;
        }
        
        public SrlFactorTemplate getFactorType() {
            return this.template;
        }
        
    }
    
    /**
     * Role variable.
     * 
     * @author mgormley
     */
    public static class RoleVar extends Var {
        
        private int parent;
        private int child;     
        
        public RoleVar(VarType type, int numStates, String name, List<String> stateNames, int parent, int child) {
            super(type, numStates, name, stateNames);
            this.parent = parent;
            this.child = child;
        }

        public int getParent() {
            return parent;
        }

        public int getChild() {
            return child;
        }
        
    }
    

    /**
     * Sense variable. 
     * 
     * @author mgormley
     */
    public static class SenseVar extends Var {
        
        private int parent;
        
        public SenseVar(VarType type, int numStates, String name, List<String> stateNames, int parent) {
            super(type, numStates, name, stateNames);
            this.parent = parent;
        }

        public int getParent() {
            return parent;
        }

    }

    // Parameters for constructing the factor graph.
    private SrlFactorGraphPrm prm;

    // Cache of the variables for this factor graph. These arrays may contain
    // null for variables we didn't include in the model.
    private LinkVar[] rootVars;
    private LinkVar[][] childVars;
    private RoleVar[][] roleVars;

    // TODO: We don't currently predict sense. The main hurdle is getting the
    // set of possible senses for each word.
    private SenseVar[] senseVars;

    // The sentence length.
    private int n;
                
    public SrlFactorGraph(SrlFactorGraphPrm prm, CoNLL09Sentence sent, Set<Integer> knownPreds, CorpusStatistics cs) {
        super();
        this.prm = prm;
        
        n = sent.size();
        
        // Create the Role variables.
        roleVars = new RoleVar[n][n];
        if (prm.roleStructure == RoleStructure.PREDS_GIVEN) {
            // CoNLL-friendly model; preds given
            for (int i : knownPreds) {
                for (int j = 0; j < sent.size();j++) {
                    roleVars[i][j] = createRoleVar(i, j, knownPreds, cs);
                }
            }
        } else if (prm.roleStructure == RoleStructure.ALL_PAIRS) {
            // n**2 model
            for (int i = 0; i < sent.size(); i++) {
                for (int j = 0; j < sent.size();j++) {
                    roleVars[i][j] = createRoleVar(i, j, knownPreds, cs);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported model structure: " + prm.roleStructure);
        }
        
        // Create the Link variables.
        if (prm.useProjDepTreeFactor && prm.linkVarType != VarType.OBSERVED) {
            ProjDepTreeFactor treeFactor = new ProjDepTreeFactor(n, prm.linkVarType);
            rootVars = treeFactor.getRootVars();
            childVars = treeFactor.getChildVars();
            // Add the global factor.
            addFactor(treeFactor);
        } else {
            rootVars = new LinkVar[n];
            childVars = new LinkVar[n][n];
            for (int i = -1; i < sent.size(); i++) {
                for (int j = 0; j < sent.size();j++) {
                    if (i == -1) {
                        rootVars[j] = createLinkVar(i, j);
                    } else {
                        childVars[i][j] = createLinkVar(i, j);
                    }
                }
            }
        }
        
        // Add the factors.
        for (int i = -1; i < sent.size(); i++) {
            for (int j = 0; j < sent.size(); j++) {
                if (i == -1) {
                    // Add unary factors on child Links
                    if (rootVars[j] != null) {
                        addFactor(new DenseFactor(new VarSet(rootVars[j])));
                    }
                } else {
                    // Add unary factors on Roles
                    if (roleVars[i][j] != null) {
                        addFactor(new DenseFactor(new VarSet(roleVars[i][j])));
                    }
                    // Add unary factors on child Links
                    if (childVars[i][j] != null) {
                        addFactor(new DenseFactor(new VarSet(childVars[i][j])));
                    }
                    // Add binary factors between Roles and Links.
                    if (roleVars[i][j] != null && childVars[i][j] != null) {
                        addFactor(new DenseFactor(new VarSet(roleVars[i][j], childVars[i][j])));
                    }
                }
            }
        }
    }

    // ----------------- Creating Variables -----------------

    private RoleVar createRoleVar(int parent, int child, Set<Integer> knownPreds, CorpusStatistics cs) {
        RoleVar roleVar;
        String roleVarName = "Role_" + parent + "_" + child;
        if (!prm.makeUnknownPredRolesLatent || knownPreds.contains((Integer) parent)) {
            roleVar = new RoleVar(VarType.PREDICTED, cs.roleStateNames.size(), roleVarName, cs.roleStateNames, parent, child);            
        } else {
            roleVar = new RoleVar(VarType.LATENT, 0, roleVarName, cs.roleStateNames, parent, child);
        }
        return roleVar;
    }

    private LinkVar createLinkVar(int parent, int child) {
        String linkVarName = LinkVar.getDefaultName(parent,  child);
        return new LinkVar(prm.linkVarType, linkVarName, parent, child);
    }
    
    // ----------------- Public Getters -----------------
    
    /**
     * Get the link var corresponding to the specified parent and child position.
     * 
     * @param parent The parent word position, or -1 to indicate the wall node.
     * @param child The child word position.
     * @return The link variable or null if it doesn't exist.
     */
    public LinkVar getLinkVar(int parent, int child) {
        if (! (-1 <= parent && parent < n && 0 <= child && child < n)) {
            return null;
        }
        
        if (parent == -1) {
            return rootVars[child];
        } else {
            return childVars[parent][child];
        }
    }

    /**
     * Gets a Role variable.
     * @param i The parent position.
     * @param j The child position.
     * @return The role variable or null if it doesn't exist.
     */
    public RoleVar getRoleVar(int i, int j) {
        if (0 <= i && i < roleVars.length && 0 <= j && j < roleVars[i].length) {
            return roleVars[i][j];
        } else {
            return null;
        }
    }

}
