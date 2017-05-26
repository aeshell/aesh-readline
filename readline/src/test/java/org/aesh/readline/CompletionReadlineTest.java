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
import org.aesh.readline.terminal.Key;
import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.utils.Config;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CompletionReadlineTest {

    @Test
    public void testCompletion() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            if(co.getBuffer().equals("foo"))
                co.addCompletionCandidate("foobar");
        });

        completions.add(co -> {
            if(co.getBuffer().equals("bar")) {
                co.addCompletionCandidate("barfoo");
                co.doAppendSeparator(false);
            }
        });

        completions.add( co -> {
            if(co.getBuffer().equals("le")) {
                co.addCompletionCandidate("less");
                co.setSeparator(':');
            }
        });

        TestConnection term = new TestConnection(completions);

        term.read("foo".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("foobar ");

        term.readline(completions);
        term.read("bar".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("barfoo");

        term.readline(completions);
        term.read("le".getBytes());
        term.read(Key.CTRL_I);
        term.read(Config.getLineSeparator());
        term.assertLine("less:");

    }
}
