package org.aesh.util;

import java.util.Arrays;

/**
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class IntArrayBuilder {

    private int[] data;
    private int size;

    public IntArrayBuilder() {
        data = new int[1];
        size = 0;
    }

    public IntArrayBuilder(int size) {
        data = new int[size];
        this.size = 0;
    }

    public IntArrayBuilder(int[] ints) {
        data = new int[ints.length];
        System.arraycopy(ints, 0, data, 0, ints.length);
        size = data.length;
    }

    public IntArrayBuilder append(int[] str) {
        int len = str.length;
        ensureCapacityInternal(size + len);
        System.arraycopy(str, 0, data, size, len);
        size += len;
        return this;
    }

    public IntArrayBuilder append(int c) {
        ensureCapacityInternal(size + 1);
        data[size++] = c;
        return this;
    }

    public int[] toArray() {
        if(size == 0)
            return new int[]{};
        else
            return Arrays.copyOf(data, size);
    }

    public int size() {
        return size;
    }

    public void deleteLastEntry() {
        if(size > 0)
            size--;
    }

    private void ensureCapacityInternal(int minimumCapacity) {
        // overflow-conscious code
        if (minimumCapacity - data.length > 0)
            expandCapacity(minimumCapacity);
    }

    private void expandCapacity(int minimumCapacity) {
        int newCapacity = data.length * 2 + 2;
        if (newCapacity - minimumCapacity < 0)
            newCapacity = minimumCapacity;
        if (newCapacity < 0) {
            if (minimumCapacity < 0) // overflow
                throw new OutOfMemoryError();
            newCapacity = Integer.MAX_VALUE;
        }
        data = Arrays.copyOf(data, newCapacity);
    }


}
