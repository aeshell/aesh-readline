package org.aesh.readline.terminal;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.editing.Variable;
import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.terminal.Device;
import org.aesh.terminal.tty.Capability;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class DeviceTest {

    @Test
    public void testAnsiCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("ansi").build();

        assertNotNull(device.getStringCapability(Capability.enter_alt_charset_mode));

        assertTrue(device.getBooleanCapability(Capability.auto_right_margin));
        assertFalse(device.getBooleanCapability(Capability.auto_left_margin));

        assertEquals(8, device.getNumericCapability(Capability.max_colors).intValue());
        assertEquals(24, device.getNumericCapability(Capability.lines).intValue());

        assertEquals("^M", device.getStringCapability(Capability.carriage_return));

        ArrayList<int[]> out = new ArrayList<>();
        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.carriage_return);
        assertArrayEquals(new int[] { 13 }, out.get(0));

        //home
        assertArrayEquals(new int[] { 27, 91, 72 }, device.getStringCapabilityAsInts(Capability.key_home));
    }

    @Test
    public void testWindowsCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("windows").build();
        assertTrue(device.getBooleanCapability(Capability.move_standout_mode));
        assertEquals(8, device.getNumericCapability(Capability.max_colors).intValue());
        assertEquals(64, device.getNumericCapability(Capability.max_pairs).intValue());

        assertArrayEquals(new int[] { 10 }, device.getStringCapabilityAsInts(Capability.scroll_forward));
    }

    @Test
    public void testEmacsKeyUpdates() {
        Device device = DeviceBuilder.builder().name("ansi").build();

        EditMode emacs = EditModeBuilder.builder()
                .addVariable(Variable.EDITING_MODE, "emacs")
                .device(device).create();

        //by default only Key.HOME is set to beginning-of-line, but the ansi
        //device should remap it to Key.HOME_2
        assertEquals("beginning-of-line", emacs.parse(Key.HOME_2).name());
    }

    @Test
    public void testXTermCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("xterm-256color").build();
        String deviceCap = device.getStringCapability(Capability.enter_ca_mode);
        assertNotNull(deviceCap);
        // Translate the device capability like ANSI does
        ArrayList<int[]> out = new ArrayList<>();
        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.enter_ca_mode);
        // Also check the out variable against a hardcoded value \u001B[?1049h
        assertArrayEquals(new int[] { 27, 91, 63, 49, 48, 52, 57, 104 }, out.get(0));
    }

    @Test
    public void testScreen256Capabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("screen-256color").build();
        String deviceCap = device.getStringCapability(Capability.enter_ca_mode);
        assertNotNull(deviceCap);
        // Translate the device capability like ANSI does
        ArrayList<int[]> out = new ArrayList<>();
        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.enter_ca_mode);
        // Also check the out variable against a hardcoded value \u001B[?1049h
        assertArrayEquals(new int[] { 27, 91, 63, 49, 48, 52, 57, 104 }, out.get(0));
    }

    @Test
    public void testMovementCapabilities() throws Exception {
        Device device = DeviceBuilder.builder().name("xterm-256color").build();

        String cup = device.getStringCapability(Capability.cursor_address);
        assertNotNull(cup);

        ArrayList<int[]> out = new ArrayList<>();

        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.cursor_address, 10, 20);

        // ESC [ 11 ; 21 H (parameters are 1-based, %i increments them)
        assertArrayEquals(new int[] { 27, 91, 49, 49, 59, 50, 49, 72 }, out.get(0));
    }

    @Test
    public void testCapabilityIsPushedToTestConnection() throws Exception {
        TestConnection connection = new TestConnection(false);
        connection.clearOutputBuffer();
        connection.put(Capability.enter_ca_mode);
        // ESC [ ? 1 0 4 9 h
        assertArrayEquals(new int[] { 27, 91, 63, 49, 48, 52, 57, 104 }, connection.getOutputBuffer().codePoints().toArray());
    }

}
