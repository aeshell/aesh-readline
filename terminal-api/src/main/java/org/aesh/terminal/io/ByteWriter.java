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
package org.aesh.terminal.io;

/**
 * Consumer of encoded byte output from {@link Encoder}.
 * <p>
 * Receives a slice of a reusable buffer. Implementations must consume
 * or copy the data before returning, as the buffer contents may be
 * overwritten on the next {@link Encoder#accept} call.
 *
 * @author Ståle W. Pedersen
 */
@FunctionalInterface
public interface ByteWriter {

    /**
     * Write bytes from a buffer slice.
     *
     * @param buf the byte buffer (may be reused by the caller after this method returns)
     * @param off the start offset in the buffer
     * @param len the number of bytes to write
     */
    void write(byte[] buf, int off, int len);
}
