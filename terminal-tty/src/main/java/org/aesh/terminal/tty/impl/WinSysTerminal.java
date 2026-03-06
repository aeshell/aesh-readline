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
import java.util.logging.Level;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;

/**
 * Windows system terminal implementation using native console API via JNI.
 */
public class WinSysTerminal extends AbstractWindowsTerminal {

    private static final int VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    // Windows key modifier constants
    private static final int RIGHT_ALT_PRESSED = 0x0001;
    private static final int LEFT_ALT_PRESSED = 0x0002;
    private static final int RIGHT_CTRL_PRESSED = 0x0004;
    private static final int LEFT_CTRL_PRESSED = 0x0008;
    private static final int SHIFT_PRESSED = 0x0010;

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
    }

    protected int getConsoleOutputCP() {
        return WinConsoleNative.getConsoleOutputCP();
    }

    @Override
    protected int getConsoleMode() {
        long hConsole = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        if (hConsole == WinConsoleNative.INVALID_HANDLE) {
            return -1;
        }
        return WinConsoleNative.getConsoleMode(hConsole);
    }

    @Override
    protected void setConsoleMode(int mode) {
        long hConsole = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        if (hConsole != WinConsoleNative.INVALID_HANDLE) {
            WinConsoleNative.setConsoleMode(hConsole, mode);
        }
    }

    public Size getSize() {
        long outputHandle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        int[] size = WinConsoleNative.getConsoleSize(outputHandle);
        if (size == null) {
            return new Size(80, 24);
        }
        return new Size(size[0], size[1]);
    }

    protected byte[] readConsoleInput() {
        long hConsole = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        if (hConsole == WinConsoleNative.INVALID_HANDLE) {
            return new byte[0];
        }
        int[] keyEvent;
        try {
            keyEvent = WinConsoleNative.readConsoleKeyEvent(hConsole);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "read Windows terminal input error: ", e);
            return new byte[0];
        }
        if (keyEvent == null) {
            return new byte[0];
        }
        // keyEvent: {keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
        boolean keyDown = keyEvent[0] != 0;
        int repeatCount = keyEvent[1];
        short vKeyCode = (short) keyEvent[2];
        char unicodeChar = (char) keyEvent[3];
        int controlKeyState = keyEvent[4];

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

    // This allows to take benefit from Windows 10+ new features.
    private static boolean setVTMode() {
        long console = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        if (console == WinConsoleNative.INVALID_HANDLE) {
            return false;
        }
        int mode = WinConsoleNative.getConsoleMode(console);
        if (mode == -1) {
            // No need to go further, not supported.
            return false;
        }
        if (!WinConsoleNative.setConsoleMode(console, mode | VIRTUAL_TERMINAL_PROCESSING)) {
            // No need to go further, not supported.
            return false;
        }

        return true;
    }

    /**
     * Check if virtual terminal mode is supported.
     *
     * @return true if virtual terminal mode is supported
     */
    public static boolean isVTSupported() {
        return setVTMode();
    }
}
