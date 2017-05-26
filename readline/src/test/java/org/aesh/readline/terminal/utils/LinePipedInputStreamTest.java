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

import org.aesh.utils.Config;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class LinePipedInputStreamTest {

    @Before
    public void beforeMethod() {
        //only run on linux
        Assume.assumeFalse(Config.isWindows());
    }

    @Test
    public void testInputStream() throws IOException {
        PipedOutputStream outputStream = new PipedOutputStream();
        LinePipedInputStream pipedInputStream = new LinePipedInputStream(outputStream);

        String filename = "src"+Config.getPathSeparator()+"test"+Config.getPathSeparator()+
                "resources"+Config.getPathSeparator()+"input_stream.txt";
        Files.lines(Paths.get(filename), StandardCharsets.UTF_8).forEach( line -> {
            try {
                outputStream.write((line+ Config.getLineSeparator()).getBytes());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        });

        outputStream.flush();


        byte[] bBuf = new byte[1024];
        Files.lines(Paths.get(filename), StandardCharsets.UTF_8).forEach( line -> {

            try {
                int read = pipedInputStream.read(bBuf);
                String in = new String(bBuf, 0, read);
                assertEquals(line+Config.getLineSeparator(), in);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
