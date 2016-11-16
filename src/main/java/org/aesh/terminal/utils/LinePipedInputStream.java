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
package org.aesh.terminal.utils;

import org.aesh.util.LoggerUtil;

import java.io.IOException;
import java.io.PipedInputStream;
import java.util.logging.Logger;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class LinePipedInputStream extends PipedInputStream {

    private static final int NEW_LINE = 10;
    private static final Logger LOGGER = LoggerUtil.getLogger(LinePipedInputStream.class.getName());

    public LinePipedInputStream(int pipeSize) {
        super(pipeSize);
    }

    @Override
    public synchronized int read(byte[] b, int off, int len)  throws IOException {
        if (off < 0 || len < 0 || len > b.length - off)
            throw new IndexOutOfBoundsException();
        else if (len == 0)
            return 0;

        /* possibly wait on the first character */
        int c = read();
        if (c < 0) {
            return -1;
        }

        b[off] = (byte) c;
        int rlen = 1;
        if(c == NEW_LINE)
            return rlen;

        int enter = findNewLine();
        LOGGER.info("enter: "+enter+", in: "+in+", out: "+out+", buffer.length: "+buffer.length);

        if(enter > 0 && in >= 0) {
            // A byte is read beforehand outside the loop
            if (enter > (len - 1)) {
                enter = len - 1;
            }
            //System.out.println("enter: "+enter+", in: "+in+", out: "+out+", buffer.length: "+buffer.length);
            //System.out.println("(off+rlen): "+(off+rlen));
            System.arraycopy(buffer, out, b, off + rlen, enter);
            out += enter;
            rlen += enter;
            len -= enter;

            if (out >= buffer.length) {
                out = 0;
            }
            if (in == out) {
                /* now empty */
                in = -1;
            }
            //LOGGER.info("RETURNING: "+new String(Arrays.copyOf(b, rlen)));
            return rlen;
        }
        else {
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
            }
            return rlen;
        }
    }

    private int findNewLine() {
        int limit = (in > out) ? Math.min((buffer.length - out), (in - out)) : buffer.length-out;
        //LOGGER.info("limit: "+limit+", out: "+out+", in: "+in);
        for(int i = out; i < limit+out; i++) {
            //LOGGER.info("checking, i: "+i+", buffer[i]: "+buffer[i]);
            if(buffer[i] == NEW_LINE)
                return i-out;
        }
        //LOGGER.info("not checking array!!! line is: "+buffer.length);
        return -1;
    }
}
