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
package org.aesh.readline.editing;

import org.aesh.readline.terminal.Key;
import org.aesh.readline.tty.terminal.TestConnection;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ViModeTest {

    @Test
    public void testSimpleMovementAndEdit() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("abcd");
        term.read(Key.ESC);
        term.read(Key.x);
        term.assertBuffer("abc");
        term.read(Key.h);
        term.read(Key.s);
        term.read(Key.T);
        term.assertBuffer("aTc");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.x);
        term.assertBuffer("Tc");

        term.read(Key.l);
        term.read(Key.a);
        term.read(Key.o);
        term.assertBuffer("Tco");
        term.read(Key.ENTER);
        term.assertLine("Tco");

        term.readline();
        term.read("123");
        term.read(Key.ESC);
        term.assertBuffer("123");
        term.read(Key.ONE);
        term.read(Key.z);
        term.assertBuffer("123");
    }

    @Test
    public void testWordMovementAndEdit() throws Exception {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("  ..");
        term.read(Key.ESC);
        term.read(Key.b);
        term.read(Key.x);
        term.assertBuffer("  .");
        term.read(Key.ZERO);
        term.read(Key.D);
        term.assertBuffer("");
        term.read(Key.i);
        term.read("foo bar");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.x);
        term.read(Key.ENTER);
        term.assertLine("foo ar");
    }

    @Test
    public void testWordMovementAndEdit2() throws Exception {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("foo  bar...  Foo-Bar.");
        term.read(Key.ESC);
        term.read(Key.B);
        term.read(Key.d);
        term.read(Key.b);

        term.assertBuffer("foo  barFoo-Bar.");

        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.W);
        term.read(Key.d);
        term.read(Key.w);

        term.assertBuffer("foo  -Bar.");
        term.read(Key.ENTER);
        term.assertLine("foo  -Bar.");
    }

    @Test
    public void testWordMovementAndEdit3() throws Exception {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("foo bar... Bar");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.h);
        term.read(Key.W);
        term.read(Key.D);
        term.read(Key.ENTER);

        term.assertLine("foo ");
    }

    @Test
    public void testEnter() throws Exception {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("foo bar");
        term.read(Key.ENTER);
        term.assertLine("foo bar");

        term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("bar");
        term.read(Key.ESC);
        term.read(Key.CTRL_M);

        term.assertLine("bar");
    }

    @Test
    public void testRepeatAndEdit() throws Exception {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());

        term.read("/cd /home/foo/ ls/ cd Desktop/ ls ../");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.c);
        term.read(Key.w);
        term.read(Key.b);
        term.read(Key.a);
        term.read(Key.r);
        term.read(Key.ESC);
        term.read(Key.W);
        term.read(Key.d);
        term.read(Key.w);
        term.read(Key.PERIOD);

        term.assertBuffer("/cd /home/bar/ cd Desktop/ ls ../");
        term.read(Key.DOLLAR);
        term.read(Key.d);
        term.read(Key.ZERO);
        term.assertBuffer("/");

        term.read(Key.C);
        term.read("/cd /home/foo/ ls/ cd Desktop/ ls ../");
        term.read(Key.ESC);
        term.read(Key.B);
        term.read(Key.D);
        term.assertBuffer("/cd /home/foo/ ls/ cd Desktop/ ls ");
        term.read(Key.B);
        term.read(Key.PERIOD);
        term.assertBuffer("/cd /home/foo/ ls/ cd Desktop/ ");
        term.read(Key.B);
        term.read(Key.PERIOD);

        term.assertBuffer("/cd /home/foo/ ls/ cd ");
        term.read(Key.ENTER);
        term.assertLine("/cd /home/foo/ ls/ cd ");
    }

    @Test
    public void testTildeAndEdit() throws Exception {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());

        term.read("apt-get install vIM");
        term.read(Key.ESC);
        term.read(Key.b);
        term.read(Key.TILDE);
        term.read(Key.TILDE);
        term.read(Key.TILDE);

        term.assertBuffer("apt-get install Vim");

        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.c);
        term.read(Key.w);

        term.read("cache");
        term.assertBuffer("apt-cache install Vim");

        term.read(Key.ESC);
        term.read(Key.w);
        term.read(Key.c);
        term.read(Key.w);

        term.read("search");
        term.read(Key.ENTER);

        term.assertLine("apt-cache search Vim");
    }

    @Test
    public void testPasteAndEdit() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("apt-get install vIM");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.d);
        term.read(Key.W);
        term.read(Key.w);
        term.read(Key.P);
        term.read(Key.W);
        term.read(Key.y);
        term.read(Key.w);
        term.read(Key.DOLLAR);
        term.read(Key.p);

        term.read(Key.ENTER);
        term.assertLine("install apt-get vIMvIM");
    }

    @Test
    public void testSearch() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);

        term.assertBuffer("(reverse-i-search) `a': asdf jkl");
    }

    @Test
    public void testSearchWithArrownRight() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.RIGHT);

        term.assertBuffer("asdf jkl");

        term.read(Key.a);
        term.assertBuffer("asdf jkla");
    }

    @Test
    public void testSearchWithArrownLeft() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.LEFT);

        term.assertBuffer("asdf jkl");
    }

    @Test
    public void testSearchWithArrownUp() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("footing\n");
        term.readline();
        term.read("asdf jkl\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.UP);

        term.assertBuffer("footing");
    }

    @Test
    public void testSearchWithArrownDown() {
        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.VI).create());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.DOWN);
        term.assertBuffer("footing");
    }

}
