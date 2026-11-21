package tytoo.grapheneui.internal.bridge;

import tytoo.grapheneui.internal.url.GrapheneOrigins;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which documents may call a surface's bridge: only the main frame, and only while it shows a page from an
 * origin that Java itself loaded into the surface. Pages reached by the page's own navigation, popups and frames
 * from other origins get no bridge.
 */
public final class GrapheneBridgeCallerPolicy {
    private final Set<String> trustedOrigins = ConcurrentHashMap.newKeySet();

    public void trust(String pageUrl) {
        String origin = GrapheneOrigins.of(pageUrl);
        if (origin != null) {
            trustedOrigins.add(origin);
        }
    }

    public Decision decide(boolean mainFrame, String frameUrl) {
        if (!mainFrame) {
            return Decision.REFUSED_SUBFRAME;
        }

        String origin = GrapheneOrigins.of(frameUrl);
        if (origin == null || !trustedOrigins.contains(origin)) {
            return Decision.REFUSED_ORIGIN;
        }

        return Decision.ALLOWED;
    }

    public enum Decision {
        ALLOWED,
        REFUSED_SUBFRAME,
        REFUSED_ORIGIN;

        public boolean allowed() {
            return this == ALLOWED;
        }
    }
}
