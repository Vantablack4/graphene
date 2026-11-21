package tytoo.grapheneui.internal.bridge;

import tytoo.grapheneui.internal.url.GrapheneOrigins;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which documents may call a surface's bridge: only the main frame, and only while it shows a page from an
 * origin that Java itself loaded into the surface. Pages reached by the page's own navigation, popups and frames
 * from other origins get no bridge. On an origin that has bundled pages, the document must also be one Graphene
 * served from the jar, because the rest of that origin is served by whoever runs the host.
 */
public final class GrapheneBridgeCallerPolicy {
    private final Set<String> trustedOrigins = ConcurrentHashMap.newKeySet();

    public void trust(String pageUrl) {
        String origin = GrapheneOrigins.of(pageUrl);
        if (origin != null) {
            trustedOrigins.add(origin);
        }
    }

    public Decision decide(boolean mainFrame, String frameUrl, boolean jarDocumentRequired, boolean jarDocument) {
        if (!mainFrame) {
            return Decision.REFUSED_SUBFRAME;
        }

        String origin = GrapheneOrigins.of(frameUrl);
        if (origin == null || !trustedOrigins.contains(origin)) {
            return Decision.REFUSED_ORIGIN;
        }

        if (jarDocumentRequired && !jarDocument) {
            return Decision.REFUSED_NOT_FROM_JAR;
        }

        return Decision.ALLOWED;
    }

    public enum Decision {
        ALLOWED,
        REFUSED_SUBFRAME,
        REFUSED_ORIGIN,
        REFUSED_NOT_FROM_JAR;

        public boolean allowed() {
            return this == ALLOWED;
        }
    }
}
