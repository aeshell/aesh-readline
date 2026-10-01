/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Framing for terminal query replies.
 * <p>
 * Both standalone probing ({@code TerminalColorQuery}) and live-connection
 * queries ({@code TerminalFeatures}) must answer the same question: which
 * bytes of an accumulated response form complete reply frames, which are
 * ordinary input, and which are an incomplete tail that must be held for
 * more bytes. This splitter answers it once, for both transports.
 * <p>
 * Reply shapes follow the parsers: OSC sequences ({@code ESC ] ... BEL/ST}),
 * device attributes ({@code ESC [ ... c}), mode reports
 * ({@code ESC [ ... $y}), and cursor position reports
 * ({@code ESC [ digits ; digits R}, uppercase only — DECSTBM's lowercase
 * {@code r} never frames). Anything else starting with ESC is an opaque
 * complete shape (arrow keys, DECSC, DA2) or an incomplete tail; neither
 * ever frames.
 *
 * @since 3.18.4
 */
public final class TerminalReplyFramer {

    /**
     * The kind of one {@link Span}.
     */
    public enum Kind {
        /** OSC reply ({@code ESC ] ... BEL/ST}). */
        OSC,
        /** Device-attributes reply ({@code ESC [ ... c}). */
        DEVICE_ATTRIBUTES,
        /** Mode report ({@code ESC [ ... $y}). */
        MODE_REPORT,
        /** Cursor position report ({@code ESC [ digits ; digits R}). */
        CURSOR_POSITION,
        /** Ordinary input without ESC: safe to deliver immediately. */
        PLAIN_TEXT,
        /** Trailing incomplete shape: hold for more bytes. */
        PARTIAL
    }

    /**
     * One framed segment: half-open {@code [start, end)} over the input.
     * Spans never overlap and appear in input order; bytes covered by no
     * span are complete non-reply shapes, which accumulate with partial
     * tails and are redelivered as ordinary input on query timeout.
     */
    public static final class Span {
        /** What this segment is. */
        public final Kind kind;
        /** Start offset (inclusive). */
        public final int start;
        /** End offset (exclusive). */
        public final int end;

        Span(Kind kind, int start, int end) {
            this.kind = kind;
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Span)) {
                return false;
            }
            Span span = (Span) other;
            return kind == span.kind && start == span.start && end == span.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, start, end);
        }

        @Override
        public String toString() {
            return kind + "[" + start + "," + end + ")";
        }
    }

    private TerminalReplyFramer() {
    }

    /**
     * Split an accumulated response into reply frames, plain-text runs,
     * and at most one trailing partial.
     * <p>
     * Stateless per call: rescanning the accumulator after each chunk
     * re-derives the same tiling, so fragmentation never matters. Plain
     * runs never contain ESC; a trailing lone ESC always reads as the
     * start of a partial shape (matching the hold-everything behavior
     * for bare Escape keys).
     *
     * @param response the accumulated response characters
     * @return the spans in input order, never null
     */
    public static List<Span> split(CharSequence response) {
        List<Span> spans = new ArrayList<>();
        int length = response.length();
        int plainStart = -1;
        int covered = 0;
        int i = 0;
        while (i < length) {
            char c = response.charAt(i);
            if (c != '\033') {
                if (plainStart < 0) {
                    plainStart = i;
                }
                i++;
                continue;
            }
            if (plainStart >= 0) {
                spans.add(new Span(Kind.PLAIN_TEXT, plainStart, i));
                plainStart = -1;
            }
            covered = i;
            if (i + 1 >= length) {
                break;
            }
            char next = response.charAt(i + 1);
            int end;
            if (next == ']') {
                end = consumeOsc(response, i, spans);
            } else if (next == '[') {
                end = consumeCsi(response, i, spans);
            } else if (next == '\\') {
                spans.add(new Span(Kind.OSC, i, i + 2));
                end = i + 2;
            } else {
                // Single-character escape (DECSC/DECRC and friends):
                // complete but never a reply.
                end = i + 2;
            }
            if (end < 0) {
                break;
            }
            covered = end;
            i = end;
        }
        if (plainStart >= 0) {
            spans.add(new Span(Kind.PLAIN_TEXT, plainStart, length));
            covered = length;
        }
        if (covered < length) {
            spans.add(new Span(Kind.PARTIAL, covered, length));
        }
        return spans;
    }

    /**
     * Consume an OSC sequence from its opening ESC.
     *
     * @return the index to continue scanning from, or -1 when unclosed
     */
    private static int consumeOsc(CharSequence response, int esc, List<Span> spans) {
        int length = response.length();
        int i = esc + 2;
        while (i < length) {
            char c = response.charAt(i);
            if (c == '\007') {
                spans.add(new Span(Kind.OSC, esc, i + 1));
                return i + 1;
            }
            if (c == '\033' && i + 1 < length && response.charAt(i + 1) == '\\') {
                spans.add(new Span(Kind.OSC, esc, i + 2));
                return i + 2;
            }
            i++;
        }
        return -1;
    }

    /**
     * Consume a CSI sequence from its opening ESC.
     *
     * @return the index to continue scanning from, or -1 when unclosed
     */
    private static int consumeCsi(CharSequence response, int esc, List<Span> spans) {
        int length = response.length();
        int bodyStart = esc + 2;
        int i = bodyStart;
        while (i < length) {
            char c = response.charAt(i);
            if (c == '\033') {
                // A fresh escape abandons the open frame (resync).
                return i;
            }
            if (c == 'c' || c == 'y' || c == 'R' || isCsiFinal(c)) {
                break;
            }
            if (!isCsiBody(c)) {
                // Complete non-reply shape: consume through its final.
                return consumeOpaqueCsi(response, i);
            }
            i++;
        }
        if (i >= length) {
            return -1;
        }
        char fin = response.charAt(i);
        Kind kind = null;
        if (fin == 'c') {
            kind = Kind.DEVICE_ATTRIBUTES;
        } else if (fin == 'y') {
            kind = Kind.MODE_REPORT;
        } else if (fin == 'R' && isCprBody(response, bodyStart, i)) {
            kind = Kind.CURSOR_POSITION;
        }
        if (kind == null) {
            return consumeOpaqueCsi(response, i);
        }
        spans.add(new Span(kind, esc, i + 1));
        return i + 1;
    }

    /**
     * Consume a complete non-reply CSI through its final byte.
     *
     * @return the index to continue scanning from, or -1 when unclosed
     */
    private static int consumeOpaqueCsi(CharSequence response, int i) {
        int length = response.length();
        while (i < length) {
            char c = response.charAt(i);
            if (c == '\033' || c == '\007') {
                return -1;
            }
            i++;
            if (isCsiFinal(c)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCsiBody(char c) {
        return (c >= '0' && c <= '9') || c == ';' || c == '?' || c == '$';
    }

    private static boolean isCsiFinal(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Whether a CSI body holds CPR coordinates: digits and ';' only, with
     * at least one of each. Lenient by design — the response parser
     * validates strictly afterwards, so a false positive only ends a
     * read early on input the parser would reject anyway.
     *
     * @param response the accumulated response
     * @param bodyStart the body start, or negative when unknown
     * @param end the final-byte index (exclusive)
     * @return true if the body is CPR-shaped
     */
    private static boolean isCprBody(CharSequence response, int bodyStart, int end) {
        if (bodyStart < 0 || bodyStart >= end) {
            return false;
        }
        boolean digit = false;
        boolean separator = false;
        for (int i = bodyStart; i < end; i++) {
            char c = response.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c == ';') {
                separator = true;
            } else {
                return false;
            }
        }
        return digit && separator;
    }
}
