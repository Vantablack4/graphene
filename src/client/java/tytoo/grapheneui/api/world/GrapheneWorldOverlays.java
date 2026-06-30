package tytoo.grapheneui.api.world;

import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.internal.world.GrapheneWorldOverlayManager;

import java.util.Objects;

@SuppressWarnings("unused")
public final class GrapheneWorldOverlays {
    private GrapheneWorldOverlays() {
    }

    public static GrapheneWorldOverlayLayer create(GrapheneHandle handle, GrapheneWorldOverlayConfig config) {
        return GrapheneWorldOverlayManager.create(
                Objects.requireNonNull(handle, "handle"),
                Objects.requireNonNull(config, "config")
        );
    }

    public static void closeOwned(Object owner) {
        GrapheneWorldOverlayManager.closeOwned(Objects.requireNonNull(owner, "owner"));
    }
}
