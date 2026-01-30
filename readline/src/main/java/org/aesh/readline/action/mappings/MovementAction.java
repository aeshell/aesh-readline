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

/**
 * Abstract base class for cursor movement actions.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
abstract class MovementAction implements Action {

    /**
     * Check if the character is a whitespace.
     *
     * @param c the character to check
     * @return true if the character is whitespace
     */
    protected boolean isSpace(char c) {
        return Character.isWhitespace(c);
    }

    /**
     * Check if the character is a delimiter (non-alphanumeric).
     *
     * @param c the character to check
     * @return true if the character is a delimiter
     */
    protected boolean isDelimiter(char c) {
        return !Character.isLetterOrDigit(c);
    }

}
