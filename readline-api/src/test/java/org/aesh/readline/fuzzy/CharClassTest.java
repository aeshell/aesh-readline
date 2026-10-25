package org.aesh.readline.fuzzy;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for character classification and bonus matrix.
 */
public class CharClassTest {

    static {
        CharClass.init(FuzzyScheme.DEFAULT);
    }

    @Test
    public void testAsciiLowercase() {
        for (int c = 'a'; c <= 'z'; c++) {
            assertEquals("char '" + (char) c + "' should be LOWER",
                    CharClass.LOWER, CharClass.classOf(c));
        }
    }

    @Test
    public void testAsciiUppercase() {
        for (int c = 'A'; c <= 'Z'; c++) {
            assertEquals("char '" + (char) c + "' should be UPPER",
                    CharClass.UPPER, CharClass.classOf(c));
        }
    }

    @Test
    public void testDigits() {
        for (int c = '0'; c <= '9'; c++) {
            assertEquals("char '" + (char) c + "' should be NUMBER",
                    CharClass.NUMBER, CharClass.classOf(c));
        }
    }

    @Test
    public void testWhitespace() {
        assertEquals(CharClass.WHITE, CharClass.classOf(' '));
        assertEquals(CharClass.WHITE, CharClass.classOf('\t'));
        assertEquals(CharClass.WHITE, CharClass.classOf('\n'));
    }

    @Test
    public void testDelimiters() {
        assertEquals(CharClass.DELIMITER, CharClass.classOf('/'));
        assertEquals(CharClass.DELIMITER, CharClass.classOf(','));
        assertEquals(CharClass.DELIMITER, CharClass.classOf(':'));
        assertEquals(CharClass.DELIMITER, CharClass.classOf(';'));
        assertEquals(CharClass.DELIMITER, CharClass.classOf('|'));
    }

    @Test
    public void testNonWord() {
        assertEquals(CharClass.NON_WORD, CharClass.classOf('-'));
        assertEquals(CharClass.NON_WORD, CharClass.classOf('_'));
        assertEquals(CharClass.NON_WORD, CharClass.classOf('.'));
        assertEquals(CharClass.NON_WORD, CharClass.classOf('('));
    }

    @Test
    public void testNonAsciiLower() {
        // Latin small letter a with umlaut
        assertEquals(CharClass.LOWER, CharClass.classOf('\u00E4'));
    }

    @Test
    public void testNonAsciiUpper() {
        // Latin capital letter A with umlaut
        assertEquals(CharClass.UPPER, CharClass.classOf('\u00C4'));
    }

    @Test
    public void testBonusWhiteBoundary() {
        // After whitespace, matching a word char gets bonus
        short bonus = CharClass.bonus(CharClass.WHITE, CharClass.LOWER);
        assertTrue("White->Lower should have boundary bonus, got " + bonus,
                bonus >= FuzzyScheme.BONUS_BOUNDARY);
    }

    @Test
    public void testBonusCamelCase() {
        // lower -> UPPER transition (camelCase)
        short bonus = CharClass.bonus(CharClass.LOWER, CharClass.UPPER);
        assertEquals(FuzzyScheme.BONUS_CAMEL_123, bonus);
    }

    @Test
    public void testBonusNumberBoundary() {
        // letter -> number transition
        short bonus = CharClass.bonus(CharClass.LOWER, CharClass.NUMBER);
        assertEquals(FuzzyScheme.BONUS_CAMEL_123, bonus);
    }

    @Test
    public void testBonusNone() {
        // lower -> lower: no bonus
        short bonus = CharClass.bonus(CharClass.LOWER, CharClass.LOWER);
        assertEquals(0, bonus);
    }

    @Test
    public void testBonusAtStartOfString() {
        int[] input = "hello".codePoints().toArray();
        CharClass.init(FuzzyScheme.DEFAULT);
        short bonus = CharClass.bonusAt(input, 0, FuzzyScheme.DEFAULT);
        assertEquals(FuzzyScheme.DEFAULT.bonusBoundaryWhite, bonus);
    }

    @Test
    public void testBonusAtWordBoundary() {
        int[] input = "hello world".codePoints().toArray();
        CharClass.init(FuzzyScheme.DEFAULT);
        // 'w' at index 6, after space at index 5
        short bonus = CharClass.bonusAt(input, 6, FuzzyScheme.DEFAULT);
        assertEquals(FuzzyScheme.DEFAULT.bonusBoundaryWhite, bonus);
    }

    @Test
    public void testHistorySchemeEqualBoundaries() {
        CharClass.init(FuzzyScheme.HISTORY);
        // In HISTORY scheme, white and delimiter boundaries are equal
        short whiteBonus = CharClass.bonus(CharClass.WHITE, CharClass.LOWER);
        short delimBonus = CharClass.bonus(CharClass.DELIMITER, CharClass.LOWER);
        assertEquals("HISTORY: white and delimiter bonus should be equal",
                whiteBonus, delimBonus);
    }

    @Test
    public void testDefaultSchemeDifferentBoundaries() {
        CharClass.init(FuzzyScheme.DEFAULT);
        short whiteBonus = CharClass.bonus(CharClass.WHITE, CharClass.LOWER);
        short delimBonus = CharClass.bonus(CharClass.DELIMITER, CharClass.LOWER);
        assertTrue("DEFAULT: white bonus should be > delimiter bonus",
                whiteBonus > delimBonus);
    }

    @Test
    public void testSchemeMatricesAreIsolated() {
        // The legacy global matrix follows the last init() call, but the
        // scheme overload must answer per-scheme regardless: WHITE->LOWER
        // is 10 under DEFAULT vs 8 under HISTORY/PATH, DELIMITER->LOWER is
        // 9 under DEFAULT/PATH vs 8 under HISTORY.
        CharClass.init(FuzzyScheme.HISTORY);
        assertEquals(10, CharClass.bonus(CharClass.WHITE, CharClass.LOWER, FuzzyScheme.DEFAULT));
        assertEquals(8, CharClass.bonus(CharClass.WHITE, CharClass.LOWER, FuzzyScheme.HISTORY));
        assertEquals(8, CharClass.bonus(CharClass.WHITE, CharClass.LOWER, FuzzyScheme.PATH));
        assertEquals(9, CharClass.bonus(CharClass.DELIMITER, CharClass.LOWER, FuzzyScheme.DEFAULT));
        assertEquals(8, CharClass.bonus(CharClass.DELIMITER, CharClass.LOWER, FuzzyScheme.HISTORY));
        CharClass.init(FuzzyScheme.DEFAULT);
        assertEquals(8, CharClass.bonus(CharClass.WHITE, CharClass.LOWER, FuzzyScheme.HISTORY));
        assertEquals(10, CharClass.bonus(CharClass.WHITE, CharClass.LOWER, FuzzyScheme.DEFAULT));
    }

    @Test
    public void testInterleavedAlgoInstancesKeepTheirScheme() {
        // End-to-end: two live instances with different schemes must each
        // score by their own scheme (previously the later init won globally).
        FuzzyAlgo history = new FuzzyAlgo(FuzzyScheme.HISTORY);
        FuzzyAlgo deflt = new FuzzyAlgo(FuzzyScheme.DEFAULT);
        int[] text = "foo bar".codePoints().toArray();
        int[] pattern = "fb".codePoints().toArray();
        int historyScore = history.match(true, text, pattern, false).score;
        int defaultScore = deflt.match(true, text, pattern, false).score;
        assertEquals(new FuzzyAlgo(FuzzyScheme.HISTORY).match(true, text, pattern, false).score,
                historyScore);
        assertEquals(new FuzzyAlgo(FuzzyScheme.DEFAULT).match(true, text, pattern, false).score,
                defaultScore);
    }
}
