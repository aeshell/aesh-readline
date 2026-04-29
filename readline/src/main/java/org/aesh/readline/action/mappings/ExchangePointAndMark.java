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

import org.aesh.readline.Buffer;
import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Swap the point with the mark. The current cursor position is set to
 * the saved position, and the old cursor position is saved as the mark.
 */
public class ExchangePointAndMark implements Action {

    /** Constructor. */
    public ExchangePointAndMark() {
    }

    @Override
    public String name() {
        return "exchange-point-and-mark";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        Buffer buffer = inputProcessor.buffer().buffer();
        int mark = buffer.getMark();
        if (mark < 0)
            return;

        int cursor = buffer.cursor();
        // Clamp mark to valid range
        if (mark > buffer.length())
            mark = buffer.length();

        buffer.setMark(cursor);
        inputProcessor.buffer().moveCursor(mark - cursor);
    }
}
