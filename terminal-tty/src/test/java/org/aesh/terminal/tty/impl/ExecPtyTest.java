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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Attributes.ControlChar;
import org.aesh.terminal.Attributes.ControlFlag;
import org.aesh.terminal.Attributes.InputFlag;
import org.aesh.terminal.Attributes.LocalFlag;
import org.aesh.terminal.Attributes.OutputFlag;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

public class ExecPtyTest {

    private final String linuxSttySample = "speed 38400 baud; rows 85; columns 244; line = 0;\n" +
            "intr = ^C; quit = ^\\; erase = ^?; kill = ^U; eof = ^D; eol = M-^?; eol2 = M-^?; swtch = M-^?; start = ^Q; stop = ^S; susp = ^Z; rprnt = ^R; werase = ^W; lnext = ^V; flush = ^O; min = 1; time = 0;\n"
            +
            "-parenb -parodd cs8 hupcl -cstopb cread -clocal -crtscts\n" +
            "-ignbrk brkint -ignpar -parmrk -inpck -istrip -inlcr -igncr icrnl ixon -ixoff -iuclc ixany imaxbel iutf8\n" +
            "opost -olcuc -ocrnl onlcr -onocr -onlret -ofill -ofdel nl0 cr0 tab0 bs0 vt0 ff0\n" +
            "isig icanon iexten echo echoe echok -echonl -noflsh -xcase -tostop -echoprt echoctl echoke";

    private final String ubuntuSttySample = "speed 38400 baud; rows 48; columns 160; line = 0;\n" +
            "intr = ^C; quit = ^\\; erase = ^?; kill = ^U; eof = ^D; eol = <undef>; eol2 = <undef>; swtch = <undef>; start = ^Q; stop = ^S; susp = ^Z; rprnt = ^R; werase = ^W;\n"
            +
            "lnext = ^V; discard = ^O; min = 1; time = 0;\n" +
            "-parenb -parodd -cmspar cs8 -hupcl -cstopb cread -clocal -crtscts\n" +
            "-ignbrk -brkint -ignpar -parmrk -inpck -istrip -inlcr -igncr icrnl ixon -ixoff -iuclc -ixany -imaxbel iutf8\n" +
            "opost -olcuc -ocrnl onlcr -onocr -onlret -ofill -ofdel nl0 cr0 tab0 bs0 vt0 ff0\n" +
            "isig icanon iexten echo echoe echok -echonl -noflsh -xcase -tostop -echoprt echoctl echoke -flusho -extproc";

    private final String solarisSttySample = "speed 38400 baud; \n" +
            "rows = 85; columns = 244; ypixels = 0; xpixels = 0;\n" +
            "csdata ?\n" +
            "eucw 1:0:0:0, scrw 1:0:0:0\n" +
            "intr = ^c; quit = ^\\; erase = ^?; kill = ^u;\n" +
            "eof = ^d; eol = -^?; eol2 = -^?; swtch = <undef>;\n" +
            "start = ^q; stop = ^s; susp = ^z; dsusp = ^y;\n" +
            "rprnt = ^r; flush = ^o; werase = ^w; lnext = ^v;\n" +
            "-parenb -parodd cs8 -cstopb -hupcl cread -clocal -loblk -crtscts -crtsxoff -parext \n" +
            "-ignbrk brkint -ignpar -parmrk -inpck -istrip -inlcr -igncr icrnl -iuclc \n" +
            "ixon ixany -ixoff imaxbel \n" +
            "isig icanon -xcase echo echoe echok -echonl -noflsh \n" +
            "-tostop echoctl -echoprt echoke -defecho -flusho -pendin iexten \n" +
            "opost -olcuc onlcr -ocrnl -onocr -onlret -ofill -ofdel tab3";

    private final String hpuxSttySample = "speed 38400 baud; line = 0;\n" +
            "rows = 85; columns = 244;\n" +
            "min = 4; time = 0;\n" +
            "intr = DEL; quit = ^\\; erase = #; kill = @\n" +
            "eof = ^D; eol = ^@; eol2 <undef>; swtch = ^@\n" +
            "stop = ^S; start = ^Q; susp <undef>; dsusp <undef>\n" +
            "werase <undef>; lnext <undef>\n" +
            "-parenb -parodd cs8 -cstopb hupcl cread -clocal -loblk -crts\n" +
            "-ignbrk brkint ignpar -parmrk -inpck istrip -inlcr -igncr icrnl -iuclc\n" +
            "ixon ixany -ixoff -imaxbel -rtsxoff -ctsxon -ienqak\n" +
            "isig icanon -iexten -xcase echo -echoe echok -echonl -noflsh\n" +
            "-echoctl -echoprt -echoke -flusho -pendin\n" +
            "opost -olcuc onlcr -ocrnl -onocr -onlret -ofill -ofdel -tostop";

    @Test
    public void testParseSize() throws IOException {
        assertEquals(new Size(244, 85), ExecPty.doGetSize(linuxSttySample));
        assertEquals(new Size(244, 85), ExecPty.doGetSize(solarisSttySample));
        String aixSttySample = "speed 38400 baud; 85 rows; 244 columns;\n" +
                "eucw 1:1:0:0, scrw 1:1:0:0:\n" +
                "intr = ^C; quit = ^\\; erase = ^?; kill = ^U; eof = ^D; eol = <undef>\n" +
                "eol2 = <undef>; start = ^Q; stop = ^S; susp = ^Z; dsusp = ^Y; reprint = ^R\n" +
                "discard = ^O; werase = ^W; lnext = ^V\n" +
                "-parenb -parodd cs8 -cstopb -hupcl cread -clocal -parext \n" +
                "-ignbrk brkint -ignpar -parmrk -inpck -istrip -inlcr -igncr icrnl -iuclc \n" +
                "ixon ixany -ixoff imaxbel \n" +
                "isig icanon -xcase echo echoe echok -echonl -noflsh \n" +
                "-tostop echoctl -echoprt echoke -flusho -pending iexten \n" +
                "opost -olcuc onlcr -ocrnl -onocr -onlret -ofill -ofdel tab3";
        assertEquals(new Size(244, 85), ExecPty.doGetSize(aixSttySample));
        String macOsSttySample = "speed 9600 baud; 85 rows; 244 columns;\n" +
                "lflags: icanon isig iexten echo echoe -echok echoke -echonl echoctl\n" +
                "-echoprt -altwerase -noflsh -tostop -flusho pendin -nokerninfo\n" +
                "-extproc\n" +
                "iflags: -istrip icrnl -inlcr -igncr ixon -ixoff ixany imaxbel iutf8\n" +
                "-ignbrk brkint -inpck -ignpar -parmrk\n" +
                "oflags: opost onlcr -oxtabs -onocr -onlret\n" +
                "cflags: cread cs8 -parenb -parodd hupcl -clocal -cstopb -crtscts -dsrflow\n" +
                "-dtrflow -mdmbuf\n" +
                "cchars: discard = ^O; dsusp = ^Y; eof = ^D; eol = <undef>;\n" +
                "eol2 = <undef>; erase = ^?; intr = ^C; kill = ^U; lnext = ^V;\n" +
                "min = 1; quit = ^\\; reprint = ^R; start = ^Q; status = ^T;\n" +
                "stop = ^S; susp = ^Z; time = 0; werase = ^W;";
        assertEquals(new Size(244, 85), ExecPty.doGetSize(macOsSttySample));
        String netBsdSttySample = "speed 38400 baud; 85 rows; 244 columns;\n" +
                "lflags: icanon isig iexten echo echoe echok echoke -echonl echoctl\n" +
                "        -echoprt -altwerase -noflsh -tostop -flusho pendin -nokerninfo\n" +
                "        -extproc\n" +
                "iflags: -istrip icrnl -inlcr -igncr ixon -ixoff ixany imaxbel -ignbrk\n" +
                "        brkint -inpck -ignpar -parmrk\n" +
                "oflags: opost onlcr -ocrnl oxtabs onocr onlret\n" +
                "cflags: cread cs8 -parenb -parodd hupcl -clocal -cstopb -crtscts -mdmbuf\n" +
                "        -cdtrcts\n" +
                "cchars: discard = ^O; dsusp = ^Y; eof = ^D; eol = <undef>;\n" +
                "        eol2 = <undef>; erase = ^?; intr = ^C; kill = ^U; lnext = ^V;\n" +
                "        min = 1; quit = ^\\; reprint = ^R; start = ^Q; status = ^T;\n" +
                "        stop = ^S; susp = ^Z; time = 0; werase = ^W;";
        assertEquals(new Size(244, 85), ExecPty.doGetSize(netBsdSttySample));
        String freeBsdSttySample = "speed 9600 baud; 85 rows; 244 columns;\n" +
                "lflags: icanon isig iexten echo echoe echok echoke -echonl echoctl\n" +
                "        -echoprt -altwerase -noflsh -tostop -flusho -pendin -nokerninfo\n" +
                "        -extproc\n" +
                "iflags: -istrip icrnl -inlcr -igncr ixon -ixoff ixany imaxbel -ignbrk\n" +
                "        brkint -inpck -ignpar -parmrk\n" +
                "oflags: opost onlcr -ocrnl tab0 -onocr -onlret\n" +
                "cflags: cread cs8 -parenb -parodd hupcl -clocal -cstopb -crtscts -dsrflow\n" +
                "        -dtrflow -mdmbuf\n" +
                "cchars: discard = ^O; dsusp = ^Y; eof = ^D; eol = <undef>;\n" +
                "        eol2 = <undef>; erase = ^?; erase2 = ^H; intr = ^C; kill = ^U;\n" +
                "        lnext = ^V; min = 1; quit = ^\\; reprint = ^R; start = ^Q;\n" +
                "        status = ^T; stop = ^S; susp = ^Z; time = 0; werase = ^W;";
        assertEquals(new Size(244, 85), ExecPty.doGetSize(freeBsdSttySample));
        assertEquals(new Size(244, 85), ExecPty.doGetSize(hpuxSttySample));
    }

    @Test
    public void testParseAttributesLinux() {
        Attributes attributes = ExecPty.doGetAttr(linuxSttySample);
        checkAttributestLinux(attributes);
    }

    @Test
    public void testOptimizedParseAttributesLinux() {
        if (Config.isOSPOSIXCompatible()) {
            Attributes attributes = ExecPty.doGetLinuxAttr(linuxSttySample);
            checkAttributestLinux(attributes);
        }
    }

    @Test
    public void testParseAttributesUbuntu() {
        if (Config.isOSPOSIXCompatible()) {
            Attributes attributes = ExecPty.doGetAttr(ubuntuSttySample);
            checkAttributestUbuntu(attributes);
        }
    }

    @Test
    public void testOptimizedParseAttributesUbuntu() {
        if (Config.isOSPOSIXCompatible()) {
            Attributes attributes = ExecPty.doGetLinuxAttr(ubuntuSttySample);
            checkAttributestUbuntu(attributes);
        }
    }

    private void checkAttributestLinux(Attributes attributes) {
        assertEquals(EnumSet.of(InputFlag.BRKINT, InputFlag.ICRNL, InputFlag.IXON, InputFlag.IXANY, InputFlag.IMAXBEL,
                InputFlag.IUTF8), attributes.getInputFlags());
        assertEquals(EnumSet.of(OutputFlag.OPOST, OutputFlag.ONLCR), attributes.getOutputFlags());
        assertEquals(EnumSet.of(ControlFlag.CREAD, ControlFlag.HUPCL, ControlFlag.CS8), attributes.getControlFlags());
        assertEquals(EnumSet.of(LocalFlag.ISIG, LocalFlag.ICANON, LocalFlag.IEXTEN, LocalFlag.ECHO, LocalFlag.ECHOK,
                LocalFlag.ECHOCTL, LocalFlag.ECHOKE, LocalFlag.ECHOE), attributes.getLocalFlags());
        assertEquals(ExecPty.parseControlChar("^C"), attributes.getControlChar(ControlChar.VINTR));
        assertEquals(ExecPty.parseControlChar("^\\"), attributes.getControlChar(ControlChar.VQUIT));
        assertEquals(ExecPty.parseControlChar("^?"), attributes.getControlChar(ControlChar.VERASE));
        assertEquals(ExecPty.parseControlChar("^U"), attributes.getControlChar(ControlChar.VKILL));
        assertEquals(ExecPty.parseControlChar("^D"), attributes.getControlChar(ControlChar.VEOF));
        assertEquals(ExecPty.parseControlChar("M-^?"), attributes.getControlChar(ControlChar.VEOL));
        assertEquals(ExecPty.parseControlChar("M-^?"), attributes.getControlChar(ControlChar.VEOL2));
        assertEquals(ExecPty.parseControlChar("^Q"), attributes.getControlChar(ControlChar.VSTART));
        assertEquals(ExecPty.parseControlChar("^S"), attributes.getControlChar(ControlChar.VSTOP));
        assertEquals(ExecPty.parseControlChar("^Z"), attributes.getControlChar(ControlChar.VSUSP));
        assertEquals(ExecPty.parseControlChar("^R"), attributes.getControlChar(ControlChar.VREPRINT));
        assertEquals(ExecPty.parseControlChar("^W"), attributes.getControlChar(ControlChar.VWERASE));
        assertEquals(ExecPty.parseControlChar("^V"), attributes.getControlChar(ControlChar.VLNEXT));
        assertEquals(1, attributes.getControlChar(ControlChar.VMIN));
        assertEquals(0, attributes.getControlChar(ControlChar.VTIME));
    }

    private void checkAttributestUbuntu(Attributes attributes) {
        assertEquals(EnumSet.of(InputFlag.ICRNL, InputFlag.IXON, InputFlag.IUTF8), attributes.getInputFlags());
        assertEquals(EnumSet.of(OutputFlag.OPOST, OutputFlag.ONLCR), attributes.getOutputFlags());
        assertEquals(EnumSet.of(ControlFlag.CREAD, ControlFlag.CS8), attributes.getControlFlags());
        assertEquals(EnumSet.of(LocalFlag.ISIG, LocalFlag.ICANON, LocalFlag.IEXTEN, LocalFlag.ECHO, LocalFlag.ECHOK,
                LocalFlag.ECHOCTL, LocalFlag.ECHOKE, LocalFlag.ECHOE), attributes.getLocalFlags());
        assertEquals(ExecPty.parseControlChar("^C"), attributes.getControlChar(ControlChar.VINTR));
        assertEquals(ExecPty.parseControlChar("^\\"), attributes.getControlChar(ControlChar.VQUIT));
        assertEquals(ExecPty.parseControlChar("^?"), attributes.getControlChar(ControlChar.VERASE));
        assertEquals(ExecPty.parseControlChar("^U"), attributes.getControlChar(ControlChar.VKILL));
        assertEquals(ExecPty.parseControlChar("^D"), attributes.getControlChar(ControlChar.VEOF));
        assertEquals(-1, attributes.getControlChar(ControlChar.VEOL));
        assertEquals(-1, attributes.getControlChar(ControlChar.VEOL2));
        assertEquals(ExecPty.parseControlChar("^Q"), attributes.getControlChar(ControlChar.VSTART));
        assertEquals(ExecPty.parseControlChar("^S"), attributes.getControlChar(ControlChar.VSTOP));
        assertEquals(ExecPty.parseControlChar("^Z"), attributes.getControlChar(ControlChar.VSUSP));
        assertEquals(ExecPty.parseControlChar("^R"), attributes.getControlChar(ControlChar.VREPRINT));
        assertEquals(ExecPty.parseControlChar("^W"), attributes.getControlChar(ControlChar.VWERASE));
        assertEquals(ExecPty.parseControlChar("^V"), attributes.getControlChar(ControlChar.VLNEXT));
        assertEquals(1, attributes.getControlChar(ControlChar.VMIN));
        assertEquals(0, attributes.getControlChar(ControlChar.VTIME));
    }

    @Test
    public void testParseAttributesSolaris() {
        Attributes attributes = ExecPty.doGetAttr(solarisSttySample);
        assertEquals(EnumSet.of(InputFlag.BRKINT, InputFlag.ICRNL, InputFlag.IXON, InputFlag.IXANY, InputFlag.IMAXBEL),
                attributes.getInputFlags());
        assertEquals(EnumSet.of(OutputFlag.OPOST, OutputFlag.ONLCR), attributes.getOutputFlags());
        assertEquals(EnumSet.of(ControlFlag.CREAD, ControlFlag.CS8), attributes.getControlFlags());
        assertEquals(EnumSet.of(LocalFlag.ISIG, LocalFlag.ICANON, LocalFlag.IEXTEN, LocalFlag.ECHO, LocalFlag.ECHOK,
                LocalFlag.ECHOCTL, LocalFlag.ECHOKE, LocalFlag.ECHOE), attributes.getLocalFlags());
        assertEquals(ExecPty.parseControlChar("^C"), attributes.getControlChar(ControlChar.VINTR));
        assertEquals(ExecPty.parseControlChar("^\\"), attributes.getControlChar(ControlChar.VQUIT));
        assertEquals(ExecPty.parseControlChar("^?"), attributes.getControlChar(ControlChar.VERASE));
        assertEquals(ExecPty.parseControlChar("^U"), attributes.getControlChar(ControlChar.VKILL));
        assertEquals(ExecPty.parseControlChar("^D"), attributes.getControlChar(ControlChar.VEOF));
        assertEquals(ExecPty.parseControlChar("-^?"), attributes.getControlChar(ControlChar.VEOL));
        assertEquals(ExecPty.parseControlChar("-^?"), attributes.getControlChar(ControlChar.VEOL2));
        assertEquals(ExecPty.parseControlChar("^Q"), attributes.getControlChar(ControlChar.VSTART));
        assertEquals(ExecPty.parseControlChar("^S"), attributes.getControlChar(ControlChar.VSTOP));
        assertEquals(ExecPty.parseControlChar("^Z"), attributes.getControlChar(ControlChar.VSUSP));
        assertEquals(ExecPty.parseControlChar("^R"), attributes.getControlChar(ControlChar.VREPRINT));
        assertEquals(ExecPty.parseControlChar("^W"), attributes.getControlChar(ControlChar.VWERASE));
        assertEquals(ExecPty.parseControlChar("^V"), attributes.getControlChar(ControlChar.VLNEXT));
    }

    @Test
    public void testParseAttributesHpux() {
        Attributes attributes = ExecPty.doGetAttr(hpuxSttySample);
        assertEquals(EnumSet.of(InputFlag.BRKINT, InputFlag.IGNPAR, InputFlag.ISTRIP, InputFlag.ICRNL, InputFlag.IXON,
                InputFlag.IXANY), attributes.getInputFlags());
        assertEquals(EnumSet.of(OutputFlag.OPOST, OutputFlag.ONLCR), attributes.getOutputFlags());
        assertEquals(EnumSet.of(ControlFlag.CREAD, ControlFlag.CS8, ControlFlag.HUPCL), attributes.getControlFlags());
        assertEquals(EnumSet.of(LocalFlag.ISIG, LocalFlag.ICANON, LocalFlag.ECHO, LocalFlag.ECHOK), attributes.getLocalFlags());
        assertEquals(127, attributes.getControlChar(ControlChar.VINTR));
        assertEquals(ExecPty.parseControlChar("^\\"), attributes.getControlChar(ControlChar.VQUIT));
        assertEquals('#', attributes.getControlChar(ControlChar.VERASE));
        assertEquals('@', attributes.getControlChar(ControlChar.VKILL));
        assertEquals(ExecPty.parseControlChar("^D"), attributes.getControlChar(ControlChar.VEOF));
        assertEquals(0, attributes.getControlChar(ControlChar.VEOL));
        assertEquals(ExecPty.parseControlChar("^Q"), attributes.getControlChar(ControlChar.VSTART));
        assertEquals(ExecPty.parseControlChar("^S"), attributes.getControlChar(ControlChar.VSTOP));
        assertEquals(4, attributes.getControlChar(ControlChar.VMIN));
        assertEquals(0, attributes.getControlChar(ControlChar.VTIME));
    }

    @Test
    public void testParseAttributesLinuxWithCRLF() {
        // stty output and samples transported through Windows checkouts
        // carry \r\n. Parsing must be identical to the LF case (regression:
        // splitting on the platform separator glued "iutf8\nopost" and
        // silently dropped IUTF8).
        Attributes attributes = ExecPty.doGetLinuxAttr(linuxSttySample.replace("\n", "\r\n"));
        checkAttributestLinux(attributes);
    }

    @Test
    public void testParseAttributesUbuntuWithCRLF() {
        Attributes attributes = ExecPty.doGetLinuxAttr(ubuntuSttySample.replace("\n", "\r\n"));
        checkAttributestUbuntu(attributes);
    }

    @Test
    public void testParseSizeHPUXWithCRLF() {
        // Explicit CRLF variant: must parse identically to the LF file.
        String crlf = "TERM='vt200'; export TERM;\r\nLINES=47; export LINES;\r\n"
                + "COLUMNS=112; export COLUMNS;\r\nERASE='^?'; export ERASE;\r\n";
        Size size = ExecPty.doGetHPUXSize(crlf);
        assertEquals(47, size.getHeight());
        assertEquals(112, size.getWidth());
    }

    @Test
    public void testParseSizeHPUX() throws IOException {
        if (Config.isOSPOSIXCompatible()) {
            String input = new String(Files.readAllBytes(
                    Config.isOSPOSIXCompatible() ? new File("src/test/resources/ttytype_hpux.txt").toPath()
                            : new File("src\\test\\resources\\ttytype_hpux.txt").toPath()));

            Size size = ExecPty.doGetHPUXSize(input);

            assertEquals(47, size.getHeight());
            assertEquals(112, size.getWidth());
        }
    }

    // ==================== Slave stream ownership ====================

    private static File tempTty() throws IOException {
        File tty = File.createTempFile("execpty", ".tmp");
        tty.deleteOnExit();
        return tty;
    }

    @Test
    public void testSlaveStreamsCached() throws IOException {
        ExecPty pty = new ExecPty(tempTty().getAbsolutePath());
        try {
            assertSame(pty.getSlaveInput(), pty.getSlaveInput());
            assertSame(pty.getSlaveOutput(), pty.getSlaveOutput());
        } finally {
            pty.close();
        }
    }

    @Test
    public void testCloseInvalidatesOwnedStreams() throws IOException {
        ExecPty pty = new ExecPty(tempTty().getAbsolutePath());
        InputStream in = pty.getSlaveInput();
        OutputStream out = pty.getSlaveOutput();
        pty.close();
        try {
            in.read();
            fail("owned input must be closed");
        } catch (IOException expected) {
        }
        try {
            out.write('x');
            fail("owned output must be closed");
        } catch (IOException expected) {
        }
        // Second close stays quiet.
        pty.close();
    }

    @Test
    public void testBorrowedStreamsSurviveClose() throws IOException {
        ExecPty pty = new ExecPty("definitely-not-a-tty-xyz");
        try {
            assertSame("invalid tty falls back to System.in",
                    System.in, pty.getSlaveInput());
            assertSame("invalid tty falls back to System.out",
                    System.out, pty.getSlaveOutput());
            pty.close();
            // FileDescriptor validity, not available(): touching
            // System.in's monitor contends with surefire's own
            // command-reader thread, which holds it in blocking read.
            assertTrue("System.in must survive", FileDescriptor.in.valid());
            assertTrue("System.out must survive", FileDescriptor.out.valid());
            assertSame(System.in, pty.getSlaveInput());
            assertSame(System.out, pty.getSlaveOutput());
        } finally {
            // Never close System.in/out, even in cleanup.
        }
    }

    @Test
    public void testPartialConstructionReleasesInput() throws Exception {
        File tty = tempTty();
        ExecPty pty = new ExecPty(tty.getAbsolutePath()) {
            @Override
            public Attributes getAttr() {
                return new Attributes();
            }

            @Override
            public OutputStream getSlaveOutput() {
                throw new RuntimeException("output open blew up");
            }
        };
        InputStream input = pty.getSlaveInput();
        try {
            new PosixSysTerminal("test", "ansi", pty, false);
            fail("construction must propagate the output failure");
        } catch (RuntimeException e) {
            assertEquals("output open blew up", e.getMessage());
        }
        try {
            input.read();
            fail("input opened during failed construction must be released");
        } catch (IOException expected) {
        }
        assertTrue(tty.delete() || !tty.exists());
    }

    @Test
    public void testCloseUnblocksReader() throws Exception {
        ExecPty pty = new ExecPty(tempTty().getAbsolutePath());
        InputStream in = pty.getSlaveInput();
        CountDownLatch started = new CountDownLatch(1);
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    started.countDown();
                    // Production read pattern (see openBlockingLegacy):
                    // never block in read(), gate on available().
                    while (true) {
                        if (in.available() <= 0) {
                            Thread.sleep(10);
                            continue;
                        }
                        if (in.read() < 0) {
                            break;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    // Expected shutdown paths: closed stream or interrupt.
                    Thread.currentThread().interrupt();
                }
            }
        });
        reader.setDaemon(true);
        reader.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        long start = System.nanoTime();
        pty.close();
        long closeMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("close() must return promptly with an active reader (#288): "
                + closeMs + "ms", closeMs < 2000);
        reader.join(5000);
        assertFalse("reader must observe the closed stream and exit", reader.isAlive());
    }
}
