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
import static org.junit.Assert.assertTrue;

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
}
