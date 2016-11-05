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

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ShortHelper {

    public static short[] toShortPoints(String input) {
        short[] out = new short[input.length()];
        final int[] counter = {0};
        input.chars().forEach( i -> {
            out[counter[0]] = (short) i;
            counter[0]++;
        });
        return out;
    }

    public static String fromShortPoints(short[] input) {
        char[] output = new char[input.length];
        for(int i=0; i < input.length; i++)
            output[i] = (char) input[i];

        return new String(output, 0, output.length);
    }
}

