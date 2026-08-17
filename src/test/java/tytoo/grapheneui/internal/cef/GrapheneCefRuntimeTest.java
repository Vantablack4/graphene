package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefRuntimeTest {
    @Test
    void macShutdownSkipsExplicitCefAppDisposal() {
        assertFalse(GrapheneCefRuntime.shouldDisposeCefAppExplicitly(true));
    }

    @Test
    void nonMacShutdownDisposesCefAppExplicitly() {
        assertTrue(GrapheneCefRuntime.shouldDisposeCefAppExplicitly(false));
    }
}
