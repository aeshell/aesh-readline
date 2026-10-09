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
package org.aesh.terminal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for OSC 7501 program status reports.
 * Vectors use decoded values from the protocol specification revision 0.3.
 * Escape bytes are built from char codes because unicode escapes
 * do not survive the authoring toolchain.
 */
public class ProgramStatusTest {

    private static final String ESC = Character.toString((char) 27);
    private static final String ST = ESC + Character.toString((char) 92);
    private static final String OSC = ESC + "]7501;";

    @Test
    public void testWorkingReportEncoding() {
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.WORKING)
                .app("brew")
                .message("Installing updates")
                .build();
        assertEquals(OSC + "state=working:app=brew:msg=SW5zdGFsbGluZyB1cGRhdGVz" + ST,
                status.toSequence());
    }

    @Test
    public void testBlockedReportEncoding() {
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.BLOCKED)
                .kind(ProgramStatus.BlockedKind.PERMISSION)
                .app("terraform")
                .message("Apply 3 to add, 1 to change, 0 to destroy?")
                .build();
        assertEquals(OSC + "state=blocked:kind=permission:app=terraform"
                + ":msg=QXBwbHkgMyB0byBhZGQsIDEgdG8gY2hhbmdlLCAwIHRvIGRlc3Ryb3k/" + ST,
                status.toSequence());
    }

    @Test
    public void testChildProgressEncoding() {
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.WORKING)
                .id("us-east")
                .title("US East")
                .progress(40)
                .message("Pushing image")
                .build();
        assertEquals(OSC + "state=working:id=us-east:title=VVMgRWFzdA==:progress=40"
                + ":msg=UHVzaGluZyBpbWFnZQ==" + ST,
                status.toSequence());
    }

    @Test
    public void testBlockedChildEncoding() {
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.BLOCKED)
                .kind(ProgramStatus.BlockedKind.PERMISSION)
                .id("eu-west")
                .title("EU West")
                .message("Approve deploy to eu-west (production)?")
                .build();
        assertEquals(OSC + "state=blocked:kind=permission:id=eu-west:title=RVUgV2VzdA=="
                + ":msg=QXBwcm92ZSBkZXBsb3kgdG8gZXUtd2VzdCAocHJvZHVjdGlvbik/" + ST,
                status.toSequence());
    }

    @Test
    public void testBareStatesEncode() {
        assertEquals(OSC + "state=idle" + ST,
                ProgramStatus.builder(ProgramStatus.State.IDLE).build().toSequence());
        assertEquals(OSC + "state=done" + ST,
                ProgramStatus.builder(ProgramStatus.State.DONE).build().toSequence());
        assertEquals(OSC + "state=error" + ST,
                ProgramStatus.builder(ProgramStatus.State.ERROR).build().toSequence());
    }

    @Test
    public void testAbsentIdMeansRoot() {
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.DONE)
                .app("brew")
                .message("Upgraded 12 packages")
                .build();
        assertNull(status.id());
        assertEquals(OSC + "state=done:app=brew:msg=VXBncmFkZWQgMTIgcGFja2FnZXM=" + ST,
                status.toSequence());
    }

    @Test
    public void testProgressBoundaries() {
        assertEquals(Integer.valueOf(0), ProgramStatus.builder(ProgramStatus.State.WORKING)
                .progress(0).build().progress());
        assertEquals(Integer.valueOf(100), ProgramStatus.builder(ProgramStatus.State.WORKING)
                .progress(100).build().progress());
        assertNull(ProgramStatus.builder(ProgramStatus.State.WORKING).build().progress());
    }

    @Test
    public void testClearSequences() {
        assertEquals(OSC + "state=clear:id=us-east" + ST,
                ProgramStatus.clearSequence("us-east"));
        assertEquals(OSC + "state=clear" + ST,
                ProgramStatus.clearAllSequence());
    }

    @Test(expected = NullPointerException.class)
    public void testNullStateRejected() {
        ProgramStatus.builder(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyIdRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).id("").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIdWithSpaceRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).id("eu west").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIdWithEmptySegmentRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).id("a//b").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIdTooDeepRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).id("a/b/c/d/e/f/g/h/i").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIdSegmentTooLongRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 33; i++)
            sb.append('a');
        ProgramStatus.builder(ProgramStatus.State.WORKING).id(sb.toString()).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyAppRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).app("").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAppWithSpaceRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).app("my app").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKindRequiresBlocked() {
        ProgramStatus.builder(ProgramStatus.State.WORKING)
                .kind(ProgramStatus.BlockedKind.PERMISSION).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProgressRequiresWorkingOrBlocked() {
        ProgramStatus.builder(ProgramStatus.State.DONE).progress(50).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProgressBelowRangeRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).progress(-1).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProgressAboveRangeRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).progress(101).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessageNewlineRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING).message("line one\nline two").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessageEscapeRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING)
                .message("hi" + (char) 27 + "there").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessageDelRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING)
                .message("hi" + (char) 127 + "there").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTitleC1Rejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING)
                .title("hi" + (char) 133 + "there").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoneSurrogateRejected() {
        ProgramStatus.builder(ProgramStatus.State.WORKING)
                .message("foo" + (char) 0xD800 + "bar").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMessageOverDecodedLimitRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2049; i++)
            sb.append('a');
        ProgramStatus.builder(ProgramStatus.State.WORKING).message(sb.toString()).build();
    }

    @Test
    public void testMessageAtDecodedLimitAccepted() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2048; i++)
            sb.append('a');
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.WORKING)
                .message(sb.toString()).build();
        assertTrue(status.toSequence().length() <= 4096);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTitleOverDecodedLimitRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 193; i++)
            sb.append('a');
        ProgramStatus.builder(ProgramStatus.State.WORKING).title(sb.toString()).build();
    }

    @Test
    public void testLargestLegalReportStaysWithinSequenceLimit() {
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < 2048; i++)
            msg.append('a');
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 192; i++)
            title.append('b');
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.BLOCKED)
                .kind(ProgramStatus.BlockedKind.AUTH)
                .id("a1/b2/c3/d4")
                .app("deploy")
                .title(title.toString())
                .progress(100)
                .message(msg.toString())
                .build();
        assertTrue(status.toSequence().length() <= 4096);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearWithEmptyIdRejected() {
        ProgramStatus.clearSequence("");
    }

    private static int[] codePoints(String text) {
        return text.codePoints().toArray();
    }

    private static final String DA1 = "" + (char) 27 + "[?1;2c";

    private static String ack(char terminatorKind) {
        // terminatorKind 0 means BEL, anything else means ST
        if (terminatorKind == 0) {
            return "" + (char) 27 + "]7501;?" + (char) 7;
        }
        return "" + (char) 27 + "]7501;?" + (char) 27 + (char) 92;
    }

    @Test
    public void testAckBeforeDa1MeansSupported() {
        assertEquals(Boolean.TRUE, ProgramStatus.parseSupportReply(codePoints(ack((char) 0) + DA1)));
        assertEquals(Boolean.TRUE, ProgramStatus.parseSupportReply(codePoints(ack((char) 1) + DA1)));
    }

    @Test
    public void testDa1BeforeAckMeansUnsupported() {
        assertEquals(Boolean.FALSE, ProgramStatus.parseSupportReply(codePoints(DA1 + ack((char) 0))));
    }

    @Test
    public void testDa1AloneMeansUnsupported() {
        assertEquals(Boolean.FALSE, ProgramStatus.parseSupportReply(codePoints(DA1)));
    }

    @Test
    public void testAckAloneMeansSupported() {
        assertEquals(Boolean.TRUE, ProgramStatus.parseSupportReply(codePoints(ack((char) 0))));
    }

    @Test
    public void testFragmentedAckWaits() {
        assertEquals(null, ProgramStatus.parseSupportReply(codePoints("" + (char) 27 + "]7501")));
        assertEquals(null, ProgramStatus.parseSupportReply(codePoints("" + (char) 27 + "]7501;?")));
    }

    @Test
    public void testFutureAckExtensionsAccepted() {
        assertEquals(Boolean.TRUE, ProgramStatus.parseSupportReply(
                codePoints("" + (char) 27 + "]7501;?:foo=bar" + (char) 7 + DA1)));
    }

    @Test
    public void testOtherOscIgnored() {
        String color = "" + (char) 27 + "]11;rgb:0000/0000/0000" + (char) 7;
        assertEquals(Boolean.FALSE, ProgramStatus.parseSupportReply(codePoints(color + DA1)));
        assertEquals(Boolean.TRUE,
                ProgramStatus.parseSupportReply(codePoints(color + ack((char) 0) + DA1)));
    }

    @Test
    public void testNonAck7501Ignored() {
        assertEquals(Boolean.FALSE, ProgramStatus.parseSupportReply(
                codePoints("" + (char) 27 + "]7501;0" + (char) 7 + DA1)));
    }

    @Test
    public void testEmptyAndNullWait() {
        assertEquals(null, ProgramStatus.parseSupportReply(new int[0]));
        assertEquals(null, ProgramStatus.parseSupportReply(null));
    }
}
