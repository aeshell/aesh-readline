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

/**
 * Results of a batched terminal feature probe via DECRQM + DA1 fence.
 * <p>
 * A single probe round-trip detects support for multiple DEC private modes.
 * The DA1 response acts as a fence: when it arrives, all DECRPM responses
 * that the terminal is going to send have already been received.
 */
public class TerminalProbeResult {

    private ModeSupport mode2026 = ModeSupport.NO_RESPONSE;
    private ModeSupport mode2027 = ModeSupport.NO_RESPONSE;
    private boolean da1Received;
    private boolean nativeGraphemeClustering;

    /**
     * Support status for Mode 2026 (synchronized output / BSU-ESU).
     */
    public ModeSupport mode2026() {
        return mode2026;
    }

    /**
     * Support status for Mode 2027 (grapheme cluster mode).
     */
    public ModeSupport mode2027() {
        return mode2027;
    }

    /**
     * Whether the terminal responded to DA1 at all. If false, this is
     * a dumb terminal and no further probes should be attempted.
     */
    public boolean da1Received() {
        return da1Received;
    }

    /**
     * Whether the terminal natively clusters grapheme sequences (detected
     * via cursor-position probe) even without Mode 2027 support.
     */
    public boolean nativeGraphemeClustering() {
        return nativeGraphemeClustering;
    }

    void setMode2026(ModeSupport support) {
        this.mode2026 = support;
    }

    void setMode2027(ModeSupport support) {
        this.mode2027 = support;
    }

    void setDa1Received(boolean received) {
        this.da1Received = received;
    }

    void setNativeGraphemeClustering(boolean supported) {
        this.nativeGraphemeClustering = supported;
    }

    @Override
    public String toString() {
        return "TerminalProbeResult{mode2026=" + mode2026
                + ", mode2027=" + mode2027
                + ", da1=" + da1Received
                + ", nativeGrapheme=" + nativeGraphemeClustering + "}";
    }
}
