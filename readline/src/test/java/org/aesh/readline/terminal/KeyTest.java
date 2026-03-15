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
package org.aesh.readline.terminal;

import static org.junit.Assert.assertEquals;

import org.aesh.readline.action.Action;
import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.action.mappings.ActionMapper;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.terminal.Key;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

/**
 * Tests for Key enum that require readline dependencies.
 * Pure Key tests are in terminal-api module.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class KeyTest {

    @Test
    public void testOtherOperations() {

        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.EMACS).build();

        Key up = Key.UP_2;

        Action action = editMode.parse(up);

        assertEquals(action.name(), ActionMapper.mapToAction("previous-history").name());

        if (Config.isOSPOSIXCompatible()) {
            int[] doubleUpKey = new int[6];
            for (int i = 0; i < 6; i++) {
                if (i > 2)
                    doubleUpKey[i] = up.getKeyValues()[i - 3];
                else
                    doubleUpKey[i] = up.getKeyValues()[i];
            }

            ActionDecoder actionDecoder = new ActionDecoder(editMode);
            actionDecoder.add(doubleUpKey);

            action = editMode.parse(actionDecoder.next());
            assertEquals(action.name(), ActionMapper.mapToAction("previous-history").name());
        }

    }
}
