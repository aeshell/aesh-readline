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
package org.aesh.readline.history;

import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for managing command line history.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public abstract class History {

    /**
     * Protected constructor for subclasses.
     */
    protected History() {
    }

    private boolean enabled = true;

    /**
     * Checks if history is enabled.
     *
     * @return true if history is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables history tracking.
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * Disables history tracking.
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * Pushes a new entry to the history.
     *
     * @param entry the entry to add
     */
    public abstract void push(int[] entry);

    /**
     * Finds an entry in the history that matches the search.
     *
     * @param search the search pattern
     * @return the matching entry, or null if not found
     */
    public abstract int[] find(int[] search);

    /**
     * Gets the history entry at the specified index.
     *
     * @param index the index of the entry to retrieve
     * @return the history entry at the specified index
     */
    public abstract int[] get(int index);

    /**
     * Returns the number of entries in the history.
     *
     * @return the size of the history
     */
    public abstract int size();

    /**
     * Sets the search direction for history searches.
     *
     * @param direction the search direction
     */
    public abstract void setSearchDirection(SearchDirection direction);

    /**
     * Gets the current search direction.
     *
     * @return the current search direction
     */
    public abstract SearchDirection getSearchDirection();

    /**
     * Gets the next entry in the history (moving forward).
     *
     * @return the next history entry, or null if at the end
     * @deprecated Use {@link #nextFetch()} instead for null-safe Optional handling.
     */
    @Deprecated
    public abstract int[] getNextFetch();

    /**
     * Gets the previous entry in the history (moving backward).
     *
     * @return the previous history entry, or empty array if at the beginning
     * @deprecated Use {@link #previousFetch()} instead for null-safe Optional handling.
     */
    @Deprecated
    public abstract int[] getPreviousFetch();

    /**
     * Fetch the next history entry.
     *
     * @return an Optional containing the next history entry, or empty if at the end
     */
    public Optional<int[]> nextFetch() {
        int[] result = getNextFetch();
        return Optional.ofNullable(result);
    }

    /**
     * Fetch the previous history entry.
     *
     * @return an Optional containing the previous history entry, or empty if at the start
     */
    public Optional<int[]> previousFetch() {
        int[] result = getPreviousFetch();
        if (result != null && result.length == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(result);
    }

    /**
     * Searches for an entry in the history that contains the search pattern.
     *
     * @param search the search pattern
     * @return the matching entry, or null if not found
     */
    public abstract int[] search(int[] search);

    /**
     * Sets the current line being edited (used for history navigation).
     *
     * @param line the current line
     */
    public abstract void setCurrent(int[] line);

    /**
     * Gets the current line being edited.
     *
     * @return the current line
     */
    public abstract int[] getCurrent();

    /**
     * Gets all entries in the history.
     *
     * @return a list of all history entries
     */
    public abstract List<int[]> getAll();

    /**
     * Clears all entries from the history.
     */
    public abstract void clear();

    /**
     * Stops the history and performs any necessary cleanup (e.g., saving to file).
     */
    public abstract void stop();

}
