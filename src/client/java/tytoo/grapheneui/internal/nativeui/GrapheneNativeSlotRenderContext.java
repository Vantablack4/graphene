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
    private final GrapheneNativeSlotPointer pointer;

    GrapheneNativeSlotRenderContext(
            GuiGraphicsExtractor graphics,
            GrapheneNativeSlot slot,
            GrapheneNativeSlotScreenRect bounds,
            GrapheneNativeSlotPointer pointer
    ) {
        this.graphics = graphics;
        this.slot = slot;
        this.bounds = bounds;
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

    JsonObject payload() {
        return slot.payload();
    }

    JsonObject renderOptions() {
        return slot.renderOptions();
    }

    JsonObject handoffOptions() {
        return slot.handoffOptions();
    }

    boolean hasPointerInside() {
        return pointer.available() && bounds.contains(pointer.x(), pointer.y());
    }

    int pointerX() {
        return pointer.x();
    }

    int pointerY() {
        return pointer.y();
    }

    void withScissor(Runnable renderAction) {
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
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
