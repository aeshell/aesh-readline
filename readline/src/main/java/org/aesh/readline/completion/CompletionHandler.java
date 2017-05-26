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

import org.aesh.readline.Buffer;
import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.mappings.ActionMapper;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.aesh.utils.Config;
import org.aesh.util.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public abstract class CompletionHandler<C extends CompleteOperation> {

    private CompletionStatus status = CompletionStatus.COMPLETE;
    private int displayCompletionSize = 100;
    private final List<Completion> completionList;
    private Function<Buffer, C> aliasHandler;

    public CompletionHandler() {
        completionList = new ArrayList<>();
    }

    public void addCompletion(Completion completion) {
        completionList.add(completion);
    }

    public void removeCompletion(Completion completion) {
        completionList.remove(completion);
    }

    public void clear() {
        completionList.clear();
    }

    public CompletionStatus completionStatus() {
        return status;
    }

    public void setCompletionStatus(CompletionStatus status) {
        this.status = status;
    }

    public void setAskCompletionSize(int size) {
        displayCompletionSize = size;
    }

    public int getAskCompletionSize() {
        return displayCompletionSize;
    }

    public void setAliasHandler(Function<Buffer, C> aliasHandler) {
        this.aliasHandler = aliasHandler;
    }

    public void addCompletions(List<Completion> completions) {
        if(completions != null && completions.size() > 0)
            this.completionList.addAll(completions);
    }

    public abstract C createCompleteOperation(String buffer, int cursor);

    /**
     * Display possible completions.
     * 1. Find all possible completions
     * 2. If we find only one, display it.
     * 3. If we find more than one, display them,
     *    but not more than 100 at once
     */
    public void complete(InputProcessor inputProcessor) {
        if(completionList.size() == 0)
            return;
        Buffer buffer = inputProcessor.buffer().buffer();

        if(completionList.size() < 1)
            return;

        List<C> possibleCompletions = createCompletionList(buffer);

        //LOGGER.info("Found completions: "+possibleCompletions);

        if(possibleCompletions.size() == 0) {
            //do nothing
        }
        // only one hit, do a completion
        else if(possibleCompletions.size() == 1 &&
                possibleCompletions.get(0).getCompletionCandidates().size() == 1) {
            //some formatted completions might not be valid and should not be displayed
            displayCompletion(
                    possibleCompletions.get(0).getFormattedCompletionCandidatesTerminalString().get(0),
                    buffer, inputProcessor,
                    possibleCompletions.get(0).hasAppendSeparator(),
                    possibleCompletions.get(0).getSeparator());
        }
        // more than one hit...
        else {
            processMultipleCompletions(possibleCompletions, buffer, inputProcessor);
        }
    }

    private List<C> createCompletionList(Buffer buffer) {
        List<C> possibleCompletions = new ArrayList<>();
        for(int i=0; i < completionList.size(); i++) {
            final C co;
            if(aliasHandler == null)
                co = createCompleteOperation(buffer.asString(), buffer.multiCursor());
            else
                co = aliasHandler.apply(buffer);

            completionList.get(i).complete(co);

            if(co.getCompletionCandidates() != null && co.getCompletionCandidates().size() > 0)
                possibleCompletions.add(co);
        }
        return possibleCompletions;
    }

    private void processMultipleCompletions(List<C> possibleCompletions, Buffer buffer, InputProcessor inputProcessor) {
        String startsWith = "";

        if(!possibleCompletions.get(0).isIgnoreStartsWith())
            startsWith = Parser.findStartsWithOperation(possibleCompletions);

        if(startsWith.length() > 0 ) {
            if(startsWith.contains(" ") && !possibleCompletions.get(0).doIgnoreNonEscapedSpace())
                displayCompletion(new TerminalString(Parser.switchSpacesToEscapedSpacesInWord(startsWith), true),
                        buffer, inputProcessor,
                        false, possibleCompletions.get(0).getSeparator());
            else
                displayCompletion(new TerminalString(startsWith, true), buffer, inputProcessor,
                        false, possibleCompletions.get(0).getSeparator());
        }
        // display all
        // check size
        else {
            List<TerminalString> completions = new ArrayList<>();
            for(int i=0; i < possibleCompletions.size(); i++)
                completions.addAll(possibleCompletions.get(i).getCompletionCandidates());

            if(completions.size() > 100) {
                if(status == CompletionStatus.ASKING_FOR_COMPLETIONS) {
                    displayCompletions(completions, buffer, inputProcessor);
                    status = CompletionStatus.COMPLETE;
                }
                else {
                    status = CompletionStatus.ASKING_FOR_COMPLETIONS;
                    inputProcessor.buffer().writeOut(Config.CR);
                    inputProcessor.buffer().writeOut("Display all " + completions.size() + " possibilities? (y or n)");
                }
            }
            // display all
            else {
                displayCompletions(completions, buffer, inputProcessor);
            }
        }
    }

    /**
     * Display the completion string in the terminal.
     * If !completion.startsWith(buffer.getLine()) the completion will be added to the line,
     * else it will replace whats at the buffer line.
     *
     * @param completion partial completion
     * @param appendSpace if its an actual complete
     */
    private void displayCompletion(TerminalString completion, Buffer buffer, InputProcessor inputProcessor,
                                   boolean appendSpace, char separator) {
        if(completion.getCharacters().startsWith(buffer.asString())) {
            ActionMapper.mapToAction("backward-kill-word").accept(inputProcessor);
            inputProcessor.buffer().writeString(completion.toString());
        }
        else {
            inputProcessor.buffer().writeString(completion.toString());
        }
        if(appendSpace) {
            inputProcessor.buffer().writeChar(separator);
        }
    }

    /**
     * Display all possible completions
     *
     * @param completions all completion items
     */
    private void displayCompletions(List<TerminalString> completions, Buffer buffer,
                                    InputProcessor inputProcessor) {
        Collections.sort(completions);

        inputProcessor.buffer().writeOut(Config.CR);
        inputProcessor.buffer().writeOut(Parser.formatDisplayListTerminalString(completions,
                inputProcessor.buffer().size().getHeight(), inputProcessor.buffer().size().getWidth()));

        buffer.setIsPromptDisplayed(false);
        buffer.invalidateCursorLocation();
        inputProcessor.buffer().drawLine();
    }

    public enum CompletionStatus {
        ASKING_FOR_COMPLETIONS, COMPLETE;
    }
}
