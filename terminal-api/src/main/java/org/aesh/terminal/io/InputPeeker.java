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

import java.io.IOException;

/**
 * Peeks at terminal input without consuming it, for escape sequence
 * timeout disambiguation.
 * <p>
 * After a bare ESC with no following bytes yet, the holder peeks with a
 * short timeout: data waiting means a longer sequence (Alt combination,
 * CSI/OSC) is in flight and the ESC stays held; timeout or EOF means the
 * ESC was a standalone keypress and is delivered as input. This is the
 * shared seam for both disambiguation layers: {@code EventDecoder} (which
 * would otherwise hold the byte indefinitely while sequence filters are
 * active) and {@code ActionDecoder} (which resolves the trie ambiguity).
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
@FunctionalInterface
public interface InputPeeker {

    /**
     * Peek at the next byte without consuming it.
     *
     * @param timeoutMs timeout in milliseconds
     * @return the byte peeked (0-255), -1 for EOF, or -2 for timeout
     * @throws IOException if an I/O error occurs
     */
    int peek(long timeoutMs) throws IOException;
}
