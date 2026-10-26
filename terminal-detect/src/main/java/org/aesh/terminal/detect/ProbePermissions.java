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
package org.aesh.terminal.detect;

/**
 * What one bounded enrichment round may touch.
 * <p>
 * Granting one source never implies another: a terminal query, an IDE or
 * config file read, and a platform subprocess are separate permissions.
 * Suppression happens before transport loading, worker submission, or file
 * lookup; a suppressed source performs no I/O of its kind. Unknown or a
 * missing reply is never permission to try another source.
 * <p>
 * Java 8, zero dependencies. Immutable value; share freely.
 *
 * @since 3.18.8
 */
public final class ProbePermissions {

    private final boolean terminalQuery;
    private final boolean ideFiles;
    private final boolean platformSubprocess;

    private ProbePermissions(boolean terminalQuery, boolean ideFiles, boolean platformSubprocess) {
        this.terminalQuery = terminalQuery;
        this.ideFiles = ideFiles;
        this.platformSubprocess = platformSubprocess;
    }

    /**
     * Permit every enrichment source.
     *
     * @return permissions granting all three sources
     */
    public static ProbePermissions all() {
        return new ProbePermissions(true, true, true);
    }

    /**
     * Permit no enrichment source. Environment-only behavior.
     *
     * @return permissions granting nothing
     */
    public static ProbePermissions none() {
        return new ProbePermissions(false, false, false);
    }

    /**
     * Permit an explicit combination of sources.
     *
     * @param terminalQuery true to allow terminal probing
     * @param ideFiles true to allow IDE and config file reads
     * @param platformSubprocess true to allow platform subprocesses
     * @return the requested combination
     */
    public static ProbePermissions of(boolean terminalQuery, boolean ideFiles, boolean platformSubprocess) {
        return new ProbePermissions(terminalQuery, ideFiles, platformSubprocess);
    }

    /**
     * Check whether terminal probing is permitted.
     *
     * @return true when terminal queries may run
     */
    public boolean allowTerminalQuery() {
        return terminalQuery;
    }

    /**
     * Check whether IDE and config file reads are permitted.
     *
     * @return true when settings files may be read
     */
    public boolean allowIdeFiles() {
        return ideFiles;
    }

    /**
     * Check whether platform subprocesses are permitted.
     *
     * @return true when subprocess fallback may run
     */
    public boolean allowPlatformSubprocess() {
        return platformSubprocess;
    }
}
