package org.aesh.readline;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.junit.Test;

public class CrlfHandlingTest {

    @Test
    public void windowsCrlfTwoCyclesViaString() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read("1234\r\n");
        term.assertLine("1234");
        term.readline();
        term.read("567\r\n");
        term.assertLine("567");
    }

    @Test
    public void windowsCrlfSplitAcrossChunks() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read("1234\r");
        term.read("\n");
        term.assertLine("1234");
        term.readline();
        term.read("567\r");
        term.read("\n");
        term.assertLine("567");
    }
}
