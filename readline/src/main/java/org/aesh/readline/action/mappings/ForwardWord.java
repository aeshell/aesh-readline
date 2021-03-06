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
import org.aesh.readline.editing.EditMode;

/**
 * TODO: change boolean params in constructors to objects/enum
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
abstract class ForwardWord extends ChangeAction {

    private boolean viMode;
    private boolean removeTrailingSpaces;

    ForwardWord() {
        super(EditMode.Status.MOVE);
        viMode = false;
    }

    ForwardWord(boolean viMode, EditMode.Status status) {
        super(status);
        this.viMode = viMode;
        if(status == EditMode.Status.CHANGE)
            removeTrailingSpaces = false;
        else
            removeTrailingSpaces = true;
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        int cursor = inputProcessor.buffer().buffer().cursor();
        String buffer = inputProcessor.buffer().buffer().asString();

        if(viMode) {
            if(cursor < buffer.length() && (isDelimiter(buffer.charAt(cursor))))
                while(cursor < buffer.length() && (isDelimiter(buffer.charAt(cursor))))
                    cursor++;
                //if we stand on a non-delimiter
            else {
                while(cursor < buffer.length() && !isDelimiter(buffer.charAt(cursor)))
                    cursor++;
                //if we end up on a space we move past that too
                if(removeTrailingSpaces)
                    if(cursor < buffer.length() && isSpace(buffer.charAt(cursor)))
                        while(cursor < buffer.length() && isSpace(buffer.charAt(cursor)))
                            cursor++;
            }
        }
        else {
            while (cursor < buffer.length() && (isDelimiter(buffer.charAt(cursor))))
                cursor++;
            while (cursor < buffer.length() && !isDelimiter(buffer.charAt(cursor)))
                cursor++;
        }

        //if we end up on a space we move past that too
        if(removeTrailingSpaces)
            if(cursor < buffer.length() && isSpace(buffer.charAt(cursor)))
                while(cursor < buffer.length() && isSpace(buffer.charAt(cursor)))
                    cursor++;

        apply(cursor, inputProcessor);
    }
}
