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

import org.aesh.readline.ConsoleBuffer;
import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Insert a '#' at the beginning of the line and accept it,
 * effectively commenting out the current command.
 */
public class InsertComment implements Action {

    @Override
    public String name() {
        return "insert-comment";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        ConsoleBuffer consoleBuffer = inputProcessor.buffer();

        // Move to beginning, insert #
        consoleBuffer.moveCursor(-consoleBuffer.buffer().cursor());
        consoleBuffer.writeChar('#');
        // Move to end and accept
        consoleBuffer.moveCursor(consoleBuffer.buffer().length());
        if (consoleBuffer.history().isEnabled()) {
            consoleBuffer.history().push(consoleBuffer.buffer().multiLine());
        }
        inputProcessor.setReturnValue(consoleBuffer.buffer().multiLine());
        consoleBuffer.buffer().reset();
    }
}
