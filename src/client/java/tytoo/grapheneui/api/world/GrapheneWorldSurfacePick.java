package tytoo.grapheneui.api.world;

import net.minecraft.world.phys.Vec3;

import java.awt.Point;
import java.util.Objects;

@SuppressWarnings("unused")
public record GrapheneWorldSurfacePick(
        GrapheneWorldSurface surface,
        Vec3 hitPosition,
        double distance,
        boolean frontSide,
        double surfaceX,
        double surfaceY,
        int renderedWidth,
        int renderedHeight,
        Point browserPoint
) {
    public GrapheneWorldSurfacePick {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(hitPosition, "hitPosition");
        if (distance < 0.0D || !Double.isFinite(distance)) {
            throw new IllegalArgumentException("distance must be finite and >= 0");
        }
        if (!Double.isFinite(surfaceX)) {
            throw new IllegalArgumentException("surfaceX must be finite");
        }
        if (!Double.isFinite(surfaceY)) {
            throw new IllegalArgumentException("surfaceY must be finite");
        }
        if (renderedWidth <= 0) {
            throw new IllegalArgumentException("renderedWidth must be > 0");
        }
        if (renderedHeight <= 0) {
            throw new IllegalArgumentException("renderedHeight must be > 0");
        }
        Objects.requireNonNull(browserPoint, "browserPoint");
    }
}
