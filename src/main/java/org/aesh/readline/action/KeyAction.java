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

import java.nio.IntBuffer;

/**
 * Represents a key value.
 *
 */
public interface KeyAction {

  default IntBuffer buffer() {
    int length = length();
    IntBuffer buf = IntBuffer.allocate(length);
    for (int i = 0;i < length;i++) {
      buf.put(getCodePointAt(i));
    }
    buf.flip();
    return buf;
  }

  int getCodePointAt(int index) throws IndexOutOfBoundsException;

  int length();

  String name();

  default boolean bufferEquals(KeyAction otherAction) {
      if(length() == otherAction.length()) {
          for(int i=0; i<length(); i++)
              if(getCodePointAt(i) != otherAction.getCodePointAt(i))
                  return false;

          return true;
      }
      return false;
  }

}
