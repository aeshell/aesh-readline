package org.aesh.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for the non-blocking read/peek API default methods on Terminal.
 * <p>
 * Verifies that the default implementations provide correct fallback behavior
 * for terminals that do not support non-blocking reads (Java 8-21 path).
 */
public class NonBlockingApiTest {

    /**
     * READ_EXPIRED constant should be -2.
     */
    @Test
    public void testReadExpiredConstant() {
        assertEquals(-2, Terminal.READ_EXPIRED);
    }

    /**
     * Default supportsNonBlockingRead() should return false.
     */
    @Test
    public void testDefaultSupportsNonBlockingRead() {
        Terminal terminal = createMinimalTerminal(new byte[] {});
        assertFalse(terminal.supportsNonBlockingRead());
    }

    /**
     * Default read(timeout) should delegate to input().read() (blocking).
     */
    @Test
    public void testDefaultReadDelegatesToInputStream() throws IOException {
        Terminal terminal = createMinimalTerminal(new byte[] { 65, 66, 67 }); // A, B, C
        assertEquals(65, terminal.read(100));
        assertEquals(66, terminal.read(100));
        assertEquals(67, terminal.read(100));
        assertEquals(-1, terminal.read(100)); // EOF
    }

    /**
     * Default peek(timeout) should return READ_EXPIRED (no peek support).
     */
    @Test
    public void testDefaultPeekReturnsReadExpired() throws IOException {
        Terminal terminal = createMinimalTerminal(new byte[] { 65 });
        assertEquals(Terminal.READ_EXPIRED, terminal.peek(100));
    }

    /**
     * Default read(b, off, len, timeout) should delegate to input().read(b, off, len).
     */
    @Test
    public void testDefaultBulkReadDelegatesToInputStream() throws IOException {
        Terminal terminal = createMinimalTerminal(new byte[] { 65, 66, 67, 68 });
        byte[] buf = new byte[4];
        int read = terminal.read(buf, 0, 4, 100);
        assertEquals(4, read);
        assertEquals(65, buf[0]);
        assertEquals(66, buf[1]);
        assertEquals(67, buf[2]);
        assertEquals(68, buf[3]);
    }

    /**
     * Default bulk read should return -1 on empty stream.
     */
    @Test
    public void testDefaultBulkReadEOF() throws IOException {
        Terminal terminal = createMinimalTerminal(new byte[] {});
        byte[] buf = new byte[4];
        int read = terminal.read(buf, 0, 4, 100);
        assertEquals(-1, read);
    }

    /**
     * Connection default supportsNonBlockingRead() should return false.
     */
    @Test
    public void testConnectionDefaultSupportsNonBlockingRead() {
        // Connection is an interface with default methods — test via
        // an anonymous implementation with minimal required methods
        Connection conn = createMinimalConnection();
        assertFalse(conn.supportsNonBlockingRead());
    }

    /**
     * Connection default peek(timeout) should throw UnsupportedOperationException.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testConnectionDefaultPeekThrows() throws IOException {
        Connection conn = createMinimalConnection();
        conn.peek(100);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Terminal createMinimalTerminal(byte[] inputData) {
        return new Terminal() {
            private final InputStream in = new ByteArrayInputStream(inputData);

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public SignalHandler handle(Signal signal, SignalHandler handler) {
                return null;
            }

            @Override
            public void raise(Signal signal) {
            }

            @Override
            public InputStream input() {
                return in;
            }

            @Override
            public OutputStream output() {
                return System.out;
            }

            @Override
            public boolean echo() {
                return false;
            }

            @Override
            public boolean echo(boolean echo) {
                return false;
            }

            @Override
            public Attributes getAttributes() {
                return new Attributes();
            }

            @Override
            public void setAttributes(Attributes attr) {
            }

            @Override
            public Size getSize() {
                return new Size(80, 24);
            }

            @Override
            public Device device() {
                return new BaseDevice("test");
            }

            @Override
            public void close() {
            }
        };
    }

    private Connection createMinimalConnection() {
        return new Connection() {
            @Override
            public Device device() {
                return new BaseDevice("test");
            }

            @Override
            public Size size() {
                return new Size(80, 24);
            }

            @Override
            public java.util.function.Consumer<int[]> stdinHandler() {
                return null;
            }

            @Override
            public void setStdinHandler(java.util.function.Consumer<int[]> h) {
            }

            @Override
            public java.util.function.Consumer<int[]> stdoutHandler() {
                return null;
            }

            @Override
            public boolean put(org.aesh.terminal.tty.Capability c, Object... p) {
                return false;
            }

            @Override
            public java.util.function.Consumer<Signal> signalHandler() {
                return null;
            }

            @Override
            public void setSignalHandler(java.util.function.Consumer<Signal> h) {
            }

            @Override
            public java.util.function.Consumer<Size> sizeHandler() {
                return null;
            }

            @Override
            public void setSizeHandler(java.util.function.Consumer<Size> h) {
            }

            @Override
            public java.util.function.Consumer<Void> closeHandler() {
                return null;
            }

            @Override
            public void setCloseHandler(java.util.function.Consumer<Void> h) {
            }

            @Override
            public void openBlocking() {
            }

            @Override
            public void openNonBlocking() {
            }

            @Override
            public void close() {
            }

            @Override
            public Attributes attributes() {
                return new Attributes();
            }

            @Override
            public void setAttributes(Attributes a) {
            }

            @Override
            public java.nio.charset.Charset inputEncoding() {
                return null;
            }

            @Override
            public java.nio.charset.Charset outputEncoding() {
                return null;
            }

            @Override
            public boolean supportsAnsi() {
                return true;
            }
        };
    }
}
