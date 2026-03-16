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
package org.aesh.terminal;

import java.io.Writer;

/**
 * A {@link java.io.Writer} adapter that delegates to a {@link Connection}.
 * <p>
 * Obtain via {@link Connection#asWriter()}.
 */
class ConnectionWriter extends Writer {

    private final Connection connection;

    ConnectionWriter(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        connection.write(new String(cbuf, off, len));
    }

    @Override
    public void write(String str) {
        connection.write(str);
    }

    @Override
    public void flush() {
        // No-op: Connection output is unbuffered
    }

    @Override
    public void close() {
        connection.close();
    }
}
