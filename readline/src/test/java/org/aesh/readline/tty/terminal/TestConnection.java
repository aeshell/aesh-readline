package org.aesh.readline.tty.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

import org.aesh.terminal.*;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Parser;

public class TestConnection implements Connection {
    protected Decoder decoder;
    protected Consumer<int[]> stdOutHandler;
    protected EventDecoder eventDecoder;
    protected final StringBuilder bufferBuilder;
    protected final Queue<String> out;
    protected final Size size;
    protected final Device device;
    protected Attributes attributes;
    private Consumer<Size> sizeHandler;
    private Consumer<Void> closeHandler;
    private final boolean stripAnsi = true;

    public TestConnection(Size size) {
        this(size, null);
    }

    public TestConnection(Size size, Device device) {
        bufferBuilder = new StringBuilder();
        out = new LinkedList<>();
        if (size == null)
            this.size = new Size(80, 20);
        else
            this.size = size;
        if (device == null) {
            this.device = new BaseDevice("ansi");
        } else {
            this.device = device;
        }
    }

    @Override
    public Device device() {
        return device;
    }

    @Override
    public Size size() {
        return size;
    }

    @Override
    public Consumer<Size> sizeHandler() {
        return sizeHandler;
    }

    @Override
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    @Override
    public Consumer<Signal> signalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    @Override
    public Consumer<int[]> stdinHandler() {
        return eventDecoder.getInputHandler();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    @Override
    public Consumer<int[]> stdoutHandler() {
        return stdOutHandler;
    }

    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public Consumer<Void> closeHandler() {
        return closeHandler;
    }

    @Override
    public void close() {
        if (closeHandler != null) {
            closeHandler.accept(null);
        }
    }

    @Override
    public void openBlocking() {
    }

    @Override
    public void openNonBlocking() {
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return device.puts(stdOutHandler, capability, params);
    }

    @Override
    public Attributes attributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attr) {
        this.attributes = attr;
    }

    @Override
    public Charset inputEncoding() {
        return null;
    }

    @Override
    public Charset outputEncoding() {
        return null;
    }

    @Override
    public boolean supportsAnsi() {
        return true;
    }

    public void assertOutputBuffer(String expected) {
        assertEquals(expected, bufferBuilder.toString().trim());
    }

    public void assertOutputBufferEndsWith(String input) {
        assertTrue(bufferBuilder.toString().trim().endsWith(input));
    }

    public void assertLine(String expected) {
        assertEquals(expected, out.poll());
    }

    public void read(int... data) {
        eventDecoder.accept(data);
    }

    public void read(byte[] data) {
        decoder.write(data);
    }

    public void read(Key key) {

        eventDecoder.accept(key.getKeyValues());
    }

    public void read(String data) {
        eventDecoder.getInputHandler().accept(Parser.toCodePoints(data));
    }
}
