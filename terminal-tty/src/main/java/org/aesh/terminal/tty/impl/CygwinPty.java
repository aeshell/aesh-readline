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

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ExecHelper;
import org.aesh.terminal.utils.OSUtils;

/**
 * PTY implementation for Cygwin environments on Windows.
 * This class provides pseudo-terminal support when running under Cygwin.
 */
public class CygwinPty extends AbstractExecPty {

    /**
     * Returns the PTY for the current terminal.
     *
     * @return the current PTY
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
        String cfg = doGetConfig();
        return doGetAttr(cfg);
    }

    @Override
    public void setAttr(Attributes attr) throws IOException {
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
