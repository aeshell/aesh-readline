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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.utils.ColorDepth;
import org.junit.Test;

public class TerminalFeaturesColorDepthTest {

    private static Connection connection(final int colors) {
        final Device device = new BaseDevice("windows") {
            @Override
            public Integer getNumericCapability(Capability capability) {
                return capability == Capability.max_colors ? colors : null;
            }
        };
        return new StreamConnection(StandardCharsets.UTF_8,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()) {
            @Override
            public Device device() {
                return device;
            }
        };
    }

    @Test
    public void testSeedUpgradesAndClearsWithoutDowngradingTerminfo() {
        Connection connection = connection(256);
        try {
            TerminalFeatures features = connection.terminal();
            assertEquals(ColorDepth.COLORS_256, features.colorDepth());
            features.seedColorDepth(ColorDepth.COLORS_8);
            assertEquals(ColorDepth.COLORS_256, features.colorDepth());
            features.seedColorDepth(ColorDepth.TRUE_COLOR);
            assertEquals(ColorDepth.TRUE_COLOR, features.colorDepth());
            features.seedColorDepth(null);
            assertEquals(ColorDepth.COLORS_256, features.colorDepth());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testUnseededConnectionKeepsItsOwnColorDepth() {
        Connection connection = connection(8);
        try {
            assertEquals("Remote terminfo must not inherit local process detection", ColorDepth.COLORS_8,
                    connection.terminal().colorDepth());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testUnseededMonochromeDeviceStaysMonochrome() {
        Connection connection = connection(0);
        try {
            assertEquals(ColorDepth.NO_COLOR, connection.terminal().colorDepth());
        } finally {
            connection.close();
        }
    }
}
