package org.aesh.readline.terminal;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.editing.Variable;
import org.aesh.terminal.Device;
import org.aesh.terminal.tty.Capability;
import org.aesh.util.Parser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class DeviceTest {

    @Test
    public void testAnsiCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("ansi").build();

        assertTrue( device.getBooleanCapability(Capability.auto_right_margin));
        assertFalse( device.getBooleanCapability(Capability.auto_left_margin));

        assertEquals(8, device.getNumericCapability(Capability.max_colors).intValue());
        assertEquals(24, device.getNumericCapability(Capability.lines).intValue());

        assertEquals("^M", device.getStringCapability(Capability.carriage_return));

        String cuf = device.getStringCapability(Capability.parm_right_cursor);
        System.out.println("CUF: "+cuf);

        ArrayList<int[]> out = new ArrayList<>();

        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.carriage_return);
        assertArrayEquals(new int[]{13}, out.get(0));

        //home
        assertArrayEquals(new int[]{27,91,72}, device.getStringCapabilityAsInts(Capability.key_home));

        //assertArrayEquals(Key.HOME.getKeyValues(), device.getStringCapabilityAsInts(Capability.key_home));

    }

    @Test
    public void testWindowsCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("windows").build();
        assertTrue( device.getBooleanCapability(Capability.move_standout_mode));
        assertEquals(8, device.getNumericCapability(Capability.max_colors).intValue());
        assertEquals(64, device.getNumericCapability(Capability.max_pairs).intValue());

        assertArrayEquals(new int[]{10}, device.getStringCapabilityAsInts(Capability.scroll_forward));
    }

    @Test
    public void testEmacsKeyUpdates() {
        Device device = DeviceBuilder.builder().name("ansi").build();

        EditMode emacs = EditModeBuilder.builder()
                        .addVariable(Variable.EDITING_MODE, "emacs")
                        .device(device).create();

        //by default only Key.HOME is set to beginning-of-line, but the ansi
        //device should remap it to Key.HOME_2
        assertEquals("beginning-of-line", emacs.parse( Key.HOME_2).name());
    }

    @Test
    public void testXTermCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("xterm-256color").build();
        Consumer<int[]> output = ints -> assertEquals("\u001B[H\u001B[2J", Parser.fromCodePoints(ints));
        device.puts(output, Capability.clear_screen);
    }

}
