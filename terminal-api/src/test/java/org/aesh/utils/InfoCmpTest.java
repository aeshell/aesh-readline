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
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class InfoCmpTest {

    @Test
    public void testANSI() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("xterm");
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);
        assertEquals(24, ints.get(Capability.lines).intValue());
        assertEquals(80, ints.get(Capability.columns).intValue());

        assertEquals(5, ints.size());
        assertEquals(8, bools.size());
        assertEquals(166, strings.size());
        assertTrue(strings.containsKey(Capability.byName("kf29")));
    }

    @Test
    public void testWindows() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("Windows");
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);
        assertEquals(24, ints.get(Capability.lines).intValue());
        assertEquals(64, ints.get(Capability.max_pairs).intValue());

        assertEquals(6, ints.size());
        assertEquals(4, bools.size());
        assertEquals(58, strings.size());
        assertTrue(strings.containsKey(Capability.byName("smso")));
    }

    @Test
    public void testNative() throws IOException, InterruptedException {
        if(Config.isOSPOSIXCompatible()) {
            Set<Capability> bools = new HashSet<>();
            Map<Capability, Integer> ints = new HashMap<>();
            Map<Capability, String> strings = new HashMap<>();

            String infocmp = InfoCmp.getInfoCmp("xterm-256color");
            if(infocmp != null) {
                InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

                assertEquals(256, ints.get(Capability.max_colors).intValue());
                assertTrue(ints.get(Capability.columns) > 0);
                assertTrue(ints.get(Capability.lines) > 0);
                assertTrue(bools.size() > 0);
                assertTrue(strings.size() > 0);
            }
        }
    }

}
