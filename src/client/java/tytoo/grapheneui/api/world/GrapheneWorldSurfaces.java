package tytoo.grapheneui.api.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import tytoo.grapheneui.api.GrapheneHandle;
import tytoo.grapheneui.internal.world.GrapheneWorldSurfaceManager;

import java.util.Objects;
import java.util.Optional;

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

    /**
     * Returns the closest visible world surface hit by a ray, mapped to logical browser-surface coordinates.
     */
    public static Optional<GrapheneWorldSurfacePick> pickNearestFromRay(
            ResourceKey<Level> dimension,
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double rayLength
    ) {
        return GrapheneWorldSurfaceManager.pickNearestFromRay(
                dimension,
                Objects.requireNonNull(rayOrigin, "rayOrigin"),
                Objects.requireNonNull(rayDirection, "rayDirection"),
                rayLength
        );
    }

    public static void closeOwned(Object owner) {
        GrapheneWorldSurfaceManager.closeOwned(Objects.requireNonNull(owner, "owner"));
    }
}
