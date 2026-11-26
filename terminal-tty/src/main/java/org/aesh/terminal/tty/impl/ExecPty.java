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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.detect.ProcessRunner;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.OSUtils;

/**
 * PTY implementation that uses external commands (stty, tty) to interact with the terminal.
 * This is the default PTY implementation for POSIX systems.
 */
public class ExecPty extends AbstractExecPty {

    private static final class Log {
        static final Logger LOGGER = LoggerUtil.getLogger(ExecPty.class.getName());
    }

    private final boolean validTTYFile;
    private static final String NOT_A_TTY = "not a tty";

    private volatile InputStream slaveInput;
    private volatile OutputStream slaveOutput;
    private boolean slaveInputOwned;
    private boolean slaveOutputOwned;

    /**
     * Returns the PTY for the current terminal.
     *
     * @return the current PTY
     * @throws IOException if not running in a TTY or an I/O error occurs
     */
    public static Pty current() throws IOException {
        try {
            boolean fine = isFineLoggingConfigured() && Log.LOGGER.isLoggable(Level.FINE);
            if (fine) {
                Log.LOGGER.log(Level.FINE, "getting pty: {0}", OSUtils.TTY_COMMAND);
            }
            ProcessBuilder starter = new ProcessBuilder(OSUtils.TTY_COMMAND)
                    .redirectInput(Redirect.INHERIT);
            ProcessRunner.Result result = ProcessRunner.execute(starter,
                    ProcessRunner.DEFAULT_TIMEOUT_MS);
            if (result.timedOut() || result.exitCode() != 0) {
                throw new IOException("Not a tty");
            }
            String tty = result.text(Charset.defaultCharset()).trim();
            if (fine) {
                Log.LOGGER.log(Level.FINE, "result: {0}", tty);
            }
            return new ExecPty(tty);
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Command interrupted").initCause(e);
        }
    }

    /**
     * Constructs an ExecPty with the specified TTY name.
     *
     * @param name the name of the TTY device
     */
    protected ExecPty(String name) {
        super(name);
        /*
         * There are some contexts (eg lxc container) in which tty returns 'not a tty' without
         * error. This file shouldn't exist on the file system but in case it exists
         * concider tty file invalid.
         */
        validTTYFile = new File(name).exists() && !NOT_A_TTY.equals(name);
        if (isFineLoggingConfigured() && Log.LOGGER.isLoggable(Level.FINE)) {
            Log.LOGGER.log(Level.FINE, "tty file {0} valid? {1}", new Object[] { name, validTTYFile });
        }
    }

    @Override
    public InputStream getSlaveInput() {
        if (slaveInput == null) {
            synchronized (this) {
                if (slaveInput == null) {
                    try {
                        if (!validTTYFile) {
                            slaveInput = System.in;
                        } else {
                            slaveInput = new FileInputStream(getName());
                            slaveInputOwned = true;
                        }
                    } catch (FileNotFoundException fnfe) {
                        // When the tty file is not accessible to the current user,
                        // fallback to using System.in.
                        slaveInput = System.in;
                    }
                }
            }
        }
        return slaveInput;
    }

    @Override
    public OutputStream getSlaveOutput() {
        if (slaveOutput == null) {
            synchronized (this) {
                if (slaveOutput == null) {
                    try {
                        if (!validTTYFile) {
                            slaveOutput = System.out;
                        } else {
                            slaveOutput = new FileOutputStream(getName());
                            slaveOutputOwned = true;
                        }
                    } catch (FileNotFoundException fnfe) {
                        // When the tty file is not accessible to the current user,
                        // fallback to using System.out.
                        slaveOutput = System.out;
                    }
                }
            }
        }
        return slaveOutput;
    }

    /**
     * Close the streams this PTY opened. Borrowed standard streams
     * ({@code System.in/out} fallbacks) are never closed. The first
     * failure propagates so {@link AbstractPosixTerminal#close()} can
     * attach it to its suppression chain.
     *
     * @throws IOException if closing an owned stream fails
     */
    @Override
    public void close() throws IOException {
        IOException firstFailure = null;
        if (slaveInputOwned && slaveInput != null) {
            try {
                slaveInput.close();
            } catch (IOException e) {
                firstFailure = e;
            }
            slaveInputOwned = false;
        }
        if (slaveOutputOwned && slaveOutput != null) {
            try {
                slaveOutput.close();
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                } else if (e != firstFailure) {
                    firstFailure.addSuppressed(e);
                }
            }
            slaveOutputOwned = false;
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @Override
    public Attributes getAttr() throws IOException {
        try {
            String cfg = doGetConfig();
            if (OSUtils.IS_HPUX || OSUtils.IS_SUNOS) {
                return doGetAttr(cfg);
            } else if (OSUtils.IS_LINUX) {
                return doGetLinuxAttr(cfg);
            } else
                return doGetAttr(cfg);
        } catch (IOException ioe) {
            //if we get permission denied on stty -F tty -a we can try without -F tty
            if (ioe.getMessage() != null && ioe.getMessage().contains("Permission denied")) {
                return doGetAttr(doGetFailSafeConfig());
            } else
                throw ioe;
        }
    }

    @Override
    public void setAttr(Attributes attr) throws IOException {
        Attributes current = getAttr();
        List<String> commands = buildSetAttrCommands(attr, current);
        if (!commands.isEmpty()) {
            if (OSUtils.IS_HPUX || OSUtils.IS_SUNOS) {
                commands.add(0, OSUtils.STTY_COMMAND);
                exec(commands.toArray(new String[0]));
            } else {
                commands.add(0, OSUtils.STTY_COMMAND);
                commands.add(1, OSUtils.STTY_F_OPTION);
                commands.add(2, getName());
                try {
                    exec(commands.toArray(new String[0]));
                } catch (IOException ex) {
                    // Try a fallback without -f
                    commands.remove(2);
                    commands.remove(1);
                    exec(commands.toArray(new String[0]));
                }
            }
        }
    }

    @Override
    public Size getSize() throws IOException {
        if (OSUtils.IS_HPUX) {
            return doGetOptimalSize(exec(OSUtils.STTY_COMMAND, "size"));
        } else if (OSUtils.IS_SUNOS) {
            String config = doGetConfig();
            return new Size(doGetInt("columns", config), doGetInt("rows", config));
        } else {
            return doGetOptimalSize(exec(OSUtils.STTY_COMMAND, OSUtils.STTY_F_OPTION, getName(), "size"));

        }

    }

    /**
     * Retrieves the terminal configuration using the stty command.
     *
     * @return the terminal configuration string
     * @throws IOException if an I/O error occurs
     */
    protected String doGetConfig() throws IOException {
        if (OSUtils.IS_HPUX || OSUtils.IS_SUNOS) {
            return exec(OSUtils.STTY_COMMAND, "-a");
        } else {
            try {
                return exec(OSUtils.STTY_COMMAND, OSUtils.STTY_F_OPTION, getName(), "-a");
            } catch (IOException ex) {
                return exec(OSUtils.STTY_COMMAND, "-a");
            }
        }
    }

    private String doGetFailSafeConfig() throws IOException {
        return exec(OSUtils.STTY_COMMAND, "-a");
    }

    static Attributes doGetLinuxAttr(String cfg) {
        String[] attr = cfg.split(";");
        if (attr.length < 22) {
            return doGetAttr(cfg);
        }
        Attributes attributes = new Attributes();
        //the first line of attributes we ignore (speed, rows, columns and line)
        //the rest which are delimited with ; are control cars
        setAttr(Attributes.ControlChar.VINTR, attr[4], attributes);
        setAttr(Attributes.ControlChar.VQUIT, attr[5], attributes);
        setAttr(Attributes.ControlChar.VERASE, attr[6], attributes);
        setAttr(Attributes.ControlChar.VKILL, attr[7], attributes);
        setAttr(Attributes.ControlChar.VEOF, attr[8], attributes);
        setAttr(Attributes.ControlChar.VEOL, attr[9], attributes);
        setAttr(Attributes.ControlChar.VEOL2, attr[10], attributes);
        //setAttr(Attributes.ControlChar.VSWTC, attr[11], attributes);
        setAttr(Attributes.ControlChar.VSTART, attr[12], attributes);
        setAttr(Attributes.ControlChar.VSTOP, attr[13], attributes);
        setAttr(Attributes.ControlChar.VSUSP, attr[14], attributes);
        setAttr(Attributes.ControlChar.VREPRINT, attr[15], attributes);
        setAttr(Attributes.ControlChar.VWERASE, attr[16], attributes);
        setAttr(Attributes.ControlChar.VLNEXT, attr[17], attributes);
        setAttr(Attributes.ControlChar.VDISCARD, attr[18], attributes);
        setAttr(Attributes.ControlChar.VMIN, attr[19], attributes);
        setAttr(Attributes.ControlChar.VTIME, attr[20], attributes);

        doParseLinuxOptions(attr[21], attributes);

        return attributes;
    }

    private static void doParseLinuxOptions(String options, Attributes attributes) {
        // Scan whitespace-delimited tokens directly (tolerant of both LF and
        // CRLF line endings) and apply positive flags via the cached lookup map.
        int len = options.length();
        int i = 0;
        while (i < len) {
            while (i < len && Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < len && !Character.isWhitespace(options.charAt(i))) {
                i++;
            }
            if (i > start && options.charAt(start) != '-') {
                applyFlag(attributes, options.substring(start, i), true);
            }
        }
    }

    private static void setAttr(Attributes.ControlChar cc, String input, Attributes attr) {
        attr.setControlChar(cc, parseControlChar(input.substring(input.lastIndexOf(' ') + 1)));
    }

    /**
     * There is only a 4 line output from ttytype -s:
     * TERM='vt200'; export TERM;
     * LINES=47; export LINES;
     * COLUMNS=112; export COLUMNS;
     * ERASE='^?'; export ERASE;
     *
     * @param cfg input
     * @return size
     */
    static Size doGetHPUXSize(String cfg) {
        // Strip carriage returns first: files checked out on Windows carry
        // CRLF, which shifts every positional substring below by one. The
        // remaining layout is what the indices were written against, and
        // single-line live ttytype output contains no CR to strip.
        String[] tokens = cfg.replace("\r", "").split(";");
        return new Size(Integer.parseInt(tokens[4].substring(9)),
                Integer.parseInt(tokens[2].substring(7)));
    }

    private static Size doGetOptimalSize(String cfg) throws IOException {
        String trimmed = cfg == null ? "" : cfg.trim();
        final String[] size = trimmed.split("\\s+");
        if (size.length < 2) {
            throw new IOException("Unable to parse stty size output: " + cfg);
        }
        try {
            return new Size(Integer.parseInt(size[1]), Integer.parseInt(size[0]));
        } catch (NumberFormatException e) {
            throw new IOException("Unable to parse stty size output: " + cfg, e);
        }
    }

}
