package edu.jhu.hltcoe.util;

import static org.junit.Assert.assertTrue;
import edu.jhu.hltcoe.gridsearch.rlt.Rlt;
import edu.jhu.hltcoe.gridsearch.rlt.SymmetricMatrix.SymVarMat;
import edu.jhu.hltcoe.math.Vectors;
import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import ilog.concert.IloException;
import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.BasisStatus;

import java.util.ArrayList;
import java.util.Arrays;

import no.uib.cipr.matrix.sparse.FastSparseVector;
import no.uib.cipr.matrix.sparse.SparseVector;

import org.junit.Assert;

public class CplexUtils {

    public static class CplexRowUpdates {
        private TIntArrayList rowIdxs;
        private ArrayList<SparseVector> coefs;

        public CplexRowUpdates() {
            rowIdxs = new TIntArrayList();
            coefs = new ArrayList<SparseVector>();
        }

        public void add(int rowIdx, SparseVector coef) {
            rowIdxs.add(rowIdx);
            coefs.add(coef);
        }

        public void updateRowsInMatrix(IloLPMatrix mat) throws IloException {
            TIntArrayList rowInd = new TIntArrayList();
            TIntArrayList colInd = new TIntArrayList();
            TDoubleArrayList val = new TDoubleArrayList();
            for (int i = 0; i < rowIdxs.size(); i++) {
                int rowind = rowIdxs.get(i);
                SparseVector row = coefs.get(i);
                rowInd.add(getRowIndArray(row, rowind));
                colInd.add(row.getIndex());
                val.add(row.getData());
            }
            mat.setNZs(rowInd.toNativeArray(), colInd.toNativeArray(), val.toNativeArray());
        }

        public ArrayList<SparseVector> getAllCoefs() {
            return coefs;
        }

        public void setAllCoefs(ArrayList<SparseVector> coefs) {
            this.coefs = coefs;
        }

        /**
         * Gets an int array of the same length as row.getIndex() and filled
         * with rowind.
         */
        private static int[] getRowIndArray(SparseVector row, int rowind) {
            int[] array = new int[row.getIndex().length];
            Arrays.fill(array, rowind);
            return array;
        }
    }

    public static class CplexRow {
        private double lb;
        private double ub;
        private SparseVector coefs;
        private String name;

        public CplexRow(double lb, SparseVector coefs, double ub, String name) {
            super();
            this.lb = lb;
            this.coefs = coefs;
            this.ub = ub;
            this.name = name;
        }

        public double getLb() {
            return lb;
        }

        public double getUb() {
            return ub;
        }

        public SparseVector getCoefs() {
            return coefs;
        }

        public String getName() {
            return name;
        }
    }

    public static class CplexRows {
        private TDoubleArrayList lbs;
        private TDoubleArrayList ubs;
        private ArrayList<SparseVector> coefs;
        private ArrayList<String> names;
        private boolean setNames;

        public CplexRows(boolean setNames) {
            lbs = new TDoubleArrayList();
            coefs = new ArrayList<SparseVector>();
            ubs = new TDoubleArrayList();
            names = new ArrayList<String>();
            this.setNames = setNames;
        }

        public int addRow(double lb, SparseVector coef, double ub) {
            return addRow(lb, coef, ub, null);
        }

        public void addRow(CplexRow row) {
            addRow(row.getLb(), row.getCoefs(), row.getUb(), row.getName());
        }

        public int addRow(double lb, SparseVector coef, double ub, String name) {
            lbs.add(lb);
            coefs.add(coef);
            ubs.add(ub);
            names.add(name);
            return lbs.size() - 1;
        }

        /**
         * Construct and return the ith row.
         */
        public CplexRow get(int i) {
            return new CplexRow(lbs.get(i), coefs.get(i), ubs.get(i), names.get(i));
        }

        /**
         * Adds the stored rows to the matrix.
         * 
         * @return The index of the first newly added row.
         */
        public int addRowsToMatrix(IloLPMatrix mat) throws IloException {
            int[][] ind = new int[coefs.size()][];
            double[][] val = new double[coefs.size()][];
            for (int i = 0; i < coefs.size(); i++) {
                ind[i] = coefs.get(i).getIndex();
                val[i] = coefs.get(i).getData();
            }
            int startRow = mat.addRows(lbs.toNativeArray(), ubs.toNativeArray(), ind, val);
            if (setNames) {
                IloRange[] ranges = mat.getRanges();
                for (int i = 1; i <= names.size(); i++) {
                    String name = names.get(names.size() - i);
                    if (name != null) {
                        ranges[ranges.length - i].setName(name);
                    }
                }
            }
            return startRow;
        }

        public int getNumRows() {
            return lbs.size();
        }

        public ArrayList<SparseVector> getAllCoefs() {
            return coefs;
        }

        public void setAllCoefs(ArrayList<SparseVector> coefs) {
            this.coefs = coefs;
        }

    }

    /**
     * The cutoff point at which to treat the value as positive infinity.
     */
    public static final double CPLEX_POS_INF_CUTOFF = 1e19;
    /**
     * The cutoff point at which to treat the value as negative infinity.
     */
    public static final double CPLEX_NEG_INF_CUTOFF = -1e19;

    public static boolean isInfinite(double v) {
        if (v < CPLEX_NEG_INF_CUTOFF || CPLEX_POS_INF_CUTOFF < v) {
            return true;
        }
        return false;
    }

    /**
     * Helper method for getting a 3D array of CPLEX variables.
     * 
     * @throws IloException
     */
    public static double[][][] getValues(IloCplex cplex, IloNumVar[][][] vars) throws IloException {
        double[][][] vals = new double[vars.length][][];
        for (int i = 0; i < vars.length; i++) {
            vals[i] = getValues(cplex, vars[i]);
        }
        return vals;
    }

    /**
     * Helper method for getting a 2D array of CPLEX variables.
     * 
     * @throws IloException
     */
    public static double[][] getValues(IloCplex cplex, IloNumVar[][] vars) throws IloException {
        double[][] vals = new double[vars.length][];
        for (int i = 0; i < vars.length; i++) {
            vals[i] = getValues(cplex, vars[i]);
        }
        return vals;
    }

    /**
     * Helper method for getting a 2D array of CPLEX variables.
     * 
     * @throws IloException
     */
    public static double[][] getValues(IloCplex cplex, SymVarMat vars) throws IloException {
        double[][] vals = new double[vars.getNrows()][];
        for (int i = 0; i < vars.getNrows(); i++) {
            IloNumVar[] varsI = vars.getRowAsArray(i);
            vals[i] = getValues(cplex, varsI);
        }
        return vals;
    }

    /**
     * Helper method for getting a 1D array of CPLEX variables.
     * 
     * @throws IloException
     */
    public static double[] getValues(IloCplex cplex, IloNumVar[] vars) throws IloException {
        double[] vals = new double[vars.length];
        for (int i = 0; i < vars.length; i++) {
            if (vars[i] != null) {
                vals[i] = cplex.getValue(vars[i]);
            } else {
                vals[i] = 0.0; // TODO: Double.NaN;
            }
        }
        return vals;
    }

    public static void addRows(IloLPMatrix mat, IloRange[][][] ranges) throws IloException {
        for (int i = 0; i < ranges.length; i++) {
            addRows(mat, ranges[i]);
        }
    }

    public static void addRows(IloLPMatrix mat, IloRange[][] ranges) throws IloException {
        for (int i = 0; i < ranges.length; i++) {
            addRows(mat, ranges[i]);
        }
    }

    public static void addRows(IloLPMatrix mat, IloRange[] ranges) throws IloException {
        for (int i = 0; i < ranges.length; i++) {
            if (ranges[i] != null) {
                mat.addRow(ranges[i]);
            }
        }
    }

    // -------- JUnit Assertions -----------

    public static void assertContainsRow(IloLPMatrix rltMat, double[] denseRow) throws IloException {
        int nCols = rltMat.getNcols();
        assertTrue(nCols == denseRow.length);
        int nRows = rltMat.getNrows();
        double[] lbs = new double[nRows];
        double[] ubs = new double[nRows];
        int[][] ind = new int[nRows][];
        double[][] val = new double[nRows][];
        rltMat.getRows(0, nRows, lbs, ubs, ind, val);

        FastSparseVector expectedRow = new FastSparseVector(denseRow);

        for (int m = 0; m < nRows; m++) {
            FastSparseVector row = new FastSparseVector(ind[m], val[m]);
            // System.out.println(row + "\n" + expectedRow + "\n" +
            // row.equals(expectedRow, 1e-13));
            if (row.equals(expectedRow, 1e-13)) {
                return;
            }
        }
        Assert.fail("Matrix does not contain row: " + Arrays.toString(denseRow));
    }

    public static void assertContainsRow(IloLPMatrix rltMat, double[] denseRow, double lb, double ub)
            throws IloException {
        int nCols = rltMat.getNcols();
        assertTrue(nCols == denseRow.length);
        int nRows = rltMat.getNrows();
        double[] lbs = new double[nRows];
        double[] ubs = new double[nRows];
        int[][] ind = new int[nRows][];
        double[][] val = new double[nRows][];
        rltMat.getRows(0, nRows, lbs, ubs, ind, val);

        FastSparseVector expectedRow = new FastSparseVector(denseRow);

        for (int m = 0; m < nRows; m++) {
            FastSparseVector row = new FastSparseVector(ind[m], val[m]);
            // System.out.println(row + "\n" + expectedRow + "\n" +
            // row.equals(expectedRow, 1e-13));
            if (row.equals(expectedRow, 1e-13) && Utilities.equals(lb, lbs[m], 1e-13)
                    && Utilities.equals(ub, ubs[m], 1e-13)) {
                return;
            }
        }
        Assert.fail("Matrix does not contain row: " + Arrays.toString(denseRow));
    }

    /**
     * Gets the upper bound of the product of two variables.
     */
    public static double getUpperBound(IloNumVar var1, IloNumVar var2) throws IloException {
        double[] prods = getProductsOfBounds(var1, var2);
        double max = Vectors.max(prods);
        assert (CPLEX_NEG_INF_CUTOFF < max);
        if (isInfinite(max)) {
            return Rlt.CPLEX_POS_INF;
        } else {
            return max;
        }
    }

    /**
     * Gets the lower bound of the product of two variables.
     */
    public static double getLowerBound(IloNumVar var1, IloNumVar var2) throws IloException {
        double[] prods = getProductsOfBounds(var1, var2);
        double min = Vectors.min(prods);
        assert (min < CPLEX_POS_INF_CUTOFF);
        if (isInfinite(min)) {
            return Rlt.CPLEX_NEG_INF;
        } else {
            return min;
        }
    }

    /**
     * Gets all possible products of the variables' bounds.
     */
    private static double[] getProductsOfBounds(IloNumVar var1, IloNumVar var2) throws IloException {
        double[] prods = new double[4];
        prods[0] = var1.getLB() * var2.getLB();
        prods[1] = var1.getLB() * var2.getUB();
        prods[2] = var1.getUB() * var2.getLB();
        prods[3] = var1.getUB() * var2.getUB();
        return prods;
    }

    /**
     * Gets the dual objective value from CPLEX.
     * 
     * This method is currently broken. We would need to test it on a CPLEX
     * problem where we can do early stopping as we do in RLT. Then we could
     * compare against the objective value given by the dual simplex algorithm,
     * which (it turns out) is exactly what we want anyway.
     */
    @Deprecated
    public static double getDualObjectiveValue(IloCplex cplex, IloLPMatrix mat) throws IloException {
        if (!cplex.isDualFeasible()) {
            throw new IllegalStateException("No objective value");
        }
        double[] duals = cplex.getDuals(mat);
        double[] redCosts = cplex.getReducedCosts(mat);
        IloNumVar[] numVars = mat.getNumVars();
        BasisStatus[] varBasis = cplex.getBasisStatuses(numVars);
        BasisStatus[] conBasis = cplex.getBasisStatuses(mat.getRanges());

        int numRows = mat.getNrows();
        double[] lb = new double[numRows];
        double[] ub = new double[numRows];
        int[][] Aind = new int[numRows][];
        double[][] Aval = new double[numRows][];
        mat.getRows(0, numRows, lb, ub, Aind, Aval);

        double dualObjVal = 0.0;
        for (int i = 0; i < duals.length; i++) {
            if (Utilities.equals(lb[i], ub[i], 1e-13) && !isInfinite(lb[i])) {
                dualObjVal += duals[i] * lb[i];
            } else {
                if (!isInfinite(lb[i]) && conBasis[i] == BasisStatus.AtLower) {
                    dualObjVal += duals[i] * lb[i];
                } else if (!isInfinite(ub[i]) && conBasis[i] == BasisStatus.AtUpper) {
                    dualObjVal += duals[i] * ub[i];
                } else if (conBasis[i] == BasisStatus.AtUpper || conBasis[i] == BasisStatus.AtLower) {
                    // In certain cases, the BasisStatus for a constraint says
                    // AtLower, but it has a finite upper bound and an infinite
                    // lower bound.
                    if (!isInfinite(lb[i]) && isInfinite(ub[i])) {
                        dualObjVal += duals[i] * lb[i];
                    } else if (isInfinite(lb[i]) && !isInfinite(ub[i])) {
                        dualObjVal += duals[i] * ub[i];
                    }
                }
            }
        }

        for (int i = 0; i < redCosts.length; i++) {
            double varLb = numVars[i].getLB();
            double varUb = numVars[i].getUB();
            if (varBasis[i] == BasisStatus.AtLower && !Utilities.equals(varLb, 0.0, 1e-13) && !isInfinite(varLb)) {
                dualObjVal += redCosts[i] * varLb;
            } else if (varBasis[i] == BasisStatus.AtUpper && !Utilities.equals(varUb, 0.0, 1e-13) && !isInfinite(varUb)) {
                dualObjVal += redCosts[i] * varUb;
            } else if (varBasis[i] == BasisStatus.AtLower || varBasis[i] == BasisStatus.AtUpper) {
                if (!isInfinite(varLb) && isInfinite(varUb)) {
                    dualObjVal += redCosts[i] * varLb;
                } else if (isInfinite(varLb) && !isInfinite(varUb)) {
                    dualObjVal += redCosts[i] * varUb;
                }
            }
        }

        return dualObjVal;
    }

}
