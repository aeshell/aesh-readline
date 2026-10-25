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

import java.util.List;

import org.aesh.terminal.formatting.TerminalString;

/**
 * Interface representing a completion operation with candidates and configuration options.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface CompleteOperation {

    /**
     * Get the input buffer string.
     *
     * @return the buffer content
     */
    String getBuffer();

    /**
     * Get the current cursor position.
     *
     * @return the cursor position
     */
    int getCursor();

    /**
     * Get the offset for completion.
     *
     * @return the offset
     */
    int getOffset();

    /**
     * Set the offset for completion.
     *
     * @param offset the offset value
     */
    void setOffset(int offset);

    /**
     * Set whether to ignore the offset.
     *
     * @param ignoreOffset true to ignore offset
     */
    void setIgnoreOffset(boolean ignoreOffset);

    /**
     * Check if offset should be ignored.
     *
     * @return true if offset is ignored
     */
    boolean isIgnoreOffset();

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
     * Check if a separator should be appended after completion.
     * By default this is true.
     *
     * @return true if separator should be appended
     */
    boolean isAppendSeparator();

    /**
     * Set if this CompletionOperation would allow a separator to
     * be appended. By default this is true.
     *
     * @param appendSeparator whether to append separator
     */
    void setAppendSeparator(boolean appendSeparator);

    /**
     * Get the list of completion candidates.
     *
     * @return the completion candidates
     */
    List<TerminalString> getCompletionCandidates();

    /**
     * Add a completion candidate.
     *
     * @param completionCandidate the candidate to add
     */
    void addCompletionCandidate(TerminalString completionCandidate);

    /**
     * Add a completion candidate as a string.
     *
     * @param completionCandidate the candidate to add
     */
    void addCompletionCandidate(String completionCandidate);

    /**
     * Add multiple completion candidates.
     *
     * @param completionCandidates the candidates to add
     */
    void addCompletionCandidates(List<String> completionCandidates);

    /**
     * Add multiple completion candidates as TerminalStrings.
     *
     * @param completionCandidates the candidates to add
     */
    void addCompletionCandidatesTerminalString(List<TerminalString> completionCandidates);

    /**
     * Remove escaped spaces from all completion candidates.
     */
    void removeEscapedSpacesFromCompletionCandidates();

    /**
     * Get the formatted completion candidates as strings.
     *
     * @return the formatted candidates
     */
    List<String> getFormattedCompletionCandidates();

    /**
     * Get the formatted completion candidates as TerminalStrings.
     *
     * @return the formatted candidates
     */
    List<TerminalString> getFormattedCompletionCandidatesTerminalString();

    /**
     * Get the formatted version of a completion string.
     *
     * @param completion the completion to format
     * @return the formatted completion
     */
    String getFormattedCompletion(String completion);

    /**
     * Check if starts-with matching should be ignored.
     *
     * @return true if starts-with is ignored
     */
    boolean isIgnoreStartsWith();

    /**
     * Set whether to ignore starts-with matching.
     *
     * @param ignoreStartsWith true to ignore starts-with
     */
    void setIgnoreStartsWith(boolean ignoreStartsWith);

    /**
     * Check if non-escaped spaces should be ignored.
     *
     * @return true if non-escaped spaces are ignored
     */
    boolean isIgnoreNonEscapedSpace();

    /**
     * Set whether non-escaped spaces should be ignored.
     *
     * @param ignoreNonEscapedSpace true to ignore non-escaped spaces
     */
    void setIgnoreNonEscapedSpace(boolean ignoreNonEscapedSpace);

    /**
     * Check whether the current word should be replaced by the completion
     * candidates instead of being extended by them.
     * <p>
     * When true, a single candidate removes the current word before the
     * full candidate is written, and common-prefix insertion is skipped in
     * favour of listing the candidates. This supports completion styles
     * where the typed word is not a prefix of the candidates, such as
     * fuzzy matching.
     *
     * @return true if the current word should be replaced
     */
    default boolean isReplacingCurrentWord() {
        return false;
    }

    /**
     * Set whether the current word should be replaced by the completion
     * candidates instead of being extended by them.
     *
     * @param replacingCurrentWord true to replace the current word
     */
    default void setReplacingCurrentWord(boolean replacingCurrentWord) {
    }
}
