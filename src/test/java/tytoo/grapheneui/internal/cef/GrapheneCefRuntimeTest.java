package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefRuntimeTest {
    @Test
    void macShutdownSkipsExplicitCefAppDisposal() {
        assertFalse(GrapheneCefRuntime.shouldDisposeCefAppExplicitly(true, false));
    }

    @Test
    void linuxShutdownSkipsExplicitCefAppDisposal() {
        assertFalse(GrapheneCefRuntime.shouldDisposeCefAppExplicitly(false, true));
    }

    @Test
    void windowsShutdownDisposesCefAppExplicitly() {
        assertTrue(GrapheneCefRuntime.shouldDisposeCefAppExplicitly(false, false));
    }
}
