package tytoo.grapheneui.api.surface;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import tytoo.grapheneui.internal.browser.GrapheneBrowser;
import tytoo.grapheneui.internal.browser.GrapheneFocusUtil;
import tytoo.grapheneui.internal.browser.GrapheneWebViewInputController;

import java.awt.*;
import java.util.Objects;

/**
 * Adapter class that translates input events from a {@link BrowserSurface} to the underlying browser instance.
 * It handles mouse movement, clicks, scrolls, and keyboard events, ensuring they are correctly forwarded to the browser.
 * The adapter also manages focus state to ensure input is only sent when the surface is focused.
 */
public final class BrowserSurfaceInputAdapter {
    private static final int WHEEL_AMOUNT_PER_STEP = 120;

    private final BrowserSurface surface;
    private final GrapheneFocusUtil focusUtil;
    private GrapheneWebViewInputController inputController;
    private double pendingWheelAmount;

    public BrowserSurfaceInputAdapter(BrowserSurface surface) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.focusUtil = new GrapheneFocusUtil(this::applyNativeFocus);
        inputController();
    }

    public boolean isFocused() {
        return focusUtil.isFocused();
    }

    public void setFocused(boolean focused) {
        focusUtil.setFocused(focused);
    }

    public boolean isPrimaryPointerButtonDown() {
        GrapheneWebViewInputController controller = inputController();
        return controller != null && controller.isPrimaryPointerButtonDown();
    }

    public void mouseMoved(double surfaceX, double surfaceY, int renderedWidth, int renderedHeight) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return;
        }

        controller.updateMousePosition(toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight));
    }

    public void mouseMoved(Point browserPoint) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return;
        }

        controller.updateMousePosition(copyBrowserPoint(browserPoint));
    }

    public void mouseExited() {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return;
        }

        controller.onMouseExited();
    }

    public void mouseClicked(int button, boolean isDoubleClick, double surfaceX, double surfaceY, int renderedWidth, int renderedHeight) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return;
        }

        controller.onMouseClicked(button, isDoubleClick, toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight));
    }

    public void mouseClicked(int button, boolean isDoubleClick, Point browserPoint) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return;
        }

        controller.onMouseClicked(button, isDoubleClick, copyBrowserPoint(browserPoint));
    }

    public boolean mouseReleased(int button, double surfaceX, double surfaceY, int renderedWidth, int renderedHeight) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return false;
        }

        return controller.onMouseReleased(button, toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight));
    }

    public boolean mouseReleased(int button, Point browserPoint) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return false;
        }

        return controller.onMouseReleased(button, copyBrowserPoint(browserPoint));
    }

    public boolean mouseDragged(int button, double surfaceX, double surfaceY, int renderedWidth, int renderedHeight) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return false;
        }

        return controller.onMouseDragged(button, toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight));
    }

    public boolean mouseDragged(int button, Point browserPoint) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return false;
        }

        return controller.onMouseDragged(button, copyBrowserPoint(browserPoint));
    }

    public void mouseScrolled(double surfaceX, double surfaceY, int amount, int rotation, int renderedWidth, int renderedHeight) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null) {
            return;
        }

        controller.onMouseScrolled(
                toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight),
                amount,
                rotation
        );
    }

    public void mouseScrolled(double surfaceX, double surfaceY, double scrollY, int renderedWidth, int renderedHeight) {
        double preciseAmount = scrollY * WHEEL_AMOUNT_PER_STEP + pendingWheelAmount;
        int amount = (int) preciseAmount;
        pendingWheelAmount = preciseAmount - amount;
        if (amount == 0) {
            return;
        }

        mouseScrolled(surfaceX, surfaceY, amount, 1, renderedWidth, renderedHeight);
    }

    public boolean keyPressed(KeyEvent keyEvent) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null || !focusUtil.isFocused()) {
            return false;
        }

        controller.onKeyPressed(keyEvent);
        return true;
    }

    public boolean keyReleased(KeyEvent keyEvent) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null || !focusUtil.isFocused()) {
            return false;
        }

        controller.onKeyReleased(keyEvent);
        return true;
    }

    public boolean charTyped(CharacterEvent characterEvent) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null || !focusUtil.isFocused()) {
            return false;
        }

        controller.onCharacterTyped(characterEvent);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null || !focusUtil.isFocused()) {
            return false;
        }

        controller.onKeyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        GrapheneWebViewInputController controller = inputController();
        if (controller == null || !focusUtil.isFocused()) {
            return false;
        }

        controller.onKeyReleased(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean charTyped(int codePoint, int modifiers) {
        GrapheneBrowser browser = surface.internalBrowser();
        if (browser == null || !focusUtil.isFocused()) {
            return false;
        }

        browser.keyTyped((char) codePoint, modifiers);
        return true;
    }

    private GrapheneWebViewInputController inputController() {
        if (inputController != null) {
            return inputController;
        }

        GrapheneBrowser browser = surface.internalBrowser();
        if (browser == null) {
            return null;
        }

        inputController = new GrapheneWebViewInputController(
                browser,
                focusUtil,
                surface.bridge(),
                surface.allowsZoom(),
                surface.allowsAltF4Close()
        );
        focusUtil.addFocusListener(inputController::onFocusChanged);
        focusUtil.syncNativeFocus();
        return inputController;
    }

    private void applyNativeFocus(boolean focused) {
        GrapheneBrowser browser = surface.internalBrowser();
        if (browser != null) {
            browser.setFocus(focused);
        }
    }

    private Point toBrowserPoint(double surfaceX, double surfaceY, int renderedWidth, int renderedHeight) {
        return surface.toBrowserPoint(surfaceX, surfaceY, renderedWidth, renderedHeight);
    }

    private static Point copyBrowserPoint(Point browserPoint) {
        return new Point(Objects.requireNonNull(browserPoint, "browserPoint"));
    }
}
