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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ExecHelper;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.OSUtils;

/**
 * PTY implementation for Cygwin environments on Windows.
 * This class provides pseudo-terminal support when running under Cygwin.
 */
public class CygwinPty extends AbstractExecPty {

    private static final Logger LOGGER = LoggerUtil.getLogger(CygwinPty.class.getName());

    // Windows console input mode flags, mirroring AbstractWindowsTerminal.
    // Used when the input is a real console (ConPTY, hidden mintty console)
    // rather than a pipe, in which case stty.exe would configure the wrong
    // object while Java reads the untouched console.
    private static final int ENABLE_PROCESSED_INPUT = 0x0001;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_WINDOW_INPUT = 0x0008;

    /**
     * Original console mode, stashed on first direct console access for
     * restoration on close. -1 means no direct access happened (pure stty
     * path), in which case close() has nothing extra to restore.
     */
    private int savedConsoleMode = -1;

    /**
     * Returns the PTY for the current terminal.
     *
     * @return the current PTY for the current terminal
     * @throws IOException if not running in a TTY or an I/O error occurs
     */
    public static Pty current() throws IOException {
        try {
            Process p = new ProcessBuilder(OSUtils.TTY_COMMAND)
                    .redirectInput(Redirect.INHERIT)
                    .start();
            String result = ExecHelper.waitAndCapture(p).trim();
            if (p.exitValue() != 0) {
                throw new IOException("Not a tty");
            }
            return new CygwinPty(result);
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Command interrupted").initCause(e);
        }
    }

    /**
     * Constructs a CygwinPty with the specified TTY name.
     *
     * @param name the name of the TTY device
     */
    protected CygwinPty(String name) {
        super(name);
    }

    @Override
    public InputStream getSlaveInput() {
        return new FileInputStream(FileDescriptor.in);
    }

    @Override
    public OutputStream getSlaveOutput() {
        return new FileOutputStream(FileDescriptor.out);
    }

    @Override
    public Attributes getAttr() throws IOException {
        Attributes direct = tryDirectGetAttr();
        if (direct != null) {
            return direct;
        }
        String cfg = doGetConfig();
        return doGetAttr(cfg);
    }

    @Override
    public void setAttr(Attributes attr) throws IOException {
        if (tryDirectSetAttr(attr)) {
            return;
        }
        Attributes current = getAttr();
        List<String> commands = new ArrayList<>();
        for (Attributes.InputFlag flag : Attributes.InputFlag.values()) {
            if (attr.getInputFlag(flag) != current.getInputFlag(flag)) {
                commands.add((attr.getInputFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        for (Attributes.OutputFlag flag : Attributes.OutputFlag.values()) {
            if (attr.getOutputFlag(flag) != current.getOutputFlag(flag)) {
                commands.add((attr.getOutputFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        for (Attributes.ControlFlag flag : Attributes.ControlFlag.values()) {
            if (attr.getControlFlag(flag) != current.getControlFlag(flag)) {
                commands.add((attr.getControlFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        for (Attributes.LocalFlag flag : Attributes.LocalFlag.values()) {
            if (attr.getLocalFlag(flag) != current.getLocalFlag(flag)) {
                commands.add((attr.getLocalFlag(flag) ? flag.name() : "-" + flag.name()).toLowerCase());
            }
        }
        String undef = System.getProperty("os.name").toLowerCase().startsWith("hp") ? "^-" : "undef";
        for (Attributes.ControlChar cchar : Attributes.ControlChar.values()) {
            if (attr.getControlChar(cchar) != current.getControlChar(cchar)) {
                String str = "";
                int v = attr.getControlChar(cchar);
                commands.add(cchar.name().toLowerCase().substring(1));
                if (cchar == Attributes.ControlChar.VMIN || cchar == Attributes.ControlChar.VTIME) {
                    commands.add(Integer.toString(v));
                } else if (v == 0) {
                    commands.add(undef);
                } else {
                    if (v >= 128) {
                        v -= 128;
                        str += "M-";
                    }
                    if (v < 32 || v == 127) {
                        v ^= 0x40;
                        str += "^";
                    }
                    str += (char) v;
                    commands.add(str);
                }
            }
        }
        if (!commands.isEmpty()) {
            commands.add(0, OSUtils.STTY_COMMAND);
            exec(commands.toArray(new String[0]));
        }
    }

    @Override
    public Size getSize() throws IOException {
        String cfg = doGetConfig();
        return doGetSize(cfg);
    }

    @Override
    public void close() {
        // Note: no checked exception — restore failures are logged, never thrown,
        // so close() stays safe to call from shutdown hooks.
        if (savedConsoleMode != -1) {
            try {
                writeConsoleMode(savedConsoleMode);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Failed to restore console mode on close", t);
            }
            savedConsoleMode = -1;
        }
        super.close();
    }

    /**
     * Try reading terminal attributes directly from the Windows console.
     * Used when stdin is a real console (ConPTY, hidden mintty console),
     * where stty.exe would configure a different object than the one Java
     * reads, silently leaving the console in cooked mode.
     *
     * @return attributes derived from the console mode, or null if no
     *         console is present (caller falls back to stty.exe)
     */
    private Attributes tryDirectGetAttr() {
        int mode = readConsoleMode();
        if (mode == -1) {
            return null;
        }
        stashConsoleMode(mode);
        LOGGER.log(Level.FINE, "Using direct console mode, bypassing stty.exe");
        return fromConsoleMode(mode);
    }

    /**
     * Try writing terminal attributes directly to the Windows console.
     *
     * @param attr the desired attributes
     * @return true if the console was programmed directly, false if the
     *         caller should fall back to stty.exe
     * @throws IOException if console programming fails after a console was found
     */
    private boolean tryDirectSetAttr(Attributes attr) throws IOException {
        int current = readConsoleMode();
        if (current == -1) {
            return false;
        }
        stashConsoleMode(current);
        try {
            writeConsoleMode(toConsoleMode(attr));
        } catch (Throwable t) {
            throw new IOException("Failed to set console mode directly", t);
        }
        return true;
    }

    /**
     * Stash the original console mode on first direct access so close()
     * can restore it exactly.
     */
    private void stashConsoleMode(int mode) {
        if (savedConsoleMode == -1) {
            savedConsoleMode = mode;
        }
    }

    /**
     * Read the raw Windows console input mode. Protected for testing.
     *
     * @return the console mode flags, or -1 if no console is present
     */
    protected int readConsoleMode() {
        try {
            long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
            if (handle == WinConsoleNative.INVALID_HANDLE) {
                return -1;
            }
            return WinConsoleNative.getConsoleMode(handle);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Write the raw Windows console input mode. Protected for testing.
     *
     * @param mode the console mode flags to set
     */
    protected void writeConsoleMode(int mode) {
        long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        WinConsoleNative.setConsoleMode(handle, mode);
    }

    /**
     * Translate terminal attributes to Windows console input mode flags,
     * mirroring AbstractWindowsTerminal.setAttributes (minus mouse handling,
     * which this path does not manage).
     *
     * @param attr the desired attributes
     * @return the console mode flags
     */
    static int toConsoleMode(Attributes attr) {
        int mode = ENABLE_WINDOW_INPUT;
        if (attr.getLocalFlag(Attributes.LocalFlag.ECHO)) {
            mode |= ENABLE_ECHO_INPUT;
        }
        if (attr.getLocalFlag(Attributes.LocalFlag.ICANON)) {
            mode |= ENABLE_LINE_INPUT;
        }
        if (attr.getLocalFlag(Attributes.LocalFlag.ISIG)) {
            mode |= ENABLE_PROCESSED_INPUT;
        }
        return mode;
    }

    /**
     * Translate Windows console input mode flags to terminal attributes.
     * Control characters keep Attributes defaults, which EventDecoder maps
     * to conventional values (intr=3, quit=28, eof=4, susp=26).
     *
     * @param mode the console mode flags
     * @return the terminal attributes
     */
    static Attributes fromConsoleMode(int mode) {
        Attributes attr = new Attributes();
        attr.setLocalFlag(Attributes.LocalFlag.ECHO, (mode & ENABLE_ECHO_INPUT) != 0);
        attr.setLocalFlag(Attributes.LocalFlag.ICANON, (mode & ENABLE_LINE_INPUT) != 0);
        attr.setLocalFlag(Attributes.LocalFlag.ISIG, (mode & ENABLE_PROCESSED_INPUT) != 0);
        return attr;
    }

    /**
     * Retrieves the terminal configuration using the stty command.
     *
     * @return the terminal configuration string
     * @throws IOException if an I/O error occurs
     */
    protected String doGetConfig() throws IOException {
        return exec(OSUtils.STTY_COMMAND, "-a");
    }

}
