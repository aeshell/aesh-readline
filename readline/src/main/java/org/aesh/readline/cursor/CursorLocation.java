/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.readline.cursor;

/**
 * Represents the row and column position of the cursor in the terminal.
 *
 * @author jdenise@redhat.com
 */
public class CursorLocation {

    private final int column;
    private final int row;

    /**
     * Creates a new cursor location with the specified row and column.
     *
     * @param row the row position of the cursor (0-indexed)
     * @param column the column position of the cursor (0-indexed)
     */
    public CursorLocation(int row, int column) {
        this.row = row;
        this.column = column;
    }

    /**
     * Returns the column position of the cursor.
     *
     * @return the column position (0-indexed)
     */
    public int getColumn() {
        return column;
    }

    /**
     * Returns the row position of the cursor.
     *
     * @return the row position (0-indexed)
     */
    public int getRow() {
        return row;
    }
}
