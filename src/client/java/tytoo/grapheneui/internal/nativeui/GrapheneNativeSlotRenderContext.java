package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import org.joml.Matrix3x2fStack;
import tytoo.grapheneui.internal.mc.McClient;

final class GrapheneNativeSlotRenderContext {
    private final GuiGraphicsExtractor graphics;
    private final GrapheneNativeSlot slot;
    private final GrapheneNativeSlotScreenRect bounds;
    private final GrapheneNativeSlotScreenRect visibleBounds;
    private final GrapheneNativeSlotPointer pointer;

    GrapheneNativeSlotRenderContext(
            GuiGraphicsExtractor graphics,
            GrapheneNativeSlot slot,
            GrapheneNativeSlotScreenRect bounds,
            GrapheneNativeSlotScreenRect visibleBounds,
            GrapheneNativeSlotPointer pointer
    ) {
        this.graphics = graphics;
        this.slot = slot;
        this.bounds = bounds;
        this.visibleBounds = visibleBounds;
        this.pointer = pointer;
    }

    GuiGraphicsExtractor graphics() {
        return graphics;
    }

    Font font() {
        return McClient.mc().font;
    }

    GrapheneNativeSlotScreenRect bounds() {
        return bounds;
    }

    String slotId() {
        return slot.id();
    }

    JsonObject payload() {
        return slot.payload();
    }

    JsonObject renderOptions() {
        return slot.renderOptions();
    }

    JsonObject handoffOptions() {
        return slot.handoffOptions();
    }

    /** The part of {@link #bounds()} the page shows: smaller when a scrolling ancestor cuts the slot. */
    GrapheneNativeSlotScreenRect visibleBounds() {
        return visibleBounds;
    }

    boolean hasPointerInside() {
        return pointer.available() && visibleBounds.contains(pointer.x(), pointer.y());
    }

    boolean pointerAvailable() {
        return pointer.available();
    }

    int pointerX() {
        return pointer.x();
    }

    int pointerY() {
        return pointer.y();
    }

    void withScissor(Runnable renderAction) {
        graphics.enableScissor(visibleBounds.x(), visibleBounds.y(), visibleBounds.right(), visibleBounds.bottom());
        try {
            renderAction.run();
        } finally {
            graphics.disableScissor();
        }
    }

    void withPose(float x, float y, float scaleX, float scaleY, Runnable renderAction) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scaleX, scaleY);
            renderAction.run();
        } finally {
            pose.popMatrix();
        }
    }
}
