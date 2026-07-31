package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class GrapheneVanillaSkinSlotRenderer implements GrapheneNativeSlotRenderer {
    private static final int MODEL_CACHE_LIMIT = 64;

    private final Map<ModelKey, PlayerModel> models = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ModelKey, PlayerModel> eldest) {
            return size() > MODEL_CACHE_LIMIT;
        }
    };

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
        PlayerModel model = playerModel(context.slotId(), slim);
        GrapheneNativeSlotScreenRect bounds = context.bounds();
        JsonObject renderOptions = context.renderOptions();
        boolean contain = "contain".equalsIgnoreCase(GrapheneNativeSlotJson.stringValue(renderOptions, null, "fit"));
        float defaultScale = contain
                ? GrapheneSkinSlotLayout.containScale(bounds)
                : GrapheneSkinSlotLayout.legacy(bounds).scale();
        float scale = GrapheneNativeSlotJson.floatValue(renderOptions, defaultScale, "scale");
        GrapheneSkinSlotLayout layout = contain
                ? GrapheneSkinSlotLayout.contain(
                        bounds,
                        GrapheneNativeSlotJson.floatValue(renderOptions, 0.5F, "alignY"),
                        scale
                )
                : new GrapheneSkinSlotLayout(scale, bounds.y(), bounds.bottom());
        float rotationX = GrapheneNativeSlotJson.floatValue(renderOptions, -10.0F, "rotationX");
        float rotationY = GrapheneNativeSlotJson.floatValue(renderOptions, 25.0F, "rotationY");
        float pivotY = GrapheneNativeSlotJson.floatValue(renderOptions, 0.0625F, "pivotY");
        float headRotationX = Math.clamp(
                GrapheneNativeSlotJson.floatValue(renderOptions, 0.0F, "headRotationX"),
                -45.0F,
                45.0F
        );
        float headRotationY = Math.clamp(
                GrapheneNativeSlotJson.floatValue(renderOptions, 0.0F, "headRotationY"),
                -60.0F,
                60.0F
        );
        model.head.resetPose();
        model.head.xRot = (float) Math.toRadians(headRotationX);
        model.head.yRot = (float) Math.toRadians(headRotationY);

        Identifier finalTexture = texture;
        context.withScissor(() -> context.graphics().skin(
                model,
                finalTexture,
                scale,
                rotationX,
                rotationY,
                pivotY,
                bounds.x(),
                layout.top(),
                bounds.right(),
                layout.bottom()
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

    private PlayerModel playerModel(String slotId, boolean slim) {
        return models.computeIfAbsent(
                new ModelKey(slotId, slim),
                ignored -> new PlayerModel(
                        McClient.mc().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),
                        slim
                )
        );
    }

    private record ModelKey(String slotId, boolean slim) {
    }
}
