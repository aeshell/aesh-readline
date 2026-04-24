/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline;

/**
 * Enumeration of flags that control readline behavior.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum ReadlineFlag {

    /**
     * Do not redraw prompt on INTR signal
     * <p>
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
    NO_MULTI_LINE_ON_QUOTE,

    /**
     * Do not discard lines starting with '#'
     */
    NO_COMMENT_DISCARD,

    /**
     * Do not automatically enable Mode 2027 (grapheme cluster segmentation)
     * for terminals that support it
     */
    NO_GRAPHEME_CLUSTER_MODE,

    /**
     * Do not automatically use Mode 2026 (synchronized output)
     * for terminals that support it
     */
    NO_SYNCHRONIZED_OUTPUT,

    /**
     * Do not emit OSC 133 shell integration sequences.
     * <p>
     * When set, Readline will not emit OSC 133 prompt start (A),
     * prompt end (B), or command start (C) markers.
     */
    NO_SHELL_INTEGRATION,

    /**
     * Do not write killed/copied text to the system clipboard via OSC 52.
     */
    NO_CLIPBOARD

}
