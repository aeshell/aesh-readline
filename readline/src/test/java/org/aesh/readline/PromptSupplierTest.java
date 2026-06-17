package org.aesh.readline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.readline.prompt.Prompt;
import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for ReadlineRequest.promptSupplier — dynamic prompt support.
 */
public class PromptSupplierTest {

    // Minimal connection for testing ReadlineRequest — only prompt() is called
    private final TestConnection conn = new TestConnection(new Size(80, 24));

    @Test
    public void testPromptSupplierReturnsPrompt() {
        Prompt supplied = new Prompt("dynamic> ");
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .promptSupplier(() -> supplied)
                .requestHandler(line -> {
                })
                .build();

        assertEquals(supplied, request.prompt());
    }

    @Test
    public void testPromptSupplierTakesPrecedence() {
        Prompt staticPrompt = new Prompt("static> ");
        Prompt dynamicPrompt = new Prompt("dynamic> ");
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .prompt(staticPrompt)
                .promptSupplier(() -> dynamicPrompt)
                .requestHandler(line -> {
                })
                .build();

        // Supplier should take precedence
        assertEquals(dynamicPrompt, request.prompt());
    }

    @Test
    public void testPromptSupplierNullFallsBackToStaticPrompt() {
        Prompt staticPrompt = new Prompt("fallback> ");
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .prompt(staticPrompt)
                .promptSupplier(() -> null)
                .requestHandler(line -> {
                })
                .build();

        // Supplier returns null — should fall back to static prompt
        assertEquals(staticPrompt, request.prompt());
    }

    @Test
    public void testPromptSupplierNullWithNoStaticPrompt() {
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .promptSupplier(() -> null)
                .requestHandler(line -> {
                })
                .build();

        // Both supplier and static are null — should return empty prompt
        assertNotNull(request.prompt());
        assertEquals(0, request.prompt().length());
    }

    @Test
    public void testNoPromptSupplierUsesStaticPrompt() {
        Prompt staticPrompt = new Prompt("$ ");
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .prompt(staticPrompt)
                .requestHandler(line -> {
                })
                .build();

        assertEquals(staticPrompt, request.prompt());
    }

    @Test
    public void testPromptSupplierCalledEachTime() {
        AtomicInteger callCount = new AtomicInteger(0);
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .promptSupplier(() -> {
                    int n = callCount.incrementAndGet();
                    return new Prompt("call-" + n + "> ");
                })
                .requestHandler(line -> {
                })
                .build();

        // Each call to prompt() invokes the supplier
        Prompt p1 = request.prompt();
        Prompt p2 = request.prompt();
        assertEquals(2, callCount.get());
        assertEquals(8, p1.length()); // "call-1> "
        assertEquals(8, p2.length()); // "call-2> "
    }

    @Test
    public void testPromptSupplierWithMultiLinePrompt() {
        ReadlineRequest request = ReadlineRequest.builder()
                .connection(conn)
                .promptSupplier(() -> Prompt.builder()
                        .line("info line")
                        .line("❯ ")
                        .build())
                .requestHandler(line -> {
                })
                .build();

        Prompt p = request.prompt();
        assertEquals(2, p.lineCount());
        assertEquals(2, p.length()); // last line "❯ "
    }
}
