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
package org.aesh.readline.completion;

import org.aesh.readline.terminal.formatting.TerminalString;

import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface CompleteOperation {

    String getBuffer();

    int getCursor();

    int getOffset();

    void setOffset(int offset);

    void setIgnoreOffset(boolean ignoreOffset);

    boolean doIgnoreOffset();

    /**
     * Get the separator character, by default its space
     *
     * @return separator
     */
    char getSeparator();

    /**
     * By default the separator is one space char, but
     * it can be overridden here.
     *
     * @param separator separator
     */
    void setSeparator(char separator);

    /**
     * Do this completion allow for appending a separator
     * after completion? By default this is true.
     *
     * @return appendSeparator
     */
    boolean hasAppendSeparator();

    /**
     * Set if this CompletionOperation would allow an separator to
     * be appended. By default this is true.
     *
     * @param appendSeparator appendSeparator
     */
    void doAppendSeparator(boolean appendSeparator);

    List<TerminalString> getCompletionCandidates();

    void addCompletionCandidate(TerminalString completionCandidate);

    void addCompletionCandidate(String completionCandidate);

    void addCompletionCandidates(List<String> completionCandidates);

    void addCompletionCandidatesTerminalString(List<TerminalString> completionCandidates);

    void removeEscapedSpacesFromCompletionCandidates();

    List<String> getFormattedCompletionCandidates();

    List<TerminalString> getFormattedCompletionCandidatesTerminalString();

    String getFormattedCompletion(String completion);

    boolean isIgnoreStartsWith();

    void setIgnoreStartsWith(boolean ignoreStartsWith);

    boolean doIgnoreNonEscapedSpace();

    void setIgnoreNonEscapedSpace(boolean ignoreNonEscapedSpace);
}
