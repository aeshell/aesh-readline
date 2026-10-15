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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aesh.readline.completion.Completion;
import org.aesh.readline.completion.impl.CompletionHandler;
import org.aesh.readline.completion.impl.SimpleCompletionHandler;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.prompt.Prompt;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

/**
 * Verifies the {@link CompletionHandler} extension seam: overriding only
 * {@code displayCompletions} reroutes list rendering while gathering,
 * common-prefix insertion, single-hit insertion, and the ask-flow stay
 * inherited.
 */
public class CompletionHandlerOverrideTest {

    /**
     * Records rendered candidates instead of printing the sorted grid.
     */
    private static class RecordingCompletionHandler extends SimpleCompletionHandler {
        final List<List<String>> displayed = new ArrayList<>();

        @Override
        protected void displayCompletions(List<TerminalString> completions, Buffer buffer,
                InputProcessor inputProcessor) {
            List<String> texts = new ArrayList<>();
            for (TerminalString candidate : completions)
                texts.add(candidate.toString());
            displayed.add(texts);
        }
    }

    private static TestReadlineConnection connection(RecordingCompletionHandler handler,
            List<Completion> completions) {
        TestReadline readline = new TestReadline(EditModeBuilder.builder().build(), handler);
        TestReadlineConnection term = new TestReadlineConnection(readline,
                EditModeBuilder.builder().build(), null, new Size(80, 24), new Prompt(": "));
        term.readline(completions);
        return term;
    }

    private static List<Completion> candidates(String... names) {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().trim().isEmpty()) {
                for (String name : names)
                    co.addCompletionCandidate(name);
            }
        });
        return completions;
    }

    @Test
    public void testDisplayOverrideReceivesMultiHit() {
        RecordingCompletionHandler handler = new RecordingCompletionHandler();
        TestReadlineConnection term = connection(handler, candidates("charlie", "alpha", "bravo"));

        term.read(Key.CTRL_I);

        assertEquals(1, handler.displayed.size());
        List<String> shown = new ArrayList<>(handler.displayed.get(0));
        Collections.sort(shown);
        assertEquals(java.util.Arrays.asList("alpha", "bravo", "charlie"), shown);
        assertEquals(CompletionHandler.CompletionStatus.COMPLETE, handler.completionStatus());
    }

    @Test
    public void testSingleHitInsertionInherited() {
        RecordingCompletionHandler handler = new RecordingCompletionHandler();
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().equals("foo"))
                co.addCompletionCandidate("foobar");
        });
        TestReadlineConnection term = connection(handler, completions);

        term.read("foo".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("foobar ");

        assertTrue("single-hit must not route through the list override",
                handler.displayed.isEmpty());
    }

    @Test
    public void testAskFlowConfirmationReachesOverride() {
        RecordingCompletionHandler handler = new RecordingCompletionHandler();
        handler.setAskCompletionSize(2);
        assertEquals(100, new RecordingCompletionHandler().getAskCompletionSize());
        TestReadlineConnection term = connection(handler, candidates("alpha", "bravo", "charlie"));

        term.read(Key.CTRL_I);
        assertEquals(CompletionHandler.CompletionStatus.ASKING_FOR_COMPLETIONS,
                handler.completionStatus());
        assertTrue(handler.displayed.isEmpty());
        term.assertOutputBufferEndsWith("Display all 3 possibilities? (y or n)");

        term.read("y");
        assertEquals(1, handler.displayed.size());
        assertEquals(3, handler.displayed.get(0).size());
        assertEquals(CompletionHandler.CompletionStatus.COMPLETE, handler.completionStatus());
    }
}
