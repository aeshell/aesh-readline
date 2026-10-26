/*
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
package org.aesh.terminal;

import java.util.Objects;

import org.aesh.terminal.tty.Size;

/**
 * Immutable creator-supplied facts about one connection endpoint.
 * Reading this value performs no probing and grants no input ownership
 * or discovery permission. Missing facts remain unknown. Sources describe
 * the evidence supplied by a creator; they are not validation performed by
 * this value. Providers must still validate the concrete handles they open.
 *
 * @since 3.18.8
 */
public final class EndpointContext {

    /** Origin known by the endpoint creator, never inferred from host hints. */
    public enum Locality {
        UNKNOWN,
        LOCAL,
        REMOTE
    }

    /** A missing answer is distinct from a negative observation. */
    public enum Truth {
        UNKNOWN,
        YES,
        NO
    }

    /**
     * Where one supplied fact came from; no source grants I/O permission.
     * ENVIRONMENT and FALLBACK describe dimensions, not terminal or console proof.
     */
    public enum Source {
        UNKNOWN,
        CALLER,
        OBSERVED,
        FALLBACK,
        ENVIRONMENT,
        NEGOTIATED;

        private int dimensionPriority() {
            switch (this) {
                case UNKNOWN:
                    return 0;
                case FALLBACK:
                    return 1;
                case ENVIRONMENT:
                    return 2;
                default:
                    return 3;
            }
        }
    }

    /** Independent terminal and native-console facts for one endpoint stream. */
    public static final class StreamFacts {
        private static final StreamFacts UNKNOWN = new StreamFacts(
                Truth.UNKNOWN, Source.UNKNOWN, Truth.UNKNOWN, Source.UNKNOWN);

        private final Truth terminal;
        private final Source terminalSource;
        private final Truth nativeConsole;
        private final Source nativeConsoleSource;

        private StreamFacts(Truth terminal, Source terminalSource,
                Truth nativeConsole, Source nativeConsoleSource) {
            this.terminal = Objects.requireNonNull(terminal, "terminal");
            this.terminalSource = checkedSource(terminal, terminalSource, "terminalSource");
            this.nativeConsole = Objects.requireNonNull(nativeConsole, "nativeConsole");
            this.nativeConsoleSource = checkedSource(nativeConsole, nativeConsoleSource, "nativeConsoleSource");
        }

        private static Source checkedSource(Truth answer, Source source, String name) {
            Objects.requireNonNull(source, name);
            if (answer != Truth.UNKNOWN && source != Source.CALLER
                    && source != Source.OBSERVED && source != Source.NEGOTIATED) {
                throw new IllegalArgumentException("a known stream answer requires caller or transport evidence: " + name);
            }
            return source;
        }

        /**
         * No observations have been supplied for this stream.
         *
         * @return shared unknown facts
         */
        public static StreamFacts unknown() {
            return UNKNOWN;
        }

        /**
         * Capture creator declarations without probing the stream.
         *
         * @param terminal whether this stream is a terminal
         * @param nativeConsole whether it is a native console
         * @return the supplied facts; omitted answers remain unknown
         */
        public static StreamFacts supplied(Truth terminal, Truth nativeConsole) {
            return of(terminal, terminal == Truth.UNKNOWN ? Source.UNKNOWN : Source.CALLER,
                    nativeConsole, nativeConsole == Truth.UNKNOWN ? Source.UNKNOWN : Source.CALLER);
        }

        /**
         * Capture both answers with their independent provenance.
         * A failed native-console check must not replace the terminal answer.
         *
         * @param terminal terminal status, never null
         * @param terminalSource origin of the terminal answer
         * @param nativeConsole native-console status, never null
         * @param nativeConsoleSource origin of the native-console answer
         * @return immutable stream facts
         * @throws IllegalArgumentException if a known answer does not have
         *         CALLER, OBSERVED or NEGOTIATED provenance
         */
        public static StreamFacts of(Truth terminal, Source terminalSource,
                Truth nativeConsole, Source nativeConsoleSource) {
            return new StreamFacts(terminal, terminalSource, nativeConsole, nativeConsoleSource);
        }

        /**
         * Read this stream's terminal status.
         *
         * @return terminal status, not an ANSI or interactivity predicate
         */
        public Truth terminal() {
            return terminal;
        }

        /**
         * Read the terminal answer's provenance.
         *
         * @return origin of the terminal answer
         */
        public Source terminalSource() {
            return terminalSource;
        }

        /**
         * Read this stream's native-console status.
         *
         * @return native-console status, independent of terminal status
         */
        public Truth nativeConsole() {
            return nativeConsole;
        }

        /**
         * Read the native-console answer's provenance.
         *
         * @return origin of the native-console answer
         */
        public Source nativeConsoleSource() {
            return nativeConsoleSource;
        }
    }

    private static final EndpointContext UNKNOWN = new EndpointContext(new Builder());

    private final Locality locality;
    private final StreamFacts input;
    private final StreamFacts output;
    private final StreamFacts error;
    private final Size dimensions;
    private final Source dimensionsSource;

    private EndpointContext(Builder builder) {
        this.locality = builder.locality;
        this.input = builder.input;
        this.output = builder.output;
        this.error = builder.error;
        this.dimensions = captureDimensions(builder.dimensions, builder.dimensionsSource);
        this.dimensionsSource = builder.dimensionsSource;
    }

    private EndpointContext(EndpointContext previous, Size dimensions, Source source) {
        this.locality = previous.locality;
        this.input = previous.input;
        this.output = previous.output;
        this.error = previous.error;
        this.dimensions = dimensions;
        this.dimensionsSource = source;
    }

    private static Size captureDimensions(Size dimensions, Source source) {
        if (dimensions == null) {
            if (source != Source.UNKNOWN) {
                throw new IllegalArgumentException("absent dimensions require UNKNOWN provenance");
            }
            return null;
        }
        if (source == Source.UNKNOWN) {
            throw new IllegalArgumentException("supplied dimensions require provenance");
        }
        int width = dimensions.getWidth();
        int height = dimensions.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        return new Size(width, height);
    }

    /**
     * Shared context with no supplied endpoint facts.
     *
     * @return the unknown context, never null
     */
    public static EndpointContext unknown() {
        return UNKNOWN;
    }

    /**
     * Create endpoint facts, leaving omitted facts unknown.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Origin supplied by the creator of this endpoint.
     *
     * @return the known locality, or UNKNOWN
     */
    public Locality locality() {
        return locality;
    }

    /**
     * Read input facts without examining process stdin.
     *
     * @return facts about the endpoint's input, never null
     */
    public StreamFacts input() {
        return input;
    }

    /**
     * Read output facts without examining process stdout.
     *
     * @return facts about the endpoint's output, never null
     */
    public StreamFacts output() {
        return output;
    }

    /**
     * Read error-stream facts independently of output facts.
     *
     * @return facts about the endpoint's error stream, never null
     */
    public StreamFacts error() {
        return error;
    }

    /**
     * Captured dimensions, distinct from legacy {@link Connection#size()} defaults.
     *
     * @return the retained immutable size, or null when no size was supplied
     */
    public Size dimensions() {
        return dimensions;
    }

    /**
     * Provenance of the captured dimensions.
     *
     * @return the size source, or UNKNOWN when absent
     */
    public Source dimensionsSource() {
        return dimensionsSource;
    }

    /**
     * Capture a size update without changing other endpoint facts.
     * Observed, negotiated and caller-supplied sizes outrank environment
     * hints, which outrank fallbacks. Equally authoritative updates replace
     * older values. Existing snapshots remain unchanged.
     *
     * @param dimensions the new size
     * @param source its provenance, never null
     * @return the updated snapshot, or this one when the update adds no fact
     * @throws IllegalArgumentException if dimensions are invalid or their
     *         presence and provenance disagree
     */
    public EndpointContext withDimensions(Size dimensions, Source source) {
        Objects.requireNonNull(source, "source");
        Size captured = captureDimensions(dimensions, source);
        if (source.dimensionPriority() < dimensionsSource.dimensionPriority()) {
            return this;
        }
        if (source == dimensionsSource && Objects.equals(captured, this.dimensions)) {
            return this;
        }
        return new EndpointContext(this, captured, source);
    }

    /** Builder whose values are captured independently on each build. */
    public static final class Builder {
        private Locality locality = Locality.UNKNOWN;
        private StreamFacts input = StreamFacts.unknown();
        private StreamFacts output = StreamFacts.unknown();
        private StreamFacts error = StreamFacts.unknown();
        private Size dimensions;
        private Source dimensionsSource = Source.UNKNOWN;

        private Builder() {
        }

        /**
         * Supply the origin known by the creator.
         *
         * @param locality the declared locality, never null
         * @return this builder
         */
        public Builder locality(Locality locality) {
            this.locality = Objects.requireNonNull(locality, "locality");
            return this;
        }

        /**
         * Supply input facts for this endpoint, without examining any fd.
         *
         * @param input the facts, never null
         * @return this builder
         */
        public Builder input(StreamFacts input) {
            this.input = Objects.requireNonNull(input, "input");
            return this;
        }

        /**
         * Supply output facts independently of input facts.
         *
         * @param output the facts, never null
         * @return this builder
         */
        public Builder output(StreamFacts output) {
            this.output = Objects.requireNonNull(output, "output");
            return this;
        }

        /**
         * Supply error-stream facts independently of output facts.
         *
         * @param error the facts, never null
         * @return this builder
         */
        public Builder error(StreamFacts error) {
            this.error = Objects.requireNonNull(error, "error");
            return this;
        }

        /**
         * Supply dimensions with explicit provenance, without reading a terminal.
         * A fallback must be identified as such, not marked as observed.
         *
         * @param dimensions the size, or null when unknown
         * @param source its origin, never null
         * @return this builder
         * @throws IllegalArgumentException on build if dimensions are invalid or
         *         their presence and provenance disagree
         */
        public Builder dimensions(Size dimensions, Source source) {
            this.dimensions = dimensions;
            this.dimensionsSource = Objects.requireNonNull(source, "source");
            return this;
        }

        /**
         * Capture the supplied facts as an immutable snapshot.
         *
         * @return the context
         */
        public EndpointContext build() {
            return new EndpointContext(this);
        }
    }
}
