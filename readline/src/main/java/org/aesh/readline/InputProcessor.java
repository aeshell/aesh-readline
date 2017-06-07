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
package org.aesh.readline;

import org.aesh.readline.editing.EditMode;
import org.aesh.terminal.Connection;

import java.util.EnumMap;

/**
 * InputProcessor is used by {@link org.aesh.readline.Readline} to process the input.
 * InputProcessor is using an instance of {@link org.aesh.readline.ConsoleBuffer} to do
 * provide easy access to writing/reading from the stream, and access to history/undo/etc.
 *
 * It is also used by many of the different action classes that react to specific user input.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface InputProcessor {

    /**
     * The value thats returned after a readline
     * @return value
     */
    String returnValue();

    /**
     * @return current console buffer
     */
    ConsoleBuffer buffer();

    /**
     * Specify the return value
     * @param value return value
     */
    void setReturnValue(int[] value);

    /**
     * @return the current edit mode
     */
    EditMode editMode();

    /**
     * Update the current edit mode
     * @param mode edit mode
     */
    void setEditMode(EditMode mode);

    /**
     * @return the Connection
     */
    Connection connection();

    /**
     * @return current flags
     */
    EnumMap<ReadlineFlag, Integer> flags();
}
