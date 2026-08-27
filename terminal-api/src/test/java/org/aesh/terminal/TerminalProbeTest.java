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
package org.aesh.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

/**
 * Tests for the batched terminal probe response parsing.
 */
public class TerminalProbeTest {

    // ---- DECRPM parser tests ----

    @Test
    public void testParseDECRPM_Mode2026Supported() {
        // ESC [ ? 2026 ; 1 $ y  (Ps=1 means "set")
        String response = "\u001B[?2026;1$y";
        int[] result = ANSI.parseDECRPM(response, 0);
        assertNotNull("Should parse DECRPM", result);
        assertEquals(2026, result[0]); // mode
        assertEquals(1, result[1]); // Ps = set
    }

    @Test
    public void testParseDECRPM_Mode2027Reset() {
        // ESC [ ? 2027 ; 2 $ y  (Ps=2 means "reset but recognized")
        String response = "\u001B[?2027;2$y";
        int[] result = ANSI.parseDECRPM(response, 0);
        assertNotNull(result);
        assertEquals(2027, result[0]);
        assertEquals(2, result[1]);
    }

    @Test
    public void testParseDECRPM_NotRecognized() {
        // ESC [ ? 2027 ; 0 $ y  (Ps=0 means "not recognized")
        String response = "\u001B[?2027;0$y";
        int[] result = ANSI.parseDECRPM(response, 0);
        assertNotNull(result);
        assertEquals(2027, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    public void testParseDECRPM_InvalidSequence() {
        String response = "\u001B[?2026;1$x"; // wrong final byte
        int[] result = ANSI.parseDECRPM(response, 0);
        assertEquals(null, result);
    }

    @Test
    public void testParseDECRPM_TooShort() {
        String response = "\u001B[?";
        int[] result = ANSI.parseDECRPM(response, 0);
        assertEquals(null, result);
    }

    // ---- DA1 finder tests ----

    @Test
    public void testFindDA1Response_Present() {
        // ESC [ ? 6 4 ; 1 ; 2 c
        String response = "\u001B[?64;1;2c";
        int endIndex = ANSI.findDA1Response(response, 0);
        assertTrue("Should find DA1 response", endIndex > 0);
    }

    @Test
    public void testFindDA1Response_WithDECRPMBefore() {
        // DECRPM for 2026 + DA1
        String response = "\u001B[?2026;1$y\u001B[?64;1c";
        int endIndex = ANSI.findDA1Response(response, 0);
        assertTrue("Should find DA1 after DECRPM", endIndex > 0);
    }

    @Test
    public void testFindDA1Response_NotPresent() {
        String response = "\u001B[?2026;1$y";
        int endIndex = ANSI.findDA1Response(response, 0);
        assertEquals("Should not find DA1", -1, endIndex);
    }

    @Test
    public void testFindDA1Response_Empty() {
        int endIndex = ANSI.findDA1Response("", 0);
        assertEquals(-1, endIndex);
    }

    // ---- Batched response parsing tests ----

    @Test
    public void testParseBatchedResponse_BothSupported() {
        // Mode 2026 supported + Mode 2027 supported + DA1
        String response = "\u001B[?2026;1$y\u001B[?2027;1$y\u001B[?64;1c";
        TerminalProbeResult result = new TerminalProbeResult();
        // Simulate parsing
        parseBatchedResponseForTest(response, result);

        assertTrue("DA1 should be received", result.da1Received());
        assertEquals(ModeSupport.SUPPORTED, result.mode2026());
        assertEquals(ModeSupport.SUPPORTED, result.mode2027());
    }

    @Test
    public void testParseBatchedResponse_2026SupportedOnly() {
        // Only Mode 2026 responded + DA1 (Mode 2027 → NOT_SUPPORTED)
        String response = "\u001B[?2026;1$y\u001B[?64;1c";
        TerminalProbeResult result = new TerminalProbeResult();
        parseBatchedResponseForTest(response, result);

        assertTrue(result.da1Received());
        assertEquals(ModeSupport.SUPPORTED, result.mode2026());
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027());
    }

    @Test
    public void testParseBatchedResponse_DA1Only() {
        // Only DA1 responded (no DECRPM at all)
        String response = "\u001B[?64;1c";
        TerminalProbeResult result = new TerminalProbeResult();
        parseBatchedResponseForTest(response, result);

        assertTrue(result.da1Received());
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2026());
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027());
    }

    @Test
    public void testParseBatchedResponse_NoResponse() {
        // Nothing responded (timeout)
        String response = "";
        TerminalProbeResult result = new TerminalProbeResult();
        parseBatchedResponseForTest(response, result);

        assertEquals(false, result.da1Received());
        assertEquals(ModeSupport.NO_RESPONSE, result.mode2026());
        assertEquals(ModeSupport.NO_RESPONSE, result.mode2027());
    }

    @Test
    public void testParseBatchedResponse_Mode2027NotRecognized() {
        // Mode 2026 supported, Mode 2027 Ps=0 (not recognized) + DA1
        String response = "\u001B[?2026;1$y\u001B[?2027;0$y\u001B[?64;1c";
        TerminalProbeResult result = new TerminalProbeResult();
        parseBatchedResponseForTest(response, result);

        assertTrue(result.da1Received());
        assertEquals(ModeSupport.SUPPORTED, result.mode2026());
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027());
    }

    /**
     * Helper that mimics TerminalFeatures.parseBatchedResponse() logic
     * without needing a real Connection.
     */
    private void parseBatchedResponseForTest(String response, TerminalProbeResult result) {
        boolean foundDA1 = ANSI.findDA1Response(response, 0) >= 0;
        result.setDa1Received(foundDA1);

        if (!foundDA1) {
            return; // all NO_RESPONSE (default)
        }

        result.setMode2026(ModeSupport.NOT_SUPPORTED);
        result.setMode2027(ModeSupport.NOT_SUPPORTED);

        int pos = 0;
        while (pos < response.length()) {
            int[] decrpm = ANSI.parseDECRPM(response, pos);
            if (decrpm == null)
                break;
            int mode = decrpm[0];
            int ps = decrpm[1];
            pos = decrpm[2];
            ModeSupport support = (ps >= 1 && ps <= 3)
                    ? ModeSupport.SUPPORTED
                    : ModeSupport.NOT_SUPPORTED;
            if (mode == 2026)
                result.setMode2026(support);
            else if (mode == 2027)
                result.setMode2027(support);
        }
    }
}
