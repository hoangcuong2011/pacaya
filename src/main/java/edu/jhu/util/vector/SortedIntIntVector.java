package edu.jhu.util.vector;

import edu.jhu.util.Lambda;
import edu.jhu.util.Utilities;
import edu.jhu.util.Lambda.LambdaBinOpInt;
import edu.jhu.util.collections.PIntArrayList;


/**
 * Infinite length sparse vector.
 * 
 * @author mgormley
 *
 */
public class SortedIntIntVector extends SortedIntIntMap {

    boolean norm2Cached = false;
    double norm2Value;
    
    public SortedIntIntVector() {
        super();
    }
    
    public SortedIntIntVector(int[] index, int[] data) {
    	super(index, data);
	}
    
    public SortedIntIntVector(SortedIntIntVector vector) {
    	super(vector);
    }

	public SortedIntIntVector(int[] denseRow) {
		this(Utilities.getIndexArray(denseRow.length), denseRow);
	}

	// TODO: This could be done with a single binary search instead of two.
    public void add(int idx, int val) {
    	int curVal = getWithDefault(idx, 0);
    	put(idx, curVal + val);
    }
    
    public void set(int idx, int val) {
    	put(idx, val);
    }
    
    @Override
	public int get(int idx) {
		return getWithDefault(idx, 0);
	}
    
    public void scale(double multiplier) {
    	for (int i=0; i<used; i++) {
    		values[i] *= multiplier;
    	}
    }

    /** Computes the dot product of this vector with the given vector. */
    public int dot(int[] other) {
        int ret = 0;
        for (int c = 0; c < used && indices[c] < other.length; c++) {
            ret += values[c] * other[indices[c]];
        }
        return ret;
    }

    /** Computes the dot product of this vector with the given vector. */   
    public int dot(SortedIntIntVector y) {
        if (y instanceof SortedIntIntVector) {
            SortedIntIntVector other = ((SortedIntIntVector) y);
            int ret = 0;
            int oc = 0;
            for (int c = 0; c < used; c++) {
                while (oc < other.used) {
                    if (other.indices[oc] < indices[c]) {
                        oc++;
                    } else if (indices[c] == other.indices[oc]) {
                        ret += values[c] * other.values[oc];
                        break;
                    } else {
                        break;
                    }
                }
            }
            return ret;
        } else {
        	throw new IllegalArgumentException("Unhandled type: " + y.getClass());
        }
    }
    

    /**
     * @return A new vector without zeros OR the same vector if it has none.
     */
    public static SortedIntIntVector getWithNoZeroValues(SortedIntIntVector row) {
        int[] origIndex = row.getIndices();
        int[] origData = row.getValues();
        
        // Count and keep track of nonzeros.
        int numNonZeros = 0;
        boolean[] isNonZero = new boolean[row.getUsed()];
        for (int i = 0; i < row.getUsed(); i++) {
            if (origData[i] != 0) {
                isNonZero[i] = true;
                numNonZeros++;
            } else {
                isNonZero[i] = false;
            }
        }
        int numZeros = row.getUsed() - numNonZeros;
        
        if (numZeros > 0) {
            // Create the new vector without the zeros.
            int[] newIndex = new int[numNonZeros];
            int[] newData = new int[numNonZeros];

            int newIdx = 0;
            for (int i = 0; i < row.getUsed(); i++) {
                if (isNonZero[i]) {
                    newIndex[newIdx] = origIndex[i];
                    newData[newIdx] = origData[i];
                    newIdx++;
                }
            }
            return new SortedIntIntVector(newIndex, newData);
        } else {
            return row;
        }
    }
    

    /**
     * TODO: Make a SortedIntIntVectorWithExplicitZeros class and move this method there.
     * 
     * Here we override the zero method so that it doesn't set the number of
     * used values to 0. This ensures that we keep explicit zeros in.
     */
    public SortedIntIntVector zero() {
        java.util.Arrays.fill(values, 0);
        //used = 0;
        return this;
    }

    /** Sets all values in this vector to those in the other vector. */
    public void set(SortedIntIntVector other) {
        this.used = other.used;
        this.indices = Utilities.copyOf(other.indices);
        this.values = Utilities.copyOf(other.values);
    }
    
    /**
     * Computes the Hadamard product (or entry-wise product) of this vector with
     * another.
     */
    // TODO: this could just be a binaryOp call.
    public SortedIntIntVector hadamardProd(SortedIntIntVector other) {
    	SortedIntIntVector ip = new SortedIntIntVector();
        int oc = 0;
        for (int c = 0; c < used; c++) {
            while (oc < other.used) {
                if (other.indices[oc] < indices[c]) {
                    oc++;
                } else if (indices[c] == other.indices[oc]) {
                    ip.set(indices[c], values[c] * other.values[oc]);
                    break;
                } else {
                    break;
                }
            }
        }
        return ip;
    }

    public void add(SortedIntIntVector other) {
        binaryOp(other, new Lambda.IntAdd());
    }
    
    public void subtract(SortedIntIntVector other) {
        binaryOp(other, new Lambda.IntSubtract());
    }
    
    public void binaryOp(SortedIntIntVector other, LambdaBinOpInt lambda) {
        PIntArrayList newIndices = new PIntArrayList(Math.max(this.indices.length, other.indices.length));
        PIntArrayList newValues = new PIntArrayList(Math.max(this.indices.length, other.indices.length));
        int i=0; 
        int j=0;
        while(i < this.used && j < other.used) {
            int e1 = this.indices[i];
            int v1 = this.values[i];
            int e2 = other.indices[j];
            int v2 = other.values[j];
            
            int diff = e1 - e2;
            if (diff == 0) {
                // Elements are equal. Add both of them.
                newIndices.add(e1);
                newValues.add(lambda.call(v1, v2));
                i++;
                j++;
            } else if (diff < 0) {
                // e1 is less than e2, so only add e1 this round.
                newIndices.add(e1);
                newValues.add(lambda.call(v1, 0));
                i++;
            } else {
                // e2 is less than e1, so only add e2 this round.
                newIndices.add(e2);
                newValues.add(lambda.call(0, v2));
                j++;
            }
        }

        // If there is a list that we didn't get all the way through, add all
        // the remaining elements. There will never be more than one such list. 
        assert (!(i < this.used && j < other.used));
        for (; i < this.used; i++) {
            int e1 = this.indices[i];
            int v1 = this.values[i];
            newIndices.add(e1);
            newValues.add(lambda.call(v1, 0));
        }
        for (; j < other.used; j++) {
            int e2 = other.indices[j];
            int v2 = other.values[j];
            newIndices.add(e2);
            newValues.add(lambda.call(0, v2));
        }
        
        this.used = newIndices.size();
        this.indices = newIndices.toNativeArray();
        this.values = newValues.toNativeArray();
    }
    
    /**
     * Counts the number of unique indices in two arrays.
     * @param indices1 Sorted array of indices.
     * @param indices2 Sorted array of indices.
     */
    public static int countUnique(int[] indices1, int[] indices2) {
        int numUniqueIndices = 0;
        int i = 0;
        int j = 0;
        while (i < indices1.length && j < indices2.length) {
            if (indices1[i] < indices2[j]) {
                numUniqueIndices++;
                i++;
            } else if (indices2[j] < indices1[i]) {
                numUniqueIndices++;
                j++;
            } else {
                // Equal indices.
                i++;
                j++;
            }
        }
        for (; i < indices1.length; i++) {
            numUniqueIndices++;
        }
        for (; j < indices2.length; j++) {
            numUniqueIndices++;
        }
        return numUniqueIndices;
    }
    
    public SortedIntIntVector getElementwiseSum(SortedIntIntVector other) {
        SortedIntIntVector sum = new SortedIntIntVector(this);
        sum.add(other);
        return sum;
    }
    
    public SortedIntIntVector getElementwiseDiff(SortedIntIntVector other) {
        SortedIntIntVector sum = new SortedIntIntVector(this);
        sum.subtract(other);
        return sum;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < used; i++) {
            sb.append(indices[i]);
            sb.append(":");
            sb.append(values[i]);
            if (i + 1 < used) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Returns true if the input vector is equal to this one.
     */
    @Override
    public boolean equals(Object obj) {
    	if (obj instanceof SortedIntIntVector) {
    		SortedIntIntVector other = (SortedIntIntVector) obj;

            SortedIntIntVector v1 = SortedIntIntVector.getWithNoZeroValues(this);
            SortedIntIntVector v2 = SortedIntIntVector.getWithNoZeroValues(other);
            
	        if (v2.size() != v1.size()) {
	            return false;
	        }
	        // This is slow, but correct.
	        for (IntIntEntry ve : v1) {
	            if (ve.get() != v2.get(ve.index())) {
	                return false;
	            }
	        }
	        for (IntIntEntry ve : v2) {
	            if (ve.get() != v1.get(ve.index())) {
	                return false;
	            }
	        }
	        return true;
    	}
    	return false;
    }
    
    @Override
    public int hashCode() {
    	throw new RuntimeException("not implemented");
    }

}