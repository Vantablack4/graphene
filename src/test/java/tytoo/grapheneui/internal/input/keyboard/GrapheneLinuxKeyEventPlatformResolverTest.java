package tytoo.grapheneui.internal.input.keyboard;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrapheneLinuxKeyEventPlatformResolverTest {
    private final GrapheneLinuxKeyEventPlatformResolver resolver = new GrapheneLinuxKeyEventPlatformResolver();

    @Test
    void sanitizeTextModifiersLeavesModifiersWhenRightAltIsNotPressed() {
        int modifiers = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT;

        int sanitizedModifiers = resolver.sanitizeTextModifiers(modifiers, false);

        assertEquals(modifiers, sanitizedModifiers);
    }

    @Test
    void sanitizeTextModifiersClearsAltGrControlAndAltModifiers() {
        int modifiers = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;

        int sanitizedModifiers = resolver.sanitizeTextModifiers(modifiers, true);

        assertEquals(GLFW.GLFW_MOD_SHIFT, sanitizedModifiers);
    }

    @Test
    void sanitizeTextModifiersClearsAltWhenAltGrReportsNoControlModifier() {
        int modifiers = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT;

        int sanitizedModifiers = resolver.sanitizeTextModifiers(modifiers, true);

        assertEquals(GLFW.GLFW_MOD_SHIFT, sanitizedModifiers);
    }
}
