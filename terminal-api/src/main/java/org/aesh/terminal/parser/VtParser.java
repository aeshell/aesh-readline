/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
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
package org.aesh.terminal.parser;

/**
 * Table-driven ANSI/VT escape sequence parser based on Paul Flo Williams'
 * <a href="https://vt100.net/emu/dec_ansi_parser">DEC-compatible parser</a>.
 * <p>
 * The parser uses a pre-computed {@code short[14][256]} transition table where
 * each entry encodes {@code (action << 4) | nextState}. This guarantees
 * completeness (every byte in every state has a defined transition) and
 * correctness (matches DEC VT220-VT525 observable behavior).
 * <p>
 * Deviations from Williams:
 * <ul>
 * <li>Colon (0x3A) is treated as a subparameter separator in CSI/DCS
 * sequences, not as an error. This is required for SGR extended
 * color sequences ({@code 38:2:R:G:B}).</li>
 * <li>BEL (0x07) terminates OSC strings (xterm convention).</li>
 * </ul>
 * <p>
 * Usage:
 *
 * <pre>
 * VtParser parser = new VtParser(new VtHandler() {
 *     public void csiDispatch(int finalChar, int[] params, int paramCount,
 *             int[] intermediates, int intermediateCount,
 *             boolean hasSubParams) {
 *         // handle CSI sequence
 *     }
 *     // ... other callbacks
 * });
 * parser.advance(bytes, 0, length);
 * </pre>
 *
 * @see VtHandler
 * @see <a href="https://vt100.net/emu/dec_ansi_parser">Williams' VT parser</a>
 */
public final class VtParser {

    // =========================================================================
    // States (14 states, matching Williams exactly)
    // =========================================================================

    static final int GROUND = 0;
    static final int ESCAPE = 1;
    static final int ESCAPE_INTERMEDIATE = 2;
    static final int CSI_ENTRY = 3;
    static final int CSI_PARAM = 4;
    static final int CSI_INTERMEDIATE = 5;
    static final int CSI_IGNORE = 6;
    static final int DCS_ENTRY = 7;
    static final int DCS_PARAM = 8;
    static final int DCS_INTERMEDIATE = 9;
    static final int DCS_PASSTHROUGH = 10;
    static final int DCS_IGNORE = 11;
    static final int OSC_STRING = 12;
    static final int SOS_PM_APC_STRING = 13;

    static final int STATE_COUNT = 14;

    // =========================================================================
    // Actions (15 actions)
    // =========================================================================

    static final int ACTION_NONE = 0;
    static final int ACTION_PRINT = 1;
    static final int ACTION_EXECUTE = 2;
    static final int ACTION_IGNORE = 3;
    static final int ACTION_CLEAR = 4;
    static final int ACTION_COLLECT = 5;
    static final int ACTION_PARAM = 6;
    static final int ACTION_ESC_DISPATCH = 7;
    static final int ACTION_CSI_DISPATCH = 8;
    static final int ACTION_HOOK = 9;
    static final int ACTION_PUT = 10;
    static final int ACTION_UNHOOK = 11;
    static final int ACTION_OSC_START = 12;
    static final int ACTION_OSC_PUT = 13;
    static final int ACTION_OSC_END = 14;

    // =========================================================================
    // Table encoding: short = (action << 4) | nextState
    // action=0 means no transition action (but entry/exit actions may fire)
    // =========================================================================

    private static short pack(int action, int state) {
        return (short) ((action << 4) | state);
    }

    /**
     * The transition table: TABLE[(state << 8) | byte] = packed (action, nextState).
     * Flat 1D array (1 heap object, single bounds check) generated from the
     * Williams specification with colon-as-subparam extension.
     */
    static final short[] TABLE = new short[STATE_COUNT * 256];

    private static final int MAX_RETAINED_OSC_CAPACITY = 4096;

    static {
        buildTable();
    }

    // =========================================================================
    // Parser state
    // =========================================================================

    private final VtHandler handler;
    private int state = GROUND;

    // Collected intermediates (max 2 per Williams, we allow 4 for safety)
    private final int[] intermediates = new int[4];
    private int intermediateCount;

    // Collected parameters (max 16 per Williams, we allow 32 for modern usage)
    private final int[] params = new int[32];
    private int paramCount;
    private boolean hasSubParams;

    // Current parameter being accumulated
    private int currentParam;
    private boolean paramStarted;

    // OSC string accumulator (allocated lazily on first OSC; trimmed after huge payloads)
    private StringBuilder oscString;

    /**
     * Creates a new parser with the given handler.
     *
     * @param handler the callback handler for parsed sequences
     */
    public VtParser(VtHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns true if the parser is in the GROUND state (no partial sequence
     * is being accumulated). Used by callers to implement fast-path skipping
     * when no escape sequences are in progress.
     *
     * @return true if in GROUND state
     */
    public boolean isGroundState() {
        return state == GROUND;
    }

    /**
     * Advances the parser with a block of input bytes.
     *
     * @param data the input bytes
     * @param offset the start offset
     * @param length the number of bytes to process
     */
    public void advance(byte[] data, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            advance(data[i] & 0xFF);
        }
    }

    /**
     * Advances the parser with a block of code points.
     * Code points &gt; 255 are treated as printable characters in the ground state
     * and as ignored characters in other states.
     *
     * @param codePoints the input code points
     * @param offset the start offset
     * @param length the number of code points to process
     */
    public void advance(int[] codePoints, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            advance(codePoints[i]);
        }
    }

    /**
     * Advances the parser with a single byte or code point.
     * Values 0-255 use the transition table directly.
     * Values &gt; 255 are treated as printable in ground state, ignored otherwise.
     *
     * @param b the byte (0-255) or code point (&gt; 255)
     */
    public void advance(int b) {
        if (b < 0) {
            return;
        }
        if (b > 255) {
            // Code points beyond the table range: printable in ground, ignore elsewhere
            if (state == GROUND) {
                handler.print(b);
            }
            return;
        }
        short entry = TABLE[(state << 8) | b];
        int action = (entry >> 4) & 0x0F;
        int nextState = entry & 0x0F;

        // If state changes, perform exit action, transition action, entry action
        if (nextState != state) {
            performExitAction(state);
            performAction(action, b);
            performEntryAction(nextState);
            state = nextState;
        } else {
            // Same state — just perform the action
            performAction(action, b);
        }
    }

    /**
     * Resets the parser to ground state.
     */
    public void reset() {
        if (state == DCS_PASSTHROUGH) {
            handler.unhook();
        } else if (state == OSC_STRING) {
            dispatchOscEnd();
        }
        state = GROUND;
        clearParams();
    }

    /**
     * Returns the current state (for testing).
     */
    int getState() {
        return state;
    }

    // =========================================================================
    // Action dispatch
    // =========================================================================

    private void performAction(int action, int b) {
        switch (action) {
            case ACTION_NONE:
                break;
            case ACTION_PRINT:
                handler.print(b);
                break;
            case ACTION_EXECUTE:
                handler.execute(b);
                break;
            case ACTION_IGNORE:
                break;
            case ACTION_CLEAR:
                clearParams();
                break;
            case ACTION_COLLECT:
                if (intermediateCount < intermediates.length) {
                    intermediates[intermediateCount++] = b;
                }
                break;
            case ACTION_PARAM:
                if (b == ';') {
                    // Separator — store current param and start next
                    flushParam();
                } else if (b == ':') {
                    // Subparameter separator
                    flushParam();
                    hasSubParams = true;
                } else {
                    // Digit 0-9 (saturate instead of overflowing negative)
                    int digit = b - '0';
                    if (currentParam > (Integer.MAX_VALUE - digit) / 10) {
                        currentParam = Integer.MAX_VALUE;
                    } else {
                        currentParam = currentParam * 10 + digit;
                    }
                    paramStarted = true;
                }
                break;
            case ACTION_ESC_DISPATCH:
                handler.escDispatch(b, intermediates, intermediateCount);
                break;
            case ACTION_CSI_DISPATCH:
                flushParam();
                handler.csiDispatch(b, params, paramCount,
                        intermediates, intermediateCount, hasSubParams);
                break;
            case ACTION_HOOK:
                flushParam();
                handler.hook(b, params, paramCount,
                        intermediates, intermediateCount);
                break;
            case ACTION_PUT:
                handler.put(b);
                break;
            case ACTION_UNHOOK:
                handler.unhook();
                break;
            case ACTION_OSC_START:
                resetOscString();
                break;
            case ACTION_OSC_PUT:
                if (oscString == null) {
                    oscString = new StringBuilder(64);
                }
                oscString.append((char) b);
                break;
            case ACTION_OSC_END:
                dispatchOscEnd();
                break;
        }
    }

    private void resetOscString() {
        if (oscString != null) {
            if (oscString.capacity() > MAX_RETAINED_OSC_CAPACITY) {
                oscString = null;
            } else {
                oscString.setLength(0);
            }
        }
    }

    private void dispatchOscEnd() {
        String data = oscString == null ? "" : oscString.toString();
        resetOscString();
        handler.oscEnd(data);
    }

    private void performEntryAction(int newState) {
        switch (newState) {
            case ESCAPE:
            case CSI_ENTRY:
            case DCS_ENTRY:
                clearParams();
                break;
            case OSC_STRING:
                resetOscString();
                break;
            case DCS_PASSTHROUGH:
                // hook is dispatched via the transition action
                break;
        }
    }

    private void performExitAction(int oldState) {
        switch (oldState) {
            case DCS_PASSTHROUGH:
                handler.unhook();
                break;
            case OSC_STRING:
                dispatchOscEnd();
                break;
        }
    }

    private void clearParams() {
        intermediateCount = 0;
        paramCount = 0;
        currentParam = 0;
        paramStarted = false;
        hasSubParams = false;
    }

    private void flushParam() {
        if (paramCount < params.length) {
            params[paramCount++] = paramStarted ? currentParam : -1; // -1 = default
        }
        currentParam = 0;
        paramStarted = false;
    }

    // =========================================================================
    // Table generation — Williams spec + colon extension + BEL for OSC
    // =========================================================================

    private static void set(int state, int b, short entry) {
        TABLE[(state << 8) | b] = entry;
    }

    private static void setRange(int state, int from, int to, short entry) {
        int base = state << 8;
        for (int b = from; b <= to; b++) {
            TABLE[base | b] = entry;
        }
    }

    private static void setC0(int state, int action) {
        short entry = pack(action, state);
        setRange(state, 0x00, 0x17, entry);
        set(state, 0x19, entry);
        setRange(state, 0x1C, 0x1F, entry);
    }

    private static void buildTable() {
        // Initialize everything to (IGNORE, same state) — safe default
        for (int s = 0; s < STATE_COUNT; s++) {
            setRange(s, 0, 255, pack(ACTION_IGNORE, s));
        }

        // ---- "anywhere" transitions (from any state) ----
        for (int s = 0; s < STATE_COUNT; s++) {
            // CAN (0x18) and SUB (0x1A) → execute and go to ground
            set(s, 0x18, pack(ACTION_EXECUTE, GROUND));
            set(s, 0x1A, pack(ACTION_EXECUTE, GROUND));
            // ESC (0x1B) → escape (no action — entry action does clear)
            set(s, 0x1B, pack(ACTION_NONE, ESCAPE));
            // C1 controls (8-bit)
            setRange(s, 0x80, 0x8F, pack(ACTION_EXECUTE, GROUND));
            setRange(s, 0x91, 0x97, pack(ACTION_EXECUTE, GROUND));
            set(s, 0x99, pack(ACTION_EXECUTE, GROUND));
            set(s, 0x9A, pack(ACTION_EXECUTE, GROUND));
            set(s, 0x90, pack(ACTION_NONE, DCS_ENTRY)); // DCS
            set(s, 0x9B, pack(ACTION_NONE, CSI_ENTRY)); // CSI
            set(s, 0x9C, pack(ACTION_NONE, GROUND)); // ST
            set(s, 0x9D, pack(ACTION_NONE, OSC_STRING)); // OSC
            set(s, 0x98, pack(ACTION_NONE, SOS_PM_APC_STRING)); // SOS
            set(s, 0x9E, pack(ACTION_NONE, SOS_PM_APC_STRING)); // PM
            set(s, 0x9F, pack(ACTION_NONE, SOS_PM_APC_STRING)); // APC
        }

        // ---- ground ----
        setC0(GROUND, ACTION_EXECUTE);
        setRange(GROUND, 0x20, 0x7F, pack(ACTION_PRINT, GROUND));

        // ---- escape ----
        setC0(ESCAPE, ACTION_EXECUTE);
        setRange(ESCAPE, 0x20, 0x2F, pack(ACTION_COLLECT, ESCAPE_INTERMEDIATE));
        setRange(ESCAPE, 0x30, 0x4F, pack(ACTION_ESC_DISPATCH, GROUND));
        set(ESCAPE, 0x50, pack(ACTION_NONE, DCS_ENTRY)); // P → DCS
        setRange(ESCAPE, 0x51, 0x57, pack(ACTION_ESC_DISPATCH, GROUND));
        set(ESCAPE, 0x58, pack(ACTION_NONE, SOS_PM_APC_STRING)); // X → SOS
        set(ESCAPE, 0x59, pack(ACTION_ESC_DISPATCH, GROUND));
        set(ESCAPE, 0x5A, pack(ACTION_ESC_DISPATCH, GROUND));
        set(ESCAPE, 0x5B, pack(ACTION_NONE, CSI_ENTRY)); // [ → CSI
        set(ESCAPE, 0x5C, pack(ACTION_ESC_DISPATCH, GROUND)); // \ (ST as esc_dispatch)
        set(ESCAPE, 0x5D, pack(ACTION_NONE, OSC_STRING)); // ] → OSC
        set(ESCAPE, 0x5E, pack(ACTION_NONE, SOS_PM_APC_STRING)); // ^ → PM
        set(ESCAPE, 0x5F, pack(ACTION_NONE, SOS_PM_APC_STRING)); // _ → APC
        setRange(ESCAPE, 0x60, 0x7E, pack(ACTION_ESC_DISPATCH, GROUND));
        set(ESCAPE, 0x7F, pack(ACTION_IGNORE, ESCAPE));

        // ---- escape intermediate ----
        setC0(ESCAPE_INTERMEDIATE, ACTION_EXECUTE);
        setRange(ESCAPE_INTERMEDIATE, 0x20, 0x2F, pack(ACTION_COLLECT, ESCAPE_INTERMEDIATE));
        setRange(ESCAPE_INTERMEDIATE, 0x30, 0x7E, pack(ACTION_ESC_DISPATCH, GROUND));
        set(ESCAPE_INTERMEDIATE, 0x7F, pack(ACTION_IGNORE, ESCAPE_INTERMEDIATE));

        // ---- csi entry ----
        setC0(CSI_ENTRY, ACTION_EXECUTE);
        setRange(CSI_ENTRY, 0x20, 0x2F, pack(ACTION_COLLECT, CSI_INTERMEDIATE));
        setRange(CSI_ENTRY, 0x30, 0x39, pack(ACTION_PARAM, CSI_PARAM));
        set(CSI_ENTRY, 0x3A, pack(ACTION_PARAM, CSI_PARAM)); // colon → subparam (deviation from Williams)
        set(CSI_ENTRY, 0x3B, pack(ACTION_PARAM, CSI_PARAM)); // semicolon
        setRange(CSI_ENTRY, 0x3C, 0x3F, pack(ACTION_COLLECT, CSI_PARAM)); // private markers
        setRange(CSI_ENTRY, 0x40, 0x7E, pack(ACTION_CSI_DISPATCH, GROUND));
        set(CSI_ENTRY, 0x7F, pack(ACTION_IGNORE, CSI_ENTRY));

        // ---- csi param ----
        setC0(CSI_PARAM, ACTION_EXECUTE);
        setRange(CSI_PARAM, 0x20, 0x2F, pack(ACTION_COLLECT, CSI_INTERMEDIATE));
        setRange(CSI_PARAM, 0x30, 0x39, pack(ACTION_PARAM, CSI_PARAM));
        set(CSI_PARAM, 0x3A, pack(ACTION_PARAM, CSI_PARAM)); // colon → subparam
        set(CSI_PARAM, 0x3B, pack(ACTION_PARAM, CSI_PARAM)); // semicolon
        setRange(CSI_PARAM, 0x3C, 0x3F, pack(ACTION_NONE, CSI_IGNORE));
        setRange(CSI_PARAM, 0x40, 0x7E, pack(ACTION_CSI_DISPATCH, GROUND));
        set(CSI_PARAM, 0x7F, pack(ACTION_IGNORE, CSI_PARAM));

        // ---- csi intermediate ----
        setC0(CSI_INTERMEDIATE, ACTION_EXECUTE);
        setRange(CSI_INTERMEDIATE, 0x20, 0x2F, pack(ACTION_COLLECT, CSI_INTERMEDIATE));
        setRange(CSI_INTERMEDIATE, 0x30, 0x3F, pack(ACTION_NONE, CSI_IGNORE));
        setRange(CSI_INTERMEDIATE, 0x40, 0x7E, pack(ACTION_CSI_DISPATCH, GROUND));
        set(CSI_INTERMEDIATE, 0x7F, pack(ACTION_IGNORE, CSI_INTERMEDIATE));

        // ---- csi ignore ----
        setC0(CSI_IGNORE, ACTION_EXECUTE);
        setRange(CSI_IGNORE, 0x20, 0x3F, pack(ACTION_IGNORE, CSI_IGNORE));
        setRange(CSI_IGNORE, 0x40, 0x7E, pack(ACTION_NONE, GROUND));
        set(CSI_IGNORE, 0x7F, pack(ACTION_IGNORE, CSI_IGNORE));

        // ---- dcs entry ----
        setC0(DCS_ENTRY, ACTION_IGNORE);
        setRange(DCS_ENTRY, 0x20, 0x2F, pack(ACTION_COLLECT, DCS_INTERMEDIATE));
        setRange(DCS_ENTRY, 0x30, 0x39, pack(ACTION_PARAM, DCS_PARAM));
        set(DCS_ENTRY, 0x3A, pack(ACTION_NONE, DCS_IGNORE)); // colon in DCS → ignore (no subparam in DCS)
        set(DCS_ENTRY, 0x3B, pack(ACTION_PARAM, DCS_PARAM));
        setRange(DCS_ENTRY, 0x3C, 0x3F, pack(ACTION_COLLECT, DCS_PARAM));
        setRange(DCS_ENTRY, 0x40, 0x7E, pack(ACTION_HOOK, DCS_PASSTHROUGH));
        set(DCS_ENTRY, 0x7F, pack(ACTION_IGNORE, DCS_ENTRY));

        // ---- dcs param ----
        setC0(DCS_PARAM, ACTION_IGNORE);
        setRange(DCS_PARAM, 0x20, 0x2F, pack(ACTION_COLLECT, DCS_INTERMEDIATE));
        setRange(DCS_PARAM, 0x30, 0x39, pack(ACTION_PARAM, DCS_PARAM));
        set(DCS_PARAM, 0x3A, pack(ACTION_NONE, DCS_IGNORE));
        set(DCS_PARAM, 0x3B, pack(ACTION_PARAM, DCS_PARAM));
        setRange(DCS_PARAM, 0x3C, 0x3F, pack(ACTION_NONE, DCS_IGNORE));
        setRange(DCS_PARAM, 0x40, 0x7E, pack(ACTION_HOOK, DCS_PASSTHROUGH));
        set(DCS_PARAM, 0x7F, pack(ACTION_IGNORE, DCS_PARAM));

        // ---- dcs intermediate ----
        setC0(DCS_INTERMEDIATE, ACTION_IGNORE);
        setRange(DCS_INTERMEDIATE, 0x20, 0x2F, pack(ACTION_COLLECT, DCS_INTERMEDIATE));
        setRange(DCS_INTERMEDIATE, 0x30, 0x3F, pack(ACTION_NONE, DCS_IGNORE));
        setRange(DCS_INTERMEDIATE, 0x40, 0x7E, pack(ACTION_HOOK, DCS_PASSTHROUGH));
        set(DCS_INTERMEDIATE, 0x7F, pack(ACTION_IGNORE, DCS_INTERMEDIATE));

        // ---- dcs passthrough ----
        setC0(DCS_PASSTHROUGH, ACTION_PUT);
        setRange(DCS_PASSTHROUGH, 0x20, 0x7E, pack(ACTION_PUT, DCS_PASSTHROUGH));
        set(DCS_PASSTHROUGH, 0x7F, pack(ACTION_IGNORE, DCS_PASSTHROUGH));
        // 9C (ST) is handled by anywhere transitions

        // ---- dcs ignore ----
        setC0(DCS_IGNORE, ACTION_IGNORE);
        setRange(DCS_IGNORE, 0x20, 0x7F, pack(ACTION_IGNORE, DCS_IGNORE));
        // 9C (ST) → ground is handled by anywhere transitions

        // ---- osc string ----
        setRange(OSC_STRING, 0x00, 0x06, pack(ACTION_IGNORE, OSC_STRING));
        set(OSC_STRING, 0x07, pack(ACTION_NONE, GROUND)); // BEL terminates OSC (xterm extension)
        setRange(OSC_STRING, 0x08, 0x17, pack(ACTION_IGNORE, OSC_STRING));
        set(OSC_STRING, 0x19, pack(ACTION_IGNORE, OSC_STRING));
        setRange(OSC_STRING, 0x1C, 0x1F, pack(ACTION_IGNORE, OSC_STRING));
        setRange(OSC_STRING, 0x20, 0x7F, pack(ACTION_OSC_PUT, OSC_STRING));
        // 9C (ST) → ground is handled by anywhere transitions

        // ---- sos/pm/apc string ----
        setC0(SOS_PM_APC_STRING, ACTION_IGNORE);
        setRange(SOS_PM_APC_STRING, 0x20, 0x7F, pack(ACTION_IGNORE, SOS_PM_APC_STRING));
        // 9C (ST) → ground is handled by anywhere transitions

        // ---- GR area (0xA0-0xFF) → same as GL (0x20-0x7F) for all states ----
        for (int s = 0; s < STATE_COUNT; s++) {
            int base = s << 8;
            for (int b = 0xA0; b <= 0xFF; b++) {
                TABLE[base | b] = TABLE[base | (b - 0x80)];
            }
        }
    }
}
