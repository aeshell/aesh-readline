package org.aesh.terminal.utils;

import java.util.Arrays;

/**
 * A dynamically resizable int array builder similar to StringBuilder.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class IntArrayBuilder {

    private int[] data;
    private int size;

    /**
     * Create an empty IntArrayBuilder with initial capacity of 1.
     */
    public IntArrayBuilder() {
        data = new int[1];
        size = 0;
    }

    /**
     * Create an empty IntArrayBuilder with the specified initial capacity.
     *
     * @param size the initial capacity
     */
    public IntArrayBuilder(int size) {
        data = new int[size];
        this.size = 0;
    }

    /**
     * Create an IntArrayBuilder initialized with a copy of the given array.
     *
     * @param ints the initial values to copy into this builder
     */
    public IntArrayBuilder(int[] ints) {
        data = new int[ints.length];
        System.arraycopy(ints, 0, data, 0, ints.length);
        size = data.length;
    }

    /**
     * Append an array of integers to this builder.
     *
     * @param str the integers to append
     * @return this builder for method chaining
     */
    public IntArrayBuilder append(int[] str) {
        int len = str.length;
        ensureCapacityInternal(size + len);
        System.arraycopy(str, 0, data, size, len);
        size += len;
        return this;
    }

    /**
     * Append a single integer to this builder.
     *
     * @param c the integer to append
     * @return this builder for method chaining
     */
    public IntArrayBuilder append(int c) {
        ensureCapacityInternal(size + 1);
        data[size++] = c;
        return this;
    }

    /**
     * Return the contents of this builder as a new int array.
     *
     * @return a new array containing all appended integers
     */
    public int[] toArray() {
        if (size == 0)
            return new int[] {};
        else
            return Arrays.copyOf(data, size);
    }

    /**
     * Return the number of integers currently in this builder.
     *
     * @return the current size
     */
    public int size() {
        return size;
    }

    /**
     * Remove the last entry from this builder.
     * If the builder is empty, this method does nothing.
     */
    public void deleteLastEntry() {
        if (size > 0)
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
