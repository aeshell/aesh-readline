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
package org.aesh.terminal;

import java.util.EnumMap;
import java.util.EnumSet;

public class Attributes {

    /**
     * Control characters
     */
    public enum ControlChar {
        VEOF,
        VEOL,
        VEOL2,
        VERASE,
        VWERASE,
        VKILL,
        VREPRINT,
        VINTR,
        VQUIT,
        VSUSP,
        VDSUSP,
        VSTART,
        VSTOP,
        VLNEXT,
        VDISCARD,
        VMIN,
        VTIME,
        VSTATUS
    }

    /**
     * Input flags - software input processing
     */
    public enum InputFlag {
        IGNBRK,       /* ignore BREAK condition */
        BRKINT,       /* map BREAK to SIGINTR */
        IGNPAR,       /* ignore (discard) parity errors */
        PARMRK,       /* mark parity and framing errors */
        INPCK,        /* enable checking of parity errors */
        ISTRIP,       /* strip 8th bit off chars */
        INLCR,        /* map NL into CR */
        IGNCR,        /* ignore CR */
        ICRNL,        /* map CR to NL (ala CRMOD) */
        IXON,         /* enable output flow control */
        IXOFF,        /* enable input flow control */
        IXANY,        /* any char will restart after stop */
        IMAXBEL,      /* ring bell on input queue full */
        IUTF8         /* maintain state for UTF-8 VERASE */
    }

    /*
     * Output flags - software output processing
     */
    public enum OutputFlag {
        OPOST,       /* enable following output processing */
        ONLCR,       /* map NL to CR-NL (ala CRMOD) */
        OXTABS,      /* expand tabs to spaces */
        ONOEOT,      /* discard EOT's (^D) on output) */
        OCRNL,       /* map CR to NL on output */
        ONOCR,       /* no CR output at column 0 */
        ONLRET,      /* NL performs CR function */
        OFILL,       /* use fill characters for delay */
        NLDLY,       /* \n delay */
        TABDLY,      /* horizontal tab delay */
        CRDLY,       /* \r delay */
        FFDLY,       /* form feed delay */
        BSDLY,       /* \b delay */
        VTDLY,       /* vertical tab delay */
        OFDEL        /* fill is DEL, else NUL */
    }

    /*
     * Control flags - hardware control of terminal
     */
    public enum ControlFlag {
        CIGNORE,          /* ignore control flags */
        CS5,              /* 5 bits    (pseudo) */
        CS6,              /* 6 bits */
        CS7,              /* 7 bits */
        CS8,              /* 8 bits */
        CSTOPB,           /* send 2 stop bits */
        CREAD,            /* enable receiver */
        PARENB,           /* parity enable */
        PARODD,           /* odd parity, else even */
        HUPCL,            /* hang up on last close */
        CLOCAL,           /* ignore modem status lines */
        CCTS_OFLOW,       /* CTS flow control of output */
        CRTS_IFLOW,       /* RTS flow control of input */
        CDTR_IFLOW,       /* DTR flow control of input */
        CDSR_OFLOW,       /* DSR flow control of output */
        CCAR_OFLOW        /* DCD flow control of output */
    }

    /*
     * "Local" flags - dumping ground for other state
     *
     * Warning: some flags in this structure begin with
     * the letter "I" and look like they belong in the
     * input flag.
     */
    public enum LocalFlag {
        ECHOKE,           /* visual erase for line kill */
        ECHOE,            /* visually erase chars */
        ECHOK,            /* echo NL after line kill */
        ECHO,             /* enable echoing */
        ECHONL,           /* echo NL even if ECHO is off */
        ECHOPRT,          /* visual erase mode for hardcopy */
        ECHOCTL,          /* echo control chars as ^(Char) */
        ISIG,             /* enable signals INTR, QUIT, [D]SUSP */
        ICANON,           /* canonicalize input lines */
        ALTWERASE,        /* use alternate WERASE algorithm */
        IEXTEN,           /* enable DISCARD and LNEXT */
        EXTPROC,          /* external processing */
        TOSTOP,           /* stop background jobs from output */
        FLUSHO,           /* output being flushed (state) */
        NOKERNINFO,       /* no kernel output from VSTATUS */
        PENDIN,           /* XXX retype pending input (state) */
        NOFLSH            /* don't flush after interrupt */
    }

    private final EnumSet<InputFlag> inputFlags = EnumSet.noneOf(InputFlag.class);
    private final EnumSet<OutputFlag> outputFlags = EnumSet.noneOf(OutputFlag.class);
    private final EnumSet<ControlFlag> controlFlags = EnumSet.noneOf(ControlFlag.class);
    private final EnumSet<LocalFlag> localFlags = EnumSet.noneOf(LocalFlag.class);
    private final EnumMap<ControlChar, Integer> controlChars = new EnumMap<>(ControlChar.class);

    public Attributes() {
    }

    public Attributes(Attributes attr) {
        copy(attr);
    }

    //
    // Input flags
    //

    public EnumSet<InputFlag> getInputFlags() {
        return inputFlags;
    }

    public void setInputFlags(EnumSet<InputFlag> flags) {
        inputFlags.clear();
        inputFlags.addAll(flags);
    }

    public boolean getInputFlag(InputFlag flag) {
        return inputFlags.contains(flag);
    }

    public void setInputFlags(EnumSet<InputFlag> flags, boolean value) {
        if (value) {
            inputFlags.addAll(flags);
        } else {
            inputFlags.removeAll(flags);
        }
    }

    public void setInputFlag(InputFlag flag, boolean value) {
        if (value) {
            inputFlags.add(flag);
        } else {
            inputFlags.remove(flag);
        }
    }

    //
    // Output flags
    //

    public EnumSet<OutputFlag> getOutputFlags() {
        return outputFlags;
    }

    public void setOutputFlags(EnumSet<OutputFlag> flags) {
        outputFlags.clear();
        outputFlags.addAll(flags);
    }

    public boolean getOutputFlag(OutputFlag flag) {
        return outputFlags.contains(flag);
    }

    public void setOutputFlags(EnumSet<OutputFlag> flags, boolean value) {
        if (value) {
            outputFlags.addAll(flags);
        } else {
            outputFlags.removeAll(flags);
        }
    }

    public void setOutputFlag(OutputFlag flag, boolean value) {
        if (value) {
            outputFlags.add(flag);
        } else {
            outputFlags.remove(flag);
        }
    }

    //
    // Control flags
    //

    public EnumSet<ControlFlag> getControlFlags() {
        return controlFlags;
    }

    public void setControlFlags(EnumSet<ControlFlag> flags) {
        controlFlags.clear();
        controlFlags.addAll(flags);
    }

    public boolean getControlFlag(ControlFlag flag) {
        return controlFlags.contains(flag);
    }

    public void setControlFlags(EnumSet<ControlFlag> flags, boolean value) {
        if (value) {
            controlFlags.addAll(flags);
        } else {
            controlFlags.removeAll(flags);
        }
    }

    public void setControlFlag(ControlFlag flag, boolean value) {
        if (value) {
            controlFlags.add(flag);
        } else {
            controlFlags.remove(flag);
        }
    }

    //
    // Local flags
    //

    public EnumSet<LocalFlag> getLocalFlags() {
        return localFlags;
    }

    public void setLocalFlags(EnumSet<LocalFlag> flags) {
        localFlags.clear();
        localFlags.addAll(flags);
    }

    public boolean getLocalFlag(LocalFlag flag) {
        return localFlags.contains(flag);
    }

    public void setLocalFlags(EnumSet<LocalFlag> flags, boolean value) {
        if (value) {
            localFlags.addAll(flags);
        } else {
            localFlags.removeAll(flags);
        }
    }

    public void setLocalFlag(LocalFlag flag, boolean value) {
        if (value) {
            localFlags.add(flag);
        } else {
            localFlags.remove(flag);
        }
    }

    //
    // Control chars
    //
    public EnumMap<ControlChar, Integer> getControlChars() {
        return controlChars;
    }

    public void setControlChars(EnumMap<ControlChar, Integer> chars) {
        controlChars.clear();
        controlChars.putAll(chars);
    }

    public int getControlChar(ControlChar c) {
        return controlChars.getOrDefault(c, -1);
    }

    public void setControlChar(ControlChar c, int value) {
        controlChars.put(c, value);
    }

    //
    // Miscellaneous methods
    //
    public void copy(Attributes attributes) {
        setControlFlags(attributes.getControlFlags());
        setInputFlags(attributes.getInputFlags());
        setLocalFlags(attributes.getLocalFlags());
        setOutputFlags(attributes.getOutputFlags());
        setControlChars(attributes.getControlChars());
    }
}
