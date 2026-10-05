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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.junit.Assume;
import org.junit.Test;

/**
 * Guards Windows FFM downcall registration for GraalVM native-image.
 * <p>
 * The overlay's handle initializer swallows every linkage failure and
 * leaves handles null, so a missing {@code foreign} entry in
 * {@code reachability-metadata.json} never throws — the whole bridge
 * silently degrades. The only observable is a null handle, hence this
 * test asserts all eleven handles are present instead of probing for a
 * thrown registration error.
 * <p>
 * All access is reflective so this test compiles on pre-22 runtimes.
 * It proves registration only where the FFM overlay loads with native
 * access enabled (native image built with the overlay kept); every
 * other shape skips.
 */
public class WinConsoleNativeFfmRegistrationTest {

    private static final String WIN_CONSOLE_NATIVE = "org.aesh.terminal.tty.impl.WinConsoleNative";

    private static final String[] HANDLES = {
            "GET_STD_HANDLE",
            "GET_CONSOLE_MODE",
            "SET_CONSOLE_MODE",
            "GET_CONSOLE_OUTPUT_CP",
            "GET_CONSOLE_SCREEN_BUFFER_INFO",
            "READ_CONSOLE_INPUT_W",
            "WRITE_CONSOLE_W",
            "WAIT_FOR_SINGLE_OBJECT",
            "GET_NUMBER_OF_CONSOLE_INPUT_EVENTS",
            "ALLOC_CONSOLE",
            "FREE_CONSOLE",
    };

    @Test
    public void testFfmDowncallHandlesInitialized() throws Exception {
        Assume.assumeTrue("Windows only",
                System.getProperty("os.name", "").toLowerCase().contains("win"));
        Class<?> nativeBridge;
        try {
            nativeBridge = Class.forName(WIN_CONSOLE_NATIVE);
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        Field probe;
        try {
            probe = nativeBridge.getDeclaredField("GET_CONSOLE_OUTPUT_CP");
        } catch (NoSuchFieldException e) {
            // JNI base facade (overlay deleted for old GraalVM): nothing
            // FFM to prove here.
            return;
        }
        if (!isNativeAccessEnabled(nativeBridge)) {
            return;
        }
        for (String name : HANDLES) {
            Field handle;
            try {
                handle = nativeBridge.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                fail("WinConsoleNative overlay lost handle field " + name
                        + ": keep the overlay and reflect-config in sync");
                return;
            }
            handle.setAccessible(true);
            assertNotNull("WinConsoleNative." + name + " is null:"
                    + " FFM downcall descriptor not registered for native-image."
                    + " Add its shape to META-INF/native-image/org.aesh/terminal-tty/"
                    + "reachability-metadata.json",
                    handle.get(null));
        }
    }

    private static boolean isNativeAccessEnabled(Class<?> clazz) {
        try {
            Object module = Class.class.getMethod("getModule").invoke(clazz);
            Object enabled = module.getClass().getMethod("isNativeAccessEnabled").invoke(module);
            return Boolean.TRUE.equals(enabled);
        } catch (Exception e) {
            return false;
        }
    }
}
