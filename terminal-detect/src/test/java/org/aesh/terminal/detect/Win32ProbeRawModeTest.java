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

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Headless tests for the Win32 probe session raw mode word (#334).
 * <p>
 * All access is reflective so this test compiles and passes on pre-22
 * runtimes (where the transport class does not exist). The word is pure
 * integer composition — no console, no downcalls — so it runs anywhere
 * the class loads.
 */
public class Win32ProbeRawModeTest {

    private static final String WIN32_TRANSPORT = "org.aesh.terminal.detect.Win32ProbeTransport";

    private static final int ENABLE_WINDOW_INPUT = 0x0008;
    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;
    private static final int ENABLE_QUICK_EDIT_MODE = 0x0040;
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

    @Test
    public void testRawInputModeWord() throws Exception {
        Class<?> clazz;
        try {
            clazz = Class.forName(WIN32_TRANSPORT);
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        Method rawInputMode = clazz.getDeclaredMethod("rawInputMode");
        int mode = (Integer) rawInputMode.invoke(null);
        assertEquals(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS, mode);
        assertEquals("QUICK_EDIT must never be composed", 0,
                mode & ENABLE_QUICK_EDIT_MODE);
        assertEquals("VIRTUAL_TERMINAL_INPUT must stay off", 0,
                mode & ENABLE_VIRTUAL_TERMINAL_INPUT);
    }
}
