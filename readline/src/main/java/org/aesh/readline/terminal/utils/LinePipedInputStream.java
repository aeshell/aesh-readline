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
package org.aesh.readline.terminal.utils;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class LinePipedInputStream extends PipedInputStream {

    private static final int NEW_LINE = 10;

    public LinePipedInputStream(int pipeSize) {
        super(pipeSize);
    }

    public LinePipedInputStream(PipedOutputStream src) throws IOException {
        super(src);
    }

    public synchronized int read(byte[] b, int off, int len)  throws IOException {
        /* possibly wait on the first character */
        int c = read();
        if (c < 0) {
            return -1;
        }
        b[off] = (byte) c;
        int rlen = 1;
        if(c == NEW_LINE) {
            return rlen;
        }
        int enter = -1;
        while ((in >= 0) && (len > 1)) {

            int available;

            if (in > out) {
                available = Math.min((buffer.length - out), (in - out));
            } else {
                available = buffer.length - out;
            }

            // A byte is read beforehand outside the loop
            if (available > (len - 1)) {
                available = len - 1;
            }
            enter = findEnter(buffer, out, out+available);
            if(enter > -1) {
                if(enter+1 <= available)
                    available = enter+1;
                else
                    available = enter;
            }
            System.arraycopy(buffer, out, b, off + rlen, available);
            out += available;
            rlen += available;
            len -= available;

            if (out >= buffer.length) {
                out = 0;
            }
            if (in == out) {
                /* now empty */
                in = -1;
            }
            if(enter > -1) {
                return rlen;
            }
        }
        return rlen;
    }

    private int findEnter(byte[] buffer, int start, int length) {
            for(int i = start; i < length; i++) {
            if(buffer[i] == NEW_LINE)
                return i-start;
        }
        return -1;
    }
}
