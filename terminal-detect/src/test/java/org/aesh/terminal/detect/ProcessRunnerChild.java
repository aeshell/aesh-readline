package org.aesh.terminal.detect;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Named-package child JVM fixture, never a substitute executable on PATH. */
public final class ProcessRunnerChild {

    private ProcessRunnerChild() {
    }

    public static void main(String[] args) throws Exception {
        Path marker = Paths.get(args[0]);
        String mode = args[1];
        try (RandomAccessFile file = new RandomAccessFile(marker.toFile(), "rw");
                FileLock lock = file.getChannel().lock()) {
            file.write(("started:" + mode + "\n").getBytes(StandardCharsets.US_ASCII));
            Thread watchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Runtime.getRuntime().halt(97);
                }
            }, "process-fixture-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            if ("flood-stderr".equals(mode)) {
                byte[] output = new byte[8192];
                Arrays.fill(output, (byte) 'x');
                int remaining = ProcessRunner.DEFAULT_MAX_OUTPUT_BYTES * 3;
                while (remaining > 0) {
                    int count = Math.min(remaining, output.length);
                    System.err.write(output, 0, count);
                    remaining -= count;
                }
                System.err.flush();
            } else if ("fork-descendant".equals(mode) || "fork-and-hold".equals(mode)) {
                Path descendant = Paths.get(args[2]);
                String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
                File java = new File(new File(System.getProperty("java.home"), "bin"), executable);
                new ProcessBuilder(java.getAbsolutePath(), "-cp", System.getProperty("java.class.path"),
                        ProcessRunnerChild.class.getName(), descendant.toString(), "hold", args[3])
                        .inheritIO().start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(descendant) || Files.size(descendant) == 0) {
                    if (System.nanoTime() >= deadline) {
                        throw new AssertionError("descendant never reached its startup marker");
                    }
                    Thread.sleep(10);
                }
                if ("fork-and-hold".equals(mode)) {
                    awaitStop(Paths.get(args[3]));
                }
            } else if ("hold".equals(mode)) {
                awaitStop(Paths.get(args[2]));
            } else {
                throw new IllegalArgumentException("unknown fixture mode " + mode);
            }
            file.write("finished\n".getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void awaitStop(Path stop) throws InterruptedException {
        while (!Files.exists(stop)) {
            Thread.sleep(10);
        }
    }
}
