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

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.readline.Prompt;
import org.aesh.readline.ReadlineFlag;
import org.aesh.readline.ReadlineRequest;
import org.aesh.readline.TestReadline;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.tty.DeviceBuilder;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Parser;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TestReadlineConnection extends TestConnection {

    private final TestReadline readline;

    private Prompt prompt = new Prompt(": ");

    public TestReadlineConnection() {
        //default emacs mode
        this(EditModeBuilder.builder().build(), null);
    }

    public TestReadlineConnection(boolean stripAnsi) {
        //default emacs mode
        this(null, null, null, null, null, null, null, stripAnsi);
    }

    public TestReadlineConnection(Prompt prompt) {
        //default emacs mode
        this(EditModeBuilder.builder().build(), null, prompt);
    }

    public TestReadlineConnection(EditMode editMode) {
        this(editMode, null);
    }

    public TestReadlineConnection(EnumMap<ReadlineFlag, Integer> flags) {
        this(null, null, null, null, null, null, flags);
    }

    public TestReadlineConnection(List<Completion> completions) {
        this(EditModeBuilder.builder().build(), completions);
    }

    public TestReadlineConnection(EditMode editMode, List<Completion> completions) {
        this(editMode, completions, null, null);
    }

    public TestReadlineConnection(EditMode editMode, List<Completion> completions, Prompt prompt) {
        this(editMode, completions, null, prompt);
    }

    public TestReadlineConnection(EditMode editMode, List<Completion> completions, Size size) {
        this(editMode, completions, size, null);
    }

    public TestReadlineConnection(EditMode editMode, List<Completion> completions, Size size, Prompt prompt) {
        this(null, editMode, completions, size, prompt);
    }

    public TestReadlineConnection(TestReadline readline, EditMode editMode, List<Completion> completions, Size size,
            Prompt prompt) {
        this(readline, editMode, completions, size, prompt, null, new EnumMap<>(ReadlineFlag.class));

    }

    public TestReadlineConnection(TestReadline readline, EditMode editMode, List<Completion> completions, Size size,
            Prompt prompt,
            Attributes attributes, EnumMap<ReadlineFlag, Integer> flags) {
        this(readline, editMode, completions, size, prompt, attributes, flags, true);
    }

    public TestReadlineConnection(TestReadline readline, EditMode editMode, List<Completion> completions, Size size,
            Prompt prompt,
            Attributes attributes, EnumMap<ReadlineFlag, Integer> flags, boolean stripAnsi) {
        super(size, DeviceBuilder.builder().name("xterm-256color").build());
        if (editMode == null)
            editMode = EditModeBuilder.builder().build();
        if (stripAnsi)
            stdOutHandler = ints -> bufferBuilder.append(Parser.stripAwayAnsiCodes(Parser.fromCodePoints(ints)));
        else
            stdOutHandler = ints -> bufferBuilder.append(Parser.fromCodePoints(ints));

        if (prompt != null)
            this.prompt = prompt;

        if (attributes != null)
            this.attributes = attributes;

        if (this.attributes != null)
            eventDecoder = new EventDecoder(this.attributes);
        else {
            eventDecoder = new EventDecoder();
            this.attributes = new Attributes();
        }

        decoder = new Decoder(512, Charset.defaultCharset(), eventDecoder);

        if (readline == null) {
            this.readline = new TestReadline(editMode);
            if (completions != null)
                readline(completions);
            else if (flags != null)
                readline(flags);
            else
                readline();
        } else
            this.readline = readline;
    }

    public void readline() {
        clearOutputBuffer();
        readline.readline(this, prompt, this.out::add);
    }

    public void assertBuffer(String expected) {
        assertEquals(expected, Parser.stripAwayAnsiCodes(readline.getBuffer()));
    }

    public void readline(Consumer<String> out) {
        clearOutputBuffer();
        readline.readline(this, prompt, out);
    }

    public void readline(List<Completion> completions) {
        clearOutputBuffer();
        readline.readline(ReadlineRequest.builder().connection(this).prompt(prompt)
                .requestHandler(this.out::add).completions(completions).build());
    }

    public void readline(List<Completion> completions, Consumer<String> out) {
        clearOutputBuffer();
        readline.readline(ReadlineRequest.builder().connection(this).prompt(prompt)
                .requestHandler(out).completions(completions).build());
    }

    public void readline(EnumMap<ReadlineFlag, Integer> flags) {
        clearOutputBuffer();
        readline.readline(ReadlineRequest.builder().connection(this).prompt(prompt)
                .requestHandler(this.out::add).flags(flags).build());
    }

    public void clearOutputBuffer() {
        if (bufferBuilder.length() > 0)
            bufferBuilder.delete(0, bufferBuilder.length());
    }

    public void clearLineBuffer() {
        if (!out.isEmpty())
            out.clear();
    }

    public String getOutputBuffer() {
        return bufferBuilder.toString();
    }

    public String getPrompt() {
        return Parser.fromCodePoints(prompt.getPromptCharacters());
    }

    public void setPrompt(Prompt prompt) {
        if (prompt != null)
            this.prompt = prompt;
    }

    public String getLine() {
        return out.poll();
    }

}
