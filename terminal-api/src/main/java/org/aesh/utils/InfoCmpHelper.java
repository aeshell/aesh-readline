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

import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class InfoCmpHelper {

    private static boolean initialized = false;
    private static Set<Capability> bools = new HashSet<>();
    private static Map<Capability, Integer> ints = new HashMap<>();
    private static Map<Capability, String> strings = new HashMap<>();

    public static int[] getCurrentTranslatedCapabilityAsInts(String cap, int[] defaultValue) {
        if(Config.isWindows())
            return defaultValue;
        else {
            String s = getCurrentTranslatedCapability(cap, new String(defaultValue, 0, defaultValue.length));
            if (s.length() == 0)
                return defaultValue;
            return s.codePoints().toArray();
        }
    }

    public static String getCurrentTranslatedCapability(String cap, String defaultValue) {
        try {
            if (!initialized) {
                String term = System.getenv("TERM");
                if (term == null) {
                    term = "xterm-256color";
                }
                String infocmp = InfoCmp.getInfoCmp(term);
                InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);
                initialized = true;
            }
            Capability capability = Capability.byName(cap);
            if (capability != null) {
                String capStr = strings.get(capability);
                if (capStr != null) {
                    StringWriter sw = new StringWriter();
                    Curses.tputs(sw, capStr);
                    return sw.toString();
                }
            }
        }
        catch (Exception e) {
            // Ignore
        }
        return defaultValue;
    }

}
