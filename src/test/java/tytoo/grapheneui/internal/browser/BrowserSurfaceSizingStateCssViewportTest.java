package tytoo.grapheneui.internal.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BrowserSurfaceSizingStateCssViewportTest {
    @Test
    void cssViewportFollowsTheSurfaceWhileTheZoomAbsorbsTheGuiScale() {
        BrowserSurfaceSizingState sizing = new BrowserSurfaceSizingState(480, 270, true, 1, 1, null, 4.0, 4.0);

        BrowserSurfaceSizingState.CssViewport viewport = sizing.cssViewport(2.0);

        assertEquals(1920, sizing.resolutionWidth());
        assertEquals(960, viewport.width());
        assertEquals(540, viewport.height());
        assertEquals(2.0, viewport.zoom(), 1.0E-9);
    }

    @Test
    void sameWindowAtALowerGuiScaleGetsALargerCssViewportAtTheSameResolution() {
        BrowserSurfaceSizingState sizing = new BrowserSurfaceSizingState(480, 270, true, 1, 1, null, 4.0, 4.0);
        sizing.setSurfaceSize(960, 540, 2.0, 2.0);

        BrowserSurfaceSizingState.CssViewport viewport = sizing.cssViewport(2.0);

        assertEquals(1920, sizing.resolutionWidth());
        assertEquals(1920, viewport.width());
        assertEquals(1.0, viewport.zoom(), 1.0E-9);
    }

    @Test
    void fractionalZoomStillCoversTheWholeBrowserResolution() {
        BrowserSurfaceSizingState sizing = new BrowserSurfaceSizingState(640, 360, true, 1, 1, null, 3.0, 3.0);

        BrowserSurfaceSizingState.CssViewport viewport = sizing.cssViewport(2.0);

        assertEquals(1280, viewport.width());
        assertEquals(1.5, viewport.zoom(), 1.0E-9);
        assertEquals(sizing.resolutionWidth(), (int) Math.round(viewport.width() * viewport.zoom()));
    }

    @Test
    void integerPixelScaleKeepsEveryFrameAWholeNumberOfPixelsInOddWindows() {
        double windowScale = 1255.0 / 628.0;
        BrowserSurfaceSizingState sizing = new BrowserSurfaceSizingState(628, 350, true, 1, 1, null, windowScale, 699.0 / 350.0, true);

        BrowserSurfaceSizingState.CssViewport viewport = sizing.cssViewport(2.0);

        assertEquals(1256, sizing.resolutionWidth());
        assertEquals(700, sizing.resolutionHeight());
        assertEquals(1.0, viewport.zoom(), 1.0E-9);
        assertEquals(700.0, viewport.height() * viewport.zoom(), 1.0E-9);
    }
}
