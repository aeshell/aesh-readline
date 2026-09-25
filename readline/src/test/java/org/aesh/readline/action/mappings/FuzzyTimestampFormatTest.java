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
package org.aesh.readline.action.mappings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

/**
 * Tests for timestamp formatting in {@link FuzzySearchHistory}.
 * <p>
 * The formatter must be thread-safe: the search UI can render from
 * multiple connections concurrently, which corrupted output with the
 * previous static {@code SimpleDateFormat}.
 */
public class FuzzyTimestampFormatTest {

    @Test
    public void testFormatMatchesExpectedPattern() {
        assertTrue(FuzzySearchHistory.formatTimestamp(1719792000000L)
                .matches("\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }

    @Test
    public void testFormatThreadSafe() throws Exception {
        long base = 1719792000000L;
        List<Long> stamps = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            stamps.add(base + i * 61_000L);
        }
        List<String> expected = new ArrayList<>();
        for (Long stamp : stamps) {
            expected.add(FuzzySearchHistory.formatTimestamp(stamp));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(new FormatTask(stamps)));
            }
            for (Future<List<String>> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class FormatTask implements Callable<List<String>> {
        private final List<Long> stamps;

        FormatTask(List<Long> stamps) {
            this.stamps = stamps;
        }

        @Override
        public List<String> call() {
            List<String> out = new ArrayList<>();
            for (Long stamp : stamps) {
                out.add(FuzzySearchHistory.formatTimestamp(stamp));
            }
            return out;
        }
    }
}
