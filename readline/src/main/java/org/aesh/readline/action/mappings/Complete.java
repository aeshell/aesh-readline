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

import org.aesh.readline.action.Action;
import org.aesh.readline.action.ActionEvent;
import org.aesh.readline.action.KeyAction;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.terminal.Key;
import org.aesh.utils.Config;
import org.aesh.readline.InputProcessor;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Complete implements ActionEvent {

    private boolean askForCompletion = false;
    private KeyAction key;

    @Override
    public String name() {
        return "complete";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        if(askForCompletion) {
            if(key == Key.y) {
                askForCompletion = false;
                key = null;
                inputProcessor.buffer().completer().complete(inputProcessor);
            }
            else if(key == Key.n){
                askForCompletion = false;
                key = null;
                inputProcessor.buffer().completer().setCompletionStatus(CompletionHandler.CompletionStatus.COMPLETE);
                inputProcessor.buffer().undoManager().clear();
                inputProcessor.buffer().writeOut(Config.CR);
                inputProcessor.buffer().drawLineForceDisplay();
            }
        }
        else {
            if(inputProcessor.buffer().completer() != null) {
                inputProcessor.buffer().completer().complete( inputProcessor);
                if(inputProcessor.buffer().completer().completionStatus() ==
                        CompletionHandler.CompletionStatus.ASKING_FOR_COMPLETIONS) {
                    askForCompletion = true;
                }
            }
        }
    }

    @Override
    public void input(Action action, KeyAction key) {
        if(askForCompletion) {
            if(Key.isPrintable(key.buffer())) {
                if(Key.y.equalTo(key.buffer().array())) {
                    this.key = Key.y;
                }
                if(Key.n.equalTo(key.buffer().array())) {
                    this.key = Key.n;
                }
            }
        }
    }

    @Override
    public boolean keepFocus() {
        return askForCompletion;
    }

}
