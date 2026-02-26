package org.aesh.readline;

/**
 * Provides inline ghost text suggestions based on the current buffer content.
 */
@FunctionalInterface
public interface SuggestionProvider {
    /**
     * Returns the suggested suffix to display after the current buffer, or null for no suggestion.
     *
     * @param buffer the current input buffer content
     * @return the suggestion suffix, or null
     */
    String suggest(String buffer);
}
