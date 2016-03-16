package org.jboss.aesh.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class IntArrayBuilderTest {

    @Test
    public void appendInts() {
        IntArrayBuilder builder = new IntArrayBuilder();
        builder.append(1);
        assertArrayEquals(new int[] {1}, builder.toArray());
        builder.append(2);
        builder.append(3);
        assertArrayEquals(new int[] {1,2,3}, builder.toArray());

        builder = new IntArrayBuilder(3);
        builder.append(3);
        assertArrayEquals(new int[] {3}, builder.toArray());
        builder.append(2);
        builder.append(1);
        assertArrayEquals(new int[] {3,2,1}, builder.toArray());
    }

    @Test
    public void appentIntArray() {
        IntArrayBuilder builder = new IntArrayBuilder();
        builder.append(new int[]{1,2,3});
        assertArrayEquals(new int[] {1,2,3}, builder.toArray());
        builder.append(new int[]{1,2,3});
        assertArrayEquals(new int[] {1,2,3,1,2,3}, builder.toArray());
        builder.append(1);
        assertArrayEquals(new int[] {1,2,3,1,2,3,1}, builder.toArray());
        builder.append(2);
        builder.append(3);
        assertArrayEquals(new int[] {1,2,3,1,2,3,1,2,3}, builder.toArray());

        builder = new IntArrayBuilder(new int[]{1,2,3});
        assertArrayEquals(new int[] {1,2,3}, builder.toArray());

    }
}
