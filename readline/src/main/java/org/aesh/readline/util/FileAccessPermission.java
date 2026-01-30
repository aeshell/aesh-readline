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

package org.aesh.readline.util;

/**
 * <code>FileAccessPermission</code> defines file access permission like readable, writable.
 *
 * <i>readable</i> and <i>writable</i> will be true by default.
 *
 * @author <a href="mailto:lgao@redhat.com">Lin Gao</a>
 *
 */
public class FileAccessPermission {

    private boolean readable = true;

    private boolean readableOwnerOnly;

    private boolean writable = true;

    private boolean writableOwnerOnly;

    private boolean executable = false;

    private boolean executableOwnerOnly;

    /**
     * Default constructor
     */
    public FileAccessPermission() {
        super();
    }

    /**
     * Checks if the file is executable.
     *
     * @return the executable flag
     */
    public boolean isExecutable() {
        return executable;
    }

    /**
     * Sets the executable permission.
     *
     * @param executable the executable flag to set
     */
    public void setExecutable(boolean executable) {
        this.executable = executable;
    }

    /**
     * Checks if executable permission is owner only.
     *
     * @return the executableOwnerOnly flag
     */
    public boolean isExecutableOwnerOnly() {
        return executableOwnerOnly;
    }

    /**
     * Sets whether executable permission is owner only.
     *
     * @param executableOwnerOnly the executableOwnerOnly flag to set
     */
    public void setExecutableOwnerOnly(boolean executableOwnerOnly) {
        this.executableOwnerOnly = executableOwnerOnly;
    }

    /**
     * Checks if the file is readable.
     *
     * @return the readable flag
     */
    public boolean isReadable() {
        return readable;
    }

    /**
     * Sets the readable permission.
     *
     * @param readable the readable flag to set
     */
    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    /**
     * Checks if readable permission is owner only.
     *
     * @return the readableOwnerOnly flag
     */
    public boolean isReadableOwnerOnly() {
        return readableOwnerOnly;
    }

    /**
     * Sets whether readable permission is owner only.
     *
     * @param readableOwnerOnly the readableOwnerOnly flag to set
     */
    public void setReadableOwnerOnly(boolean readableOwnerOnly) {
        this.readableOwnerOnly = readableOwnerOnly;
    }

    /**
     * Checks if the file is writable.
     *
     * @return the writable flag
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Sets the writable permission.
     *
     * @param writable the writable flag to set
     */
    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    /**
     * Checks if writable permission is owner only.
     *
     * @return the writableOwnerOnly flag
     */
    public boolean isWritableOwnerOnly() {
        return writableOwnerOnly;
    }

    /**
     * Sets whether writable permission is owner only.
     *
     * @param writableOwnerOnly the writableOwnerOnly flag to set
     */
    public void setWritableOwnerOnly(boolean writableOwnerOnly) {
        this.writableOwnerOnly = writableOwnerOnly;
    }

}
