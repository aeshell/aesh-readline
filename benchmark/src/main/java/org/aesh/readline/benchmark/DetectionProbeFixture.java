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
package org.aesh.readline.benchmark;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.detect.ModeSupport;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalProbeSession;
import org.aesh.terminal.detect.TerminalProbeTransport;

/** In-memory replies, not a measurement of terminal or raw-mode I/O. */
final class DetectionProbeFixture implements TerminalProbeTransport {

    private final boolean available;
    private final byte[] reply;
    int opens;
    int writes;
    int closes;

    DetectionProbeFixture(boolean available) {
        this.available = available;
        String esc = Character.toString((char) 27);
        String bel = Character.toString((char) 7);
        StringBuilder response = new StringBuilder();
        response.append(esc).append("[?2026;1$y").append(esc).append("[?2027;1$y");
        response.append(esc).append("[?63;1;2;4c");
        response.append(esc).append("]10;rgb:ffff/ffff/ffff").append(bel);
        response.append(esc).append("]11;rgb:0000/0000/0000").append(bel);
        for (int i = 0; i < 16; i++) {
            String red = Integer.toHexString(0x10000 + i * 0x1111).substring(1);
            response.append(esc).append("]4;").append(i).append(";rgb:").append(red).append("/1111/2222").append(bel);
        }
        response.append(esc).append("]4;255;rgb:ffff/ffff/ffff").append(bel);
        reply = response.toString().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public TerminalProbeSession open() {
        opens++;
        final InputStream input = new ByteArrayInputStream(reply);
        return new TerminalProbeSession() {
            @Override
            public void write(byte[] data) {
                writes++;
            }

            @Override
            public InputStream input() {
                return input;
            }

            @Override
            public void close() {
                closes++;
            }
        };
    }

    void verify(int rounds, TerminalCapabilities caps) {
        if (opens != rounds || writes != rounds || closes != rounds) {
            throw new IllegalStateException("expected " + rounds + " rounds, got opens=" + opens
                    + " writes=" + writes + " closes=" + closes);
        }
        if (rounds != 0 && (caps.paletteColors().size() != 16 || caps.backgroundRGB() == null
                || caps.foregroundRGB() == null || caps.synchronizedOutputSupport() != ModeSupport.SUPPORTED
                || caps.graphemeClusterSupport() != ModeSupport.SUPPORTED)) {
            throw new IllegalStateException("canned replies were skipped or not completely parsed; scrub multiplexer env");
        }
    }
}
