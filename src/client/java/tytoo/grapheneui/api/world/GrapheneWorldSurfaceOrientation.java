package tytoo.grapheneui.api.world;

import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Objects;

@SuppressWarnings("unused")
public final class GrapheneWorldSurfaceOrientation {
    private static final float HALF_PI = (float) (Math.PI * 0.5D);
    private static final float PI = (float) Math.PI;

    private final Quaternionf rotation;

    private GrapheneWorldSurfaceOrientation(Quaternionfc rotation) {
        this.rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
    }

    public static GrapheneWorldSurfaceOrientation identity() {
        return new GrapheneWorldSurfaceOrientation(new Quaternionf());
    }

    public static GrapheneWorldSurfaceOrientation custom(Quaternionfc rotation) {
        return new GrapheneWorldSurfaceOrientation(rotation);
    }

    public static GrapheneWorldSurfaceOrientation rotationDegrees(float pitch, float yaw, float roll) {
        return new GrapheneWorldSurfaceOrientation(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll)
        ));
    }

    public static GrapheneWorldSurfaceOrientation horizontalUp() {
        return custom(new Quaternionf().rotationX(-HALF_PI));
    }

    public static GrapheneWorldSurfaceOrientation horizontalDown() {
        return custom(new Quaternionf().rotationX(HALF_PI));
    }

    public static GrapheneWorldSurfaceOrientation vertical(Direction front) {
        Direction requiredFront = Objects.requireNonNull(front, "front");
        if (requiredFront.getAxis().isVertical()) {
            throw new IllegalArgumentException("front must be horizontal for a vertical world surface");
        }

        return blockFace(requiredFront);
    }

    public static GrapheneWorldSurfaceOrientation blockFace(Direction front) {
        return custom(rotationForFront(Objects.requireNonNull(front, "front")));
    }

    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    private static Quaternionf rotationForFront(Direction front) {
        return switch (front) {
            case SOUTH -> new Quaternionf();
            case NORTH -> new Quaternionf().rotationY(PI);
            case EAST -> new Quaternionf().rotationY(HALF_PI);
            case WEST -> new Quaternionf().rotationY(-HALF_PI);
            case UP -> new Quaternionf().rotationX(-HALF_PI);
            case DOWN -> new Quaternionf().rotationX(HALF_PI);
        };
    }
}
