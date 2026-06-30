package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.Set;
import java.util.UUID;

final class GrapheneVanillaSkinSlotRenderer implements GrapheneNativeSlotRenderer {
    private PlayerModel wideModel;
    private PlayerModel slimModel;

    @Override
    public Set<String> kinds() {
        return Set.of("skin", "player-skin", "player_skin", "minecraft:skin", "vanilla:skin");
    }

    @Override
    public void render(GrapheneNativeSlotRenderContext context) {
        JsonObject payload = context.payload();
        PlayerSkin skin = defaultSkin(payload);
        Identifier texture = GrapheneNativeSlotJson.identifierValue(payload, "texture", "skin", "id");
        if (texture == null) {
            texture = skin.body().texturePath();
        }

        boolean slim = isSlim(payload, skin);
        PlayerModel model = playerModel(slim);
        GrapheneNativeSlotScreenRect bounds = context.bounds();
        JsonObject renderOptions = context.renderOptions();
        float scale = GrapheneNativeSlotJson.floatValue(
                renderOptions,
                Math.max(1.0F, Math.min(bounds.width(), bounds.height()) * 0.55F),
                "scale"
        );
        float rotationX = GrapheneNativeSlotJson.floatValue(renderOptions, -10.0F, "rotationX");
        float rotationY = GrapheneNativeSlotJson.floatValue(renderOptions, 25.0F, "rotationY");
        float pivotY = GrapheneNativeSlotJson.floatValue(renderOptions, 0.0625F, "pivotY");

        Identifier finalTexture = texture;
        context.withScissor(() -> context.graphics().skin(
                model,
                finalTexture,
                scale,
                rotationX,
                rotationY,
                pivotY,
                bounds.x(),
                bounds.y(),
                bounds.right(),
                bounds.bottom()
        ));
    }

    private PlayerSkin defaultSkin(JsonObject payload) {
        UUID uuid = GrapheneNativeSlotJson.uuidValue(payload, "uuid", "playerUuid");
        return uuid == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(uuid);
    }

    private boolean isSlim(JsonObject payload, PlayerSkin skin) {
        String model = GrapheneNativeSlotJson.stringValue(payload, null, "model", "body");
        if (model != null) {
            return "slim".equalsIgnoreCase(model);
        }

        return skin.model() == PlayerModelType.SLIM;
    }

    private PlayerModel playerModel(boolean slim) {
        if (slim) {
            if (slimModel == null) {
                slimModel = new PlayerModel(McClient.mc().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            }

            return slimModel;
        }

        if (wideModel == null) {
            wideModel = new PlayerModel(McClient.mc().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }

        return wideModel;
    }
}
