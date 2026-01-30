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
package org.aesh.terminal;

import java.nio.IntBuffer;

/**
 * Represents a key value.
 *
 */
public interface KeyAction {

    /**
     * Get the key values as an IntBuffer.
     *
     * @return the key values as an IntBuffer
     */
    default IntBuffer buffer() {
        int length = length();
        IntBuffer buf = IntBuffer.allocate(length);
        for (int i = 0; i < length; i++) {
            buf.put(getCodePointAt(i));
        }
        buf.flip();
        return buf;
    }

    /**
     * Get the code point at the specified index.
     *
     * @param index the index of the code point
     * @return the code point at the given index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    int getCodePointAt(int index) throws IndexOutOfBoundsException;

    /**
     * Get the number of code points in this key action.
     *
     * @return the length
     */
    int length();

    /**
     * Get the name of this key action.
     *
     * @return the name
     */
    String name();

    /**
     * Check if this key action equals another based on buffer contents.
     *
     * @param otherAction the other key action to compare
     * @return true if the buffers are equal
     */
    default boolean bufferEquals(KeyAction otherAction) {
        if (length() == otherAction.length()) {
            for (int i = 0; i < length(); i++)
                if (getCodePointAt(i) != otherAction.getCodePointAt(i))
                    return false;

            return true;
        }
        return false;
    }

}
