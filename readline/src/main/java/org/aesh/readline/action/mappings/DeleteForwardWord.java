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
 * Action that deletes text from cursor to end of word.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class DeleteForwardWord extends ForwardWord {

    /**
     * Creates a new DeleteForwardWord action in emacs mode.
     */
    public DeleteForwardWord() {
        super(false, EditMode.Status.DELETE);
    }

    /**
     * Creates a new DeleteForwardWord action with delete status.
     *
     * @param viMode true for vi mode, false for emacs mode
     */
    public DeleteForwardWord(boolean viMode) {
        super(viMode, EditMode.Status.DELETE);
    }

    /**
     * Creates a new DeleteForwardWord action with the specified status.
     *
     * @param viMode true for vi mode, false for emacs mode
     * @param status the edit mode status
     */
    public DeleteForwardWord(boolean viMode, EditMode.Status status) {
        super(viMode, status);
    }

    @Override
    public String name() {
        return "kill-word";
    }
}
