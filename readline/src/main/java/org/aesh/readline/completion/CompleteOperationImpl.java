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

import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.utils.Parser;

/**
 * A payload object to store completion data
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class CompleteOperationImpl implements CompleteOperation {
    private String buffer;
    private int cursor;
    private int offset;
    private List<TerminalString> completionCandidates;
    private boolean trimmed = false;
    private boolean ignoreStartsWith = false;
    private String nonTrimmedBuffer;
    private boolean ignoreNonEscapedSpace = false;

    private char separator = ' ';
    private boolean appendSeparator = true;
    private boolean ignoreOffset = false;

    /**
     * Creates a new CompleteOperationImpl with the given buffer and cursor position.
     *
     * @param buffer the input buffer to complete
     * @param cursor the cursor position in the buffer
     */
    public CompleteOperationImpl(String buffer, int cursor) {
        setCursor(cursor);
        setSeparator(' ');
        setAppendSeparator(true);
        completionCandidates = new ArrayList<>();
        setBuffer(buffer);
    }

    @Override
    public String getBuffer() {
        return buffer;
    }

    private void setBuffer(String buffer) {
        //if buffer is longer than cursor it means we try to complete
        //with the cursor not at the end of the buffer, if so we crop away
        //what is beyond the cursor
        if (buffer != null && buffer.length() > cursor + 1) {
            trimmed = true;
            nonTrimmedBuffer = buffer;
            this.buffer = buffer.substring(0, cursor);
        }
        if (buffer != null && buffer.startsWith(" ")) {
            trimmed = true;
            this.buffer = Parser.trimInFront(buffer);
            nonTrimmedBuffer = buffer;
            setCursor(cursor - getTrimmedSize());
        }
        //make sure we dont forget to set the buffer
        if (this.buffer == null)
            this.buffer = buffer;
    }

    /**
     * Checks if the buffer has been trimmed.
     *
     * @return true if the buffer was trimmed, false otherwise
     */
    public boolean isTrimmed() {
        return trimmed;
    }

    /**
     * Returns the number of characters that were trimmed from the buffer.
     *
     * @return the difference in length between the non-trimmed and trimmed buffer
     */
    public int getTrimmedSize() {
        return nonTrimmedBuffer.length() - buffer.length();
    }

    /**
     * Returns the original non-trimmed buffer.
     *
     * @return the non-trimmed buffer string
     */
    public String getNonTrimmedBuffer() {
        return nonTrimmedBuffer;
    }

    @Override
    public int getCursor() {
        return cursor;
    }

    private void setCursor(int cursor) {
        if (cursor < 0)
            this.cursor = 0;
        else
            this.cursor = cursor;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public void setIgnoreOffset(boolean ignoreOffset) {
        this.ignoreOffset = ignoreOffset;
    }

    @Override
    public boolean isIgnoreOffset() {
        return ignoreOffset;
    }

    /**
     * Get the separator character, by default its space
     *
     * @return separator
     */
    @Override
    public char getSeparator() {
        return separator;
    }

    /**
     * By default the separator is one space char, but
     * it can be overridden here.
     *
     * @param separator separator
     */
    @Override
    public void setSeparator(char separator) {
        this.separator = separator;
    }

    @Override
    public boolean isAppendSeparator() {
        return appendSeparator;
    }

    @Override
    public void setAppendSeparator(boolean appendSeparator) {
        this.appendSeparator = appendSeparator;
    }

    @Override
    public List<TerminalString> getCompletionCandidates() {
        return completionCandidates;
    }

    /**
     * Sets the completion candidates from a list of strings.
     *
     * @param completionCandidates the list of completion candidate strings
     */
    public void setCompletionCandidates(List<String> completionCandidates) {
        addCompletionCandidates(completionCandidates);
    }

    /**
     * Sets the completion candidates from a list of TerminalString objects.
     *
     * @param completionCandidates the list of TerminalString completion candidates
     */
    public void setCompletionCandidatesTerminalString(List<TerminalString> completionCandidates) {
        this.completionCandidates = completionCandidates;
    }

    @Override
    public void addCompletionCandidate(TerminalString completionCandidate) {
        this.completionCandidates.add(completionCandidate);
    }

    @Override
    public void addCompletionCandidate(String completionCandidate) {
        addStringCandidate(completionCandidate);
    }

    @Override
    public void addCompletionCandidates(List<String> completionCandidates) {
        addStringCandidates(completionCandidates);
    }

    @Override
    public void addCompletionCandidatesTerminalString(List<TerminalString> completionCandidates) {
        this.completionCandidates.addAll(completionCandidates);
    }

    @Override
    public void removeEscapedSpacesFromCompletionCandidates() {
        Parser.switchEscapedSpacesToSpacesInTerminalStringList(getCompletionCandidates());
    }

    private void addStringCandidate(String completionCandidate) {
        this.completionCandidates.add(new TerminalString(completionCandidate, true));
    }

    private void addStringCandidates(List<String> completionCandidates) {
        for (String s : completionCandidates)
            addStringCandidate(s);
    }

    @Override
    public List<String> getFormattedCompletionCandidates() {
        List<String> fixedCandidates = new ArrayList<>(completionCandidates.size());
        for (TerminalString c : completionCandidates) {
            if (!ignoreOffset && offset < cursor) {
                int pos = cursor - offset;
                if (c.getCharacters().length() >= pos)
                    fixedCandidates.add(c.getCharacters().substring(pos));
                else
                    fixedCandidates.add("");
            } else {
                fixedCandidates.add(c.getCharacters());
            }
        }
        return fixedCandidates;
    }

    @Override
    public List<TerminalString> getFormattedCompletionCandidatesTerminalString() {
        List<TerminalString> fixedCandidates = new ArrayList<>(completionCandidates.size());
        for (TerminalString c : completionCandidates) {
            if (!ignoreOffset && offset < cursor) {
                int pos = cursor - offset;
                if (c.getCharacters().length() >= pos) {
                    c.setCharacters(c.getCharacters().substring(pos));
                    fixedCandidates.add(c);
                } else
                    fixedCandidates.add(new TerminalString("", true));
            } else {
                fixedCandidates.add(c);
            }
        }
        return fixedCandidates;
    }

    @Override
    public String getFormattedCompletion(String completion) {
        if (offset < cursor) {
            int pos = cursor - offset;
            if (completion.length() > pos)
                return completion.substring(pos);
            else
                return "";
        } else
            return completion;
    }

    @Override
    public boolean isIgnoreStartsWith() {
        return ignoreStartsWith;
    }

    @Override
    public void setIgnoreStartsWith(boolean ignoreStartsWith) {
        this.ignoreStartsWith = ignoreStartsWith;
    }

    @Override
    public boolean isIgnoreNonEscapedSpace() {
        return ignoreNonEscapedSpace;
    }

    @Override
    public void setIgnoreNonEscapedSpace(boolean ignoreNonEscapedSpace) {
        this.ignoreNonEscapedSpace = ignoreNonEscapedSpace;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Buffer: ").append(buffer)
                .append(", Cursor:").append(cursor)
                .append(", Offset:").append(offset)
                .append(", IgnoreOffset:").append(ignoreOffset)
                .append(", Append separator: ").append(appendSeparator)
                .append(", Candidates:").append(completionCandidates);

        return sb.toString();
    }

}
