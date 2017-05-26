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
package org.aesh.readline.alias;

import org.aesh.utils.Config;
import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ReadlineAliasTest {
    @Test
    public void alias() throws Exception {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out);
        Readline readline = new Readline();

        File aliasFile = Config.isOSPOSIXCompatible() ?
                new File("src/test/resources/alias1") : new File("src\\test\\resources\\alias1");
        AliasManager aliasManager = new AliasManager(aliasFile, false);
        AliasPreProcessor aliasPreProcessor = new AliasPreProcessor(aliasManager);
        List<Function<String, Optional<String>>> preProcessors = new ArrayList<>();
        preProcessors.add(aliasPreProcessor);

        readline.readline(connection, new Prompt(""),
                s -> {
                    //first check this
                    assertEquals("ls -alF", s);
                    //then call readline again, to check the next line
                    readline.readline(connection, new Prompt(""),
                            t -> assertEquals("grep --color=auto -l", t),
                            null, preProcessors);
                } , null, preProcessors);

        outputStream.write(("ll"+Config.getLineSeparator()).getBytes());
        outputStream.write(("grep -l"+Config.getLineSeparator()).getBytes());
        outputStream.flush();
        connection.openNonBlocking();
        Thread.sleep(200);
    }
}
