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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.aesh.terminal.utils.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class AliasManagerTest {

    private AliasManager manager;
    private File fooFile;

    @Before
    public void setTup() {
        try {
            fooFile = new File("foo");
            manager = new AliasManager(fooFile, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @After
    public void cleanup() {
        fooFile.delete();
    }

    @Test
    public void testParseAlias() {

        assertFalse(manager.addAlias("alias foo2='bar -s -h'").isPresent());
        assertFalse(manager.addAlias("alias foo=bar").isPresent());
        assertFalse(manager.addAlias("alias foo3=bar --help").isPresent());

        Optional<String> out = manager.addAlias("alias foo");
        assertTrue(out.isPresent());
        Assert.assertEquals("alias foo='bar'" + Config.getLineSeparator(), out.get());
        out = manager.addAlias("alias foo2");
        assertTrue(out.isPresent());
        assertEquals("alias foo2='bar -s -h'" + Config.getLineSeparator(), out.get());
        out = manager.addAlias("alias foo3");
        assertTrue(out.isPresent());
        assertEquals("alias foo3='bar --help'" + Config.getLineSeparator(), out.get());
        out = manager.addAlias("alias");
        assertTrue(out.isPresent());
        StringBuilder sb = new StringBuilder();
        sb.append("alias foo='bar'").append(Config.getLineSeparator())
                .append("alias foo2='bar -s -h'").append(Config.getLineSeparator())
                .append("alias foo3='bar --help'").append(Config.getLineSeparator());
        assertEquals(sb.toString(), out.get());

        // the help information for the (un)alias itself
        out = manager.addAlias("alias --help");
        assertTrue(out.isPresent());
        assertEquals("alias: usage: alias [name[=value] ... ]" + Config.getLineSeparator(), out.get());

        out = manager.addAlias("alias        --help");
        assertTrue(out.isPresent());
        assertEquals("alias: usage: alias [name[=value] ... ]" + Config.getLineSeparator(), out.get());
    }

    @Test
    public void testUnalias() {

        manager.addAlias("alias foo2='bar -s -h'");
        manager.addAlias("alias foo=bar");
        manager.addAlias("alias foo3=bar --help");

        manager.removeAlias("unalias foo3");
        assertEquals("unalias: foo3: not found" + Config.getLineSeparator(), manager.removeAlias("unalias foo3"));

        String out = manager.removeAlias("unalias --help");
        assertEquals("unalias: usage: unalias name [name ...]" + Config.getLineSeparator(), out);

        out = manager.removeAlias("unalias        --help");
        assertEquals("unalias: usage: unalias name [name ...]" + Config.getLineSeparator(), out);
    }

    @Test
    public void testPrintAllAliases() {
        String alias = "alias foo='bar'";
        manager.addAlias(alias);
        Assert.assertEquals(alias + Config.getLineSeparator(), manager.printAllAliases());
    }

    @Test
    public void testPersist() throws Exception {
        manager.persist();
        assertTrue("The persistent file should be a file", fooFile.isFile());
    }
}
