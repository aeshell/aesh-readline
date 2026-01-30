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
 * Action that changes text from cursor to beginning of previous word.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ChangeBackwardWord extends BackwardWord {

    /**
     * Creates a new change backward word action.
     *
     * @param status the edit mode status
     */
    public ChangeBackwardWord(EditMode.Status status) {
        super(false, status);
    }

    /**
     * Creates a new change backward word action.
     *
     * @param viMode whether vi mode is enabled
     * @param status the edit mode status
     */
    public ChangeBackwardWord(boolean viMode, EditMode.Status status) {
        super(viMode, status);
    }

    @Override
    public String name() {
        return "change-backward-word";
    }
}
