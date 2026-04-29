package org.aesh.readline;

/**
 * A suggestion provider that chains multiple providers, returning the first non-null result.
 */
public class CompositeSuggestionProvider implements SuggestionProvider {

    /** Field. */
    private final SuggestionProvider[] providers;

    /**
     * Constructor.
     *
     * @param providers the suggestion providers to chain
     */
    public CompositeSuggestionProvider(SuggestionProvider... providers) {
        this.providers = providers;
    }

    @Override
    public String suggest(String buffer) {
        for (SuggestionProvider p : providers) {
            String s = p.suggest(buffer);
            if (s != null) {
                return s;
            }
        }
        return null;
    }
}
