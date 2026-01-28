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
package org.aesh.readline.action;

import org.aesh.terminal.KeyAction;

/**
 * An action that can receive input events and maintain focus state.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface ActionEvent extends Action {

    /**
     * Processes an input event with the given action and key.
     *
     * @param action the action that triggered this input
     * @param key the key action associated with the input
     */
    void input(Action action, KeyAction key);

    /**
     * Returns whether this action event should retain focus after processing.
     *
     * @return true if this event should keep focus
     */
    boolean keepFocus();

}
