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
package org.aesh.readline;

import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.utils.Config;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class PasteReadlineTest {

    @Test
    public void paste() throws Exception {
        TestConnection connection = new TestConnection();
        connection.read("connect" + Config.getLineSeparator() +
                        "admin" + Config.getLineSeparator() +
                        "admin!");
        connection.assertLine("connect");
        connection.readline(s -> {
            assertEquals("admin", s);
            connection.setPrompt(new Prompt("[password:] ",'\u0000'));
        });
        connection.readline();
        connection.assertBuffer("admin!");
        assertEquals("[password:] ", connection.getOutputBuffer());
        connection.read("234"+ Config.getLineSeparator());
        connection.assertLine("admin!234");
    }
}
