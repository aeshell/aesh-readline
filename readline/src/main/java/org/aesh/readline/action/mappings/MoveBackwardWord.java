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

import org.aesh.readline.editing.EditMode;

/**
 * Action that moves the cursor backward to the start of the previous word.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class MoveBackwardWord extends BackwardWord {

    /**
     * Creates a new MoveBackwardWord action with default settings.
     */
    public MoveBackwardWord() {
        super();
    }

    /**
     * Creates a new MoveBackwardWord action.
     *
     * @param viMode true for vi mode, false for emacs mode
     */
    public MoveBackwardWord(boolean viMode) {
        super(viMode, EditMode.Status.MOVE);
    }

    @Override
    public String name() {
        return "backward-word";
    }
}
