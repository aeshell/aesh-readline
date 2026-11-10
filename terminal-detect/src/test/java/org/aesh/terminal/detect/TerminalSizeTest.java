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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Value contract for {@link TerminalSize}: positive dimensions,
 * no fallback defaults.
 */
public class TerminalSizeTest {

    @Test
    public void testGetters() {
        TerminalSize size = new TerminalSize(80, 24);
        assertEquals(80, size.width());
        assertEquals(24, size.height());
    }

    @Test
    public void testRejectsNonPositive() {
        try {
            new TerminalSize(0, 24);
            fail("zero width must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new TerminalSize(80, 0);
            fail("zero height must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new TerminalSize(-1, 24);
            fail("negative width must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new TerminalSize(80, -5);
            fail("negative height must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testEqualsHashCodeToString() {
        TerminalSize a = new TerminalSize(132, 50);
        TerminalSize b = new TerminalSize(132, 50);
        TerminalSize c = new TerminalSize(80, 24);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));
        assertFalse(a.equals(null));
        assertFalse(a.equals("132x50"));
        String text = a.toString();
        assertTrue("toString must carry both dimensions: " + text,
                text.contains("132") && text.contains("50"));
    }
}
