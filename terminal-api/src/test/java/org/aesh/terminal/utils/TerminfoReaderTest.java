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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.tty.Capability;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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

    // ==================== Layout and precedence fixtures ====================
    //
    // Hand-built minimal terminfo binaries in fixture databases: no
    // dependence on the machine's own database, so every platform layout
    // and precedence rule is testable on any host.

    @Rule
    public TemporaryFolder fixtureDirs = new TemporaryFolder();

    /**
     * Build a minimal valid terminfo entry: one alias pair, cols set to
     * the given value, one trivial string capability.
     *
     * @param cols the cols value to record
     * @return the entry bytes in legacy (16-bit) format
     */
    private static byte[] minimalEntry(int cols) {
        byte[] names = "t|Test term\0".getBytes(StandardCharsets.ISO_8859_1);
        byte[] table = new byte[] { 'x', 0 };
        ByteBuffer buf = ByteBuffer.allocate(12 + names.length + 2 + 2 + table.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0x011A);
        buf.putShort((short) names.length);
        buf.putShort((short) 0);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putShort((short) table.length);
        buf.put(names);
        buf.putShort((short) cols);
        buf.putShort((short) 0);
        buf.put(table);
        return buf.array();
    }

    private static void writeEntry(File root, String subdir, String name, byte[] data)
            throws Exception {
        File dir = new File(root, subdir);
        dir.mkdirs();
        assertTrue(dir.isDirectory());
        Files.write(new File(dir, name).toPath(), data);
    }

    private static Map<String, String> env(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return env;
    }

    @Test
    public void testHexLayoutResolves() throws Exception {
        File db = fixtureDirs.newFolder("hexdb");
        writeEntry(db, "78", "xtest", minimalEntry(80));

        String text = TerminfoReader.readEntry("xtest",
                env("TERMINFO", db.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath());

        assertNotNull(text);
        assertTrue(text.contains("cols#80"));
    }

    @Test
    public void testCharLayoutRetained() throws Exception {
        File db = fixtureDirs.newFolder("chardb");
        writeEntry(db, "x", "xtest", minimalEntry(80));

        String text = TerminfoReader.readEntry("xtest",
                env("TERMINFO", db.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath());

        assertNotNull(text);
        assertTrue(text.contains("cols#80"));
    }

    @Test
    public void testTerminfoIsExclusive() throws Exception {
        File home = fixtureDirs.newFolder("home");
        writeEntry(new File(home, ".terminfo"), "t", "testhome", minimalEntry(80));
        File other = fixtureDirs.newFolder("otherdb");

        assertNull("an explicit TERMINFO must not fall through to home",
                TerminfoReader.readEntry("testhome",
                        env("TERMINFO", other.getAbsolutePath()),
                        home.getAbsolutePath()));
    }

    @Test
    public void testTerminfoBeatsHome() throws Exception {
        File home = fixtureDirs.newFolder("home");
        writeEntry(new File(home, ".terminfo"), "t", "testconf", minimalEntry(80));
        File selected = fixtureDirs.newFolder("selected");
        writeEntry(selected, "t", "testconf", minimalEntry(132));

        String text = TerminfoReader.readEntry("testconf",
                env("TERMINFO", selected.getAbsolutePath()),
                home.getAbsolutePath());

        assertNotNull(text);
        assertTrue("the selected database must win",
                text.contains("cols#132"));
    }

    @Test
    public void testTerminfoDirsOrder() throws Exception {
        File dirA = fixtureDirs.newFolder("dirA");
        File dirB = fixtureDirs.newFolder("dirB");
        writeEntry(dirA, "t", "testconf", minimalEntry(80));
        writeEntry(dirB, "t", "testconf", minimalEntry(132));

        String text = TerminfoReader.readEntry("testconf",
                env("TERMINFO_DIRS",
                        dirA.getAbsolutePath() + File.pathSeparator + dirB.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath());

        assertNotNull(text);
        assertTrue("first TERMINFO_DIRS entry must win", text.contains("cols#80"));
    }

    @Test
    public void testSplitDirsKeepsDriveLetters() {
        // Windows absolute paths join with either separator; the drive
        // colon must survive. Pure string logic: runs on every OS, and
        // fails against the old split-on-colon on Linux too.
        assertEquals(Arrays.asList("C:\\dbA", "C:\\dbB"),
                TerminfoReader.splitDirs("C:\\dbA;C:\\dbB"));
        assertEquals(Arrays.asList("C:\\dbA", "C:\\dbB"),
                TerminfoReader.splitDirs("C:\\dbA:C:\\dbB"));
        assertEquals(Arrays.asList("/a", "/b"),
                TerminfoReader.splitDirs("/a:/b"));
        assertEquals(Arrays.asList("/a", "/b"),
                TerminfoReader.splitDirs("/a;/b"));
    }

    @Test
    public void testTerminfoDirsEmptyEntriesExpand() {
        String home = new File(fixtureDirs.getRoot(), "home").getAbsolutePath();
        List<String> dirs = TerminfoReader.candidateDirs(
                env("TERMINFO_DIRS", "dirA::dirB"), home);
        List<String> expected = new ArrayList<>();
        expected.add(home + File.separator + ".terminfo");
        expected.add("dirA");
        expected.add("/etc/terminfo");
        expected.add("/usr/share/terminfo");
        expected.add("/usr/lib/terminfo");
        expected.add("dirB");
        assertEquals(expected, dirs);

        List<String> trailing = TerminfoReader.candidateDirs(
                env("TERMINFO_DIRS", "dirA:"), home);
        List<String> expectedTrailing = new ArrayList<>(expected.subList(0, 2));
        expectedTrailing.addAll(expected.subList(2, 5));
        assertEquals("trailing empty entries must expand too",
                expectedTrailing, trailing);
    }

    @Test
    public void testMalformedEntryFallsThrough() throws Exception {
        File db = fixtureDirs.newFolder("mixeddb");
        byte[] malformed = minimalEntry(80);
        malformed[0] = 0;
        malformed[1] = 0;
        writeEntry(db, "t", "testconf", malformed);
        writeEntry(db, "74", "testconf", minimalEntry(132));

        String text = TerminfoReader.readEntry("testconf",
                env("TERMINFO", db.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath());

        assertNotNull(text);
        assertTrue(text.contains("cols#132"));
    }

    @Test
    public void testUnknownStillNullWithExplicitDb() throws Exception {
        File db = fixtureDirs.newFolder("emptydb");

        assertNull(TerminfoReader.readEntry("nope",
                env("TERMINFO", db.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath()));
    }

    // ==================== Compiled sentinels ====================

    /**
     * Build a legacy entry with 14 numeric slots, cols and colors set
     * to the given values, everything else absent.
     */
    private static byte[] legacyNumsEntry(int colsValue, int colorsValue) {
        byte[] names = "t|Test term\0".getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer buf = ByteBuffer.allocate(12 + names.length + 14 * 2)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0x011A);
        buf.putShort((short) names.length);
        buf.putShort((short) 0);
        buf.putShort((short) 14);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.put(names);
        for (int i = 0; i < 14; i++) {
            int value = ABSENT_SENTINEL;
            if (i == 0) {
                value = colsValue;
            } else if (i == 13) {
                value = colorsValue;
            }
            buf.putShort((short) value);
        }
        return buf.array();
    }

    private static final int ABSENT_SENTINEL = 0xFFFF;

    /**
     * Build an extended (32-bit) entry with 14 numeric slots, colors
     * and wnum set to the given values, everything else absent.
     */
    private static byte[] extendedNumsEntry(long colorsValue, long wnumValue) {
        byte[] names = "t|Test term\0".getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer buf = ByteBuffer.allocate(12 + names.length + 14 * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0x021E);
        buf.putShort((short) names.length);
        buf.putShort((short) 0);
        buf.putShort((short) 14);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.put(names);
        for (int i = 0; i < 14; i++) {
            long value = 0xFFFFFFFFL;
            if (i == 12) {
                value = wnumValue;
            } else if (i == 13) {
                value = colorsValue;
            }
            buf.putInt((int) value);
        }
        return buf.array();
    }

    @Test
    public void testCancelledNumericsOmitted() throws Exception {
        File db = fixtureDirs.newFolder("canceldb");
        writeEntry(db, "t", "testcancel", legacyNumsEntry(80, 0xFFFE));

        String text = TerminfoReader.readEntry("testcancel",
                env("TERMINFO", db.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath());

        assertNotNull(text);
        assertTrue(text.contains("cols#80"));
        assertTrue("cancelled colors must not advertise as 65534",
                !text.contains("colors"));
    }

    @Test
    public void testExtended32BitSentinels() throws Exception {
        File db = fixtureDirs.newFolder("extdb");
        writeEntry(db, "t", "testvalid", extendedNumsEntry(65535, 0xFFFFFFFFL));
        writeEntry(db, "t", "testcancelled", extendedNumsEntry(0xFFFFFFFEL, 0xFFFFFFFFL));
        String home = fixtureDirs.newFolder("home").getAbsolutePath();

        String valid = TerminfoReader.readEntry("testvalid",
                env("TERMINFO", db.getAbsolutePath()), home);
        assertNotNull(valid);
        assertTrue("65535 is a real 32-bit value", valid.contains("colors#65535"));
        assertTrue(!valid.contains("wnum"));

        String cancelled = TerminfoReader.readEntry("testcancelled",
                env("TERMINFO", db.getAbsolutePath()), home);
        assertNotNull(cancelled);
        assertTrue("cancelled colors must not advertise", !cancelled.contains("colors"));
    }

    @Test
    public void testCancelledStringOmitted() throws Exception {
        // A near-full string table defeats the bounds check that used to
        // stand in for an explicit cancelled-offset check: only the
        // explicit check omits the entry instead of emitting emptiness.
        int strSize = 65535;
        byte[] names = "t|Test term\0".getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer buf = ByteBuffer.allocate(12 + names.length + 2 + strSize)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0x011A);
        buf.putShort((short) names.length);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 1);
        buf.putShort((short) strSize);
        buf.put(names);
        buf.putShort((short) 0xFFFE);
        for (int i = 0; i < strSize; i++) {
            buf.put((byte) 0);
        }
        File db = fixtureDirs.newFolder("strdb");
        writeEntry(db, "t", "testcancel", buf.array());

        String text = TerminfoReader.readEntry("testcancel",
                env("TERMINFO", db.getAbsolutePath()),
                fixtureDirs.newFolder("home").getAbsolutePath());

        assertNotNull(text);
        assertTrue("cancelled string must not emit emptiness", !text.contains("cbt"));
    }

    private static boolean hasTic() {
        for (String dir : new String[] { "/usr/bin", "/bin", "/usr/local/bin" }) {
            if (new File(dir, "tic").isFile()) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testTicCancelledEntryMatchesInfocmp() throws Exception {
        Assume.assumeTrue("tic not available", hasTic());
        Assume.assumeTrue("infocmp not available", hasInfocmp());
        File work = fixtureDirs.newFolder("ticdb");
        File src = new File(work, "cancel.src");
        Files.write(src.toPath(),
                "testcancel|test cancel,\n\tcolors@,\n\tcols#80,\n"
                        .getBytes(StandardCharsets.ISO_8859_1));
        File db = new File(work, "db");
        assertTrue(db.mkdir());
        Process tic = new ProcessBuilder("tic", "-o", db.getAbsolutePath(),
                src.getAbsolutePath()).redirectErrorStream(true).start();
        Assume.assumeTrue("tic failed", tic.waitFor() == 0);

        String direct = TerminfoReader.readEntry("testcancel",
                env("TERMINFO", db.getAbsolutePath()), work.getAbsolutePath());
        assertNotNull(direct);

        ProcessBuilder infocmp = new ProcessBuilder("infocmp", "testcancel");
        infocmp.environment().put("TERMINFO", db.getAbsolutePath());
        Process response = infocmp.redirectErrorStream(true).start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InputStream in = response.getInputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, n);
        }
        Assume.assumeTrue("infocmp failed", response.waitFor() == 0);
        Parsed expected = parse(new String(buffer.toByteArray(), "ISO-8859-1"));
        Parsed actual = parse(direct);
        assertEquals(expected.ints, actual.ints);
        assertEquals(expected.bools, actual.bools);
    }
}
