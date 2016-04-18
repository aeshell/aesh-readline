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
package org.jboss.aesh.readline.actions;

import org.jboss.aesh.readline.InputProcessor;
import org.jboss.aesh.readline.Action;
import org.jboss.aesh.readline.KeyAction;
import org.jboss.aesh.readline.SearchAction;
import org.jboss.aesh.readline.history.SearchDirection;
import org.jboss.aesh.terminal.Key;
import org.jboss.aesh.util.ANSI;
import org.jboss.aesh.util.LoggerUtil;

import java.util.logging.Logger;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
abstract class SearchHistory implements SearchAction {

    private SearchAction.Status status = Status.SEARCH_NOT_STARTED;
    private StringBuilder searchArgument;
    private String searchResult;
    private SearchAction.Status defaultAction;

    private static final Logger LOGGER = LoggerUtil.getLogger(SearchHistory.class.getName());

    SearchHistory(SearchAction.Status defaultAction) {
        this.defaultAction = defaultAction;
    }

     @Override
    public void input(Action action, KeyAction key) {
         if(action == null && Key.isPrintable(key.buffer().array())) {
             if(searchArgument == null)
                 searchArgument = new StringBuilder();
             status = defaultAction;
             searchArgument.append((char) key.buffer().array()[0]);
         }
         else if(action instanceof Interrupt) {
             status = Status.SEARCH_INTERRUPT;
         }
         else if(action instanceof Enter) {
             status = Status.SEARCH_END;
         }
         else if(action instanceof ReverseSearchHistory) {
             status = Status.SEARCH_PREV;
         }
         else if(action instanceof ForwardSearchHistory) {
             status = Status.SEARCH_NEXT;
         }
         else if(action instanceof DeletePrevChar) {
             status = Status.SEARCH_DELETE;
         }
         else if(action instanceof PrevHistory)
             status = Status.SEARCH_MOVE_PREV;
         else if(action instanceof NextHistory)
             status = Status.SEARCH_MOVE_NEXT;
         else if(action instanceof ForwardChar) {
             status = Status.SEARCH_MOVE_RIGHT;
         }
         else if(action instanceof BackwardChar)
             status = Status.SEARCH_MOVE_LEFT;
         else {
             if(key == Key.ESC) {
                 status = Status.SEARCH_EXIT;
             }
             if(Key.isPrintable(key.buffer().array())) {
                 if(searchArgument == null)
                     searchArgument = new StringBuilder();
                 status = defaultAction;
                 searchArgument.append((char) key.buffer().array()[0]);
             }
         }
    }

    @Override
    public boolean keepFocus() {
        return (status == Status.SEARCH_INPUT || status == Status.SEARCH_PREV ||
                status == Status.SEARCH_NEXT || status == Status.SEARCH_DELETE );
    }

   @Override
    public void apply(InputProcessor inputProcessor) {

       if(status == Status.SEARCH_INTERRUPT) {
           inputProcessor.getBuffer().setBufferLine("");
           searchArgument = null;
           searchResult = null;
       }
       else {
           switch(status) {
               case SEARCH_PREV:
                   if(inputProcessor.getBuffer().getHistory().getSearchDirection() != SearchDirection.REVERSE)
                       inputProcessor.getBuffer().getHistory().setSearchDirection(SearchDirection.REVERSE);
                   if(searchArgument != null && searchArgument.length() > 0) {
                       String tmpResult = inputProcessor.getBuffer().getHistory().search(searchArgument.toString());
                       if(tmpResult == null)
                           searchArgument.deleteCharAt(searchArgument.length()-1);
                       else
                           searchResult = tmpResult;
                   }
                   break;
               case SEARCH_NEXT:
                   if(inputProcessor.getBuffer().getHistory().getSearchDirection() != SearchDirection.FORWARD)
                       inputProcessor.getBuffer().getHistory().setSearchDirection(SearchDirection.FORWARD);
                   if(searchArgument != null && searchArgument.length() > 0) {
                       String tmpResult = inputProcessor.getBuffer().getHistory().search(searchArgument.toString());
                       if(tmpResult == null)
                           searchArgument.deleteCharAt(searchArgument.length()-1);
                       else
                           searchResult = tmpResult;
                   }
                   break;
               case SEARCH_NOT_STARTED:
                   status = Status.SEARCH_PREV;
                   inputProcessor.getBuffer().getHistory().setSearchDirection(SearchDirection.REVERSE);
                   if(inputProcessor.getBuffer().getBuffer().length() > 0) {
                       searchArgument = new StringBuilder( inputProcessor.getBuffer().getBuffer().getAsString());
                       searchResult = inputProcessor.getBuffer().getHistory().search(searchArgument.toString());
                   }
                   break;
               case SEARCH_DELETE:
                   if(searchArgument != null && searchArgument.length() > 0) {
                       searchArgument.deleteCharAt(searchArgument.length() - 1);
                       searchResult = inputProcessor.getBuffer().getHistory().search(searchArgument.toString());
                   }
                   break;
               case SEARCH_END:
                   if(searchResult != null) {
                       inputProcessor.getBuffer().moveCursor(-inputProcessor.getBuffer().getBuffer().getMultiCursor());
                       inputProcessor.getBuffer().setBufferLine( searchResult);
                       inputProcessor.getBuffer().getHistory().push(inputProcessor.getBuffer().getBuffer().getLine());
                       inputProcessor.getBuffer().getBuffer().reset();
                       inputProcessor.setReturnValue(searchResult);
                       break;
                   }
                   else {
                       inputProcessor.getBuffer().moveCursor(-inputProcessor.getBuffer().getBuffer().getMultiCursor());
                       inputProcessor.getBuffer().setBufferLine("");
                   }
                   break;
               case SEARCH_EXIT:
                   if(searchResult != null) {
                       inputProcessor.getBuffer().setBufferLine(searchResult);
                   }
                   else {
                       inputProcessor.getBuffer().setBufferLine("");
                   }
                   break;
               case SEARCH_MOVE_NEXT:
                   searchResult = inputProcessor.getBuffer().getHistory().getNextFetch();
                   inputProcessor.getBuffer().setBufferLine(searchResult);
                   break;
               case SEARCH_MOVE_PREV:
                   searchResult = inputProcessor.getBuffer().getHistory().getPreviousFetch();
                   inputProcessor.getBuffer().setBufferLine(searchResult);
                   break;
               case SEARCH_MOVE_RIGHT:
                   inputProcessor.getBuffer().setBufferLine(searchResult);
               case SEARCH_MOVE_LEFT:
                   inputProcessor.getBuffer().setBufferLine(searchResult);
           }

           if(!keepFocus()) {
               searchArgument = null;
               searchResult = null;
               if(status != Status.SEARCH_END) {
                   //inputProcessor.getBuffer().drawLine(false);
                   moveCursorAtExit(inputProcessor);
               }
           }
           else {
               if(searchArgument == null || searchArgument.length() == 0) {
                   if(searchResult != null)
                       printSearch("", searchResult, inputProcessor);
                   else
                       printSearch("", "", inputProcessor);
               }
               else {
                   if(searchResult != null && searchResult.length() > 0)
                       printSearch(searchArgument.toString(), searchResult, inputProcessor);
               }
           }
       }
    }

    private void printSearch(String searchTerm, String result, InputProcessor inputProcessor) {
        //cursor should be placed at the index of searchTerm
        int cursor = result.indexOf(searchTerm);

        StringBuilder builder;
        if(inputProcessor.getBuffer().getHistory().getSearchDirection() == SearchDirection.REVERSE)
            builder = new StringBuilder("(reverse-i-search) `");
        else
            builder = new StringBuilder("(forward-i-search) `");
        builder.append(searchTerm).append("': ");
        cursor += builder.length();
        //LOGGER.info("setting cursor to: "+cursor);
        builder.append(result);
        inputProcessor.getBuffer().getBuffer().disablePrompt(true);
        inputProcessor.getBuffer().moveCursor(-inputProcessor.getBuffer().getBuffer().getMultiCursor());
        inputProcessor.getBuffer().writeOut(ANSI.CURSOR_START);
        inputProcessor.getBuffer().writeOut(ANSI.ERASE_WHOLE_LINE);
        inputProcessor.getBuffer().setBufferLine(builder.toString());
        //inputProcessor.getBuffer().drawLine(false, false);
        //LOGGER.info("moving to: "+cursor);
        inputProcessor.getBuffer().moveCursor(cursor-inputProcessor.getBuffer().getBuffer().getMultiCursor());
        inputProcessor.getBuffer().getBuffer().disablePrompt(false);
    }

    //TODO: depending on specific actions, the cursor should be moved to a correct spot
    private void moveCursorAtExit(InputProcessor inputProcessor) {
        if(status == Status.SEARCH_MOVE_RIGHT)
            inputProcessor.getBuffer().moveCursor(inputProcessor.getBuffer().getBuffer().length());
    }

 }
