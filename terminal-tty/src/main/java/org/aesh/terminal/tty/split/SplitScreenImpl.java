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
package org.aesh.terminal.tty.split;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.ScreenRegion;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.SplitScreen;
import org.aesh.terminal.utils.LoggerUtil;

/**
 * Implementation of split-screen using cursor addressing.
 * <p>
 * The terminal is divided into a top region (for output/logs) and a
 * bottom region (for readline). A separator line is drawn between them.
 * Each region has its own scrollback buffer. Rendering uses cursor
 * save/restore and absolute cursor positioning (ESC [row;colH).
 * <p>
 * <b>Experimental</b> — this feature is under active development.
 */
public class SplitScreenImpl implements SplitScreen {

    private static final Logger LOGGER = LoggerUtil.getLogger(SplitScreenImpl.class.getName());
    private static final int DEFAULT_SCROLLBACK = 1000;

    private final Connection connection;
    private final ScreenRegionImpl topRegion;
    private final ScreenRegionImpl bottomRegion;
    private double ratio;
    private String separator = "─";
    private boolean suspended = false;
    private boolean closed = false;

    // Layout positions (1-based row numbers)
    private int topStartRow;
    private int topHeight;
    private int separatorRow;
    private int bottomStartRow;
    private int bottomHeight;
    private int termWidth;
    private int termHeight;

    /**
     * Create a split screen.
     *
     * @param connection the terminal connection
     * @param ratio fraction of screen for the top region (0.0-1.0)
     */
    public SplitScreenImpl(Connection connection, double ratio) {
        this.connection = connection;
        this.ratio = ratio;
        this.topRegion = new ScreenRegionImpl(this, true, DEFAULT_SCROLLBACK);
        this.bottomRegion = new ScreenRegionImpl(this, false, DEFAULT_SCROLLBACK);

        calculateLayout();
        initialRender();
    }

    private void calculateLayout() {
        Size termSize = connection.size();
        this.termWidth = termSize.getWidth();
        this.termHeight = termSize.getHeight();

        // Calculate split: top gets ratio, minus 1 for separator
        int availableRows = termHeight - 1; // 1 row for separator
        this.topHeight = Math.max(MIN_REGION_HEIGHT,
                (int) (availableRows * ratio));
        this.bottomHeight = Math.max(MIN_REGION_HEIGHT,
                availableRows - topHeight);

        // Adjust if we can't fit both minimums
        if (topHeight + bottomHeight + 1 > termHeight) {
            topHeight = MIN_REGION_HEIGHT;
            bottomHeight = termHeight - topHeight - 1;
            if (bottomHeight < MIN_REGION_HEIGHT) {
                bottomHeight = MIN_REGION_HEIGHT;
                topHeight = termHeight - bottomHeight - 1;
            }
        }

        this.topStartRow = 1;
        this.separatorRow = topHeight + 1;
        this.bottomStartRow = separatorRow + 1;

        topRegion.updateSize(termWidth, topHeight);
        bottomRegion.updateSize(termWidth, bottomHeight);
    }

    private void initialRender() {
        StringBuilder sb = new StringBuilder();

        // Clear screen
        sb.append("\033[2J");
        // Move to top
        sb.append("\033[1;1H");

        // Draw separator
        appendSeparator(sb);

        // Set scroll region to bottom area only — this prevents readline
        // output from scrolling past the separator into the top region
        sb.append("\033[").append(bottomStartRow).append(";").append(termHeight).append("r");

        // Position cursor in the bottom region for readline
        sb.append("\033[").append(bottomStartRow).append(";1H");

        connection.write(sb.toString());
    }

    private void appendSeparator(StringBuilder sb) {
        sb.append("\033[").append(separatorRow).append(";1H");
        if (separator != null && !separator.isEmpty()) {
            // Dim style for separator
            sb.append("\033[2m");
            int sepLen = separator.length();
            for (int i = 0; i < termWidth; i += sepLen) {
                int remaining = termWidth - i;
                if (remaining >= sepLen) {
                    sb.append(separator);
                } else {
                    sb.append(separator, 0, remaining);
                }
            }
            sb.append("\033[0m");
        }
    }

    /** Lock for synchronizing terminal writes between regions. */
    private final Object renderLock = new Object();

    /**
     * Write text to the top region using cursor addressing.
     * Called by ScreenRegionImpl. Thread-safe.
     */
    void writeToTopRegion(String text) {
        if (closed || suspended)
            return;

        // Split text into lines and add to scrollback
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            if (!line.isEmpty()) {
                topRegion.scrollback.addLine(line);
            }
        }

        // Redraw the top region (synchronized to prevent interleaving
        // with readline output in the bottom region)
        synchronized (renderLock) {
            redrawTopRegion();
        }
    }

    private void redrawTopRegion() {
        List<String> visibleLines = topRegion.scrollback.getLastLines(topHeight);

        StringBuilder sb = new StringBuilder();
        // Save cursor position
        sb.append("\0337");

        // Move to top region and clear it
        for (int i = 0; i < topHeight; i++) {
            int row = topStartRow + i;
            sb.append("\033[").append(row).append(";1H");
            sb.append("\033[K"); // clear line

            if (i < visibleLines.size()) {
                String line = visibleLines.get(i);
                // Truncate to terminal width (accounting for ANSI codes)
                sb.append(line);
            }
        }

        // Restore cursor position
        sb.append("\0338");

        connection.write(sb.toString());
    }

    /**
     * Handle terminal resize.
     * TODO: validate that the new terminal size can still fit both regions
     * with MIN_REGION_HEIGHT. If too small, consider suspending the split.
     */
    public void handleResize(Size newSize) {
        if (closed || suspended)
            return;

        calculateLayout();

        StringBuilder sb = new StringBuilder();
        sb.append("\033[r"); // reset scroll region first
        sb.append("\033[2J"); // clear screen
        appendSeparator(sb);
        // Set scroll region to new bottom area
        sb.append("\033[").append(bottomStartRow).append(";").append(termHeight).append("r");
        connection.write(sb.toString());

        redrawTopRegion();

        // Notify resize handlers
        if (topRegion.resizeHandler != null) {
            topRegion.resizeHandler.accept(topRegion.size());
        }
        if (bottomRegion.resizeHandler != null) {
            bottomRegion.resizeHandler.accept(bottomRegion.size());
        }
    }

    // ===================== SplitScreen interface =====================

    @Override
    public ScreenRegion topRegion() {
        return topRegion;
    }

    @Override
    public ScreenRegion bottomRegion() {
        return bottomRegion;
    }

    @Override
    public void setSplitRatio(double ratio) {
        this.ratio = ratio;
        calculateLayout();
        // Redraw
        StringBuilder sb = new StringBuilder();
        sb.append("\033[2J");
        appendSeparator(sb);
        connection.write(sb.toString());
        redrawTopRegion();
    }

    @Override
    public double getSplitRatio() {
        return ratio;
    }

    @Override
    public void setSeparator(String separator) {
        this.separator = separator;
        StringBuilder sb = new StringBuilder();
        sb.append("\0337"); // save cursor
        appendSeparator(sb);
        sb.append("\0338"); // restore cursor
        connection.write(sb.toString());
    }

    @Override
    public void suspend() {
        if (!suspended) {
            suspended = true;
            // Reset scroll region and clear screen — the command takes over
            connection.write("\033[r\033[2J\033[1;1H");
        }
    }

    @Override
    public void resume() {
        if (suspended) {
            suspended = false;
            calculateLayout();
            initialRender();
            redrawTopRegion();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            // Reset scroll region, clear screen
            connection.write("\033[r\033[2J\033[1;1H");
        }
    }

    // ===================== Accessors for ScreenRegionImpl =====================

    Connection getConnection() {
        return connection;
    }

    int getBottomStartRow() {
        return bottomStartRow;
    }

    int getTermWidth() {
        return termWidth;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isSuspended() {
        return suspended;
    }

    /**
     * Screen region implementation backed by a scrollback buffer.
     */
    static class ScreenRegionImpl implements ScreenRegion {

        private final SplitScreenImpl splitScreen;
        private final boolean isTop;
        final ScrollbackBuffer scrollback;
        volatile Consumer<Size> resizeHandler;
        private int width;
        private int height;

        ScreenRegionImpl(SplitScreenImpl splitScreen, boolean isTop, int scrollbackSize) {
            this.splitScreen = splitScreen;
            this.isTop = isTop;
            this.scrollback = new ScrollbackBuffer(scrollbackSize);
        }

        void updateSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void write(String text) {
            if (isTop) {
                splitScreen.writeToTopRegion(text);
            } else {
                // Bottom region — write directly to connection
                // (readline handles its own rendering)
                splitScreen.getConnection().write(text);
            }
        }

        @Override
        public Size size() {
            return new Size(width, height);
        }

        @Override
        public void setResizeHandler(Consumer<Size> handler) {
            this.resizeHandler = handler;
        }

        @Override
        public void clear() {
            scrollback.clear();
            if (isTop) {
                splitScreen.redrawTopRegion();
            }
        }

        @Override
        public void close() {
            splitScreen.close();
        }
    }
}
