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
package org.jboss.aesh.readline.completion;

import org.jboss.aesh.readline.Buffer;
import org.jboss.aesh.readline.InputProcessor;
import org.jboss.aesh.parser.Parser;
import org.jboss.aesh.readline.actions.ActionMapper;
import org.jboss.aesh.terminal.formatting.TerminalString;
import org.jboss.aesh.util.Config;
import org.jboss.aesh.util.LoggerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SimpleCompletionHandler implements CompletionHandler {

    private boolean askDisplayCompletion = false;
    private int displayCompletionSize = 100;
    private final List<Completion> completionList;
    private Function<Buffer, CompleteOperation> aliasHandler;

    private static final Logger LOGGER = LoggerUtil.getLogger(SimpleCompletionHandler.class.getName());

    public SimpleCompletionHandler() {
        completionList = new ArrayList<>();
    }

    @Override
    public void addCompletion(Completion completion) {
        completionList.add(completion);
    }

    @Override
    public void removeCompletion(Completion completion) {
        completionList.remove(completion);
    }

    @Override
    public void clear() {
        completionList.clear();
    }

    @Override
    public boolean doAskDisplayCompletion() {
        return askDisplayCompletion;
    }

    @Override
    public void setAskDisplayCompletion(boolean askDisplayCompletion) {
        this.askDisplayCompletion = askDisplayCompletion;
    }

    @Override
    public void setAskCompletionSize(int size) {
        displayCompletionSize = size;
    }

    @Override
    public int getAskCompletionSize() {
        return displayCompletionSize;
    }

    @Override
    public void setAliasHandler(Function<Buffer, CompleteOperation> aliasHandler) {
        this.aliasHandler = aliasHandler;
    }

    @Override
    public void addCompletions(List<Completion> completions) {
        if(completions != null && completions.size() > 0)
            this.completionList.addAll(completions);
    }

    /**
     * Display possible completions.
     * 1. Find all possible completions
     * 2. If we find only one, display it.
     * 3. If we find more than one, display them,
     *    but not more than 100 at once
     *
     * @throws IOException stream
     */
    @Override
    public void complete(InputProcessor inputProcessor) {
        if(completionList.size() == 0)
            return;
        Buffer buffer = inputProcessor.getBuffer().getBuffer();

        if(completionList.size() < 1)
            return;

        List<CompleteOperation> possibleCompletions = new ArrayList<>();
        for(int i=0; i < completionList.size(); i++) {

            final CompleteOperation co;
            if(aliasHandler == null)
            co = new CompleteOperation(buffer.asString(), buffer.getCursor());
            else
                co = aliasHandler.apply(buffer);

            completionList.get(i).complete(co);

            if(co.getCompletionCandidates() != null && co.getCompletionCandidates().size() > 0)
                possibleCompletions.add(co);
        }

        LOGGER.info("Found completions: "+possibleCompletions);

        if(possibleCompletions.size() == 0) {
            //do nothing
        }
        // only one hit, do a completion
        else if(possibleCompletions.size() == 1 &&
                possibleCompletions.get(0).getCompletionCandidates().size() == 1) {
            //some formatted completions might not be valid and shouldnt be displayed
            displayCompletion(
                    possibleCompletions.get(0).getFormattedCompletionCandidatesTerminalString().get(0),
                    buffer, inputProcessor,
                    possibleCompletions.get(0).hasAppendSeparator(),
                    possibleCompletions.get(0).getSeparator());
        }
        // more than one hit...
        else {

            String startsWith = "";

            if(!possibleCompletions.get(0).isIgnoreStartsWith())
                startsWith = Parser.findStartsWithOperation(possibleCompletions);

            LOGGER.info("startsWith="+startsWith);
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
                    //if(displayCompletion) {
                     if(askDisplayCompletion) {
                        displayCompletions(completions, buffer, inputProcessor);
                        //displayCompletion = false;
                         askDisplayCompletion = false;
                    }
                    else {
                        askDisplayCompletion = true;
                         inputProcessor.getBuffer().writeOut(Config.CR);
                        inputProcessor.getBuffer().writeOut("Display all " + completions.size() + " possibilities? (y or n)");
                    }
                }
                // display all
                else {
                    displayCompletions(completions, buffer, inputProcessor);
                }
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
        LOGGER.info("completion: "+completion.getCharacters()+" and buffer: "+buffer.asString());
        if(completion.getCharacters().startsWith(buffer.asString())) {
            ActionMapper.mapToAction("backward-kill-word").apply(inputProcessor);
            //consoleBuffer.performAction(new PrevWordAction(buffer.getMultiCursor(), Action.DELETE, EditMode.Mode.EMACS));
            //buffer.write(completion.getCharacters());
            inputProcessor.getBuffer().writeString(completion.toString());

            //only append space if its an actual complete, not a partial
        }
        else {
            inputProcessor.getBuffer().writeString(completion.toString());
            //buffer.insert(completion.toString());
        }
        if(appendSpace) { // && fullCompletion.startsWith(buffer.getLine())) {
            inputProcessor.getBuffer().writeChar(separator);
            //buffer.write(separator);
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

        inputProcessor.getBuffer().writeOut(Config.CR);
        inputProcessor.getBuffer().writeOut(Parser.formatDisplayListTerminalString(completions,
                inputProcessor.getBuffer().getSize().getHeight(), inputProcessor.getBuffer().getSize().getWidth()));

        buffer.setIsPromptDisplayed(false);
        inputProcessor.getBuffer().drawLine();
    }

    /*
    private CompleteOperation findAliases(String buffer, int cursor) {
        if(aliasManager != null) {
            String command = Parser.findFirstWord(buffer);
            Alias alias = aliasManager.getAlias(command);
            if(alias != null) {
                return new CompleteOperation(aeshContext, alias.getValue()+buffer.substring(command.length()),
                        cursor+(alias.getValue().length()-command.length()));
            }
        }

        return new CompleteOperation(aeshContext, buffer, cursor);
    }
    */
}
