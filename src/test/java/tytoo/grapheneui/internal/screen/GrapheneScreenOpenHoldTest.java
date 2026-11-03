package tytoo.grapheneui.internal.screen;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.internal.screen.GrapheneScreenOpenHold.WebViewState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneScreenOpenHoldTest {
    @Test
    void holdsWhileAWebViewHasPaintedNothing() {
        assertTrue(GrapheneScreenOpenHold.holdsBackgroundForStates(0L, List.of(WebViewState.BLANK)));
        assertTrue(GrapheneScreenOpenHold.holdsBackgroundForStates(0L, List.of(WebViewState.PAINTED, WebViewState.BLANK)));
    }

    @Test
    void releasesOnceEveryWebViewHasPainted() {
        assertFalse(GrapheneScreenOpenHold.holdsBackgroundForStates(0L, List.of(WebViewState.PAINTED)));
    }

    @Test
    void neverWaitsPastTheLimitForAPageThatStaysEmpty() {
        assertFalse(GrapheneScreenOpenHold.holdsBackgroundForStates(GrapheneScreenOpenHold.LIMIT_NANOS, List.of(WebViewState.BLANK)));
    }

    @Test
    void doesNotHoldForAScreenWithoutWebViews() {
        assertFalse(GrapheneScreenOpenHold.holdsBackgroundForStates(0L, List.of()));
    }

    @Test
    void doesNotHoldBehindTheStartupPlaceholder() {
        assertFalse(GrapheneScreenOpenHold.holdsBackgroundForStates(0L, List.of(WebViewState.UNAVAILABLE, WebViewState.BLANK)));
    }
}
