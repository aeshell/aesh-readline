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
package org.aesh.readline.benchmark;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.aesh.terminal.image.SixelImage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Sixel encoding cost across image shapes (#350).
 * <p>
 * Photo (many colors, bucket palette, memo misses), logo (flat blocks,
 * memo hits), narrow (band-heavy), and large (quantize-heavy) fixtures
 * are PNG-encoded once in setup; every invocation encodes a fresh
 * {@code SixelImage}, bypassing the output cache. Few warmup iterations:
 * each encode runs for milliseconds with hot inner loops, so JIT
 * thresholds trip inside single invocations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class SixelEncodeBenchmark {

    byte[] photo;
    byte[] logo;
    byte[] narrow;
    byte[] large;

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("fixture PNG encoding failed", e);
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        BufferedImage photoImage = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 480; y++) {
            for (int x = 0; x < 640; x++) {
                int red = (x * 255) / 639;
                int green = (y * 255) / 479;
                int blue = random.nextInt(256);
                photoImage.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        photo = png(photoImage);

        BufferedImage logoImage = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        int[] blocks = new int[] { 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00,
                0xFF00FF, 0x00FFFF, 0xFFFFFF, 0x000000 };
        for (int y = 0; y < 480; y++) {
            for (int x = 0; x < 640; x++) {
                logoImage.setRGB(x, y, blocks[(x / 80) % blocks.length]);
            }
        }
        logo = png(logoImage);

        BufferedImage narrowImage = new BufferedImage(120, 1200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 1200; y++) {
            for (int x = 0; x < 120; x++) {
                narrowImage.setRGB(x, y, blocks[(y / 150) % blocks.length]);
            }
        }
        narrow = png(narrowImage);

        BufferedImage largeImage = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 800; y++) {
            for (int x = 0; x < 1280; x++) {
                int red = (x * 255) / 1279;
                int green = (y * 255) / 799;
                int blue = (red + green) / 2;
                largeImage.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        large = png(largeImage);
    }

    @Benchmark
    public String encodePhoto() {
        return new SixelImage(photo).encode();
    }

    @Benchmark
    public String encodeLogo() {
        return new SixelImage(logo).encode();
    }

    @Benchmark
    public String encodeNarrow() {
        return new SixelImage(narrow).encode();
    }

    @Benchmark
    public String encodeLarge() {
        return new SixelImage(large).encode();
    }
}
