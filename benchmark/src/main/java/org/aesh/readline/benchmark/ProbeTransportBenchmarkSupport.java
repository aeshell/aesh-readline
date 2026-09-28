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
package org.aesh.readline.benchmark;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.aesh.terminal.detect.TerminalProbeTransport;

/**
 * Accesses the package-private built-in probe transports from the Java 8
 * benchmark module. Reflection runs in JMH trial setup, never in a timed
 * method. It also avoids a Java 22 compile dependency on the MRJAR layer.
 */
final class ProbeTransportBenchmarkSupport {

    private ProbeTransportBenchmarkSupport() {
    }

    static TerminalProbeTransport stty() throws ReflectiveOperationException {
        Class<?> clazz = Class.forName("org.aesh.terminal.detect.SttyProbeTransport");
        Field instance = clazz.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        return (TerminalProbeTransport) instance.get(null);
    }

    static TerminalProbeTransport ffm() throws ReflectiveOperationException {
        Class<?> clazz = Class.forName("org.aesh.terminal.detect.FfmProbeTransport");
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        TerminalProbeTransport transport = (TerminalProbeTransport) constructor.newInstance();
        if (!transport.isAvailable()) {
            throw new IllegalStateException("FFM probe requires Java 22+, native access, and /dev/tty");
        }
        return transport;
    }

    /** Prime the JVM-wide Linker without creating FfmPosix's downcall handles. */
    static void initializeLinker() throws ReflectiveOperationException {
        Class<?> linker = Class.forName("java.lang.foreign.Linker");
        linker.getMethod("nativeLinker").invoke(null);
    }
}
