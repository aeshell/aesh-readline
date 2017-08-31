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
package org.aesh.readline.tty.terminal;

import org.aesh.readline.ReadlineFlag;
import org.aesh.readline.terminal.DeviceBuilder;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.Capability;
import org.aesh.io.Decoder;
import org.aesh.readline.Prompt;
import org.aesh.readline.TestReadline;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.terminal.Key;
import org.aesh.util.Parser;

import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import org.aesh.terminal.tty.Signal;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TestConnection implements Connection {

    private final Decoder decoder;
    private Consumer<Size> sizeHandler;
    private Consumer<int[]> stdOutHandler;
    private Consumer<Void> closeHandler;
    private EventDecoder eventDecoder;

    private StringBuilder bufferBuilder;
    private Queue<String> out;
    private TestReadline readline;
    private Size size;
    private Device device;
    private Attributes attributes;

    private Prompt prompt = new Prompt(": ");

    public TestConnection() {
        //default emacs mode
        this(EditModeBuilder.builder().create(), null);
    }

    public TestConnection(Prompt prompt) {
        //default emacs mode
        this(EditModeBuilder.builder().create(), null, prompt);
    }

    public TestConnection(EditMode editMode) {
        this(editMode, null);
    }

    public TestConnection(EnumMap<ReadlineFlag, Integer> flags) {
        this(null, null, null, null, null, null, flags);
    }

    public TestConnection(List<Completion> completions) {
        this(EditModeBuilder.builder().create(), completions);
    }

    public TestConnection(EditMode editMode, List<Completion> completions) {
        this(editMode, completions, null, null);
    }

    public TestConnection(EditMode editMode, List<Completion> completions, Prompt prompt) {
        this(editMode, completions, null, prompt);
    }

     public TestConnection(EditMode editMode, List<Completion> completions, Size size) {
        this(editMode, completions, size, null);
    }

    public TestConnection(EditMode editMode, List<Completion> completions, Size size, Prompt prompt) {
        this(null, editMode, completions, size, prompt);
    }
    public TestConnection(TestReadline readline, EditMode editMode, List<Completion> completions, Size size, Prompt prompt) {
        this(readline, editMode, completions, size, prompt, null, new EnumMap<>(ReadlineFlag.class));

    }
    public TestConnection(TestReadline readline, EditMode editMode, List<Completion> completions, Size size, Prompt prompt,
                          Attributes attributes, EnumMap<ReadlineFlag, Integer> flags) {
        if(editMode == null)
            editMode = EditModeBuilder.builder().create();
        bufferBuilder = new StringBuilder();
        stdOutHandler = ints ->
                bufferBuilder.append(Parser.stripAwayAnsiCodes(Parser.fromCodePoints(ints)));

        if(size == null)
            this.size = new Size(80, 20);
        else
            this.size = size;

        if(prompt != null)
            this.prompt = prompt;

        if(attributes != null)
            this.attributes = attributes;

        if(this.attributes != null)
            eventDecoder = new EventDecoder(this.attributes);
        else {
            eventDecoder = new EventDecoder();
            this.attributes = new Attributes();
        }

        device = DeviceBuilder.builder().name("ansi").build();
        decoder = new Decoder(512, Charset.defaultCharset(), eventDecoder);


        out = new LinkedList<>();
        if(readline == null) {
            this.readline = new TestReadline(editMode);
            if(completions != null)
                readline(completions);
            else if(flags != null)
                readline(flags);
            else
                readline();
        }
        else
            this.readline = readline;
   }

    public void readline() {
        clearOutputBuffer();
        readline.readline(this, prompt, out -> this.out.add(out));
    }

    public void readline(Consumer<String> out) {
        clearOutputBuffer();
        readline.readline(this, prompt, out);
    }

    public void readline(List<Completion> completions) {
        clearOutputBuffer();
        readline.readline(this, prompt, out -> this.out.add(out), completions );
    }

    public void readline(List<Completion> completions, Consumer<String> out) {
        clearOutputBuffer();
        readline.readline(this, prompt, out, completions );
    }

    public void readline(EnumMap<ReadlineFlag, Integer> flags) {
        clearOutputBuffer();
        readline.readline(this, prompt, out -> this.out.add(out), null, null, null, null, flags );
    }

    public void clearOutputBuffer() {
        if(bufferBuilder.length() > 0)
            bufferBuilder.delete(0, bufferBuilder.length());
    }

    public void clearLineBuffer() {
        if(out.size() > 0)
            out.clear();
    }

    public String getOutputBuffer() {
        return bufferBuilder.toString();
    }

    public String getPrompt() {
        return Parser.fromCodePoints(prompt.getPromptAsString());
    }

    public void setPrompt(Prompt prompt) {
        if(prompt != null)
            this.prompt = prompt;
    }

    public String getLine() {
        return out.poll();
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
    public Consumer<Size> getSizeHandler() {
        return sizeHandler;
    }

    @Override
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    @Override
    public Consumer<Signal> getSignalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    @Override
    public Consumer<int[]> getStdinHandler() {
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
    public Consumer<Void> getCloseHandler() {
        return closeHandler;
    }

    @Override
    public void close() {
        closeHandler.accept(null);
    }

    @Override
    public void openBlocking() {
    }

    @Override
    public void openNonBlocking() {
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return false;
    }

    @Override
    public Attributes getAttributes() {
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
        assertEquals(expected, bufferBuilder.toString());
    }

    public void assertBuffer(String expected) {
        assertEquals(expected, Parser.stripAwayAnsiCodes(readline.getBuffer()));
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
