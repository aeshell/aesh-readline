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
package org.aesh.terminal.tty;

/**
 * A persistent status line displayed between scrolling output and the prompt.
 * <p>
 * Status lines are registered via {@link org.aesh.terminal.Connection#registerStatusLine(int)}
 * and displayed in priority order (lowest priority at top, highest at bottom,
 * closest to the prompt). They persist across {@code printAbove()} calls and
 * are redrawn automatically when the prompt is redrawn.
 * <p>
 * Status lines support ANSI escape sequences for colors and styling.
 * <p>
 * Example:
 *
 * <pre>{@code
 * StatusLine buildStatus = connection.registerStatusLine(100);
 * buildStatus.setMessage("[Building] src/main/java...");
 * // ... later
 * buildStatus.setMessage("[Ready] Build complete in 1.2s");
 * // ... when no longer needed
 * buildStatus.close();
 * }</pre>
 */
public interface StatusLine {

    /**
     * Update the message displayed on this status line.
     * <p>
     * The message may contain ANSI escape sequences for styling.
     * Setting to null or empty string hides the line without closing it.
     * <p>
     * Thread-safe — can be called from any thread.
     *
     * @param message the message to display, or null to hide
     */
    void setMessage(String message);

    /**
     * Get the current message.
     *
     * @return the current message, or null if not set
     */
    String getMessage();

    /**
     * Remove this status line from the display.
     * <p>
     * After calling close(), the status line is no longer displayed and
     * cannot be reused. Calling setMessage() after close() has no effect.
     */
    void close();
}
