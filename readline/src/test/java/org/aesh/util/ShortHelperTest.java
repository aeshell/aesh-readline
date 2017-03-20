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
package org.aesh.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ShortHelperTest {

    @Test
    public void testShortConverter() {
        testString("foo");
        testString("as')(#/&!\"=#)dfae09fajfjdf aeufa3jas");
    }

    private void testString(String input) {
        short[] shorts = ShortHelper.toShortPoints(input);
        int[] ints = Parser.toCodePoints(input);

        assertTrue(shorts.length == ints.length);

        for(int i=0; i < shorts.length; i++) {
            assertTrue(shorts[i] == ints[i]);
        }
    }

    @Test
    public void testFromShortToString() {
        fromShortToString("foo");
        fromShortToString("as')(#/&!\"=#)dfae09fajfjdf aeufa3jas");
    }

    private void fromShortToString(String input) {
        short[] shorts = ShortHelper.toShortPoints(input);
        String out = ShortHelper.fromShortPoints(shorts);

        assertEquals(input, out);
    }
}
