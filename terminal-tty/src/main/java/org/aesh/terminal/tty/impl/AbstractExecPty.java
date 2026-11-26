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
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.detect.ProcessRunner;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.LoggerUtil;

/**
 * Abstract base class for PTY implementations that use external commands
 * (stty, tty) to interact with the terminal.
 */
public abstract class AbstractExecPty implements Pty {

    private static final class Log {
        static final Logger LOGGER = LoggerUtil.getLogger(AbstractExecPty.class.getName());
    }

    static boolean isFineLoggingConfigured() {
        return System.getProperty("java.util.logging.config.file") != null
                || System.getProperty("java.util.logging.config.class") != null
                || System.getProperty("java.util.logging.manager") != null
                || Boolean.getBoolean("org.aesh.terminal.debug");
    }

    private static final class SttyLookup {
        static final Map<String, Enum<?>> FLAGS = new HashMap<>(128);
        static final Map<String, Attributes.ControlChar> CONTROL_CHARS = new HashMap<>(32);
        static final ConcurrentHashMap<String, Pattern[]> INT_PATTERNS = new ConcurrentHashMap<>();

        static {
            for (Attributes.InputFlag f : Attributes.InputFlag.values()) {
                FLAGS.put(f.name().toLowerCase(Locale.ROOT), f);
            }
            for (Attributes.OutputFlag f : Attributes.OutputFlag.values()) {
                FLAGS.put(f.name().toLowerCase(Locale.ROOT), f);
            }
            for (Attributes.ControlFlag f : Attributes.ControlFlag.values()) {
                FLAGS.put(f.name().toLowerCase(Locale.ROOT), f);
            }
            for (Attributes.LocalFlag f : Attributes.LocalFlag.values()) {
                FLAGS.put(f.name().toLowerCase(Locale.ROOT), f);
            }
            for (Attributes.ControlChar cc : Attributes.ControlChar.values()) {
                CONTROL_CHARS.put(cc.name().toLowerCase(Locale.ROOT).substring(1), cc);
            }
            CONTROL_CHARS.put("rprnt", Attributes.ControlChar.VREPRINT);
        }

        static Pattern[] intPatterns(String name) {
            return INT_PATTERNS.computeIfAbsent(name, n -> new Pattern[] {
                    Pattern.compile("\\b([0-9]+)\\s+" + n + "\\b"),
                    Pattern.compile("\\b" + n + "\\s+([0-9]+)\\b"),
                    Pattern.compile("\\b" + n + "\\s*=\\s*([0-9]+)\\b")
            });
        }
    }

    private final String name;

    /**
     * Constructor.
     *
     * @param name the pty name
     */
    protected AbstractExecPty(String name) {
        this.name = name;
    }

    /**
     * Returns the name of the TTY device.
     *
     * @return the TTY device name
     */
    public String getName() {
        return name;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public InputStream getMasterInput() {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getMasterOutput() {
        throw new UnsupportedOperationException();
    }

    static Attributes doGetAttr(String cfg) {
        Attributes attributes = new Attributes();
        int len = cfg.length();
        int i = 0;
        while (i < len) {
            i = skipDelim(cfg, i, len);
            if (i >= len) {
                break;
            }
            int tokStart = i;
            int eqIdx = -1;
            while (i < len && !isDelim(cfg.charAt(i))) {
                if (eqIdx < 0 && cfg.charAt(i) == '=') {
                    eqIdx = i;
                }
                i++;
            }
            i = processToken(cfg, tokStart, i, eqIdx, len, attributes);
        }
        return attributes;
    }

    private static int processToken(String cfg, int tokStart, int tokEnd, int eqIdx, int len,
            Attributes attributes) {
        if (eqIdx > tokStart) {
            Attributes.ControlChar cc = SttyLookup.CONTROL_CHARS.get(cfg.substring(tokStart, eqIdx));
            if (cc == null) {
                return tokEnd;
            }
            if (eqIdx + 1 < tokEnd) {
                setControlCharValue(attributes, cc, cfg.substring(eqIdx + 1, tokEnd));
                return tokEnd;
            }
            return readControlCharValue(cfg, tokEnd, len, cc, attributes);
        }
        String token = cfg.substring(tokStart, tokEnd);
        Attributes.ControlChar cc = SttyLookup.CONTROL_CHARS.get(token);
        if (cc != null) {
            int next = skipWhitespace(cfg, tokEnd, len);
            if (next < len && cfg.charAt(next) == '=') {
                return readControlCharValue(cfg, next + 1, len, cc, attributes);
            }
            return tokEnd;
        }
        boolean negated = !token.isEmpty() && token.charAt(0) == '-';
        String flagName = negated ? token.substring(1) : token;
        applyFlag(attributes, flagName, !negated);
        return tokEnd;
    }

    private static int readControlCharValue(String cfg, int pos, int len,
            Attributes.ControlChar cc, Attributes attributes) {
        int valStart = skipWhitespace(cfg, pos, len);
        int valEnd = valStart;
        while (valEnd < len && !isDelim(cfg.charAt(valEnd))) {
            valEnd++;
        }
        if (valEnd > valStart) {
            setControlCharValue(attributes, cc, cfg.substring(valStart, valEnd));
        }
        return valEnd;
    }

    private static void setControlCharValue(Attributes attributes, Attributes.ControlChar cc, String raw) {
        attributes.setControlChar(cc, parseControlChar(raw.toUpperCase(Locale.ROOT)));
    }

    static void applyFlag(Attributes attributes, String flagName, boolean value) {
        Enum<?> flag = SttyLookup.FLAGS.get(flagName);
        if (flag == null) {
            return;
        }
        if (flag instanceof Attributes.InputFlag) {
            attributes.setInputFlag((Attributes.InputFlag) flag, value);
        } else if (flag instanceof Attributes.OutputFlag) {
            attributes.setOutputFlag((Attributes.OutputFlag) flag, value);
        } else if (flag instanceof Attributes.ControlFlag) {
            attributes.setControlFlag((Attributes.ControlFlag) flag, value);
        } else if (flag instanceof Attributes.LocalFlag) {
            attributes.setLocalFlag((Attributes.LocalFlag) flag, value);
        }
    }

    private static boolean isDelim(char c) {
        return c == ';' || Character.isWhitespace(c);
    }

    private static int skipDelim(String s, int i, int len) {
        while (i < len && isDelim(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int skipWhitespace(String s, int i, int len) {
        while (i < len && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    static int parseControlChar(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        // Any <...> token is undef (handles localized variants like <indéfini>)
        if (str.charAt(0) == '<') {
            return -1;
        }
        // octal
        if (str.charAt(0) == '0') {
            return Integer.parseInt(str, 8);
        }
        // decimal
        if (str.charAt(0) >= '1' && str.charAt(0) <= '9') {
            return Integer.parseInt(str, 10);
        }
        // control char
        if (str.charAt(0) == '^') {
            return parseCaretChar(str, 1);
        } else if (str.length() >= 2 && str.charAt(0) == 'M' && str.charAt(1) == '-') {
            if (str.length() < 3) {
                return -1;
            }
            if (str.charAt(2) == '^') {
                int ctrl = parseCaretChar(str, 3);
                return ctrl < 0 ? -1 : ctrl + 128;
            }
            return str.charAt(2) + 128;
        }
        // del
        if ("DEL".equalsIgnoreCase(str)) {
            return 127;
        }
        return str.charAt(0);
    }

    private static int parseCaretChar(String str, int index) {
        if (index >= str.length()) {
            return -1;
        }
        char ch = str.charAt(index);
        return ch == '?' ? 127 : ch - 64;
    }

    static Size doGetSize(String cfg) throws IOException {
        return new Size(doGetInt("columns", cfg), doGetInt("rows", cfg));
    }

    static int doGetInt(String name, String cfg) throws IOException {
        for (Pattern pattern : SttyLookup.intPatterns(name)) {
            Matcher matcher = pattern.matcher(cfg);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        throw new IOException("Unable to parse " + name);
    }

    /**
     * Execute a command.
     *
     * @param cmd the command to execute
     * @return the command output
     * @throws IOException if execution fails
     */
    protected static String exec(final String... cmd) throws IOException {
        assert cmd != null && !cmd[0].isEmpty();
        try {
            boolean fine = isFineLoggingConfigured() && Log.LOGGER.isLoggable(Level.FINE);
            if (fine) {
                Log.LOGGER.log(Level.FINE, "Running: {0}", Arrays.toString(cmd));
            }
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectInput(Redirect.INHERIT);
            // Force C locale so stty and other commands produce English output
            processBuilder.environment().put("LC_ALL", "C");
            ProcessRunner.Result result = ProcessRunner.execute(processBuilder,
                    ProcessRunner.DEFAULT_TIMEOUT_MS);
            String output = result.text(Charset.defaultCharset());
            if (fine) {
                Log.LOGGER.log(Level.FINE, "Result: {0}", output);
            }
            if (result.timedOut() || result.exitCode() != 0) {
                throw new IOException("Error executing '" + String.join(" ", cmd) + "': " + output);
            }
            return output;
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Command interrupted").initCause(e);
        }
    }

}
