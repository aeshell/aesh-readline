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
package org.aesh.readline.action.mappings;

import org.aesh.readline.history.SearchDirection;
import org.aesh.terminal.utils.Parser;

/**
 * Search backward through the history starting at the current line
 * and moving 'up' through the history as necessary using a
 * non-incremental search for a string supplied by the user.
 */
public class NonIncrementalReverseSearchHistory extends NonIncrementalSearchHistory {

    private static final int[] PROMPT = Parser.toCodePoints("(reverse-search): ");

    public NonIncrementalReverseSearchHistory() {
        super(SearchDirection.REVERSE, PROMPT);
    }

    @Override
    public String name() {
        return "non-incremental-reverse-search-history";
    }
}
