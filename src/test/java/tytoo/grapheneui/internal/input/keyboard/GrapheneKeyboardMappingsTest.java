package tytoo.grapheneui.internal.input.keyboard;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrapheneKeyboardMappingsTest {
    private static Stream<Arguments> win32VirtualKeysByGlfwKey() {
        return Stream.of(
                Arguments.of("Backspace", GLFW.GLFW_KEY_BACKSPACE, 0x08),
                Arguments.of("Tab", GLFW.GLFW_KEY_TAB, 0x09),
                Arguments.of("Enter", GLFW.GLFW_KEY_ENTER, 0x0D),
                Arguments.of("NumpadEnter", GLFW.GLFW_KEY_KP_ENTER, 0x0D),
                Arguments.of("LeftShift", GLFW.GLFW_KEY_LEFT_SHIFT, 0x10),
                Arguments.of("RightShift", GLFW.GLFW_KEY_RIGHT_SHIFT, 0x10),
                Arguments.of("LeftControl", GLFW.GLFW_KEY_LEFT_CONTROL, 0x11),
                Arguments.of("RightControl", GLFW.GLFW_KEY_RIGHT_CONTROL, 0x11),
                Arguments.of("LeftAlt", GLFW.GLFW_KEY_LEFT_ALT, 0x12),
                Arguments.of("RightAlt", GLFW.GLFW_KEY_RIGHT_ALT, 0x12),
                Arguments.of("Pause", GLFW.GLFW_KEY_PAUSE, 0x13),
                Arguments.of("CapsLock", GLFW.GLFW_KEY_CAPS_LOCK, 0x14),
                Arguments.of("Escape", GLFW.GLFW_KEY_ESCAPE, 0x1B),
                Arguments.of("Space", GLFW.GLFW_KEY_SPACE, 0x20),
                Arguments.of("PageUp", GLFW.GLFW_KEY_PAGE_UP, 0x21),
                Arguments.of("PageDown", GLFW.GLFW_KEY_PAGE_DOWN, 0x22),
                Arguments.of("End", GLFW.GLFW_KEY_END, 0x23),
                Arguments.of("Home", GLFW.GLFW_KEY_HOME, 0x24),
                Arguments.of("Left", GLFW.GLFW_KEY_LEFT, 0x25),
                Arguments.of("Up", GLFW.GLFW_KEY_UP, 0x26),
                Arguments.of("Right", GLFW.GLFW_KEY_RIGHT, 0x27),
                Arguments.of("Down", GLFW.GLFW_KEY_DOWN, 0x28),
                Arguments.of("PrintScreen", GLFW.GLFW_KEY_PRINT_SCREEN, 0x2C),
                Arguments.of("Insert", GLFW.GLFW_KEY_INSERT, 0x2D),
                Arguments.of("Delete", GLFW.GLFW_KEY_DELETE, 0x2E),
                Arguments.of("Digit0", GLFW.GLFW_KEY_0, 0x30),
                Arguments.of("Digit9", GLFW.GLFW_KEY_9, 0x39),
                Arguments.of("LeftSuper", GLFW.GLFW_KEY_LEFT_SUPER, 0x5B),
                Arguments.of("RightSuper", GLFW.GLFW_KEY_RIGHT_SUPER, 0x5C),
                Arguments.of("Numpad0", GLFW.GLFW_KEY_KP_0, 0x60),
                Arguments.of("Numpad1", GLFW.GLFW_KEY_KP_1, 0x61),
                Arguments.of("Numpad2", GLFW.GLFW_KEY_KP_2, 0x62),
                Arguments.of("Numpad3", GLFW.GLFW_KEY_KP_3, 0x63),
                Arguments.of("Numpad4", GLFW.GLFW_KEY_KP_4, 0x64),
                Arguments.of("Numpad5", GLFW.GLFW_KEY_KP_5, 0x65),
                Arguments.of("Numpad6", GLFW.GLFW_KEY_KP_6, 0x66),
                Arguments.of("Numpad7", GLFW.GLFW_KEY_KP_7, 0x67),
                Arguments.of("Numpad8", GLFW.GLFW_KEY_KP_8, 0x68),
                Arguments.of("Numpad9", GLFW.GLFW_KEY_KP_9, 0x69),
                Arguments.of("NumpadMultiply", GLFW.GLFW_KEY_KP_MULTIPLY, 0x6A),
                Arguments.of("NumpadAdd", GLFW.GLFW_KEY_KP_ADD, 0x6B),
                Arguments.of("NumpadSubtract", GLFW.GLFW_KEY_KP_SUBTRACT, 0x6D),
                Arguments.of("NumpadDecimal", GLFW.GLFW_KEY_KP_DECIMAL, 0x6E),
                Arguments.of("NumpadDivide", GLFW.GLFW_KEY_KP_DIVIDE, 0x6F),
                Arguments.of("NumpadEqual", GLFW.GLFW_KEY_KP_EQUAL, 0x92),
                Arguments.of("F1", GLFW.GLFW_KEY_F1, 0x70),
                Arguments.of("F12", GLFW.GLFW_KEY_F12, 0x7B),
                Arguments.of("NumLock", GLFW.GLFW_KEY_NUM_LOCK, 0x90),
                Arguments.of("ScrollLock", GLFW.GLFW_KEY_SCROLL_LOCK, 0x91),
                Arguments.of("Semicolon", GLFW.GLFW_KEY_SEMICOLON, 0xBA),
                Arguments.of("Equal", GLFW.GLFW_KEY_EQUAL, 0xBB),
                Arguments.of("Comma", GLFW.GLFW_KEY_COMMA, 0xBC),
                Arguments.of("Minus", GLFW.GLFW_KEY_MINUS, 0xBD),
                Arguments.of("Period", GLFW.GLFW_KEY_PERIOD, 0xBE),
                Arguments.of("Slash", GLFW.GLFW_KEY_SLASH, 0xBF),
                Arguments.of("Backquote", GLFW.GLFW_KEY_GRAVE_ACCENT, 0xC0),
                Arguments.of("BracketLeft", GLFW.GLFW_KEY_LEFT_BRACKET, 0xDB),
                Arguments.of("Backslash", GLFW.GLFW_KEY_BACKSLASH, 0xDC),
                Arguments.of("BracketRight", GLFW.GLFW_KEY_RIGHT_BRACKET, 0xDD),
                Arguments.of("Quote", GLFW.GLFW_KEY_APOSTROPHE, 0xDE),
                Arguments.of("IntlBackslash", GLFW.GLFW_KEY_WORLD_1, 0xE2)
        );
    }

    private static Stream<Arguments> nonCharacterWin32VirtualKeys() {
        return Stream.of(
                Arguments.of("Delete", GLFW.GLFW_KEY_DELETE, 0x2E),
                Arguments.of("Insert", GLFW.GLFW_KEY_INSERT, 0x2D),
                Arguments.of("Home", GLFW.GLFW_KEY_HOME, 0x24),
                Arguments.of("End", GLFW.GLFW_KEY_END, 0x23),
                Arguments.of("PageUp", GLFW.GLFW_KEY_PAGE_UP, 0x21),
                Arguments.of("PageDown", GLFW.GLFW_KEY_PAGE_DOWN, 0x22),
                Arguments.of("PrintScreen", GLFW.GLFW_KEY_PRINT_SCREEN, 0x2C),
                Arguments.of("Escape", GLFW.GLFW_KEY_ESCAPE, 0x1B),
                Arguments.of("Left", GLFW.GLFW_KEY_LEFT, 0x25)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("win32VirtualKeysByGlfwKey")
    void windowsVkFromGlfwMatchesWin32VirtualKey(String keyName, int glfwKeyCode, int win32VirtualKey) {
        assertEquals(win32VirtualKey, GrapheneKeyboardMappings.windowsVkFromGlfw(glfwKeyCode), keyName);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonCharacterWin32VirtualKeys")
    void resolveDomKeyCodeUsesWin32VirtualKeyForNonCharacterKeys(String keyName, int glfwKeyCode, int win32VirtualKey) {
        int domKeyCode = GrapheneKeyboardSharedUtil.resolveDomKeyCode(glfwKeyCode, KeyEvent.CHAR_UNDEFINED, false);

        assertEquals(win32VirtualKey, domKeyCode, keyName);
    }
}
