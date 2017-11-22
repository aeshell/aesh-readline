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
package org.aesh.utils;

import org.aesh.terminal.tty.Capability;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infocmp helper methods.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public final class InfoCmp {

    private static final Map<String, String> CAPS = new HashMap<>();

    private InfoCmp() {
    }

    public static String getInfoCmp( String terminal ) throws IOException, InterruptedException {
        String caps = CAPS.get(terminal);
        if (caps == null) {
            Process p = new ProcessBuilder(OSUtils.INFOCMP_COMMAND, terminal).start();
            caps = ExecHelper.waitAndCapture(p);
            CAPS.put(terminal, caps);
        }
        return caps;
    }

    public static String getDefaultInfoCmp(String terminal) {
        if(terminal.toLowerCase().contains("windows")) {
            return readDefaultInfoCmp("windows_caps.src");
        }
        else if(terminal.toLowerCase().contains("xterm_256color")) {
            return readDefaultInfoCmp("xterm_256color_caps.src");
        }
        else if(terminal.toLowerCase().contains("xterm")) {
            return readDefaultInfoCmp("xterm_caps.src");
        }
        else if(terminal.toLowerCase().contains("vt100")) {
            return readDefaultInfoCmp("vt100_caps.src");
        }
         else
            return readDefaultInfoCmp("ansi_caps.src");
    }

    private static String readDefaultInfoCmp(String filename) {
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        try (InputStream in = InfoCmp.class.getResourceAsStream(filename)) {
            byte[] buffer = new byte[256];
            while (true) {
                int len = in.read(buffer);
                if (len == -1) {
                    break;
                }
                res.write(buffer, 0, len);
            }
            return res.toString("ISO-8859-1");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setDefaultInfoCmp(String terminal, String caps) {
        CAPS.putIfAbsent(terminal, caps);
    }

    public static void parseInfoCmp(
            String capabilities,
            Set<Capability> bools,
            Map<Capability, Integer> ints,
            Map<Capability, String> strings ) {

        String[] lines = capabilities.split("\n");
        for (int i = 1; i < lines.length; i++) {
            Matcher m = Pattern.compile("\\s*(([^,]|\\\\,)+)\\s*[,$]").matcher(lines[i]);
            while (m.find()) {
                String cap = m.group(1);
                if (cap.contains("#")) {
                    int index = cap.indexOf('#');
                    String key = cap.substring(0, index);
                    String val = cap.substring(index + 1);
                    int iVal = val.startsWith("0x") ?
                            Integer.parseInt(val.substring(2), 16) :
                            Integer.parseInt(val);
                    Capability c = Capability.byName(key);
                    if (c != null) {
                        ints.put(c, iVal);
                    }
                } else if (cap.contains("=")) {
                    int index = cap.indexOf('=');
                    String key = cap.substring(0, index);
                    String val = cap.substring(index + 1);
                    Capability c = Capability.byName(key);
                    if (c != null) {
                        strings.put(c, val);
                    }
                } else {
                    Capability c = Capability.byName(cap);
                    if (c != null) {
                        bools.add(c);
                    }
                }
            }
        }
    }

}
