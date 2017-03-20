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
package org.aesh.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AttributesTest {

    @Test
    public void testAddingAndRemovingValues() {
        Attributes attributes = new Attributes();
        attributes.setControlChar(Attributes.ControlChar.VDSUSP, 42);
        attributes.setControlChar(Attributes.ControlChar.VDISCARD, 43);

        assertEquals(43, attributes.getControlChar(Attributes.ControlChar.VDISCARD));
        assertEquals(42, attributes.getControlChar(Attributes.ControlChar.VDSUSP));

        attributes.setControlChar(Attributes.ControlChar.VDSUSP, 24);
        assertEquals(24, attributes.getControlChar(Attributes.ControlChar.VDSUSP));

        attributes.setLocalFlag(Attributes.LocalFlag.ECHO, true);
        attributes.setLocalFlag(Attributes.LocalFlag.ECHONL, true);
        assertEquals(true, attributes.getLocalFlag(Attributes.LocalFlag.ECHO));
        assertEquals(true, attributes.getLocalFlag(Attributes.LocalFlag.ECHONL));

        Attributes copy = new Attributes();
        copy.copy(attributes);

        assertEquals(43, copy.getControlChar(Attributes.ControlChar.VDISCARD));
        assertEquals(24, copy.getControlChar(Attributes.ControlChar.VDSUSP));
        assertEquals(true, copy.getLocalFlag(Attributes.LocalFlag.ECHO));
        assertEquals(true, copy.getLocalFlag(Attributes.LocalFlag.ECHONL));

    }
}
