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

import org.junit.Test;

/**
 * Eligibility matrix for the FFM-based POSIX backends (#343).
 * <p>
 * The versioned layouts implement Linux/macOS on 64-bit x86/ARM only.
 * Every other platform must decline to the portable subprocess fallback:
 * a foreign struct mismatch reads wrong fields or flips wrong terminal
 * flags. Host-independent by construction — pure literals, runs on every
 * JDK and OS leg.
 */
public class FfmPosixEligibilityTest {

    @Test
    public void testSupportedCombinations() {
        String[][] supported = {
                { "Linux", "amd64" },
                { "Linux", "x86_64" },
                { "Linux", "aarch64" },
                { "Linux", "arm64" },
                { "LINUX", "AMD64" },
                { "Mac OS X", "x86_64" },
                { "Mac OS X", "aarch64" },
                { "Darwin", "aarch64" },
        };
        for (String[] platform : supported) {
            assertEquals("supported: " + platform[0] + "/" + platform[1],
                    true, OSUtils.isFfmPosixSupported(platform[0], platform[1]));
        }
    }

    @Test
    public void testUnsupportedCombinations() {
        String[][] unsupported = {
                // Other POSIX systems have no implemented layouts (#343)
                { "FreeBSD", "amd64" },
                { "SunOS", "amd64" },
                { "AIX", "ppc64" },
                { "HP-UX", "ia64" },
                // Windows/Cygwin never take the POSIX path
                { "Windows 11", "amd64" },
                { "Windows 10", "x86_64" },
                // 32-bit and unmapped 64-bit arches decline (nfds_t width)
                { "Linux", "i386" },
                { "Linux", "x86" },
                { "Linux", "ppc64le" },
                { "Linux", "riscv64" },
                { "Linux", "s390x" },
                { "Mac OS X", "ppc" },
                { "Linux", "" },
                { "", "amd64" },
        };
        for (String[] platform : unsupported) {
            assertEquals("unsupported: " + platform[0] + "/" + platform[1],
                    false, OSUtils.isFfmPosixSupported(platform[0], platform[1]));
        }
    }

    @Test
    public void testHostConstantAgreesWithPredicate() {
        assertEquals("host constant must equal the predicate on host values",
                OSUtils.isFfmPosixSupported(
                        System.getProperty("os.name", ""),
                        System.getProperty("os.arch", "")),
                OSUtils.IS_FFM_POSIX_SUPPORTED);
    }
}
