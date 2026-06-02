/*
 * Fuzzy matching algorithms, ported from fzf.
 *
 * Original: https://github.com/junegunn/fzf/blob/master/src/algo/algo.go
 * License: MIT (https://github.com/junegunn/fzf/blob/master/LICENSE)
 *
 * FuzzyMatchV1 finds the first "fuzzy" occurrence in O(n) time.
 * FuzzyMatchV2 implements a modified Smith-Waterman algorithm to find
 * the optimal (highest-scoring) occurrence in O(nm) time.
 */
package org.aesh.readline.fuzzy;

/**
 * Fuzzy matching algorithms ported from fzf's Go implementation.
 * <p>
 * Two algorithms are provided:
 * <ul>
 * <li><b>V1 (greedy)</b>: O(n) forward scan + backward scan. Fast but may not find the
 * highest-scoring alignment. Used as a fallback for very long inputs.</li>
 * <li><b>V2 (optimal)</b>: Modified Smith-Waterman O(nm). Finds the highest-scoring
 * alignment by examining all possible positions. Used for typical inputs.</li>
 * </ul>
 * <p>
 * The instance holds pre-allocated working arrays to avoid per-query allocation.
 * Not thread-safe — use one instance per thread.
 *
 * @see <a href="https://github.com/junegunn/fzf/blob/master/src/algo/algo.go">fzf algo.go</a>
 */
public final class FuzzyAlgo {

    // Threshold: if N*M exceeds this, fall back to V1
    private static final int V2_THRESHOLD = 8192;

    // Maximum pattern length for V2 to avoid short overflow in score matrix
    private static final int MAX_PATTERN_LENGTH = 1000;

    // Reusable working arrays (grown as needed)
    private short[] h0Buf = new short[256];
    private short[] c0Buf = new short[256];
    private short[] bonusBuf = new short[256];
    private int[] fBuf = new int[64];
    private int[] tBuf = new int[256];
    private short[] hBuf = new short[4096];
    private short[] cBuf = new short[4096];

    private final FuzzyScheme scheme;

    /**
     * Create a new FuzzyAlgo instance with the given scoring scheme.
     *
     * @param scheme the scoring scheme
     */
    public FuzzyAlgo(FuzzyScheme scheme) {
        this.scheme = scheme;
        CharClass.init(scheme);
    }

    /**
     * Perform fuzzy matching using the optimal algorithm (V2 with V1 fallback).
     *
     * @param caseSensitive whether matching is case-sensitive
     * @param text the input text as code points
     * @param pattern the search pattern as code points
     * @param withPos whether to compute matched character positions
     * @return the match result
     */
    public FuzzyResult match(boolean caseSensitive, int[] text, int[] pattern, boolean withPos) {
        int M = pattern.length;
        int N = text.length;

        if (M == 0) {
            return new FuzzyResult(0, 0, 0, withPos ? new int[0] : null);
        }
        if (M > N) {
            return FuzzyResult.NO_MATCH;
        }

        // For large inputs or long patterns, use V1
        if ((long) N * M > V2_THRESHOLD || M > MAX_PATTERN_LENGTH) {
            return fuzzyMatchV1(caseSensitive, text, pattern, withPos);
        }

        return fuzzyMatchV2(caseSensitive, text, pattern, withPos);
    }

    /**
     * V1: Greedy fuzzy match in O(n).
     * Forward scan finds the first occurrence, backward scan shortens it.
     */
    FuzzyResult fuzzyMatchV1(boolean caseSensitive, int[] text, int[] pattern, boolean withPos) {
        int M = pattern.length;
        int N = text.length;

        if (M == 0) {
            return new FuzzyResult(0, 0, 0, null);
        }

        // Forward scan: find the first fuzzy occurrence
        int pidx = 0;
        int sidx = -1;
        int eidx = -1;

        for (int i = 0; i < N; i++) {
            int ch = text[i];
            if (!caseSensitive) {
                ch = Character.toLowerCase(ch);
            }
            if (ch == pattern[pidx]) {
                if (sidx < 0) {
                    sidx = i;
                }
                pidx++;
                if (pidx == M) {
                    eidx = i + 1;
                    break;
                }
            }
        }

        if (sidx < 0 || eidx < 0) {
            return FuzzyResult.NO_MATCH;
        }

        // Backward scan: shorten the match from the end
        pidx = M - 1;
        for (int i = eidx - 1; i >= sidx; i--) {
            int ch = text[i];
            if (!caseSensitive) {
                ch = Character.toLowerCase(ch);
            }
            if (ch == pattern[pidx]) {
                pidx--;
                if (pidx < 0) {
                    sidx = i;
                    break;
                }
            }
        }

        // Calculate score for the found match region
        int score = calculateScore(caseSensitive, text, pattern, sidx, eidx, withPos);
        int[] positions = null;
        if (withPos) {
            positions = calculatePositions(caseSensitive, text, pattern, sidx, eidx);
        }

        return new FuzzyResult(sidx, eidx, score, positions);
    }

    /**
     * V2: Modified Smith-Waterman for optimal fuzzy match in O(nm).
     */
    FuzzyResult fuzzyMatchV2(boolean caseSensitive, int[] text, int[] pattern, boolean withPos) {
        int M = pattern.length;
        int N = text.length;

        if (M == 0) {
            return new FuzzyResult(0, 0, 0, withPos ? new int[0] : null);
        }

        // Phase 1: Quick check — can pattern be a subsequence of text?
        // Also find first occurrence of each pattern char and narrow the range.
        int minIdx = 0;
        int maxIdx = N;

        // For ASCII inputs, narrow the search range
        if (isAscii(text) && isAscii(pattern)) {
            int[] range = asciiFuzzyIndex(text, pattern, caseSensitive);
            if (range == null) {
                return FuzzyResult.NO_MATCH;
            }
            minIdx = range[0];
            maxIdx = range[1];
        }

        int rangeN = maxIdx - minIdx;

        // Ensure working arrays are large enough
        ensureCapacity(rangeN, M);

        short[] H0 = h0Buf;
        short[] C0 = c0Buf;
        short[] B = bonusBuf;
        int[] F = fBuf;
        int[] T = tBuf;

        // Copy text range into T, normalizing case
        for (int i = 0; i < rangeN; i++) {
            T[i] = text[minIdx + i];
        }

        // Phase 2: Calculate bonus for each position and find pattern char positions
        short maxScore = 0;
        int maxScorePos = 0;
        int pidx = 0;
        int lastIdx = 0;
        int pchar0 = pattern[0];
        int pchar = pchar0;
        short prevH0 = 0;
        int prevClass = scheme.initialCharClass;
        boolean inGap = false;

        for (int off = 0; off < rangeN; off++) {
            int ch = T[off];
            int curClass;
            if (ch < 128) {
                curClass = CharClass.classOfAscii(ch);
                if (!caseSensitive && curClass == CharClass.UPPER) {
                    ch += 32;
                    T[off] = ch;
                }
            } else {
                curClass = CharClass.classOf(ch);
                if (!caseSensitive && curClass == CharClass.UPPER) {
                    ch = Character.toLowerCase(ch);
                }
                T[off] = ch;
            }

            short bonus = CharClass.bonus(prevClass, curClass);
            B[off] = bonus;
            prevClass = curClass;

            if (ch == pchar) {
                if (pidx < M) {
                    F[pidx] = off;
                    pidx++;
                    pchar = pidx < M ? pattern[pidx] : pattern[M - 1];
                }
                lastIdx = off;
            }

            if (ch == pchar0) {
                short score = (short) (FuzzyScheme.SCORE_MATCH + bonus * FuzzyScheme.BONUS_FIRST_CHAR_MULTIPLIER);
                H0[off] = score;
                C0[off] = 1;
                if (M == 1 && score > maxScore) {
                    maxScore = score;
                    maxScorePos = off;
                    if (bonus >= FuzzyScheme.BONUS_BOUNDARY) {
                        break;
                    }
                }
                inGap = false;
            } else {
                if (inGap) {
                    H0[off] = (short) Math.max(prevH0 + FuzzyScheme.SCORE_GAP_EXTENSION, 0);
                } else {
                    H0[off] = (short) Math.max(prevH0 + FuzzyScheme.SCORE_GAP_START, 0);
                }
                C0[off] = 0;
                inGap = true;
            }
            prevH0 = H0[off];
        }

        if (pidx != M) {
            return FuzzyResult.NO_MATCH;
        }

        if (M == 1) {
            FuzzyResult result = new FuzzyResult(
                    minIdx + maxScorePos, minIdx + maxScorePos + 1, maxScore, null);
            if (withPos) {
                return new FuzzyResult(result.start, result.end, result.score,
                        new int[] { minIdx + maxScorePos });
            }
            return result;
        }

        // Phase 3: Fill in score matrix (H)
        int f0 = F[0];
        int width = lastIdx - f0 + 1;
        int matrixSize = width * M;

        // Ensure H and C arrays are big enough
        if (hBuf.length < matrixSize) {
            hBuf = new short[matrixSize];
            cBuf = new short[matrixSize];
        }
        short[] H = hBuf;
        short[] C = cBuf;

        // Copy first row
        System.arraycopy(H0, f0, H, 0, width);
        System.arraycopy(C0, f0, C, 0, width);

        for (int i = 1; i < M; i++) {
            int f = F[i];
            int row = i * width;
            inGap = false;

            for (int off = f; off <= lastIdx; off++) {
                int col = off - f0;
                short s1 = 0, s2;
                short consecutive = 0;

                if (inGap) {
                    s2 = (short) (H[row + col - 1] + FuzzyScheme.SCORE_GAP_EXTENSION);
                } else {
                    s2 = (short) (H[row + col - 1] + FuzzyScheme.SCORE_GAP_START);
                }
                // H[row + col - 1] needs to handle the case when col == 0
                if (col == 0) {
                    s2 = 0;
                } else if (inGap) {
                    s2 = (short) (H[row + col - 1] + FuzzyScheme.SCORE_GAP_EXTENSION);
                } else {
                    s2 = (short) (H[row + col - 1] + FuzzyScheme.SCORE_GAP_START);
                }

                if (pattern[i] == T[off]) {
                    // Diagonal: match
                    s1 = (short) (((i > 0 && col > 0) ? H[(i - 1) * width + col - 1] : 0)
                            + FuzzyScheme.SCORE_MATCH);
                    short b = B[off];
                    consecutive = (short) (((i > 0 && col > 0) ? C[(i - 1) * width + col - 1] : 0) + 1);

                    if (consecutive > 1) {
                        short fb = B[off - consecutive + 1];
                        if (b >= FuzzyScheme.BONUS_BOUNDARY && b > fb) {
                            consecutive = 1;
                        } else {
                            b = (short) Math.max(b, Math.max(FuzzyScheme.BONUS_CONSECUTIVE, fb));
                        }
                    }
                    if (s1 + b < s2) {
                        s1 = (short) (s1 + B[off]);
                        consecutive = 0;
                    } else {
                        s1 = (short) (s1 + b);
                    }
                }
                C[row + col] = consecutive;

                inGap = s1 < s2;
                short score = (short) Math.max(Math.max(s1, s2), 0);
                if (i == M - 1 && score > maxScore) {
                    maxScore = score;
                    maxScorePos = off;
                }
                H[row + col] = score;
            }
        }

        // Phase 4: Backtrace for character positions
        int[] positions = null;
        int j0 = f0;
        if (withPos) {
            positions = new int[M];
            int i = M - 1;
            int j = maxScorePos;
            boolean preferMatch = true;

            while (true) {
                int row = i * width;
                int col = j - f0;
                short s = H[row + col];

                short s1 = 0, s2 = 0;
                if (i > 0 && col > 0) {
                    s1 = H[(i - 1) * width + col - 1];
                }
                if (col > 0) {
                    s2 = H[row + col - 1];
                }

                if (s > s1 && (s > s2 || (s == s2 && preferMatch))) {
                    positions[i] = j + minIdx;
                    if (i == 0) {
                        j0 = j;
                        break;
                    }
                    i--;
                }
                preferMatch = C[row + col] > 1
                        || (row + width + col + 1 < matrixSize && C[row + width + col + 1] > 0);
                j--;
            }
        }

        return new FuzzyResult(minIdx + j0, minIdx + maxScorePos + 1, maxScore, positions);
    }

    /**
     * Calculate the match score for a known match region (used by V1).
     * Implements the same scoring as V2 but for a fixed alignment.
     */
    private int calculateScore(boolean caseSensitive, int[] text, int[] pattern,
            int sidx, int eidx, boolean withPos) {
        int pidx = 0;
        int score = 0;
        boolean inGap = false;
        int consecutive = 0;
        short firstBonus = 0;
        int prevClass = scheme.initialCharClass;

        if (sidx > 0) {
            prevClass = CharClass.classOf(text[sidx - 1]);
        }

        for (int idx = sidx; idx < eidx; idx++) {
            int ch = text[idx];
            int curClass = CharClass.classOf(ch);
            if (!caseSensitive) {
                ch = Character.toLowerCase(ch);
            }

            if (ch == pattern[pidx]) {
                score += FuzzyScheme.SCORE_MATCH;
                short bonus = CharClass.bonus(prevClass, curClass);
                if (consecutive == 0) {
                    firstBonus = bonus;
                } else {
                    if (bonus >= FuzzyScheme.BONUS_BOUNDARY && bonus > firstBonus) {
                        firstBonus = bonus;
                    }
                    bonus = (short) Math.max(bonus, Math.max(firstBonus, FuzzyScheme.BONUS_CONSECUTIVE));
                }
                if (pidx == 0) {
                    score += bonus * FuzzyScheme.BONUS_FIRST_CHAR_MULTIPLIER;
                } else {
                    score += bonus;
                }
                inGap = false;
                consecutive++;
                pidx++;
            } else {
                if (inGap) {
                    score += FuzzyScheme.SCORE_GAP_EXTENSION;
                } else {
                    score += FuzzyScheme.SCORE_GAP_START;
                }
                inGap = true;
                consecutive = 0;
                firstBonus = 0;
            }
            prevClass = curClass;
        }
        return score;
    }

    /**
     * Calculate matched character positions for a known match region (V1).
     */
    private int[] calculatePositions(boolean caseSensitive, int[] text, int[] pattern,
            int sidx, int eidx) {
        int[] positions = new int[pattern.length];
        int pidx = 0;
        for (int idx = sidx; idx < eidx && pidx < pattern.length; idx++) {
            int ch = text[idx];
            if (!caseSensitive) {
                ch = Character.toLowerCase(ch);
            }
            if (ch == pattern[pidx]) {
                positions[pidx] = idx;
                pidx++;
            }
        }
        return positions;
    }

    /**
     * ASCII fast path: quickly determine if a match is possible and narrow the range.
     * Returns {minIdx, maxIdx} or null if no match is possible.
     */
    private static int[] asciiFuzzyIndex(int[] text, int[] pattern, boolean caseSensitive) {
        int firstIdx = 0;
        int idx = 0;
        int lastIdx = 0;

        for (int pidx = 0; pidx < pattern.length; pidx++) {
            int pch = pattern[pidx];
            boolean found = false;
            while (idx < text.length) {
                int ch = text[idx];
                if (!caseSensitive && ch >= 'A' && ch <= 'Z') {
                    ch += 32;
                }
                if (ch == pch) {
                    if (pidx == 0 && idx > 0) {
                        firstIdx = idx - 1;
                    }
                    lastIdx = idx;
                    idx++;
                    found = true;
                    break;
                }
                idx++;
            }
            if (!found) {
                return null; // No match possible
            }
        }

        // Try to extend the range by finding the last occurrence of the last pattern char
        int lastPch = pattern[pattern.length - 1];
        for (int i = text.length - 1; i > lastIdx; i--) {
            int ch = text[i];
            if (!caseSensitive && ch >= 'A' && ch <= 'Z') {
                ch += 32;
            }
            if (ch == lastPch) {
                return new int[] { firstIdx, i + 1 };
            }
        }

        return new int[] { firstIdx, lastIdx + 1 };
    }

    /**
     * Check if all code points are ASCII.
     */
    private static boolean isAscii(int[] codePoints) {
        for (int cp : codePoints) {
            if (cp >= 128) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ensure all working arrays are at least the given size.
     */
    private void ensureCapacity(int n, int m) {
        if (h0Buf.length < n) {
            h0Buf = new short[n];
            c0Buf = new short[n];
            bonusBuf = new short[n];
            tBuf = new int[n];
        }
        if (fBuf.length < m) {
            fBuf = new int[m];
        }
        int matrixSize = n * m;
        if (hBuf.length < matrixSize) {
            hBuf = new short[matrixSize];
            cBuf = new short[matrixSize];
        }
    }
}
