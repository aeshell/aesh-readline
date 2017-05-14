package org.aesh.terminal;

import org.aesh.terminal.tty.Capability;

import java.util.function.Consumer;

/**
 * Contains info regarding the current device connected to readline
 *
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public interface Device {

    String type();

    boolean getBooleanCapability(Capability capability);

    Integer getNumericCapability(Capability capability);

    String getStringCapability(Capability capability);

    int[] getStringCapabilityAsInts(Capability capability);

    boolean puts(Consumer<int[]> output, Capability capability);
}
