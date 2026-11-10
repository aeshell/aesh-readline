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
package org.aesh.terminal.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Tests for DECRPM response parsing in TerminalColorQuery.
 */
public class TerminalModeProbeTest {

    @Test
    public void testParseDECRPM_BothSupported() {
        // Mode 2026 + 2027 both supported + DA1
        String response = "\033[?2026;1$y\033[?2027;1$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue("DA1 should be received", result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_2026OnlySupported() {
        // Only Mode 2026 responded + DA1 (Mode 2027 → NOT_SUPPORTED)
        String response = "\033[?2026;1$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_DA1Only() {
        // Only DA1 responded
        String response = "\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_NoDA1() {
        // No DA1 response — dumb terminal
        String response = "";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertEquals(false, result.da1Received);
        assertEquals(ModeSupport.NO_RESPONSE, result.mode2026);
        assertEquals(ModeSupport.NO_RESPONSE, result.mode2027);
    }

    @Test
    public void testParseDECRPM_NotRecognized() {
        // Mode 2027 Ps=0 (not recognized) + DA1
        String response = "\033[?2026;1$y\033[?2027;0$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_Mode2027Reset() {
        // Mode 2027 Ps=2 (reset but recognized) → SUPPORTED
        String response = "\033[?2027;2$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDA1Response_AfterDecrpm() {
        // Batched probe order: DECRPM responses arrive before DA1.
        // DA1 parsing must skip them, not misread mode 2026 as device class.
        String response = "\033[?2026;1$y\033[?2027;0$y\033[?63;1;2;4c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        assertEquals(63, result.da1DeviceClass);
        assertTrue(result.supportsSixel);
    }

    @Test
    public void testParseDECRPM_WithOSCResponses() {
        // DECRPM + DA1 + OSC responses mixed together
        String response = "\033[?2026;1$y\033[?2027;1$y\033[?64;1c"
                + "\033]10;rgb:ffff/ffff/ffff\007"
                + "\033]11;rgb:0000/0000/0000\007";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
        assertTrue(result.da1Received);
    }

    @Test
    public void testParseDECRPM_DA1First() {
        // DA1 arriving before the mode reports must not consume them:
        // each CSI is framed independently (#307).
        String response = "\033[?62;4c\033[?2026;1$y\033[?2027;1$y";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_DA1Between() {
        String response = "\033[?2026;1$y\033[?62;4c\033[?2027;0$y";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_DA1After() {
        String response = "\033[?2026;1$y\033[?2027;1$y\033[?62;4c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
    }

    @Test
    public void testParseDECRPM_2048Supported() {
        // Mode 2048 answered like any other DEC mode + DA1
        String response = "\033[?2026;1$y\033[?2027;1$y\033[?2048;1$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
        assertEquals(ModeSupport.SUPPORTED, result.mode2048);
    }

    @Test
    public void testParseDECRPM_2048NotRecognized() {
        // Ps=0 (not recognized) renders as not-supported, per the
        // existing parser semantics — pinned, not re-decided here.
        String response = "\033[?2048;0$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2048);
    }

    @Test
    public void testParseDECRPM_2048FenceDefault() {
        // DA1 without a 2048 reply: fence default, same as 2026/2027.
        String response = "\033[?2026;1$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertTrue(result.da1Received);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2048);
    }

    @Test
    public void testBatchQueriesMode2048() {
        // The live reply can only arrive if the query asks for it.
        String batch = new String(TerminalColorQuery.buildColorQuery(),
                StandardCharsets.US_ASCII);
        assertTrue("batch must probe mode 2048: " + escape(batch),
                batch.contains("\033[?2048$p"));
    }

    @Test
    public void testBatchQueriesKeyboard() {
        String batch = new String(TerminalColorQuery.buildColorQuery(),
                StandardCharsets.US_ASCII);
        assertTrue("batch must ask for keyboard enhancements: " + escape(batch),
                batch.contains("\033[?u"));
    }

    @Test
    public void testKittyFenceDefault() {
        // DA1 without a keyboard reply: measured absent, not unprobed.
        String response = "\033[?2026;1$y\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);
        TerminalColorQuery.parseKittyKeyboardResponse(response, result);

        assertTrue(result.da1Received);
        assertEquals(0, result.kittyKeyboardFlags);
    }

    @Test
    public void testKittyUnprobedWithoutDA1() {
        // No DA1 response — dumb terminal, keyboard stays unprobed.
        String response = "";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);
        TerminalColorQuery.parseKittyKeyboardResponse(response, result);

        assertEquals(false, result.da1Received);
        assertEquals(-1, result.kittyKeyboardFlags);
    }

    @Test
    public void testKittyReplyOverridesFence() {
        String response = "\033[?5u\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);
        TerminalColorQuery.parseKittyKeyboardResponse(response, result);

        assertTrue(result.da1Received);
        assertEquals(5, result.kittyKeyboardFlags);
    }

    @Test
    public void testKittyZeroReplyIsAnswered() {
        // A zero-bitmask reply is an answer (offers nothing),
        // distinct from the unprobed state.
        String response = "\033[?0u\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);
        TerminalColorQuery.parseKittyKeyboardResponse(response, result);

        assertTrue(result.da1Received);
        assertEquals(0, result.kittyKeyboardFlags);
    }

    @Test
    public void testKittyGarbageKeepsFence() {
        // Non-numeric u-terminated body is ignored; the fence stands.
        String response = "\033[?xu\033[?64;1c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);
        TerminalColorQuery.parseKittyKeyboardResponse(response, result);

        assertTrue(result.da1Received);
        assertEquals(0, result.kittyKeyboardFlags);
    }

    @Test
    public void testParseDECRPM_2048NoDA1() {
        // No DA1 response — dumb terminal, 2048 stays unprobed.
        String response = "";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        TerminalColorQuery.parseDECRPMResponses(response, result);

        assertEquals(false, result.da1Received);
        assertEquals(ModeSupport.NO_RESPONSE, result.mode2048);
    }

    @Test
    public void testFullBatchTerminatesAtFrameCount() throws Exception {
        // Full 23-frame batch (3 DECRPM + 19 OSC + DA1 last), one frame
        // per read, then the stream hangs: the query must return at the
        // frame count. DA1 rides last on purpose: with a stale
        // pre-fence count (22) the read breaks at frame 22 and never
        // sees the fence, so da1Received pins the 22 -> 23 update.
        java.util.List<String> frames = new java.util.ArrayList<String>();
        frames.add("\033[?2026;1$y");
        frames.add("\033[?2027;1$y");
        frames.add("\033[?2048;1$y");
        frames.add("\033]10;rgb:ffff/ffff/ffff\007");
        frames.add("\033]11;rgb:0000/0000/0000\007");
        for (int i = 0; i <= 15; i++) {
            frames.add("\033]4;" + i + ";rgb:0000/0000/0000\007");
        }
        frames.add("\033]4;255;rgb:ffff/ffff/ffff\007");
        frames.add("\033[?64;1c");
        final String[] frameArray = frames.toArray(new String[frames.size()]);

        TerminalProbeTransport transport = fakeTransport(frameArray);

        final AtomicReference<TerminalColorQuery> out = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    out.set(TerminalColorQuery.query(transport));
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "mode-2048-frame-count");
        worker.setDaemon(true);
        worker.start();
        worker.join(10000);
        try {
            assertFalse("full batch must terminate at the frame count, not hang",
                    worker.isAlive());
        } finally {
            worker.interrupt();
        }
        assertNull("query must not fail: " + failure.get(), failure.get());
        TerminalColorQuery result = out.get();
        assertNotNull(result);
        assertTrue("fence must be seen even when DA1 rides last", result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2048);
        assertTrue(result.supports256);
        assertEquals("no keyboard reply: fence maps to measured absent",
                0, result.kittyKeyboardFlags);
    }

    @Test
    public void testKittyBatchTerminatesAtFrameCount() throws Exception {
        // 24-frame batch in wire order with the keyboard reply in
        // place, one frame per read, then the stream hangs: the
        // post-fence budget needs kittySeen, or the read breaks at 23
        // and drops the trailing color reply.
        java.util.List<String> frames = new java.util.ArrayList<String>();
        frames.add("\033[?2026;1$y");
        frames.add("\033[?2027;1$y");
        frames.add("\033[?2048;1$y");
        frames.add("\033[?5u");
        frames.add("\033[?64;1c");
        frames.add("\033]10;rgb:ffff/ffff/ffff\007");
        frames.add("\033]11;rgb:0000/0000/0000\007");
        for (int i = 0; i <= 15; i++) {
            frames.add("\033]4;" + i + ";rgb:0000/0000/0000\007");
        }
        frames.add("\033]4;255;rgb:ffff/ffff/ffff\007");
        final String[] frameArray = frames.toArray(new String[frames.size()]);
        final AtomicReference<TerminalColorQuery> out = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    out.set(TerminalColorQuery.query(fakeTransport(frameArray)));
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "kitty-frame-count");
        worker.setDaemon(true);
        worker.start();
        worker.join(10000);
        try {
            assertFalse("full batch must terminate at the frame count, not hang",
                    worker.isAlive());
        } finally {
            worker.interrupt();
        }
        assertNull("query must not fail: " + failure.get(), failure.get());
        TerminalColorQuery result = out.get();
        assertNotNull(result);
        assertTrue(result.da1Received);
        assertEquals(5, result.kittyKeyboardFlags);
        assertTrue("trailing 256-color reply must be read (budget needs kitty)",
                result.supports256);
    }

    /**
     * Fake transport serving canned frames, one per read, then hanging.
     */
    private static TerminalProbeTransport fakeTransport(final String[] frameArray) {
        return new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public TerminalProbeSession open() {
                return new TerminalProbeSession() {
                    private final InputStream in = new FrameThenHangStream(frameArray);

                    @Override
                    public void write(byte[] data) {
                    }

                    @Override
                    public InputStream input() {
                        return in;
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
    }

    /**
     * Serves one reply frame per read, then hangs until interrupted.
     * Proves frame-count termination: with a correct count the query
     * returns while this stream is still hanging.
     */

    /**
     * Render control characters visibly for assertion messages.
     */
    private static String escape(String batch) {
        return batch.replace("\033", "<ESC>").replace("\007", "<BEL>");
    }

    /**
     * Serves one reply frame per read, then hangs until interrupted.
     * Proves frame-count termination: with a correct count the query
     * returns while this stream is still hanging.
     */
    private static final class FrameThenHangStream extends InputStream {
        private final byte[][] frames;
        private int index;

        FrameThenHangStream(String[] frames) {
            byte[][] encoded = new byte[frames.length][];
            for (int i = 0; i < frames.length; i++) {
                encoded[i] = frames[i].getBytes(StandardCharsets.US_ASCII);
            }
            this.frames = encoded;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (index >= frames.length) {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
            byte[] frame = frames[index++];
            int count = Math.min(len, frame.length);
            System.arraycopy(frame, 0, b, off, count);
            return count;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }
    }
}
