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
 * Manages a split-screen terminal layout where the screen is divided
 * into independently scrolling regions.
 * <p>
 * Created via {@link org.aesh.terminal.Connection#splitScreen(double)}.
 * The original screen becomes the "main" region (typically bottom, where
 * readline operates). The split creates a new region (typically top,
 * for output/logs).
 * <p>
 * <b>Experimental API</b> — this feature is under active development
 * and the API may change.
 *
 * @see org.aesh.terminal.Connection#splitScreen(double)
 * @see ScreenRegion
 */
public interface SplitScreen {

    /** Minimum number of rows a region can have. */
    int MIN_REGION_HEIGHT = 3;

    /**
     * Returns the new region created by the split (top region).
     *
     * @return the top screen region
     */
    ScreenRegion topRegion();

    /**
     * Returns the main/original region (bottom region, where readline operates).
     *
     * @return the bottom screen region
     */
    ScreenRegion bottomRegion();

    /**
     * Change the split ratio.
     *
     * @param ratio the new ratio (0.0-1.0, fraction of screen for the top region)
     */
    void setSplitRatio(double ratio);

    /**
     * Get the current split ratio.
     *
     * @return the ratio (fraction of screen for the top region)
     */
    double getSplitRatio();

    /**
     * Set the separator line style.
     * <p>
     * The separator is drawn between the top and bottom regions.
     * The string is repeated to fill the terminal width. Use null
     * or empty string for no separator.
     *
     * @param separator the separator character(s), e.g. "─" or "="
     */
    void setSeparator(String separator);

    /**
     * Suspend the split-screen layout temporarily.
     * <p>
     * Used when a command needs the full screen (e.g., entering alternate
     * screen buffer for an editor or pager). The split layout is hidden
     * and can be restored with {@link #resume()}.
     */
    void suspend();

    /**
     * Resume the split-screen layout after a {@link #suspend()}.
     * Redraws the separator and all regions.
     */
    void resume();

    /**
     * Close the split-screen layout and restore the terminal to
     * a single full-screen region.
     */
    void close();
}
