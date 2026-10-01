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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Host-independent platform facts for provider selection (#356).
 * <p>
 * Every combination is a literal: no JVM-global property is read or
 * mutated, so this matrix runs identically on any host or CI leg.
 */
public class PlatformContextTest {

    private static Map<String, String> env(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return env;
    }

    private static PlatformContext context(String os, String arch, Map<String, String> env) {
        return new PlatformContext(os, arch, env, "/home/test");
    }

    @Test
    public void testLinuxPosix() {
        PlatformContext context = context("Linux", "amd64", env());
        assertTrue(context.isLinux());
        assertFalse(context.isWindows());
        assertFalse(context.isMac());
        assertFalse(context.isCygwin());
        assertTrue(context.isFfmPosixSupported());
    }

    @Test
    public void testMacArm() {
        PlatformContext context = context("Mac OS X", "aarch64", env());
        assertTrue(context.isMac());
        assertTrue(context.isFfmPosixSupported());
    }

    @Test
    public void testWindowsNative() {
        PlatformContext context = context("Windows 11", "amd64", env());
        assertTrue(context.isWindows());
        assertFalse(context.isCygwin());
        assertFalse(context.isFfmPosixSupported());
    }

    @Test
    public void testCygwinIndicators() {
        assertTrue(context("Windows 10", "amd64", env("MSYSTEM", "MINGW64")).isCygwin());
        assertTrue(context("Windows 10", "amd64", env("CYGWIN", "nodosfilewarning")).isCygwin());
        assertTrue(context("Windows 10", "amd64", env("TERM_PROGRAM", "mintty")).isCygwin());
        assertTrue(context("Windows 10", "amd64", env("PWD", "/home/user")).isCygwin());
        assertFalse(context("Windows 10", "amd64", env("PWD", "C:\\Users")).isCygwin());
        assertFalse("posix PWD without Windows is not Cygwin",
                context("Linux", "amd64", env("PWD", "/home/user")).isCygwin());
    }

    @Test
    public void testUnsupportedAbisDecline() {
        assertFalse(context("FreeBSD", "amd64", env()).isFfmPosixSupported());
        assertFalse(context("SunOS", "sparc", env()).isFfmPosixSupported());
        assertFalse(context("Linux", "ppc64le", env()).isFfmPosixSupported());
        assertFalse(context("Linux", "i386", env()).isFfmPosixSupported());
        assertFalse(context("Mac OS X", "ppc", env()).isFfmPosixSupported());
    }

    @Test
    public void testTermLookup() {
        assertEquals("xterm-256color", context("Linux", "amd64", env("TERM", "xterm-256color")).term());
        assertEquals(null, context("Linux", "amd64", env()).term());
    }

    @Test
    public void testEnvironmentIsCopied() {
        Map<String, String> env = env("TERM", "xterm");
        PlatformContext context = context("Linux", "amd64", env);
        env.put("TERM", "mutated");
        assertEquals("context must snapshot the map", "xterm", context.term());
        try {
            context.env().put("TERM", "mutated");
            assertTrue("environment map must stay unmodifiable", false);
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void testNullsDefault() {
        PlatformContext context = new PlatformContext(null, null, new HashMap<String, String>(), null);
        assertEquals("", context.osName());
        assertEquals("", context.osArch());
        assertEquals("", context.userHome());
        assertFalse(context.isWindows());
        assertFalse(context.isFfmPosixSupported());
    }

    @Test
    public void testSystemSnapshotAgreesWithLive() {
        PlatformContext context = PlatformContext.system();
        assertEquals(System.getProperty("os.name", ""), context.osName());
        assertEquals(System.getProperty("os.arch", ""), context.osArch());
        assertEquals(System.getenv("TERM"), context.term());
    }
}
