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
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.readline.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aesh.terminal.utils.Parser;
import org.junit.Test;

/**
 * Tests for {@link HistorySuggestionProvider}.
 */
public class HistorySuggestionProviderTest {

    @Test
    public void testPrefixMatch() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("mvn clean install"));
        history.push(Parser.toCodePoints("git status"));
        history.push(Parser.toCodePoints("mvn clean test -pl aesh"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        // Should match most recent "mvn" entry
        assertEquals("lean test -pl aesh", provider.suggest("mvn c"));
    }

    @Test
    public void testMostRecentWins() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("mvn clean install"));
        history.push(Parser.toCodePoints("mvn clean test"));
        history.push(Parser.toCodePoints("mvn clean deploy"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        // Most recent matching entry is "mvn clean deploy"
        assertEquals("lean deploy", provider.suggest("mvn c"));
    }

    @Test
    public void testNoMatch() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("git status"));
        history.push(Parser.toCodePoints("git push"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        assertNull(provider.suggest("mvn"));
    }

    @Test
    public void testExactMatchReturnsNull() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("git status"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        // Exact match should return null (nothing to suggest beyond what's typed)
        assertNull(provider.suggest("git status"));
    }

    @Test
    public void testEmptyBuffer() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("git status"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        assertNull(provider.suggest(""));
        assertNull(provider.suggest(null));
    }

    @Test
    public void testEmptyHistory() {
        InMemoryHistory history = new InMemoryHistory();
        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        assertNull(provider.suggest("git"));
    }

    @Test
    public void testSingleCharMatch() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("mvn clean test"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        assertEquals("vn clean test", provider.suggest("m"));
    }

    @Test
    public void testSuffixOnly() {
        InMemoryHistory history = new InMemoryHistory();
        history.push(Parser.toCodePoints("mvn clean test -Dtest=Foo"));

        HistorySuggestionProvider provider = new HistorySuggestionProvider(history);

        // Should return only the untyped suffix
        assertEquals(" -Dtest=Foo", provider.suggest("mvn clean test"));
    }
}
