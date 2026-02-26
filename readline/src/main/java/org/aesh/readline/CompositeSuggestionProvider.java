package org.aesh.readline;

/**
 * A suggestion provider that chains multiple providers, returning the first non-null result.
 */
public class CompositeSuggestionProvider implements SuggestionProvider {

    private final SuggestionProvider[] providers;

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
