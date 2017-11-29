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

import org.aesh.terminal.Attributes;
import org.aesh.utils.Config;
import org.aesh.utils.ExecHelper;
import org.aesh.utils.OSUtils;
import org.aesh.util.LoggerUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aesh.terminal.tty.Size;

public class ExecPty implements Pty {

    private static final Logger LOGGER = LoggerUtil.getLogger(ExecPty.class.getName());

    private final String name;

    public static Pty current() throws IOException {
        try {
            LOGGER.log(Level.FINE,"getting pty: "+OSUtils.TTY_COMMAND);
            Process p = new ProcessBuilder(OSUtils.TTY_COMMAND)
                    .redirectInput(Redirect.INHERIT).start();
            String result = ExecHelper.waitAndCapture(p).trim();
            if (p.exitValue() != 0) {
                throw new IOException("Not a tty");
            }
            LOGGER.log(Level.FINE,"result: "+result);
            return new ExecPty(result);
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Command interrupted").initCause(e);
        }
    }

    protected ExecPty(String name) {
        this.name = name;
    }

    @Override
    public void close() throws IOException {
    }

    public String getName() {
        return name;
    }

    @Override
    public InputStream getMasterInput() {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getMasterOutput() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getSlaveInput() throws IOException {
        try {
            return new FileInputStream(getName());
        } catch (FileNotFoundException fnfe) {
            // When the tty file is not accessible to the current user,
            // fallback to using System.in.
            return System.in;
        }
    }

    @Override
    public OutputStream getSlaveOutput() throws IOException {
        try {
            return new FileOutputStream(getName());
        } catch (FileNotFoundException fnfe) {
            // When the tty file is not accessible to the current user,
            // fallback to using System.out.
            return System.out;
        }
    }

    @Override
    public Attributes getAttr() throws IOException {
        try {
            String cfg = doGetConfig();
            if (OSUtils.IS_HPUX || OSUtils.IS_SUNOS) {
                return doGetAttr(cfg);
            }
            else if(OSUtils.IS_LINUX) {
                return doGetLinuxAttr(cfg);
            }
             else
                return doGetAttr(cfg);
        }
        catch(IOException ioe) {
            //if we get permission denied on stty -F tty -a we can try without -F tty
            if(ioe.getMessage().contains("Permission denied")) {
               return doGetAttr(doGetFailSafeConfig());
            }
            else
                throw ioe;
        }
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
                    commands.add(Integer.toBinaryString(v));
                }
                else if (v == 0) {
                    commands.add(undef);
                }
                else {
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
            if(OSUtils.IS_HPUX || OSUtils.IS_SUNOS) {
                commands.add(0, OSUtils.STTY_COMMAND);
                exec(commands.toArray(new String[commands.size()]));
            }
            else {
                commands.add(0, OSUtils.STTY_COMMAND);
                commands.add(1, OSUtils.STTY_F_OPTION);
                commands.add(2, getName());
                try {
                    exec(commands.toArray(new String[commands.size()]));
                } catch (IOException ex) {
                    // Try a fallback without -f
                    commands.remove(2);
                    commands.remove(1);
                    exec(commands.toArray(new String[commands.size()]));
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
            try {
                return doGetOptimalSize(exec(OSUtils.STTY_COMMAND, OSUtils.STTY_F_OPTION, getName(), "size"));
            } catch (IOException ex) {
                // Try a fallback without -f
                return doGetOptimalSize(exec(OSUtils.STTY_COMMAND, "size"));
            }
        }

    }


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
        Attributes attributes = new Attributes();
        String[] attr = cfg.split(";");
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
        String[] optionLines = options.split(Config.getLineSeparator());
        for(String line : optionLines)
            for(String option : line.trim().split(" ")) {
                //options starting with - are ignored
                option = option.trim();
                if(option.length() > 0 && option.charAt(0) != '-') {
                    Attributes.ControlFlag controlFlag = getEnumFromString(Attributes.ControlFlag.class, option);
                    if(controlFlag != null)
                        attributes.setControlFlag(controlFlag, true);
                    else {
                        Attributes.InputFlag inputFlag = getEnumFromString(Attributes.InputFlag.class, option);
                        if(inputFlag != null)
                            attributes.setInputFlag(inputFlag, true);
                        else {
                            Attributes.LocalFlag localFlag = getEnumFromString(Attributes.LocalFlag.class, option);
                            if(localFlag != null)
                                attributes.setLocalFlag(localFlag, true);
                            else {
                                Attributes.OutputFlag outputFlag = getEnumFromString(Attributes.OutputFlag.class, option);
                                if(outputFlag != null)
                                    attributes.setOutputFlag(outputFlag, true);
                            }
                        }
                    }
                }
            }
    }

    private static void setAttr(Attributes.ControlChar cc, String input, Attributes attr) {
        attr.setControlChar(cc, parseControlChar(input.substring(input.lastIndexOf(' ')+1)));
    }

    private static <T extends Enum<T>> T getEnumFromString(Class<T> c, String string) {
        for(T flag : c.getEnumConstants())
            if(flag.name().toLowerCase().equals(string))
                return flag;
        return null;
    }

    static Attributes doGetAttr(String cfg) throws IOException {
        Attributes attributes = new Attributes();
        for (Attributes.InputFlag flag : Attributes.InputFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setInputFlag(flag, value);
            }
        }
        for (Attributes.OutputFlag flag : Attributes.OutputFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setOutputFlag(flag, value);
            }
        }
        for (Attributes.ControlFlag flag : Attributes.ControlFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setControlFlag(flag, value);
            }
        }
        for (Attributes.LocalFlag flag : Attributes.LocalFlag.values()) {
            Boolean value = doGetFlag(cfg, flag);
            if (value != null) {
                attributes.setLocalFlag(flag, value);
            }
        }
        for (Attributes.ControlChar cchar : Attributes.ControlChar.values()) {
            String name = cchar.name().toLowerCase().substring(1);
            if ("reprint".endsWith(name)) {
                name = "(?:reprint|rprnt)";
            }
            Matcher matcher = Pattern.compile("[\\s;]" + name + "\\s*=\\s*(.+?)[\\s;]").matcher(cfg);
            if (matcher.find()) {
                attributes.setControlChar(cchar, parseControlChar(matcher.group(1).toUpperCase()));
            }
        }
        return attributes;
    }

    private static Boolean doGetFlag(String cfg, Enum<?> flag) {
        Matcher matcher = Pattern.compile("(?:^|[\\s;])(\\-?" + flag.name().toLowerCase() + ")(?:[\\s;]|$)").matcher(cfg);
        return matcher.find() ? !matcher.group(1).startsWith("-") : null;
    }

    static int parseControlChar(String str) {
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
            if (str.charAt(1) == '?') {
                return 127;
            } else {
                return str.charAt(1) - 64;
            }
        }
        else if (str.charAt(0) == 'M' && str.charAt(1) == '-') {
            if (str.charAt(2) == '^') {
                if (str.charAt(3) == '?') {
                    return 127 + 128;
                } else {
                    return str.charAt(3) - 64 + 128;
                }
            } else {
                return str.charAt(2) + 128;
            }
        }
        // undef
        if ("<UNDEF>".equals(str) || "<undef>".equals(str)) {
            return -1;
        }
        // del
        if ("DEL".equalsIgnoreCase(str)) {
            return 127;
        }

        else {
            return str.charAt(0);
        }
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
        String[] tokens = cfg.split(";");
        return new Size(Integer.parseInt(tokens[4].substring(9)),
                Integer.parseInt(tokens[2].substring(7)));
    }

    private static Size doGetOptimalSize(String cfg) throws IOException {
        final String[] size = cfg.split(" ");
        return new Size(Integer.parseInt(size[1].trim()), Integer.parseInt(size[0].trim()));
    }

    static Size doGetSize(String cfg) throws IOException {
        return new Size(doGetInt("columns", cfg), doGetInt("rows", cfg));
    }

    private static int doGetInt(String name, String cfg) throws IOException {
        String[] patterns = new String[] {
                "\\b([0-9]+)\\s+" + name + "\\b",
                "\\b" + name + "\\s+([0-9]+)\\b",
                "\\b" + name + "\\s*=\\s*([0-9]+)\\b"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(cfg);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        throw new IOException("Unable to parse " + name);
    }

    private static String exec(final String... cmd) throws IOException {
        assert cmd != null && cmd[0].length() > 0;
        try {
            LOGGER.log(Level.FINE, "Running: "+ Arrays.toString(cmd));
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectInput(Redirect.INHERIT);
            Process p = processBuilder.start();
            String result = ExecHelper.waitAndCapture(p);
            LOGGER.log(Level.FINE, "Result: "+ result);
            if (p.exitValue() != 0) {
                throw new IOException("Error executing '" + String.join(" ", cmd) + "': " + result);
            }
            return result;
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Command interrupted").initCause(e);
        }
    }

}
