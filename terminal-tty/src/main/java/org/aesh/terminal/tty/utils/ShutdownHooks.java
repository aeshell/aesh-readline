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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.utils.LoggerUtil;

/**
 * Manages the shutdown-hook thread and tasks to execute on shutdown.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 */
public final class ShutdownHooks {

    /**
     * Private constructor to prevent instantiation.
     */
    private ShutdownHooks() {
    }

    private static final Logger LOGGER = LoggerUtil.getLogger(ShutdownHooks.class.getName());

    private static final List<Task> tasks = new ArrayList<>();

    private static Thread hook;

    /**
     * Adds a task to be executed on JVM shutdown.
     * If this is the first task, a shutdown hook thread is registered.
     *
     * @param <T> the task type
     * @param task the task to execute on shutdown
     * @return the task that was added
     */
    public static synchronized <T extends Task> T add(final T task) {
        assert task != null;

        // Install the hook thread if needed
        if (hook == null) {
            hook = addHook(new Thread("Aesh Shutdown Hook") {
                @Override
                public void run() {
                    runTasks();
                }
            });
        }

        // Track the task
        LOGGER.log(Level.FINE, "Adding shutdown-hook task: ", task);
        tasks.add(task);

        return task;
    }

    private static synchronized void runTasks() {
        LOGGER.log(Level.FINE, "Running all shutdown-hook tasks");

        // Iterate through copy of tasks list
        for (Task task : tasks.toArray(new Task[0])) {
            LOGGER.log(Level.FINE, "Running task: ", task);
            try {
                task.run();
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Task failed", e);
            }
        }

        tasks.clear();
    }

    private static Thread addHook(final Thread thread) {
        LOGGER.log(Level.FINE, "Registering shutdown-hook: ", thread);
        try {
            Runtime.getRuntime().addShutdownHook(thread);
        } catch (AbstractMethodError e) {
            // JDK 1.3+ only method. Bummer.
            LOGGER.log(Level.FINE, "Failed to register shutdown-hook", e);
        }
        return thread;
    }

    /**
     * Removes a task from the shutdown hook.
     * If no tasks remain, the shutdown hook thread is unregistered.
     *
     * @param task the task to remove
     */
    public static synchronized void remove(final Task task) {
        assert task != null;

        // ignore if hook never installed
        if (hook == null) {
            return;
        }

        // Drop the task
        tasks.remove(task);

        // If there are no more tasks, then remove the hook thread
        if (tasks.isEmpty()) {
            removeHook(hook);
            hook = null;
        }
    }

    private static void removeHook(final Thread thread) {
        LOGGER.log(Level.FINE, "Removing shutdown-hook: ", thread);

        try {
            Runtime.getRuntime().removeShutdownHook(thread);
        } catch (AbstractMethodError e) {
            // JDK 1.3+ only method. Bummer.
            LOGGER.log(Level.FINE, "Failed to remove shutdown-hook", e);
        } catch (IllegalStateException e) {
            // The VM is shutting down, not a big deal; ignore
        }
    }

    /**
     * A task that can be executed on shutdown.
     * Similar to {@link Runnable} but allows throwing checked exceptions.
     */
    public interface Task {
        /**
         * Run the task.
         *
         * @throws Exception if an error occurs
         */
        void run() throws Exception;
    }
}
