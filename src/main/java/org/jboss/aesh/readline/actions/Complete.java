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

import org.jboss.aesh.readline.ConsoleBuffer;
import org.jboss.aesh.readline.InputProcessor;
import org.jboss.aesh.readline.Action;
import org.jboss.aesh.readline.ActionEvent;
import org.jboss.aesh.readline.KeyAction;
import org.jboss.aesh.terminal.Key;
import org.jboss.aesh.util.Config;
import org.jboss.aesh.util.LoggerUtil;

import java.util.logging.Logger;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Complete implements ActionEvent {

    private boolean askForCompletion = false;
    private KeyAction key;

    private static Logger LOGGER = LoggerUtil.getLogger(Complete.class.getName());

    @Override
    public String name() {
        return "complete";
    }

    @Override
    public void apply(InputProcessor inputProcessor) {
        if(askForCompletion) {
            askForCompletion = false;
            if(key == Key.y) {
                //inputProcessor.getBuffer().getCompleter().setAskDisplayCompletion(false);
                inputProcessor.getBuffer().getCompleter().complete(inputProcessor);
            }
            else {
                inputProcessor.getBuffer().getCompleter().setAskDisplayCompletion(false);
                inputProcessor.getBuffer().getUndoManager().clear();
                inputProcessor.getBuffer().writeOut(Config.CR);
                //clearBufferAndDisplayPrompt(inputProcessor.getBuffer());
                inputProcessor.getBuffer().drawLine();
                //inputProcessor.getBuffer().moveCursor(inputProcessor.getBuffer().getBuffer().getMultiCursor());
            }
        }
        else {
            if(inputProcessor.getBuffer().getCompleter() != null) {
                LOGGER.info("trying to complete...");
                inputProcessor.getBuffer().getCompleter().complete( inputProcessor);
                if(inputProcessor.getBuffer().getCompleter().doAskDisplayCompletion()) {
                    askForCompletion = true;
                }
            }
        }
    }

    @Override
    public void input(Action action, KeyAction key) {
        if(askForCompletion) {
            this.key = key;
        }
    }

    @Override
    public boolean keepFocus() {
        return askForCompletion;
    }

    /*
    private void clearBufferAndDisplayPrompt(ConsoleBuffer consoleBuffer) {
        consoleBuffer.getBuffer().reset();
        consoleBuffer.getUndoManager().clear();
    }
    */
}
