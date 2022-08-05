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
package org.aesh.readline.terminal.impl;

import java.io.IOException;
import java.util.logging.Level;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.internal.Kernel32;
import static org.fusesource.jansi.internal.Kernel32.GetStdHandle;
import org.fusesource.jansi.internal.Kernel32.INPUT_RECORD;
import org.fusesource.jansi.internal.Kernel32.KEY_EVENT_RECORD;

import static org.fusesource.jansi.internal.Kernel32.STD_OUTPUT_HANDLE;

public class WinSysTerminal extends AbstractWindowsTerminal {

    private static final int VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    public WinSysTerminal(String name, boolean nativeSignals) throws IOException {
        this(name, nativeSignals, SignalHandlers.SIG_DFL);
    }

    public WinSysTerminal(String name, boolean nativeSignals, SignalHandler signalHandler) throws IOException {
        super(setVTMode(), AnsiConsole.out(), name, nativeSignals, signalHandler);
    }

    protected int getConsoleOutputCP() {
        return Kernel32.GetConsoleOutputCP();
    }

    @Override
    protected int getConsoleMode() {
        long hConsole = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        if (hConsole == (long)Kernel32.INVALID_HANDLE_VALUE) {
            return -1;
        } else {
            int[] mode = new int[1];
            return Kernel32.GetConsoleMode(hConsole, mode) == 0 ? -1 : mode[0];
        }
    }

    @Override
    protected void setConsoleMode(int mode) {
        long hConsole = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        if (hConsole != (long)Kernel32.INVALID_HANDLE_VALUE) {
            Kernel32.SetConsoleMode(hConsole, mode);
        }
    }

    public Size getSize() {
        long outputHandle = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
        Kernel32.GetConsoleScreenBufferInfo(outputHandle, info);
        Size size = new Size(info.windowWidth(), info.windowHeight());
        return size;
    }

    protected byte[] readConsoleInput() {
        // XXX does how many events to read in one call matter?
        INPUT_RECORD[] events = null;
        long hConsole = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        try {
            events = hConsole == (long)Kernel32.INVALID_HANDLE_VALUE ? null : Kernel32.readConsoleInputHelper(hConsole, 1, false);
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "read Windows terminal input error: ", e);
        }
        if (events == null) {
            return new byte[0];
        }
        StringBuilder sb = new StringBuilder();
        for (INPUT_RECORD event : events) {
            KEY_EVENT_RECORD keyEvent = event.keyEvent;
            // support some C1 control sequences: ALT + [@-_] (and [a-z]?) => ESC <ascii>
            // http://en.wikipedia.org/wiki/C0_and_C1_control_codes#C1_set
            final int altState = KEY_EVENT_RECORD.LEFT_ALT_PRESSED | KEY_EVENT_RECORD.RIGHT_ALT_PRESSED;
            // Pressing "Alt Gr" is translated to Alt-Ctrl, hence it has to be checked that Ctrl is _not_ pressed,
            // otherwise inserting of "Alt Gr" codes on non-US keyboards would yield errors
            final int ctrlState = KEY_EVENT_RECORD.LEFT_CTRL_PRESSED | KEY_EVENT_RECORD.RIGHT_CTRL_PRESSED;
            // Compute the overall alt state
            boolean isAlt = ((keyEvent.controlKeyState & altState) != 0) && ((keyEvent.controlKeyState & ctrlState) == 0);

            //Log.trace(keyEvent.keyDown? "KEY_DOWN" : "KEY_UP", "key code:", keyEvent.keyCode, "char:", (long)keyEvent.uchar);
            if (keyEvent.keyDown) {
                if (keyEvent.uchar > 0) {
                    boolean shiftPressed = (keyEvent.controlKeyState & KEY_EVENT_RECORD.SHIFT_PRESSED) != 0;
                    if (keyEvent.uchar == '\t' && shiftPressed) {
                        sb.append(getSequence(Capability.key_btab));
                    } else {
                        if (isAlt) {
                            sb.append('\033');
                        }
                        sb.append(keyEvent.uchar);
                    }
                }
                else {
                    // virtual keycodes: http://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
                    // TODO: numpad keys, modifiers
                    String escapeSequence = getEscapeSequence(keyEvent.keyCode);
                    if (escapeSequence != null) {
                        for (int k = 0; k < keyEvent.repeatCount; k++) {
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
                if (keyEvent.keyCode == 0x12/*VK_MENU ALT key*/ && keyEvent.uchar > 0) {
                    sb.append(keyEvent.uchar);
                }
            }
        }
        return sb.toString().getBytes();
    }

    // This allows to take benefit from Windows 10+ new features.
    private static boolean setVTMode() {
        long console = GetStdHandle(STD_OUTPUT_HANDLE);
        int[] mode = new int[1];
        if (Kernel32.GetConsoleMode(console, mode) == 0) {
            // No need to go further, not supported.
            return false;
        }
        if (Kernel32.SetConsoleMode(console, mode[0] | VIRTUAL_TERMINAL_PROCESSING) == 0) {
            // No need to go further, not supported.
            return false;
        }

        return true;
    }

    public static boolean isVTSupported() {
        return setVTMode();
    }
}
