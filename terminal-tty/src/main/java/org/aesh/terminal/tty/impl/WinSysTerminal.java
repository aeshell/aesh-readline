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
import java.util.function.Consumer;
import java.util.logging.Level;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.MouseEvent;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

/**
 * Windows system terminal implementation using native console API via JNI.
 */
public class WinSysTerminal extends AbstractWindowsTerminal {

    private static final long INPUT_HANDLE = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
    private static final long OUTPUT_HANDLE = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);

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

    private Consumer<MouseEvent> mouseHandler;
    private int lastButtonState;

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
        super(setVTMode(), System.out, name, nativeSignals, signalHandler);
        enableVTInput();
    }

    protected int getConsoleOutputCP() {
        return WinConsoleNative.getConsoleOutputCP();
    }

    @Override
    protected int getConsoleMode() {
        if (INPUT_HANDLE == WinConsoleNative.INVALID_HANDLE) {
            return -1;
        }
        return WinConsoleNative.getConsoleMode(INPUT_HANDLE);
    }

    @Override
    protected void setConsoleMode(int mode) {
        if (INPUT_HANDLE != WinConsoleNative.INVALID_HANDLE) {
            WinConsoleNative.setConsoleMode(INPUT_HANDLE, mode);
        }
    }

    public Size getSize() {
        int[] size = WinConsoleNative.getConsoleSize(OUTPUT_HANDLE);
        if (size == null) {
            return new Size(80, 24);
        }
        return new Size(size[0], size[1]);
    }

    protected byte[] readConsoleInput() {
        if (INPUT_HANDLE == WinConsoleNative.INVALID_HANDLE) {
            return new byte[0];
        }
        int[] event;
        try {
            event = WinConsoleNative.readConsoleInputEvent(INPUT_HANDLE);
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

        // Key event: {1, keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
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
                boolean shiftPressed = (controlKeyState & SHIFT_PRESSED) != 0;
                if (unicodeChar == '\t' && shiftPressed) {
                    sb.append(getSequence(Capability.key_btab));
                } else {
                    if (isAlt) {
                        sb.append('\033');
                    }
                    sb.append(unicodeChar);
                }
            } else {
                // virtual keycodes: http://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
                String escapeSequence = getEscapeSequence(vKeyCode);
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
                sb.append(unicodeChar);
            }
        }
        return sb.toString().getBytes();
    }

    /**
     * Try to enable VT input mode on the console input handle.
     * When enabled, Windows Terminal delivers special keys and mouse events
     * as VT escape sequences through KEY_EVENT records instead of as
     * virtual key codes or MOUSE_EVENT records.
     */
    /**
     * Set the mouse event handler. Enables/disables ENABLE_MOUSE_INPUT
     * on the console input handle.
     */
    public void setMouseHandler(Consumer<MouseEvent> handler) {
        this.mouseHandler = handler;
        if (INPUT_HANDLE == WinConsoleNative.INVALID_HANDLE) {
            return;
        }
        int mode = WinConsoleNative.getConsoleMode(INPUT_HANDLE);
        if (mode == -1) {
            return;
        }
        if (handler != null) {
            mode |= ENABLE_MOUSE_INPUT;
            // Disable quick edit mode — it conflicts with mouse tracking
            mode &= ~ENABLE_QUICK_EDIT_MODE;
        } else {
            mode &= ~ENABLE_MOUSE_INPUT;
        }
        WinConsoleNative.setConsoleMode(INPUT_HANDLE, mode);
    }

    /**
     * Get the current mouse handler.
     */
    public Consumer<MouseEvent> getMouseHandler() {
        return mouseHandler;
    }

    private void enableVTInput() {
        if (INPUT_HANDLE == WinConsoleNative.INVALID_HANDLE) {
            return;
        }
        int mode = WinConsoleNative.getConsoleMode(INPUT_HANDLE);
        if (mode == -1) {
            return;
        }
        originalInputMode = mode;
        if (WinConsoleNative.setConsoleMode(INPUT_HANDLE, mode | ENABLE_VIRTUAL_TERMINAL_INPUT)) {
            vtInputEnabled = true;
        }
    }

    private static boolean setVTMode() {
        if (OUTPUT_HANDLE == WinConsoleNative.INVALID_HANDLE) {
            return false;
        }
        int mode = WinConsoleNative.getConsoleMode(OUTPUT_HANDLE);
        if (mode == -1) {
            return false;
        }
        return WinConsoleNative.setConsoleMode(OUTPUT_HANDLE,
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
            button = (buttonState >> 16) > 0
                    ? MouseEvent.Button.SCROLL_DOWN
                    : MouseEvent.Button.SCROLL_UP;
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
