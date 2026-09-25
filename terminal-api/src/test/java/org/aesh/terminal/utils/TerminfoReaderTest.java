/*
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
package org.aesh.terminal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.tty.Capability;
import org.junit.Assume;
import org.junit.Test;

/**
 * Differential tests for {@link TerminfoReader}: database-direct reads must
 * agree with {@code infocmp} subprocess output once parsed.
 */
public class TerminfoReaderTest {

    private static boolean hasInfocmp() {
        for (String dir : new String[] { "/usr/bin", "/bin", "/usr/local/bin" }) {
            if (new File(dir, "infocmp").isFile()) {
                return true;
            }
        }
        return false;
    }

    private static String infocmp(String terminal) throws Exception {
        Process process = new ProcessBuilder("infocmp", terminal).start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InputStream in = process.getInputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, n);
        }
        int exit = process.waitFor();
        Assume.assumeTrue("infocmp failed for " + terminal, exit == 0);
        return new String(buffer.toByteArray(), "ISO-8859-1");
    }

    private static Parsed parse(String text) {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();
        InfoCmp.parseInfoCmp(text, bools, ints, strings);
        return new Parsed(bools, ints, strings);
    }

    private static final class Parsed {
        final Set<Capability> bools;
        final Map<Capability, Integer> ints;
        final Map<Capability, String> strings;

        Parsed(Set<Capability> bools, Map<Capability, Integer> ints, Map<Capability, String> strings) {
            this.bools = bools;
            this.ints = ints;
            this.strings = strings;
        }
    }

    private static void assertDifferential(String terminal) throws Exception {
        Assume.assumeTrue("infocmp not available", hasInfocmp());
        String direct = TerminfoReader.readEntry(terminal);
        assertNotNull("Database entry missing for " + terminal, direct);
        Parsed expected = parse(infocmp(terminal));
        Parsed actual = parse(direct);
        assertEquals("Bool sets differ for " + terminal, expected.bools, actual.bools);
        assertEquals("Int maps differ for " + terminal, expected.ints, actual.ints);
        List<String> stringDiffs = new ArrayList<>();
        for (Capability key : expected.strings.keySet()) {
            String want = expected.strings.get(key);
            String got = actual.strings.get(key);
            if (got == null) {
                stringDiffs.add(key + ": missing (want length " + want.length() + ")");
            } else if (!want.equals(got)) {
                stringDiffs.add(key + ":\n  want=" + escape(want) + "\n  got =" + escape(got));
            }
        }
        for (Capability key : actual.strings.keySet()) {
            if (!expected.strings.containsKey(key)) {
                stringDiffs.add(key + ": unexpected extra");
            }
        }
        assertTrue("String mismatches for " + terminal + ":\n" + String.join("\n", stringDiffs),
                stringDiffs.isEmpty());
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @Test
    public void testXterm256Color() throws Exception {
        assertDifferential("xterm-256color");
    }

    @Test
    public void testScreen() throws Exception {
        assertDifferential("screen");
    }

    @Test
    public void testXterm() throws Exception {
        assertDifferential("xterm");
    }

    @Test
    public void testVt100() throws Exception {
        assertDifferential("vt100");
    }

    @Test
    public void testUnknownTerminalReturnsNull() {
        assertNull(TerminfoReader.readEntry("definitely-not-a-terminal-xyz"));
    }

    @Test
    public void testNullAndEmptyReturnNull() {
        assertNull(TerminfoReader.readEntry(null));
        assertNull(TerminfoReader.readEntry(""));
        assertNull(TerminfoReader.readEntry("/etc/passwd"));
    }
}
