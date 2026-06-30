package tytoo.grapheneui.api.world;

import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.internal.world.GrapheneWorldSurfaceManager;

import java.util.Objects;

@SuppressWarnings("unused")
public final class GrapheneWorldSurfaces {
    private GrapheneWorldSurfaces() {
    }

    public static GrapheneWorldSurface create(GrapheneHandle handle, GrapheneWorldSurfaceConfig config) {
        return GrapheneWorldSurfaceManager.create(
                Objects.requireNonNull(handle, "handle"),
                Objects.requireNonNull(config, "config")
        );
    }

    public static void closeOwned(Object owner) {
        GrapheneWorldSurfaceManager.closeOwned(Objects.requireNonNull(owner, "owner"));
    }
}
