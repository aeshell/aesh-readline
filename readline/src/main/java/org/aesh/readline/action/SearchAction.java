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

/**
 * Action interface for history search operations with status tracking.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface SearchAction extends ActionEvent {

    /**
     * Enumeration of possible search operation statuses.
     */
    enum Status {
        /** Search has not been initiated yet. */
        SEARCH_NOT_STARTED,
        /** Search has been exited. */
        SEARCH_EXIT,
        /** Search is accepting input. */
        SEARCH_INPUT,
        /** Search was interrupted. */
        SEARCH_INTERRUPT,
        /** Search has ended. */
        SEARCH_END,
        /** Search for previous match. */
        SEARCH_PREV,
        /** Search for next match. */
        SEARCH_NEXT,
        /** Delete character during search. */
        SEARCH_DELETE,
        /** Move to previous search result. */
        SEARCH_MOVE_PREV,
        /** Move to next search result. */
        SEARCH_MOVE_NEXT,
        /** Move cursor right during search. */
        SEARCH_MOVE_RIGHT,
        /** Move cursor left during search. */
        SEARCH_MOVE_LEFT,
        /** Move cursor to beginning of line during search. */
        SEARCH_MOVE_BEGINNING_OF_LINE,
        /** Move cursor to end of line during search. */
        SEARCH_MOVE_END_OF_LINE,
    }
}
