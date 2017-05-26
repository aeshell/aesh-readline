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
package org.aesh.readline;

import org.aesh.readline.example.SimpleTestExample;
import org.aesh.utils.Config;
import org.aesh.util.LoggerUtil;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;


public class TestReadlineInSeparateProcess {

    private static final Logger LOGGER = LoggerUtil.getLogger(TestReadlineInSeparateProcess.class.getName());
    private static final String EMBED_SERVER = "embed-server";
    private static final int TIMEOUT = Integer.getInteger("start.timeout", 30);
    private static final String EMBED_SERVER_PROMPT =
            EMBED_SERVER +
            Config.getLineSeparator()
            + "=====> embed-server" + Config.getLineSeparator();
    private CharPumper outPump;
    private BufferedWriter writer;

    @Before
    public void posixOnly() {
        assumeTrue(Config.isOSPOSIXCompatible());
    }

    /**
     * Send a line to the process input. Use null for empty line.
     *
     * @param line Line to we want to send to process input.
     */
    private void sendLine(final String line) {
        //LOGGER.info("Line Sent: \n" + line);
        try {
            // to make sure that all is printed
            Thread.sleep(50L);
            outPump.clearOutput();
            Thread.sleep(50L);
            writer.write(line);
            writer.write(Config.getLineSeparator());
            writer.flush();
        }
        catch (Exception ex) {
            //LOGGER.info(ex.getMessage());
        }
    }

    /**
     * Wait for expected output from process, fail the test if the expected string is not a part of the output.
     * Wait time is determined by TIMEOUT variable.
     * <p/>
     * Be cautious, while choosing the right expected string, as the output from the process contains also the line
     * passed by sendLine() method.
     *
     * @param expected     String we are expect on the output of the process.
     * @param errorMessage Message which will be a part of assertion violation message.
     * @return Output of the process if expected string was found, fail the test otherwise.
     */
    private String waitFor(final String expected, final String errorMessage) {
        boolean found = false;
        for (int i = 0; i < TIMEOUT * 100; i++) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                // ignore
            }
            if (outPump.getOutput().contains(expected)) {
                found = true;
                break;
            }
        }
        String out = outPump.clearOutput();
        // print output to the console temporarily
        //LOGGER.info("Expected String: \n" + expected);
        //LOGGER.info("Output Found: \n" + out);
        assertEquals(expected, out);
        if (!found) {
            throw new RuntimeException(String.format("%s\nFailed to found expected <%s> in following output:\n%s",
                    errorMessage, expected, out));
        }
        return out;
    }





    /**
     * Start the cli process
     */
    @Test
    public void startProcessClassPath() throws Exception {
        String java = System.getProperty("java.home") + Config.getPathSeparator() + "bin" + Config.getPathSeparator() + "java";
        String classpath = Arrays.stream(((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs())
                .map(URL::getFile)
                .collect(Collectors.joining(File.pathSeparator));

        ProcessBuilder pb = new ProcessBuilder(
                java,
                "-classpath",
                classpath,
                SimpleTestExample.class.getCanonicalName());

        //LOGGER.info("using classpath: "+classpath);

        testReadlineByProcessBuilder(pb);
    }


    /**
     * Start the cli process
     */
    @Test
    public void startProcessJar() throws Exception {
        String jarName = String.format("target%stest.jar", File.separator);
        String java = System.getProperty("java.home") + Config.getPathSeparator() + "bin" + Config.getPathSeparator() + "java";

        JavaArchive archive = ShrinkWrap.create(MavenImporter.class).loadPomFromFile("pom.xml").importBuildOutput().importBuildOutput().as(JavaArchive.class);
        archive.addAsManifestResource(new StringAsset(String.format("Manifest-Version: 1.0\n" + "Main-Class: %s\n", SimpleTestExample.class.getCanonicalName())), "MANIFEST.MF");
        archive.addClass(SimpleTestExample.class);
        File fileArchive = new File(jarName);
        archive.as(ZipExporter.class).exportTo(fileArchive, true);


        ProcessBuilder pb = new ProcessBuilder(
                java,
                "-jar",
                jarName);
        testReadlineByProcessBuilder(pb);
    }

    private void testReadlineByProcessBuilder(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true);
        Process process = pb.start();

        Thread.sleep(200);

        // consume process input & error streams
        final InputStreamReader isr = new InputStreamReader(process.getInputStream());
        outPump = new CharPumper(isr);
        outPump.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        sendLine(EMBED_SERVER);
        waitFor(EMBED_SERVER_PROMPT, "Failed to start the embedded EAP instance.");
        sendLine("exit");
    }
    /**
     * Help class for consumption of process output streams.
     */
    private class CharPumper extends Thread {
        // do not use BufferedReader - sometime is the last line of output same as line for input
        private final InputStreamReader inputReader;
        private final StringBuffer output;

        /**
         * Create a new instance.
         *
         * @param outputReader Stream to be consumed by the pumper.
         */
        CharPumper(final InputStreamReader outputReader) {
            this.inputReader = outputReader;
            this.output = new StringBuffer();
        }

        /**
         * Consume the stream.
         */
        public void run() {
            try {
                int c;
                while ((c = inputReader.read()) != -1) {
                    append((char) c);
                }
            } catch (IOException e) {
                // Ignore these
            } finally {
                try {
                    if (inputReader != null) {
                        inputReader.close();
                    }
                }
                catch (IOException ex) {
                    //LOGGER.info(ex.getMessage());
                }
            }
        }

        /**
         * Append the character to the output buffer.
         *
         * @param c character to be appended
         */
        private synchronized void append(final char c) {
            output.append(c);
        }

        /**
         * Get so far collected output from process output stream.
         *
         * @return So far collected output from process since last clean-up.
         */
        private synchronized String getOutput() {
            return output.toString();
        }

        /**
         * Get and clear collected output from process input stream.
         *
         * @return Output collected from process input stream since last clean-up.
         */
        private synchronized String clearOutput() {
            String out = output.toString();
            output.setLength(0);
            return out;
        }
    }

}
