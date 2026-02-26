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

import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Rotate the kill ring, and yank the new top. You can only do this if
 * the prior command is yank or yank-pop.
 */
public class YankPop implements Action {

    private int killRingIndex = 0;

    @Override
    public String name() {
        return "yank-pop";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        // yank-pop only works if the previous action was yank or yank-pop
        if (inputProcessor.editMode().prevKey() == null)
            return;

        Action prevAction = inputProcessor.editMode().parse(inputProcessor.editMode().prevKey());
        if (prevAction == null)
            return;

        String prevName = prevAction.name();
        if (!"yank".equals(prevName) && !"yank-pop".equals(prevName))
            return;

        // Get the previous yank text to determine how much to delete
        int[] prevYank = inputProcessor.buffer().pasteManager().get(killRingIndex);
        if (prevYank == null)
            return;

        killRingIndex++;
        int[] newYank = inputProcessor.buffer().pasteManager().get(killRingIndex);
        if (newYank == null) {
            killRingIndex = 0;
            newYank = inputProcessor.buffer().pasteManager().get(killRingIndex);
        }
        if (newYank == null)
            return;

        // Delete the previously yanked text and insert the new one
        inputProcessor.buffer().addActionToUndoStack();
        inputProcessor.buffer().moveCursor(1); // yank leaves cursor one before end
        inputProcessor.buffer().delete(-prevYank.length);
        inputProcessor.buffer().insert(newYank);
        inputProcessor.buffer().moveCursor(-1);
    }
}
