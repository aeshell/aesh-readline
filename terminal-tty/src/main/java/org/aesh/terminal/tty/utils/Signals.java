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
package org.aesh.terminal.tty.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Signals helpers.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public final class Signals {

    private static final class SignalApi {
        static final Class<?>[] HANDLER_INTERFACES;
        static final Constructor<?> SIGNAL_CTOR;
        static final Method HANDLE_STATIC;
        static final Method HANDLE_INSTANCE;
        static final Object SIG_DFL;
        static final Object SIG_IGN;
        static final boolean AVAILABLE;

        static {
            Class<?>[] interfaces = null;
            Constructor<?> ctor = null;
            Method handleStatic = null;
            Method handleInstance = null;
            Object dfl = null;
            Object ign = null;
            boolean ok = false;
            try {
                Class<?> signalClass = Class.forName("sun.misc.Signal");
                Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
                interfaces = new Class<?>[] { signalHandlerClass };
                ctor = signalClass.getConstructor(String.class);
                handleStatic = signalClass.getMethod("handle", signalClass, signalHandlerClass);
                handleInstance = signalHandlerClass.getMethod("handle", signalClass);
                dfl = signalHandlerClass.getField("SIG_DFL").get(null);
                ign = signalHandlerClass.getField("SIG_IGN").get(null);
                ok = true;
            } catch (Exception | LinkageError e) {
                // sun.misc.Signal API unavailable on this JVM
            }
            HANDLER_INTERFACES = interfaces;
            SIGNAL_CTOR = ctor;
            HANDLE_STATIC = handleStatic;
            HANDLE_INSTANCE = handleInstance;
            SIG_DFL = dfl;
            SIG_IGN = ign;
            AVAILABLE = ok;
        }
    }

    private Signals() {
    }

    /**
     * Registers a signal handler for the specified signal.
     *
     * @param name the signal, CONT, STOP, etc...
     * @param handler the callback to run
     *
     * @return an object that needs to be passed to the {@link #unregister(String, Object)}
     *         method to unregister the handler
     */
    public static Object register(String name, Runnable handler) {
        assert name != null;
        assert handler != null;
        return register(name, handler, handler.getClass().getClassLoader());
    }

    /**
     * Registers a signal handler using the specified class loader.
     *
     * @param name the signal name (e.g., CONT, STOP)
     * @param handler the callback to run when the signal is received
     * @param loader the class loader to use for creating the signal handler proxy
     * @return an object that needs to be passed to {@link #unregister(String, Object)}
     *         to unregister the handler, or null if registration failed
     */
    public static Object register(String name, final Runnable handler, ClassLoader loader) {
        if (!SignalApi.AVAILABLE) {
            return null;
        }
        try {
            // Implement signal handler
            Object signalHandler = Proxy.newProxyInstance(loader,
                    SignalApi.HANDLER_INTERFACES, (proxy, method, args) -> {
                        // only method we are proxying is handle()
                        handler.run();
                        return null;
                    });
            return doRegister(name, signalHandler);
        } catch (Exception e) {
            // Ignore this one too, if the above failed, the signal API is incompatible with what we're expecting
        }
        return null;
    }

    /**
     * Registers the default signal handler for the specified signal.
     *
     * @param name the signal name (e.g., CONT, STOP)
     * @return an object that needs to be passed to {@link #unregister(String, Object)}
     *         to unregister the handler, or null if registration failed
     */
    public static Object registerDefault(String name) {
        if (!SignalApi.AVAILABLE) {
            return null;
        }
        try {
            return doRegister(name, SignalApi.SIG_DFL);
        } catch (Exception e) {
            // Ignore this one too, if the above failed, the signal API is incompatible with what we're expecting
        }
        return null;
    }

    /**
     * Registers an ignore handler for the specified signal, causing the signal to be ignored.
     *
     * @param name the signal name (e.g., CONT, STOP)
     * @return an object that needs to be passed to {@link #unregister(String, Object)}
     *         to unregister the handler, or null if registration failed
     */
    public static Object registerIgnore(String name) {
        if (!SignalApi.AVAILABLE) {
            return null;
        }
        try {
            return doRegister(name, SignalApi.SIG_IGN);
        } catch (Exception e) {
            // Ignore this one too, if the above failed, the signal API is incompatible with what we're expecting
        }
        return null;
    }

    /**
     * Invokes a signal handler directly for the specified signal.
     *
     * @param name the signal name (e.g., CONT, STOP)
     * @param handler the signal handler to invoke
     */
    public static void invokeHandler(String name, Object handler) {
        if (!SignalApi.AVAILABLE) {
            return;
        }
        try {
            Object signal = SignalApi.SIGNAL_CTOR.newInstance(name);
            SignalApi.HANDLE_INSTANCE.invoke(handler, signal);
        } catch (Exception e) {
        }
    }

    /**
     * Unregisters a signal handler by restoring the previous handler.
     *
     * @param name the signal name (e.g., CONT, STOP)
     * @param previous the previous handler returned by a register method
     */
    public static void unregister(String name, Object previous) {
        try {
            // We should make sure the current signal is the one we registered
            if (previous != null && SignalApi.AVAILABLE) {
                doRegister(name, previous);
            }
        } catch (Exception e) {
        }
    }

    private static Object doRegister(String name, Object handler) throws Exception {
        Object signal = SignalApi.SIGNAL_CTOR.newInstance(name);
        return SignalApi.HANDLE_STATIC.invoke(null, signal, handler);
    }

}
