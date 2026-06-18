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
 * Represents a mouse event from the terminal.
 * <p>
 * Mouse events are produced when mouse tracking is enabled via
 * {@link MouseTracking} and the terminal sends mouse reports.
 * The event includes the type (press, release, drag, move, scroll),
 * the button involved, the position (1-based), and modifier keys.
 *
 * @see MouseTracking
 */
public final class MouseEvent {

    /**
     * The type of mouse event.
     */
    public enum Type {
        /** A button was pressed. */
        PRESS,
        /** A button was released. */
        RELEASE,
        /** The mouse was moved while a button was held (drag). */
        DRAG,
        /** The mouse was moved with no button held (hover). */
        MOVE,
        /** The scroll wheel was used. */
        SCROLL
    }

    /**
     * The mouse button involved in the event.
     */
    public enum Button {
        /** Left mouse button. */
        LEFT,
        /** Middle mouse button. */
        MIDDLE,
        /** Right mouse button. */
        RIGHT,
        /** Scroll wheel up. */
        SCROLL_UP,
        /** Scroll wheel down. */
        SCROLL_DOWN,
        /** No button (used for move events and legacy release). */
        NONE
    }

    private final Type type;
    private final Button button;
    private final int x;
    private final int y;
    private final boolean shift;
    private final boolean alt;
    private final boolean ctrl;

    /**
     * Creates a new mouse event.
     *
     * @param type the event type
     * @param button the button
     * @param x the column (1-based)
     * @param y the row (1-based)
     * @param shift true if shift was held
     * @param alt true if alt/meta was held
     * @param ctrl true if ctrl was held
     */
    public MouseEvent(Type type, Button button, int x, int y,
            boolean shift, boolean alt, boolean ctrl) {
        this.type = type;
        this.button = button;
        this.x = x;
        this.y = y;
        this.shift = shift;
        this.alt = alt;
        this.ctrl = ctrl;
    }

    /**
     * Returns the event type.
     *
     * @return the event type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the button.
     *
     * @return the button
     */
    public Button button() {
        return button;
    }

    /**
     * Returns the column (1-based).
     *
     * @return the column position
     */
    public int x() {
        return x;
    }

    /**
     * Returns the row (1-based).
     *
     * @return the row position
     */
    public int y() {
        return y;
    }

    /**
     * Returns true if shift was held.
     *
     * @return true if shift was held
     */
    public boolean shift() {
        return shift;
    }

    /**
     * Returns true if alt/meta was held.
     *
     * @return true if alt/meta was held
     */
    public boolean alt() {
        return alt;
    }

    /**
     * Returns true if ctrl was held.
     *
     * @return true if ctrl was held
     */
    public boolean ctrl() {
        return ctrl;
    }

    // =========================================================================
    // SGR mouse parsing
    // =========================================================================

    /**
     * Parses an SGR mouse event from a CSI dispatch.
     * <p>
     * SGR format: {@code CSI < Pb ; Px ; Py M} (press/drag/move/scroll)
     * or {@code CSI < Pb ; Px ; Py m} (release).
     * <p>
     * This method is designed to be called directly from a
     * {@link org.aesh.terminal.parser.VtHandler#csiDispatch} callback
     * when the private marker is {@code <}.
     *
     * @param finalChar the final character ('M' for press, 'm' for release)
     * @param params the CSI parameters [Pb, Px, Py]
     * @param paramCount the number of parameters (must be 3)
     * @return the parsed mouse event, or null if the parameters are invalid
     */
    public static MouseEvent parseSgr(int finalChar, int[] params, int paramCount) {
        if (paramCount < 3) {
            return null;
        }

        int pb = params[0];
        int px = params[1];
        int py = params[2];

        if (px < 1 || py < 1) {
            return null;
        }

        boolean sgrRelease = (finalChar == 'm');

        return decodeButton(pb, px, py, sgrRelease);
    }

    /**
     * Checks if a CSI dispatch represents an SGR mouse event.
     *
     * @param finalChar the CSI final character
     * @param intermediates the intermediate/private-marker characters
     * @param intermediateCount the number of intermediates
     * @return true if this is an SGR mouse event
     */
    public static boolean isSgrMouseEvent(int finalChar, int[] intermediates, int intermediateCount) {
        return intermediateCount > 0
                && intermediates[0] == '<'
                && (finalChar == 'M' || finalChar == 'm');
    }

    // =========================================================================
    // Button byte decoding (shared across encodings)
    // =========================================================================

    /**
     * Decodes the button byte into a MouseEvent.
     * <p>
     * The button byte bitfield (after removing +32 offset for legacy):
     *
     * <pre>
     * bits 0-1 (0x03): button index  0=left, 1=middle, 2=right, 3=none/release
     * bit 2    (0x04): shift modifier
     * bit 3    (0x08): alt/meta modifier
     * bit 4    (0x10): ctrl modifier
     * bit 5    (0x20): motion event flag
     * bit 6    (0x40): scroll wheel flag
     * </pre>
     */
    private static MouseEvent decodeButton(int pb, int x, int y, boolean sgrRelease) {
        int buttonIndex = pb & 0x03;
        boolean shiftMod = (pb & 0x04) != 0;
        boolean altMod = (pb & 0x08) != 0;
        boolean ctrlMod = (pb & 0x10) != 0;
        boolean motion = (pb & 0x20) != 0;
        boolean scroll = (pb & 0x40) != 0;

        Type type;
        Button button;

        if (scroll) {
            type = Type.SCROLL;
            button = (buttonIndex == 0) ? Button.SCROLL_UP : Button.SCROLL_DOWN;
        } else if (sgrRelease) {
            type = Type.RELEASE;
            button = buttonFromIndex(buttonIndex);
        } else if (!motion && buttonIndex == 3) {
            // Legacy/URXVT: release without button identification
            type = Type.RELEASE;
            button = Button.NONE;
        } else if (motion && buttonIndex == 3) {
            // Hover: motion with no button held
            type = Type.MOVE;
            button = Button.NONE;
        } else if (motion) {
            type = Type.DRAG;
            button = buttonFromIndex(buttonIndex);
        } else {
            type = Type.PRESS;
            button = buttonFromIndex(buttonIndex);
        }

        return new MouseEvent(type, button, x, y, shiftMod, altMod, ctrlMod);
    }

    private static Button buttonFromIndex(int index) {
        switch (index) {
            case 0:
                return Button.LEFT;
            case 1:
                return Button.MIDDLE;
            case 2:
                return Button.RIGHT;
            default:
                return Button.NONE;
        }
    }

    // =========================================================================
    // Object methods
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MouseEvent))
            return false;
        MouseEvent that = (MouseEvent) o;
        return type == that.type && button == that.button
                && x == that.x && y == that.y
                && shift == that.shift && alt == that.alt && ctrl == that.ctrl;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + button.hashCode();
        result = 31 * result + x;
        result = 31 * result + y;
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MouseEvent{");
        sb.append(type).append(' ').append(button);
        sb.append(" at (").append(x).append(',').append(y).append(')');
        if (shift)
            sb.append(" +Shift");
        if (alt)
            sb.append(" +Alt");
        if (ctrl)
            sb.append(" +Ctrl");
        sb.append('}');
        return sb.toString();
    }
}
