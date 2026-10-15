package tytoo.grapheneui.internal.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BrowserSurfaceViewportMapperTest {
    @Test
    void mapCoordinateReturnsSourceStartWhenSizesAreInvalid() {
        assertEquals(5, BrowserSurfaceViewportMapper.mapCoordinate(10.0, 0, 5, 100));
        assertEquals(5, BrowserSurfaceViewportMapper.mapCoordinate(10.0, 100, 5, 0));
    }

    @Test
    void mapCoordinateScalesCoordinateIntoSourceRange() {
        int mappedCoordinate = BrowserSurfaceViewportMapper.mapCoordinate(50.0, 100, 10, 200);

        assertEquals(110, mappedCoordinate);
    }

    @Test
    void mapCoordinateUsesTruncationForFractionalValues() {
        int mappedCoordinate = BrowserSurfaceViewportMapper.mapCoordinate(33.9, 100, 10, 200);

        assertEquals(77, mappedCoordinate);
    }

    @Test
    void unmapCoordinateReturnsZeroWhenSizesAreInvalid() {
        assertEquals(0.0, BrowserSurfaceViewportMapper.unmapCoordinate(10.0, 0, 5, 100));
        assertEquals(0.0, BrowserSurfaceViewportMapper.unmapCoordinate(10.0, 100, 5, 0));
    }

    @Test
    void unmapCoordinateScalesSourceCoordinateBackToRenderedRange() {
        assertEquals(50.0, BrowserSurfaceViewportMapper.unmapCoordinate(110.0, 100, 10, 200));
    }

    @Test
    void unmappedCoordinatesMapBackToTheSameSourcePixel() {
        for (int sourceCoordinate = 10; sourceCoordinate < 210; sourceCoordinate++) {
            double renderedCoordinate = BrowserSurfaceViewportMapper.unmapCoordinate(sourceCoordinate + 0.5, 130, 10, 200);

            assertEquals(sourceCoordinate, BrowserSurfaceViewportMapper.mapCoordinate(renderedCoordinate, 130, 10, 200));
        }
    }
}
