package tytoo.grapheneui.api.world;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.BrowserSurfaceInputAdapter;

import java.util.Optional;

@SuppressWarnings("unused")
public interface GrapheneWorldSurface extends AutoCloseable {
    Identifier surfaceId();

    BrowserSurface surface();

    GrapheneBridge bridge();

    GrapheneNativeSlots nativeSlots();

    BrowserSurfaceInputAdapter inputAdapter();

    ResourceKey<Level> dimension();

    void setDimension(ResourceKey<Level> dimension);

    Vec3 position();

    void setPosition(Vec3 position);

    float worldWidth();

    float worldHeight();

    void setWorldSize(float width, float height);

    Quaternionf rotation();

    GrapheneWorldSurfaceOrientation orientation();

    void setRotation(Quaternionfc rotation);

    void setOrientation(GrapheneWorldSurfaceOrientation orientation);

    void setRotationDegrees(float pitch, float yaw, float roll);

    GrapheneWorldSurfaceFacing facing();

    void setFacing(GrapheneWorldSurfaceFacing facing);

    GrapheneWorldSurfaceSide side();

    void setSide(GrapheneWorldSurfaceSide side);

    double maxDistance();

    void setMaxDistance(double maxDistance);

    double interactionReach();

    void setInteractionReach(double interactionReach);

    boolean renderWhenScreenOpen();

    void setRenderWhenScreenOpen(boolean renderWhenScreenOpen);

    /**
     * Intersects a world ray with this rendered surface using the same orientation, facing, side, distance,
     * dimension, and screen-open visibility rules as rendering.
     */
    Optional<GrapheneWorldSurfacePick> pickFromRay(ResourceKey<Level> dimension, Vec3 rayOrigin, Vec3 rayDirection, double rayLength);

    @Override
    void close();
}
