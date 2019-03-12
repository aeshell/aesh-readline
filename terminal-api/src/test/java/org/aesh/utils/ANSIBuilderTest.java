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
package org.aesh.utils;

import org.aesh.utils.ANSIBuilder.TextType;
import org.junit.Test;

import static org.aesh.utils.ANSIBuilder.Color.*;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ANSIBuilderTest {

    private static final String COLOR_START = "\u001B[";
    private static final String RESET = "\u001B[0m";

    @Test
    public void testAnsiBuilder() {
        ANSIBuilder builder = new ANSIBuilder();

        assertEquals(COLOR_START+"0;"+ YELLOW.text()+";"+ DEFAULT.bg()+"m"+"FOO"+RESET,
                builder.yellowText().append("FOO").toString());

        builder.clear();
        assertEquals(COLOR_START+"0;"+ YELLOW.text()+";"+ DEFAULT.bg()+"m"+"FOO"+RESET,
                builder.yellowText("FOO").toString());

        builder.clear();
        assertEquals("FOO"+COLOR_START+"0;"+ DEFAULT.text()+";"+ BLUE.bg()+"m"+" BAR"+RESET,
                builder.append("FOO").resetColors().blueBg().append(" BAR").toString());

        builder.clear();
        assertEquals("FOO"+COLOR_START+"0;"+ DEFAULT.text()+";"+ BLUE.bg()+"m"+" BAR"+RESET,
                builder.append("FOO").resetColors().blueBg(" BAR").toString());

        builder.clear();
        assertEquals(COLOR_START+ TextType.BOLD.value()+"mFOO"+
                             COLOR_START+ TextType.BOLD_OFF.value()+"m BAR"+RESET,
                builder.bold("FOO").append(' ').append("BAR").toString());
    }
}
