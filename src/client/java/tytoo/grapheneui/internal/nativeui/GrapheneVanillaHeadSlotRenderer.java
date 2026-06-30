package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Set;
import java.util.UUID;

final class GrapheneVanillaHeadSlotRenderer implements GrapheneNativeSlotRenderer {
    @Override
    public Set<String> kinds() {
        return Set.of("head", "player-head", "player_head", "minecraft:head", "vanilla:head");
    }

    @Override
    public void render(GrapheneNativeSlotRenderContext context) {
        GrapheneNativeSlotScreenRect bounds = context.bounds();
        int size = Math.max(1, Math.min(bounds.width(), bounds.height()));
        int x = bounds.x() + (bounds.width() - size) / 2;
        int y = bounds.y() + (bounds.height() - size) / 2;
        JsonObject payload = context.payload();
        boolean hat = GrapheneNativeSlotJson.booleanValue(context.renderOptions(), true, "hat", "overlay");
        boolean upsideDown = GrapheneNativeSlotJson.booleanValue(context.renderOptions(), false, "upsideDown");
        int color = GrapheneNativeSlotJson.colorValue(context.renderOptions(), 0xFFFFFFFF, "color", "tint");
        Identifier texture = GrapheneNativeSlotJson.identifierValue(payload, "texture", "skin", "id");

        context.withScissor(() -> {
            if (texture != null) {
                PlayerFaceExtractor.extractRenderState(context.graphics(), texture, x, y, size, hat, upsideDown, color);
                return;
            }

            PlayerSkin skin = defaultSkin(payload);
            PlayerFaceExtractor.extractRenderState(context.graphics(), skin, x, y, size);
        });
    }

    private PlayerSkin defaultSkin(JsonObject payload) {
        UUID uuid = GrapheneNativeSlotJson.uuidValue(payload, "uuid", "playerUuid");
        return uuid == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(uuid);
    }
}
