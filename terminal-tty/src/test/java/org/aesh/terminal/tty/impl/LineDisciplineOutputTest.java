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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.Attributes;
import org.junit.Test;

/**
 * Bulk output batching for line-discipline terminals (#349).
 * <p>
 * Without OPOST/ONLCR postprocessing, bulk writes must reach the master
 * output in a single call; with it, newline runs batch around the
 * expansions with byte-identical results. All headless: the terminal
 * constructor only builds pipes.
 */
public class LineDisciplineOutputTest {

    /** Master output recording bulk/single call shapes plus bytes. */
    private static final class CountingOutput extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final List<String> calls = new ArrayList<>();

        @Override
        public void write(int b) {
            calls.add("single");
            bytes.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            calls.add("bulk:" + len);
            bytes.write(b, off, len);
        }

        int bulkCalls() {
            int count = 0;
            for (String call : calls) {
                if (call.startsWith("bulk:")) {
                    count++;
                }
            }
            return count;
        }

        int singleCalls() {
            return calls.size() - bulkCalls();
        }
    }

    private static LineDisciplineTerminal terminal(CountingOutput master,
            boolean opost, boolean onlcr) throws IOException {
        LineDisciplineTerminal terminal = new LineDisciplineTerminal("test", "test", master);
        Attributes attributes = terminal.getAttributes();
        attributes.setOutputFlag(Attributes.OutputFlag.OPOST, opost);
        attributes.setOutputFlag(Attributes.OutputFlag.ONLCR, onlcr);
        terminal.setAttributes(attributes);
        return terminal;
    }

    private static byte[] payload() {
        // Mixed newlines, NUL, high bytes, UTF-8 multibyte (CJK, emoji).
        byte[] text = ("line one\nline two\n\nlast \u00e9\u4e2d\ud83d\ude00\n".getBytes(StandardCharsets.UTF_8));
        byte[] binary = new byte[] { 0x00, 0x0a, (byte) 0xff, 0x0d, 0x0a };
        byte[] payload = new byte[text.length + binary.length];
        System.arraycopy(text, 0, payload, 0, text.length);
        System.arraycopy(binary, 0, payload, text.length, binary.length);
        return payload;
    }

    private static byte[] reference(byte[] payload, boolean expand) {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        for (byte b : payload) {
            if (expand && b == '\n') {
                expected.write('\r');
            }
            expected.write(b);
        }
        return expected.toByteArray();
    }

    @Test
    public void testBulkPassthroughWithoutPostprocessing() throws Exception {
        byte[] payload = payload();
        for (boolean opost : new boolean[] { false, true }) {
            CountingOutput master = new CountingOutput();
            LineDisciplineTerminal terminal = terminal(master, opost, false);
            try {
                terminal.output().write(payload);
                terminal.output().flush();
                assertArrayEquals("bytes must pass through unchanged (opost=" + opost + ")",
                        reference(payload, false), master.bytes.toByteArray());
                assertEquals("one bulk call without ONLCR (opost=" + opost + ")",
                        1, master.bulkCalls());
                assertEquals(0, master.singleCalls());
            } finally {
                terminal.close();
            }
        }
    }

    @Test
    public void testOnlcrWithoutOpostPassesThrough() throws Exception {
        byte[] payload = payload();
        CountingOutput master = new CountingOutput();
        LineDisciplineTerminal terminal = terminal(master, false, true);
        try {
            terminal.output().write(payload);
            terminal.output().flush();
            assertArrayEquals("ONLCR alone must not transform",
                    reference(payload, false), master.bytes.toByteArray());
            assertEquals(1, master.bulkCalls());
            assertEquals(0, master.singleCalls());
        } finally {
            terminal.close();
        }
    }

    @Test
    public void testOpostOnlcrBatchesAroundExpansions() throws Exception {
        byte[] payload = payload();
        CountingOutput master = new CountingOutput();
        LineDisciplineTerminal terminal = terminal(master, true, true);
        try {
            terminal.output().write(payload);
            terminal.output().flush();
            assertArrayEquals("CR-LF expansion must match per-byte semantics",
                    reference(payload, true), master.bytes.toByteArray());
            assertEquals("no single-byte writes in bulk path",
                    0, master.singleCalls());
            assertTrue("batched bulk writes expected, got " + master.calls,
                    master.bulkCalls() >= 1);
        } finally {
            terminal.close();
        }
    }

    @Test
    public void testEmptyAndOffsetWrites() throws Exception {
        CountingOutput master = new CountingOutput();
        LineDisciplineTerminal terminal = terminal(master, false, false);
        try {
            terminal.output().write(new byte[0]);
            assertEquals(0, master.calls.size());
            byte[] payload = payload();
            terminal.output().write(payload, 3, payload.length - 6);
            byte[] expected = new byte[payload.length - 6];
            System.arraycopy(payload, 3, expected, 0, expected.length);
            assertArrayEquals(expected, master.bytes.toByteArray());
        } finally {
            terminal.close();
        }
    }
}
