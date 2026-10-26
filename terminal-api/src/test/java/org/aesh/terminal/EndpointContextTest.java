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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

public class EndpointContextTest {

    private static StreamConnection stream() {
        return new StreamConnection(StandardCharsets.UTF_8, new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("metadata must not read input");
            }
        }, new OutputStream() {
            @Override
            public void write(int value) {
                throw new AssertionError("metadata must not write output");
            }
        });
    }

    @Test
    public void testStreamOriginRemainsUnknownWithoutCreatorFacts() {
        try (StreamConnection connection = stream()) {
            EndpointContext context = connection.endpointContext();
            assertEquals(EndpointContext.Locality.UNKNOWN, context.locality());
            assertSame("metadata reads retain the current immutable snapshot",
                    context, connection.endpointContext());
            assertFalse("metadata must not start a reader", connection.reading());
            assertFalse("metadata must not infer ANSI support", connection.supportsAnsi());
        }
    }

    @Test
    public void testCreatorSuppliedOriginIsCapturedPerConnection() {
        try (StreamConnection first = stream(); StreamConnection second = stream()) {
            EndpointContext.Builder builder = EndpointContext.builder()
                    .locality(EndpointContext.Locality.REMOTE);
            EndpointContext remote = builder.build();
            first.setEndpointContext(remote);
            builder.locality(EndpointContext.Locality.LOCAL);

            assertSame(remote, first.endpointContext());
            assertEquals(EndpointContext.Locality.REMOTE, first.endpointContext().locality());
            assertEquals(EndpointContext.Locality.UNKNOWN, second.endpointContext().locality());
            assertFalse("supplying facts does not start input", first.reading());
        }
    }

    @Test
    public void testStreamFactsKeepConsoleAndTerminalEvidenceSeparate() {
        EndpointContext.StreamFacts pty = EndpointContext.StreamFacts.of(
                EndpointContext.Truth.YES, EndpointContext.Source.OBSERVED,
                EndpointContext.Truth.NO, EndpointContext.Source.OBSERVED);
        EndpointContext context = EndpointContext.builder()
                .locality(EndpointContext.Locality.LOCAL)
                .input(pty)
                .output(EndpointContext.StreamFacts.supplied(
                        EndpointContext.Truth.NO, EndpointContext.Truth.UNKNOWN))
                .build();
        try (StreamConnection connection = stream()) {
            connection.setEndpointContext(context);
            EndpointContext current = connection.endpointContext();
            assertSame(pty, current.input());
            assertEquals(EndpointContext.Truth.YES, current.input().terminal());
            assertEquals(EndpointContext.Truth.NO, current.input().nativeConsole());
            assertEquals(EndpointContext.Source.OBSERVED, current.input().terminalSource());
            assertEquals(EndpointContext.Truth.NO, current.output().terminal());
            assertEquals(EndpointContext.Source.CALLER, current.output().terminalSource());
            assertEquals(EndpointContext.Truth.UNKNOWN, current.output().nativeConsole());
            assertEquals(EndpointContext.Truth.UNKNOWN, current.error().terminal());
            assertEquals(EndpointContext.Source.UNKNOWN, current.error().terminalSource());
            assertFalse("terminal metadata is not permission to emit ANSI", connection.supportsAnsi());
        }
    }

    @Test
    public void testMissingDimensionsDifferFromExplicitFallback() {
        try (StreamConnection unknown = stream(); StreamConnection fallback = stream()) {
            assertNull("legacy size defaults are not measured dimensions",
                    unknown.endpointContext().dimensions());
            assertEquals(EndpointContext.Source.UNKNOWN, unknown.endpointContext().dimensionsSource());
            EndpointContext context = EndpointContext.builder()
                    .dimensions(new Size(120, 40), EndpointContext.Source.FALLBACK)
                    .build();
            fallback.setEndpointContext(context);
            assertEquals(new Size(120, 40), fallback.endpointContext().dimensions());
            assertEquals(EndpointContext.Source.FALLBACK, fallback.endpointContext().dimensionsSource());
            assertNull("one endpoint must not fill another endpoint's missing size",
                    unknown.endpointContext().dimensions());
        }
    }

    @Test
    public void testResizeSnapshotOutranksHintsWithoutChangingAnotherClient() {
        EndpointContext.StreamFacts input = EndpointContext.StreamFacts.supplied(
                EndpointContext.Truth.NO, EndpointContext.Truth.UNKNOWN);
        EndpointContext.StreamFacts output = EndpointContext.StreamFacts.supplied(
                EndpointContext.Truth.YES, EndpointContext.Truth.NO);
        EndpointContext.StreamFacts error = EndpointContext.StreamFacts.supplied(
                EndpointContext.Truth.NO, EndpointContext.Truth.NO);
        EndpointContext initial = EndpointContext.builder()
                .locality(EndpointContext.Locality.REMOTE)
                .input(input).output(output).error(error)
                .dimensions(new Size(90, 24), EndpointContext.Source.ENVIRONMENT)
                .build();
        try (StreamConnection first = stream(); StreamConnection second = stream()) {
            first.setEndpointContext(initial);
            second.setEndpointContext(initial);
            EndpointContext resized = first.endpointContext().withDimensions(
                    new Size(160, 50), EndpointContext.Source.NEGOTIATED);
            first.setEndpointContext(resized);

            assertEquals(new Size(160, 50), first.endpointContext().dimensions());
            assertEquals(EndpointContext.Source.NEGOTIATED, first.endpointContext().dimensionsSource());
            assertSame(input, first.endpointContext().input());
            assertSame(output, first.endpointContext().output());
            assertSame(error, first.endpointContext().error());
            assertSame(initial, second.endpointContext());
            assertEquals(new Size(90, 24), second.endpointContext().dimensions());
            assertSame("late environment hints cannot replace a negotiated size",
                    resized, resized.withDimensions(new Size(80, 20), EndpointContext.Source.ENVIRONMENT));
            assertSame("repeated size observations need no replacement snapshot",
                    resized, resized.withDimensions(new Size(160, 50), EndpointContext.Source.NEGOTIATED));
            assertEquals(EndpointContext.Locality.REMOTE, resized.locality());
        }
    }

    @Test
    public void testIncompleteDimensionsCannotClaimAnObservation() {
        try {
            EndpointContext.builder().dimensions(null, EndpointContext.Source.OBSERVED).build();
            fail("no size was supplied, so no measurement may be reported");
        } catch (IllegalArgumentException expected) {
        }
        try {
            EndpointContext.builder().dimensions(new Size(80, 24), EndpointContext.Source.UNKNOWN).build();
            fail("a supplied size needs provenance");
        } catch (IllegalArgumentException expected) {
        }
        Size invalid = new Size(1, 1) {
            @Override
            public int getWidth() {
                return 0;
            }
        };
        try {
            EndpointContext.builder().dimensions(invalid, EndpointContext.Source.OBSERVED).build();
            fail("invalid dimensions must not become Size's implicit fallback");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testClosedConnectionCannotResurrectEndpointFacts() {
        EndpointContext declared = EndpointContext.builder()
                .locality(EndpointContext.Locality.REMOTE)
                .dimensions(new Size(160, 50), EndpointContext.Source.NEGOTIATED)
                .build();
        StreamConnection connection = stream();
        try {
            connection.setEndpointContext(declared);
            connection.close();
            assertSame("closed endpoints no longer expose live observations",
                    EndpointContext.unknown(), connection.endpointContext());
            connection.setEndpointContext(declared);
            assertSame("late creator/resize writes cannot revive a closed endpoint",
                    EndpointContext.unknown(), connection.endpointContext());
            assertEquals("a previously captured snapshot remains a historical value",
                    new Size(160, 50), declared.dimensions());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testDelegatingContextReadsFollowTheEndpointWithoutCaching() {
        final StreamConnection endpoint = stream();
        try (StreamConnection wrapper = new StreamConnection(StandardCharsets.UTF_8,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()) {
            @Override
            public EndpointContext endpointContext() {
                return endpoint.endpointContext();
            }
        }) {
            EndpointContext first = EndpointContext.builder().locality(EndpointContext.Locality.REMOTE).build();
            endpoint.setEndpointContext(first);
            assertSame(first, wrapper.endpointContext());
            EndpointContext next = first.withDimensions(new Size(160, 50), EndpointContext.Source.NEGOTIATED);
            endpoint.setEndpointContext(next);
            assertSame(next, wrapper.endpointContext());
            endpoint.close();
            assertSame(EndpointContext.unknown(), wrapper.endpointContext());
        } finally {
            endpoint.close();
        }
    }

    @Test
    public void testCallerSizeSubclassCannotMutateCapturedDimensions() {
        final int[] width = { 160 };
        Size supplied = new Size(160, 50) {
            @Override
            public int getWidth() {
                return width[0];
            }
        };
        EndpointContext context = EndpointContext.builder()
                .dimensions(supplied, EndpointContext.Source.CALLER)
                .build();
        width[0] = 20;
        assertEquals(new Size(160, 50), context.dimensions());
        assertSame("reads return the retained immutable size without copying",
                context.dimensions(), context.dimensions());
    }

    @Test
    public void testClearingMetadataDoesNotReplaceCapabilityOwnership() {
        try (StreamConnection connection = stream()) {
            TerminalDetection owner = TerminalDetection.managed(null);
            connection.attachDetection(owner);
            connection.setEndpointContext(EndpointContext.builder()
                    .locality(EndpointContext.Locality.REMOTE).build());
            connection.setEndpointContext(null);
            assertSame(EndpointContext.unknown(), connection.endpointContext());
            assertSame("endpoint metadata and capability ownership are independent",
                    owner, connection.attachedDetection());
        }
    }

    @Test
    public void testKnownStreamAnswerRequiresProvenance() {
        try {
            EndpointContext.StreamFacts.of(EndpointContext.Truth.YES, EndpointContext.Source.UNKNOWN,
                    EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN);
            fail("a terminal assertion needs explicit provenance");
        } catch (IllegalArgumentException expected) {
        }
        try {
            EndpointContext.StreamFacts.of(EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN,
                    EndpointContext.Truth.NO, EndpointContext.Source.UNKNOWN);
            fail("a negative console assertion also needs provenance");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnvironmentCannotAssertTerminalStatus() {
        EndpointContext.StreamFacts.of(EndpointContext.Truth.YES, EndpointContext.Source.ENVIRONMENT,
                EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnvironmentCannotAssertNativeConsoleStatus() {
        EndpointContext.StreamFacts.of(EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN,
                EndpointContext.Truth.NO, EndpointContext.Source.ENVIRONMENT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFallbackCannotAssertTerminalStatus() {
        EndpointContext.StreamFacts.of(EndpointContext.Truth.NO, EndpointContext.Source.FALLBACK,
                EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFallbackCannotAssertNativeConsoleStatus() {
        EndpointContext.StreamFacts.of(EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN,
                EndpointContext.Truth.YES, EndpointContext.Source.FALLBACK);
    }
}
