/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public enum ReadlineFlag {

    /**
     * Do not redraw prompt on INTR signal
     *
     * Default behaviour is to redraw .
     */
    NO_PROMPT_REDRAW_ON_INTR,

    /**
     * Ignore EOF signal a given number of times
     */
    IGNORE_EOF,

    /**
     * Do not activate multi-line mode
     * <ul>
     * <li>0 = Disable for both single and double quote
     * <li>1 = Disable for double quote
     * <li>2 = Disable for single quote
     * </ul>
     */
    NO_MULTI_LINE_ON_QUOTE

}
