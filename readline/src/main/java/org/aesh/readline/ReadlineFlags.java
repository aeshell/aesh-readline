package org.aesh.readline;

import java.util.EnumSet;

/**
 * Utility class for creating common combinations of {@link ReadlineFlag} values.
 */
public final class ReadlineFlags {

    private ReadlineFlags() {
        // utility class
    }

    /**
     * Creates an empty set of readline flags.
     *
     * @return an empty EnumSet of ReadlineFlag
     */
    public static EnumSet<ReadlineFlag> none() {
        return EnumSet.noneOf(ReadlineFlag.class);
    }

    /**
     * Creates a set containing the specified readline flags.
     *
     * @param first the first flag
     * @param rest additional flags
     * @return an EnumSet containing the specified flags
     */
    public static EnumSet<ReadlineFlag> of(ReadlineFlag first, ReadlineFlag... rest) {
        return EnumSet.of(first, rest);
    }

    /**
     * Creates a set containing all readline flags.
     *
     * @return an EnumSet containing all ReadlineFlag values
     */
    public static EnumSet<ReadlineFlag> all() {
        return EnumSet.allOf(ReadlineFlag.class);
    }
}
