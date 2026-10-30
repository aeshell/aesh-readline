/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag
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

import java.util.ArrayList;
import java.util.List;

import org.aesh.readline.completion.Completion;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

/**
 * Tests for the replacingCurrentWord completion flag, used by completion
 * styles where the typed word is not a prefix of the candidates
 * (e.g. fuzzy matching).
 */
public class CompletionReplaceWordTest {

    @Test
    public void testSingleCandidateReplacesTypedWord() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().equals("comit")) {
                co.addCompletionCandidate("commit");
                co.setOffset(co.getCursor());
                co.setReplacingCurrentWord(true);
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("comit".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("commit ");
    }

    @Test
    public void testSingleCandidateReplacesWordAfterOtherWords() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().equals("git comit")) {
                co.addCompletionCandidate("commit");
                co.setOffset(co.getCursor());
                co.setReplacingCurrentWord(true);
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("git comit".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("git commit ");
    }

    @Test
    public void testSingleCandidateReplacesDashPrefixedWord() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().equals("commit --amnd")) {
                co.addCompletionCandidate("--amend");
                co.setOffset(co.getCursor());
                co.setReplacingCurrentWord(true);
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("commit --amnd".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("commit --amend ");
    }

    @Test
    public void testMultipleCandidatesAreListedWithoutPrefixInsert() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().equals("comit")) {
                co.addCompletionCandidate("commit");
                co.addCompletionCandidate("compiler");
                co.setOffset(co.getCursor());
                co.setReplacingCurrentWord(true);
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("comit".getBytes());
        term.read(Key.CTRL_I);
        term.assertOutputBuffer(": comit" + Config.getLineSeparator()
                + "commit  compiler  " + Config.getLineSeparator() + ": comit");
        term.clearOutputBuffer();
        term.read(Config.getLineSeparator());
        term.assertLine("comit");
    }

    @Test
    public void testDefaultBehaviorUnchangedForPrefixCompletion() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if (co.getBuffer().equals("com"))
                co.addCompletionCandidate("commit");
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        term.read("com".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("commit ");
    }
}
