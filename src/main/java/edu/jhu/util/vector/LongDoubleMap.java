package edu.jhu.util.vector;

import java.util.Iterator;

public interface LongDoubleMap extends Iterable<LongDoubleEntry> {

    public abstract void clear();

    // TODO: rename to containsKey.
    public abstract boolean contains(long idx);

    public abstract double get(long idx);

    public abstract double getWithDefault(long idx, double defaultVal);

    public abstract void remove(long idx);

    public abstract void put(long idx, double val);

    public abstract Iterator<LongDoubleEntry> iterator();

    public abstract int size();

    /**
     * Returns the indices.
     */
    public abstract long[] getIndices();

    /**
     * Returns the values.
     */
    public abstract double[] getValues();

}