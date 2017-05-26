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
package org.aesh.readline;

import org.aesh.readline.completion.Completion;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.terminal.Key;
import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.utils.Config;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import org.aesh.terminal.tty.Size;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ReadlineTest {

    @Test
    public void testPaste() {
        TestConnection term = new TestConnection();
        term.read("1234\nfoo bar\ngah bah");
        term.assertLine("1234");
        term.readline();
        term.assertLine("foo bar");
        term.readline();
        term.assertBuffer("gah bah");
    }

    @Test
    public void testMasking() {
        Prompt prompt = new Prompt(": ", '#');
        TestConnection term = new TestConnection(null, null, null, prompt);
        term.setSignalHandler(null);
        term.read("foo bar");
        assertEquals(": #######", term.getOutputBuffer());
        term.read(Key.BACKSPACE);
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.read(Key.ENTER);
        term.assertLine("oo ba");

        prompt = new Prompt("", '\0');
        term.setPrompt(prompt);
        term.readline();
        term.read("foo bar");
        assertEquals("", term.getOutputBuffer());
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.ENTER);
        term.assertLine("foo b");
    }

    @Test
    public void testMultiLine() {
        TestConnection term = new TestConnection();
        term.read("foo \\");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertLine(null);
        assertEquals(Config.getLineSeparator()+"> ", term.getOutputBuffer());
        term.read("bar\n");
        term.assertLine("foo bar");
    }

    @Test
    public void testMultiLineDelete() {
        TestConnection term = new TestConnection();
        term.read("foo \\");
        term.clearOutputBuffer();
        term.read(Key.ENTER);
        term.assertBuffer("foo ");
        term.assertLine(null);
        assertEquals(Config.getLineSeparator()+"> ", term.getOutputBuffer());
        term.read("bar");
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.read(Key.ENTER);
        term.assertLine("foo ");
    }

    @Test
    public void testSingleCompleteResult() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if(completeOperation.getBuffer().equals("f"))
                completeOperation.addCompletionCandidate("foo");
            else if(completeOperation.getBuffer().equals("b"))
                completeOperation.addCompletionCandidate("bar");
        });

        TestConnection term = new TestConnection(completions);

        term.read("fo");
        term.read(Key.CTRL_I);
        term.assertBuffer("fo");
        term.read(Key.BACKSPACE);
        term.read(Key.CTRL_I);
        term.assertBuffer("foo ");
        term.read("1");
        term.assertBuffer("foo 1");
        term.read(Key.ENTER);
        term.assertLine("foo 1");
    }

    @Test
    public void testMultipleCompleteResults() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if(completeOperation.getBuffer().equals("f")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.addCompletionCandidate("foo bar");
            }
            else if(completeOperation.getBuffer().equals("foo")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.addCompletionCandidate("foo bar");
            }
            else if(completeOperation.getBuffer().equals("b")) {
                completeOperation.addCompletionCandidate("bar bar");
                completeOperation.addCompletionCandidate("bar baar");
            }
        });

        TestConnection term = new TestConnection(completions);

        term.read("f");
        term.read(Key.CTRL_I);
        term.assertBuffer("foo");
        term.clearOutputBuffer();
        term.read(Key.CTRL_I);
        assertEquals(Config.getLineSeparator()+"foo  foo bar  "+
                Config.getLineSeparator()+term.getPrompt()+"foo", term.getOutputBuffer());
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("b");
        term.read(Key.CTRL_I);
        term.assertBuffer("bar\\ ba");
    }

    @Test
    public void testCompleteResultsMultipleLines() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if(completeOperation.getBuffer().equals("ff")) {
                completeOperation.addCompletionCandidate("ffoo");
            }
            else if(completeOperation.getBuffer().endsWith("f")) {
                completeOperation.addCompletionCandidate(completeOperation.getBuffer()+"oo");
            }
            else if(completeOperation.getBuffer().endsWith("foo")) {
                completeOperation.addCompletionCandidate(completeOperation.getBuffer()+"foo");
                completeOperation.addCompletionCandidate(completeOperation.getBuffer()+"foo bar");
            }
            else if(completeOperation.getBuffer().endsWith("b")) {
                completeOperation.addCompletionCandidate(completeOperation.getBuffer()+"bar bar");
                completeOperation.addCompletionCandidate(completeOperation.getBuffer()+"bar baar");
            }
        });

        Size termSize = new Size(10, 10);
        TestConnection term =
                new TestConnection(EditModeBuilder.builder().create(), completions, termSize);

        term.read("ff");
        term.read(Key.CTRL_I);
        term.assertBuffer("ffoo ");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("11111111111f");
        term.read(Key.CTRL_I);
        term.assertBuffer("11111111111foo ");
    }

    @Test
    public void testCompletionDoNotMatchBuffer() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if(completeOperation.getBuffer().endsWith("f")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.setOffset(2);
            }
            else if(completeOperation.getBuffer().endsWith("foo")) {
                completeOperation.addCompletionCandidate("foo bar");
                completeOperation.setOffset(completeOperation.getCursor()-3);
            }
            else if(completeOperation.getBuffer().endsWith("b")) {
                completeOperation.addCompletionCandidate("bar bar");
                completeOperation.addCompletionCandidate("bar baar");
                completeOperation.setOffset(completeOperation.getCursor()-1);
            }
        });

        TestConnection term = new TestConnection(completions);

        term.read("oof");
        term.read(Key.CTRL_I);
        term.assertBuffer("oofoo ");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("bab");
        term.read(Key.CTRL_I);
        term.assertBuffer("babar\\ ba");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("foo foo");
        term.read(Key.CTRL_I);
        term.assertBuffer("foo foo bar ");
    }

    @Test
    public void testCompletionOnMultiline() {
        List<Completion> completions = new ArrayList<>();
        completions.add(completeOperation -> {
            if(completeOperation.getBuffer().endsWith("f")) {
                completeOperation.addCompletionCandidate("foo");
                completeOperation.setOffset(completeOperation.getCursor()-1);
            }
            else if(completeOperation.getBuffer().endsWith("foo")) {
                completeOperation.addCompletionCandidate("foo bar");
                completeOperation.setOffset(completeOperation.getCursor()-3);
            }
            else if(completeOperation.getBuffer().endsWith("b")) {
                completeOperation.addCompletionCandidate("bar bar");
                completeOperation.addCompletionCandidate("bar baar");
                completeOperation.setOffset(completeOperation.getCursor()-1);
            }
        });

        TestConnection term = new TestConnection(completions);

        term.read("fooish \\\noof");
        term.read(Key.CTRL_I);
        term.assertBuffer("fooish oofoo ");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("bar bar \\\n bab");
        term.read(Key.CTRL_I);
        term.assertBuffer("bar bar  babar\\ ba");
        term.read(Key.ENTER);
        term.readline(completions);
        term.read("foo \\\n foo \\\nfoo");
        term.read(Key.CTRL_I);
        term.assertBuffer("foo  foo foo bar ");

    }
}
