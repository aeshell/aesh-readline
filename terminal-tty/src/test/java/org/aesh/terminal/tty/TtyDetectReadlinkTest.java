/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Collections;

import org.aesh.terminal.utils.PlatformContext;
import org.junit.Test;

/**
 * Classification table tests for the readlink pre-check (#400).
 * <p>
 * The table is a pure function: every row pins an answer or an explicit
 * decline (null). Only certain shapes answer; everything else falls
 * through to the regular probes, so a wrong row would be a wrong answer
 * anywhere it runs — these tests are the contract, not just coverage.
 */
public class TtyDetectReadlinkTest {

    private static void assertLink(String target, boolean statOk, boolean regular,
            boolean directory, boolean other, Boolean expected) {
        assertEquals("classify(" + target + ")",
                expected, TtyDetect.classifyLinkTarget(target, statOk, regular, directory, other));
    }

    @Test
    public void testNullAndUnreadableDecline() {
        assertNull(TtyDetect.classifyLinkTarget(null, false, false, false, false));
        assertNull(TtyDetect.classifyLinkTarget("", false, false, false, false));
        assertNull(TtyDetect.classifyLinkTarget("/dev/pts/0", false, false, false, false));
        assertNull(TtyDetect.classifyLinkTarget("anon_inode:[eventfd]", false, false, false, false));
        assertNull(TtyDetect.classifyLinkTarget("/tmp/removed (deleted)", false, false, false, false));
    }

    @Test
    public void testPipesSocketsAndNullAreNeverTerminals() {
        assertLink("pipe:[12345]", true, false, false, true, Boolean.FALSE);
        assertLink("socket:[12345]", true, false, false, true, Boolean.FALSE);
        assertLink("/dev/null", true, false, false, true, Boolean.FALSE);
        assertLink("/dev/null", false, false, false, false, Boolean.FALSE);
    }

    @Test
    public void testRegularFilesAreNeverTerminals() {
        assertLink("/tmp/out.txt", true, true, false, false, Boolean.FALSE);
        assertLink("/dev/pts/9", true, true, false, false, Boolean.FALSE);
        assertLink("/home/user/.bash_history", true, true, false, false, Boolean.FALSE);
    }

    @Test
    public void testPtySlavesAreTerminals() {
        assertLink("/dev/pts/0", true, false, false, true, Boolean.TRUE);
        assertLink("/dev/pts/12", true, false, false, true, Boolean.TRUE);
    }

    @Test
    public void testMountedOverPtyPathNeverAffirms() {
        assertLink("/dev/pts/0", true, false, true, false, Boolean.FALSE);
    }

    @Test
    public void testSerialConsolesAndDevicesDecline() {
        assertLink("/dev/tty", true, false, false, true, null);
        assertLink("/dev/ttyS0", true, false, false, true, null);
        assertLink("/dev/ttyUSB0", true, false, false, true, null);
        assertLink("/dev/ttyprintk", true, false, false, true, null);
        assertLink("/dev/console", true, false, false, true, null);
    }

    @Test
    public void testOtherDevicesAreNeverTerminals() {
        assertLink("/dev/zero", true, false, false, true, Boolean.FALSE);
        assertLink("/dev/urandom", true, false, false, true, Boolean.FALSE);
        assertLink("/dev/ptmx", true, false, false, true, Boolean.FALSE);
        assertLink("/tmp/myfifo", true, false, false, true, Boolean.FALSE);
    }

    @Test
    public void testPlainDirectoriesDecline() {
        // Directories are never terminal devices; no probe needed.
        assertFalse(TtyDetect.classifyLinkTarget("/tmp", true, false, true, false));
    }

    @Test
    public void testUnknownShapesDecline() {
        assertFalse("block devices are never terminals",
                TtyDetect.classifyLinkTarget("/dev/sda1", true, false, false, true));
        assertNull(TtyDetect.classifyLinkTarget("/memfd:jit (deleted)", false, false, false, false));
    }

    @Test
    public void testWindowsNeverClassifies() {
        PlatformContext windows = new PlatformContext("Windows 11", "amd64",
                Collections.<String, String> emptyMap(), "");
        assertNull(TtyDetect.tryReadlinkTty(0, windows));
        assertNull(TtyDetect.tryReadlinkTty(1, windows));
        assertNull(TtyDetect.tryReadlinkTty(2, windows));
    }

    @Test
    public void testInvalidDescriptorsDecline() {
        PlatformContext system = PlatformContext.system();
        assertNull(TtyDetect.tryReadlinkTty(-1, system));
        assertNull(TtyDetect.tryReadlinkTty(3, system));
    }
}
