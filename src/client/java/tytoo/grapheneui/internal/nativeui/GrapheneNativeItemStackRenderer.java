package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

final class GrapheneNativeItemStackRenderer {
    private static final int ITEM_SIZE = 16;

    private GrapheneNativeItemStackRenderer() {
    }

    static void render(GrapheneNativeSlotRenderContext context, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        GrapheneNativeSlotScreenRect bounds = context.bounds();
        JsonObject renderOptions = context.renderOptions();
        int seed = GrapheneNativeSlotJson.intValue(renderOptions, 0, "seed");
        boolean fake = GrapheneNativeSlotJson.booleanValue(renderOptions, false, "fake");
        boolean decorations = GrapheneNativeSlotJson.booleanValue(renderOptions, true, "decorations");
        String countText = GrapheneNativeSlotJson.stringValue(renderOptions, null, "countText", "countLabel");

        int renderSize = Math.max(1, Math.min(bounds.width(), bounds.height()));
        int itemX = bounds.x() + (bounds.width() - renderSize) / 2;
        int itemY = bounds.y() + (bounds.height() - renderSize) / 2;
        float scale = (float) renderSize / ITEM_SIZE;

        context.withScissor(() -> context.withPose(itemX, itemY, scale, scale, () -> {
            if (fake) {
                context.graphics().fakeItem(stack, 0, 0, seed);
            } else {
                context.graphics().item(stack, 0, 0, seed);
            }

            if (decorations) {
                if (countText == null) {
                    context.graphics().itemDecorations(context.font(), stack, 0, 0);
                } else {
                    context.graphics().itemDecorations(context.font(), stack, 0, 0, countText);
                }
            }
        }));

        if (context.hasPointerInside() && GrapheneNativeSlotJson.booleanValue(context.handoffOptions(), false, "tooltip")) {
            context.graphics().setTooltipForNextFrame(context.font(), stack, context.pointerX(), context.pointerY());
        }
    }
}
