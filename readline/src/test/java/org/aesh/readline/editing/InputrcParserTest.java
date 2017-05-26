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

import org.aesh.readline.action.ActionDecoder;
import org.aesh.utils.Config;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class InputrcParserTest {

    @Test
    public void testParseInputrc() throws IOException {
        EditMode editMode = InputrcParser.parseInputrc(
                new FileInputStream(
                        Config.isOSPOSIXCompatible() ?
                                new File("src/test/resources/inputrc1") : new File("src\\test\\resources\\inputrc1")));

        assertEquals("vi", editMode.variableValue(Variable.EDITING_MODE));

        assertEquals("visible", editMode.variableValue(Variable.BELL_STYLE));

        assertEquals(300, Integer.parseInt(editMode.variableValue(Variable.HISTORY_SIZE)));

        assertEquals("on", editMode.variableValue(Variable.DISABLE_COMPLETION));

    }

    @Test
    public void testParseInputrc2() throws IOException {
        if(Config.isOSPOSIXCompatible()) {  //TODO: must fix this for windows

            EditMode editMode = EditModeBuilder.builder().create();
            ActionDecoder actionQueue = new ActionDecoder();
            actionQueue.add(new int[]{27, 91, 68});
            Assert.assertEquals("backward-char", editMode.parse( actionQueue.next()).name());
            actionQueue.add(new int[]{27, 91, 66});
            Assert.assertEquals("next-history", editMode.parse( actionQueue.next()).name());
            actionQueue.add(1);
            Assert.assertEquals("beginning-of-line", editMode.parse( actionQueue.next()).name());

            editMode = InputrcParser.parseInputrc(
                    new FileInputStream( Config.isOSPOSIXCompatible() ?
                            new File("src/test/resources/inputrc2") : new File("src\\test\\resources\\inputrc2")));

            actionQueue.add(new int[]{27, 91, 68});
            Assert.assertEquals("forward-char", editMode.parse( actionQueue.next()).name());
            actionQueue.add(new int[]{27, 91, 66});
            Assert.assertEquals("previous-history", editMode.parse(actionQueue.next()).name());
            actionQueue.add(new int[]{27,10});
            Assert.assertEquals("backward-char", editMode.parse(actionQueue.next()).name());
            actionQueue.add(1);
            Assert.assertEquals("forward-word", editMode.parse(actionQueue.next()).name());
        }
    }

}
