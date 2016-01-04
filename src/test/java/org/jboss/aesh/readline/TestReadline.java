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
package org.jboss.aesh.readline;

import org.jboss.aesh.readline.completion.Completion;
import org.jboss.aesh.terminal.Key;
import org.jboss.aesh.tty.TestConnection;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TestReadline {

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
}
