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
package org.aesh.terminal.tty.impl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.MouseEvent;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

/**
 * Windows system terminal implementation using native console API via JNI.
 */
public class WinSysTerminal extends AbstractWindowsTerminal {

    // Lazy holder — avoids calling WinConsoleNative in static init,
    // which would fail on non-Windows or in GraalVM native-image at build time (#218).
    private static final class Handles {
        static final long INPUT = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        static final long OUTPUT = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
    }

    // Windows key modifier constants
    private static final int RIGHT_ALT_PRESSED = 0x0001;
    private static final int LEFT_ALT_PRESSED = 0x0002;
    private static final int RIGHT_CTRL_PRESSED = 0x0004;
    private static final int LEFT_CTRL_PRESSED = 0x0008;
    private static final int SHIFT_PRESSED = 0x0010;

    // Windows mouse button state constants
    private static final int FROM_LEFT_1ST_BUTTON_PRESSED = 0x0001;
    private static final int RIGHTMOST_BUTTON_PRESSED = 0x0002;
    private static final int FROM_LEFT_2ND_BUTTON_PRESSED = 0x0004;

    // Windows mouse event flags
    private static final int MOUSE_MOVED = 0x0001;
    private static final int DOUBLE_CLICK = 0x0002;
    private static final int MOUSE_WHEELED = 0x0004;
    private static final int MOUSE_HWHEELED = 0x0008;

    private volatile Consumer<MouseEvent> mouseHandler;
    private int lastButtonState;

    /**
     * Charset for encoding translated input characters. Written once by
     * the owning connection (its resolved input charset) and read by the
     * input pump thread, hence volatile. Defaults to the JVM default,
     * preserving the previous implicit behavior until pushed.
     */
    private volatile Charset inputCharset = Charset.defaultCharset();
    /** High-surrogate state across KEY_EVENT records for this terminal. */
    private final PendingSurrogate pendingSurrogate = new PendingSurrogate();

    /**
     * Create a new Windows system terminal with the specified name.
     *
     * @param name the terminal name
     * @param nativeSignals whether to use native signal handling
     * @throws IOException if an I/O error occurs
     */
    public WinSysTerminal(String name, boolean nativeSignals) throws IOException {
        this(name, nativeSignals, SignalHandlers.SIG_DFL);
    }

    /**
     * Create a new Windows system terminal with custom signal handler.
     *
     * @param name the terminal name
     * @param nativeSignals whether to use native signal handling
     * @param signalHandler the signal handler to use
     * @throws IOException if an I/O error occurs
     */
    public WinSysTerminal(String name, boolean nativeSignals, SignalHandler signalHandler) throws IOException {
        // Pass false: VT output mode is enabled below, AFTER super() has saved
        // the original output mode. Enabling it here would corrupt the save.
        super(false, System.out, name, nativeSignals, signalHandler);
        enableVTOutput();
        // Transport selection depends only on having a real console, not on
        // VTP success: WriteConsoleW renders text correctly with or without
        // VT interpretation, while the Encoder byte path depends on the
        // console output codepage matching the JVM charset.
        setConsumeCP(isOutputConsoleValid());
    }

    /**
     * Enables virtual terminal processing on the output handle so ANSI
     * escape sequences render. Independent of output transport selection.
     *
     * @return true if VT processing was enabled
     */
    protected boolean enableVTOutput() {
        return setVTMode();
    }

    /**
     * Whether console output goes through WriteConsoleW (UTF-16,
     * codepage-independent) rather than the Encoder byte stream.
     * <p>
     * Microsoft documents that WriteConsole fails on redirected handles,
     * so a valid file or pipe handle is not enough: the handle must
     * answer GetConsoleMode. Deliberately not System.console(), which
     * would start the JDK's competing input pump (see #276).
     *
     * @return true when the output handle is a real console
     */
    protected boolean isOutputConsoleValid() {
        if (Handles.OUTPUT == WinConsoleNative.INVALID_HANDLE) {
            return false;
        }
        return isConsoleModeValid(WinConsoleNative.getConsoleMode(Handles.OUTPUT));
    }

    /**
     * Whether a GetConsoleMode result identifies a real console.
     * Both native bridges report -1 for anything that is not a console
     * (file, pipe, dead handle, missing downcall); a bitmask of zero
     * still means a console with all flags off. Package-visible so the
     * decision is unit-testable without native code.
     *
     * @param mode the GetConsoleMode result, or -1 on failure
     * @return true for any real console mode
     */
    static boolean isConsoleModeValid(int mode) {
        return mode != -1;
    }

    protected int getConsoleOutputCP() {
        return WinConsoleNative.getConsoleOutputCP();
    }

    @Override
    protected int getConsoleMode() {
        if (Handles.INPUT == WinConsoleNative.INVALID_HANDLE) {
            return -1;
        }
        return WinConsoleNative.getConsoleMode(Handles.INPUT);
    }

    @Override
    protected void setConsoleMode(int mode) {
        if (Handles.INPUT != WinConsoleNative.INVALID_HANDLE) {
            WinConsoleNative.setConsoleMode(Handles.INPUT, mode);
        }
    }

    @Override
    protected int getOutputConsoleMode() {
        if (Handles.OUTPUT == WinConsoleNative.INVALID_HANDLE) {
            return -1;
        }
        return WinConsoleNative.getConsoleMode(Handles.OUTPUT);
    }

    @Override
    protected void setOutputConsoleMode(int mode) {
        if (Handles.OUTPUT != WinConsoleNative.INVALID_HANDLE) {
            WinConsoleNative.setConsoleMode(Handles.OUTPUT, mode);
        }
    }

    public Size getSize() {
        int[] size = WinConsoleNative.getConsoleSize(Handles.OUTPUT);
        if (size == null) {
            return new Size(80, 24);
        }
        return new Size(size[0], size[1]);
    }

    protected byte[] readConsoleInput() {
        if (Handles.INPUT == WinConsoleNative.INVALID_HANDLE) {
            return new byte[0];
        }
        int[] event;
        try {
            event = WinConsoleNative.readConsoleInputEvent(Handles.INPUT);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "read Windows terminal input error: ", e);
            return new byte[0];
        }
        if (event == null) {
            return new byte[0];
        }

        // Check event type (first element)
        if (event[0] == WinConsoleNative.WINDOW_BUFFER_SIZE_EVENT) {
            raise(Signal.WINCH);
            return new byte[0];
        }

        if (event[0] == WinConsoleNative.MOUSE_EVENT) {
            if (mouseHandler != null) {
                MouseEvent mouseEvent = translateMouseEvent(event);
                if (mouseEvent != null) {
                    mouseHandler.accept(mouseEvent);
                }
            }
            return new byte[0];
        }

        if (event[0] != WinConsoleNative.KEY_EVENT) {
            return new byte[0];
        }

        return processKeyEvent(event, this::getEscapeSequence, this::getSequence,
                inputCharset, pendingSurrogate);
    }

    /**
     * Converts a Windows KEY_EVENT record into terminal bytes.
     * <p>
     * Extracted as a package-private static method so it can be unit-tested
     * without a live Windows console.
     * <p>
     * Note: ENABLE_VIRTUAL_TERMINAL_INPUT is deliberately NOT enabled on the
     * console input handle. That flag causes Windows to generate duplicate
     * KEY_EVENT records per keypress (one traditional, one VT with vk=0),
     * leading to double character input (#276). Instead, we use traditional
     * ReadConsoleInput KEY_EVENT records and translate virtual key codes to
     * ANSI escape sequences manually via {@code escapeSequenceLookup}.
     * Mouse events are handled separately via ENABLE_MOUSE_INPUT and
     * native MOUSE_EVENT records.
     * <p>
     * Each KEY_EVENT carries one UTF-16 code unit, so a supplementary
     * character arrives as separate high/low records. Pairing state
     * travels in {@code pending}: a high stashes, the following low
     * completes the pair, and any other record flushes a stale high as
     * {@code ?} (never dropped silently). Repeat counts ride the record
     * that completes the unit. Bytes use {@code charset} explicitly so
     * both sides of the terminal boundary agree (see #280).
     *
     * @param event the KEY_EVENT record: {1, keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
     * @param escapeSequenceLookup maps virtual key codes to escape sequences (e.g., arrow keys)
     * @param capabilityLookup maps terminal capabilities to sequences (e.g., key_btab)
     * @param charset the charset for encoding produced characters
     * @param pending high-surrogate state carried across records
     * @return the bytes to feed to the terminal input pipe, or empty array for filtered events
     */
    static byte[] processKeyEvent(int[] event,
            java.util.function.Function<Short, String> escapeSequenceLookup,
            java.util.function.Function<Capability, String> capabilityLookup,
            java.nio.charset.Charset charset, PendingSurrogate pending) {
        boolean keyDown = event[1] != 0;
        int repeatCount = event[2];
        short vKeyCode = (short) event[3];
        char unicodeChar = (char) event[4];
        int controlKeyState = event[5];

        StringBuilder sb = new StringBuilder();
        // support some C1 control sequences: ALT + [@-_] (and [a-z]?) => ESC <ascii>
        // http://en.wikipedia.org/wiki/C0_and_C1_control_codes#C1_set
        final int altState = LEFT_ALT_PRESSED | RIGHT_ALT_PRESSED;
        // Pressing "Alt Gr" is translated to Alt-Ctrl, hence it has to be checked that Ctrl is _not_ pressed,
        // otherwise inserting of "Alt Gr" codes on non-US keyboards would yield errors
        final int ctrlState = LEFT_CTRL_PRESSED | RIGHT_CTRL_PRESSED;
        // Compute the overall alt state
        boolean isAlt = ((controlKeyState & altState) != 0)
                && ((controlKeyState & ctrlState) == 0);

        if (keyDown) {
            if (unicodeChar > 0) {
                if (Character.isHighSurrogate(unicodeChar)) {
                    flushPending(sb, pending);
                    pending.high = unicodeChar;
                    pending.repeatCount = repeatCount;
                } else if (Character.isLowSurrogate(unicodeChar)) {
                    if (pending.high != -1) {
                        int codePoint = Character.toCodePoint((char) pending.high, unicodeChar);
                        pending.high = -1;
                        for (int k = 0; k < repeatCount; k++) {
                            sb.appendCodePoint(codePoint);
                        }
                    } else {
                        sb.append('?');
                    }
                } else {
                    flushPending(sb, pending);
                    boolean shiftPressed = (controlKeyState & SHIFT_PRESSED) != 0;
                    if (unicodeChar == '\t' && shiftPressed) {
                        String btab = capabilityLookup.apply(Capability.key_btab);
                        if (btab != null) {
                            for (int k = 0; k < repeatCount; k++) {
                                sb.append(btab);
                            }
                        }
                    } else {
                        // Windows coalesces held-key repeats into one record:
                        // repeat the whole unit, mirroring the virtual-key branch.
                        for (int k = 0; k < repeatCount; k++) {
                            if (isAlt) {
                                sb.append('\033');
                            }
                            sb.append(unicodeChar);
                        }
                    }
                }
            } else {
                flushPending(sb, pending);
                // virtual keycodes: http://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
                String escapeSequence = escapeSequenceLookup.apply(vKeyCode);
                if (escapeSequence != null) {
                    for (int k = 0; k < repeatCount; k++) {
                        if (isAlt) {
                            sb.append('\033');
                        }
                        sb.append(escapeSequence);
                    }
                }
            }
        } else {
            // key up event
            // support ALT+NumPad input method
            if (vKeyCode == 0x12/* VK_MENU ALT key */ && unicodeChar > 0) {
                flushPending(sb, pending);
                sb.append(unicodeChar);
            }
        }
        return sb.toString().getBytes(charset);
    }

    /**
     * Emit a stale pending high surrogate as replacements and clear it.
     * A high left over by an unrelated record is orphaned input, not part
     * of a pair, so it resolves exactly like #313 lone surrogates.
     *
     * @param sb the output being built
     * @param pending the pairing state, cleared by this call
     */
    private static void flushPending(StringBuilder sb, PendingSurrogate pending) {
        if (pending.high != -1) {
            for (int k = 0; k < pending.repeatCount; k++) {
                sb.append('?');
            }
            pending.high = -1;
        }
    }

    /**
     * High-surrogate state carried across KEY_EVENT records so a
     * supplementary character split into high/low records reassembles.
     * Owned by one terminal instance and touched only by its input path.
     */
    static final class PendingSurrogate {
        /** Pending high surrogate, or -1 when empty. */
        int high = -1;
        /** Repeat count of the record that stashed it. */
        int repeatCount;
    }

    /**
     * Set the charset for encoding translated input characters, so both
     * sides of the terminal boundary agree with the connection's resolved
     * input charset instead of the ambient JVM default.
     *
     * @param charset the input charset, never null
     */
    public void setInputCharset(Charset charset) {
        this.inputCharset = Objects.requireNonNull(charset, "inputCharset");
    }

    /**
     * The active input charset. Package-visible for headless tests.
     *
     * @return the input charset
     */
    Charset getInputCharset() {
        return inputCharset;
    }

    /**
     * Set the mouse event handler. Enables/disables ENABLE_MOUSE_INPUT
     * on the console input handle.
     *
     * @param handler the mouse event handler, or {@code null} to disable mouse input
     */
    public void setMouseHandler(Consumer<MouseEvent> handler) {
        this.mouseHandler = handler;
        mouseInputEnabled = handler != null;
        // Re-apply the current attributes to update ENABLE_MOUSE_INPUT
        // and ENABLE_EXTENDED_FLAGS in the console mode
        setAttributes(new Attributes(attributes));
    }

    /**
     * Get the current mouse handler.
     *
     * @return the current mouse event handler, or {@code null} if none is set
     */
    public Consumer<MouseEvent> getMouseHandler() {
        return mouseHandler;
    }

    private static boolean setVTMode() {
        if (Handles.OUTPUT == WinConsoleNative.INVALID_HANDLE) {
            return false;
        }
        int mode = WinConsoleNative.getConsoleMode(Handles.OUTPUT);
        if (mode == -1) {
            return false;
        }
        return WinConsoleNative.setConsoleMode(Handles.OUTPUT,
                mode | WinConsoleNative.ENABLE_VIRTUAL_TERMINAL_PROCESSING);
    }

    /**
     * Check if virtual terminal mode is supported.
     *
     * @return true if virtual terminal mode is supported
     */
    public static boolean isVTSupported() {
        return setVTMode();
    }

    /**
     * Translate a Windows MOUSE_EVENT_RECORD into a MouseEvent.
     * Event format: {2, x, y, buttonState, controlKeyState, eventFlags}
     */
    private MouseEvent translateMouseEvent(int[] event) {
        int x = event[1] + 1; // Windows is 0-based, MouseEvent is 1-based
        int y = event[2] + 1;
        int buttonState = event[3];
        int controlKeyState = event[4];
        int eventFlags = event[5];

        boolean shift = (controlKeyState & SHIFT_PRESSED) != 0;
        boolean alt = ((controlKeyState & LEFT_ALT_PRESSED) != 0)
                || ((controlKeyState & RIGHT_ALT_PRESSED) != 0);
        boolean ctrl = ((controlKeyState & LEFT_CTRL_PRESSED) != 0)
                || ((controlKeyState & RIGHT_CTRL_PRESSED) != 0);

        MouseEvent.Type type;
        MouseEvent.Button button;

        if ((eventFlags & MOUSE_WHEELED) != 0) {
            type = MouseEvent.Type.SCROLL;
            // High word of buttonState indicates direction
            // Win32 API: positive high word = scroll away from user (up),
            // negative high word = scroll toward user (down)
            button = (buttonState >> 16) > 0
                    ? MouseEvent.Button.SCROLL_UP
                    : MouseEvent.Button.SCROLL_DOWN;
        } else if ((eventFlags & MOUSE_MOVED) != 0) {
            if (buttonState != 0) {
                type = MouseEvent.Type.DRAG;
                button = buttonFromState(buttonState);
            } else {
                type = MouseEvent.Type.MOVE;
                button = MouseEvent.Button.NONE;
            }
        } else {
            // Button press or release — compare with last state to determine
            int pressed = buttonState & ~lastButtonState;
            int released = lastButtonState & ~buttonState;

            if (pressed != 0) {
                type = MouseEvent.Type.PRESS;
                button = buttonFromState(pressed);
            } else if (released != 0) {
                type = MouseEvent.Type.RELEASE;
                button = buttonFromState(released);
            } else {
                // No change — likely a duplicate or focus event
                lastButtonState = buttonState;
                return null;
            }
        }

        lastButtonState = buttonState;
        return new MouseEvent(type, button, x, y, shift, alt, ctrl);
    }

    private static MouseEvent.Button buttonFromState(int state) {
        if ((state & FROM_LEFT_1ST_BUTTON_PRESSED) != 0) {
            return MouseEvent.Button.LEFT;
        }
        if ((state & RIGHTMOST_BUTTON_PRESSED) != 0) {
            return MouseEvent.Button.RIGHT;
        }
        if ((state & FROM_LEFT_2ND_BUTTON_PRESSED) != 0) {
            return MouseEvent.Button.MIDDLE;
        }
        return MouseEvent.Button.NONE;
    }
}
