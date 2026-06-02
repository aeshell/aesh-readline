/*
 * Fuzzy matching character classification, ported from fzf.
 *
 * Original: https://github.com/junegunn/fzf/blob/master/src/algo/algo.go
 * License: MIT (https://github.com/junegunn/fzf/blob/master/LICENSE)
 *
 * Classifies characters into categories (white, delimiter, lower, upper, etc.)
 * and provides a precomputed bonus matrix for scoring matches at word boundaries,
 * camelCase transitions, and other special positions.
 */
package org.aesh.readline.fuzzy;

/**
 * Character classification and bonus matrix for fuzzy matching.
 * <p>
 * This is a direct port of fzf's character classification system.
 * Characters are classified into one of 7 categories, and a precomputed
 * {@code bonusMatrix[prevClass][curClass]} lookup table provides the bonus
 * score for matching at each position based on the transition between
 * adjacent character classes.
 * <p>
 * The ASCII fast path ({@link #classOfAscii(int)}) uses a precomputed
 * 128-entry lookup table for O(1) classification of ASCII characters.
 */
public final class CharClass {

    // Character class constants (ordinals match fzf's charClass iota)
    public static final int WHITE = 0;
    public static final int NON_WORD = 1;
    public static final int DELIMITER = 2;
    public static final int LOWER = 3;
    public static final int UPPER = 4;
    public static final int LETTER = 5;
    public static final int NUMBER = 6;

    static final int CLASS_COUNT = 7;

    // Precomputed ASCII character class lookup (0..127)
    private static final int[] ASCII_CLASSES = new int[128];

    // Precomputed bonus matrix: bonusMatrix[prevClass][curClass]
    // Indexed by character class constants above
    private static final short[][] BONUS_MATRIX = new short[CLASS_COUNT][CLASS_COUNT];

    // Delimiter characters (matching fzf)
    private static final String DELIMITER_CHARS = "/,:;|";
    private static final String WHITE_CHARS = " \t\n\u000B\f\r\u0085\u00A0";

    private CharClass() {
        // Utility class
    }

    static {
        // Initialize ASCII character classes
        for (int i = 0; i < 128; i++) {
            if (i >= 'a' && i <= 'z') {
                ASCII_CLASSES[i] = LOWER;
            } else if (i >= 'A' && i <= 'Z') {
                ASCII_CLASSES[i] = UPPER;
            } else if (i >= '0' && i <= '9') {
                ASCII_CLASSES[i] = NUMBER;
            } else if (WHITE_CHARS.indexOf(i) >= 0) {
                ASCII_CLASSES[i] = WHITE;
            } else if (DELIMITER_CHARS.indexOf(i) >= 0) {
                ASCII_CLASSES[i] = DELIMITER;
            } else {
                ASCII_CLASSES[i] = NON_WORD;
            }
        }
    }

    /**
     * Initialize the bonus matrix for a given scheme.
     * Must be called before using the bonus matrix.
     *
     * @param scheme the scoring scheme to use
     */
    public static void init(FuzzyScheme scheme) {
        for (int i = 0; i < CLASS_COUNT; i++) {
            for (int j = 0; j < CLASS_COUNT; j++) {
                BONUS_MATRIX[i][j] = bonusFor(i, j, scheme);
            }
        }
    }

    /**
     * Classify a character (code point).
     * Uses the fast ASCII lookup table for code points 0-127,
     * falls back to Unicode classification for non-ASCII.
     *
     * @param codePoint the character code point
     * @return the character class constant
     */
    public static int classOf(int codePoint) {
        if (codePoint < 128) {
            return ASCII_CLASSES[codePoint];
        }
        return classOfNonAscii(codePoint);
    }

    /**
     * Fast ASCII-only classification.
     *
     * @param ch ASCII character (0-127)
     * @return the character class constant
     */
    static int classOfAscii(int ch) {
        return ASCII_CLASSES[ch];
    }

    /**
     * Classify a non-ASCII character using Unicode properties.
     */
    private static int classOfNonAscii(int codePoint) {
        if (Character.isLowerCase(codePoint)) {
            return LOWER;
        } else if (Character.isUpperCase(codePoint)) {
            return UPPER;
        } else if (Character.isDigit(codePoint)) {
            return NUMBER;
        } else if (Character.isLetter(codePoint)) {
            return LETTER;
        } else if (Character.isWhitespace(codePoint)) {
            return WHITE;
        } else if (DELIMITER_CHARS.indexOf(codePoint) >= 0) {
            return DELIMITER;
        }
        return NON_WORD;
    }

    /**
     * Look up the bonus for a character at a given position based on the
     * previous and current character classes.
     *
     * @param prevClass the class of the preceding character
     * @param curClass the class of the current character
     * @return the bonus score
     */
    public static short bonus(int prevClass, int curClass) {
        return BONUS_MATRIX[prevClass][curClass];
    }

    /**
     * Calculate the bonus for matching at a given position in the input.
     *
     * @param input the input code points
     * @param idx the position in the input
     * @param scheme the scoring scheme (for initialCharClass)
     * @return the bonus score
     */
    public static short bonusAt(int[] input, int idx, FuzzyScheme scheme) {
        if (idx == 0) {
            return scheme.bonusBoundaryWhite;
        }
        return BONUS_MATRIX[classOf(input[idx - 1])][classOf(input[idx])];
    }

    /**
     * Calculate the bonus for a transition between two character classes.
     */
    private static short bonusFor(int prevClass, int curClass, FuzzyScheme scheme) {
        if (curClass >= NON_WORD) {
            // Current char is a "word" character (non-word, delimiter, lower, upper, letter, number)
            switch (prevClass) {
                case WHITE:
                    return scheme.bonusBoundaryWhite;
                case DELIMITER:
                    return scheme.bonusBoundaryDelimiter;
                case NON_WORD:
                    return FuzzyScheme.BONUS_BOUNDARY;
            }
        }

        // camelCase: lower -> UPPER or non-number -> number
        if (prevClass == LOWER && curClass == UPPER
                || prevClass != NUMBER && curClass == NUMBER) {
            return FuzzyScheme.BONUS_CAMEL_123;
        }

        switch (curClass) {
            case NON_WORD:
            case DELIMITER:
                return FuzzyScheme.BONUS_NON_WORD;
            case WHITE:
                return scheme.bonusBoundaryWhite;
        }
        return 0;
    }
}
