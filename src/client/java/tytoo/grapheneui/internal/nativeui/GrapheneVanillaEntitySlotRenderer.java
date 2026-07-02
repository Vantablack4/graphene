package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class GrapheneVanillaEntitySlotRenderer implements GrapheneNativeSlotRenderer {
    private static final Identifier PLAYER_IDENTIFIER = Identifier.fromNamespaceAndPath("minecraft", "player");

    private final Map<Identifier, Entity> entitiesByIdentifier = new HashMap<>();
    private ClientLevel cachedLevel;

    @Override
    public Set<String> kinds() {
        return Set.of("entity", "minecraft:entity", "vanilla:entity");
    }

    @Override
    public void render(GrapheneNativeSlotRenderContext context) {
        Entity entity = entity(context.payload());
        if (entity == null) {
            return;
        }

        GrapheneNativeSlotScreenRect bounds = context.bounds();
        JsonObject renderOptions = context.renderOptions();
        float scale = GrapheneNativeSlotJson.floatValue(
                renderOptions,
                Math.max(1.0F, Math.min(bounds.width(), bounds.height()) * 0.55F),
                "scale",
                "size"
        );
        float offsetY = GrapheneNativeSlotJson.floatValue(renderOptions, 0.0F, "offsetY");
        boolean followAlways = "always".equalsIgnoreCase(GrapheneNativeSlotJson.stringValue(renderOptions, null, "followMouse"));
        boolean followMouse = followAlways || GrapheneNativeSlotJson.booleanValue(renderOptions, true, "followMouse");
        boolean pointerActive = followAlways ? context.pointerAvailable() : context.hasPointerInside();

        context.withScissor(() -> {
            if (followMouse && pointerActive && entity instanceof LivingEntity livingEntity) {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        context.graphics(),
                        bounds.x(),
                        bounds.y(),
                        bounds.right(),
                        bounds.bottom(),
                        Math.round(scale),
                        offsetY,
                        context.pointerX(),
                        context.pointerY(),
                        livingEntity
                );
                return;
            }

            EntityRenderState state = McClient.mc().getEntityRenderDispatcher().extractEntity(entity, 1.0F);
            state.shadowPieces.clear();
            state.outlineColor = EntityRenderState.NO_OUTLINE;
            float rotationX = (float) Math.toRadians(GrapheneNativeSlotJson.floatValue(renderOptions, 0.0F, "rotationX"));
            float rotationY = (float) Math.toRadians(GrapheneNativeSlotJson.floatValue(renderOptions, 0.0F, "rotationY"));
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(rotationX).rotateY(rotationY);
            Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + offsetY, 0.0F);

            context.graphics().entity(state, scale, translation, rotation, null, bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        });
    }

    private Entity entity(JsonObject payload) {
        ClientLevel level = McClient.mc().level;
        if (level == null) {
            cachedLevel = null;
            entitiesByIdentifier.clear();
            return null;
        }

        if (cachedLevel != level) {
            cachedLevel = level;
            entitiesByIdentifier.clear();
        }

        Entity player = player(payload, level);
        if (player != null) {
            return player;
        }

        Identifier identifier = GrapheneNativeSlotJson.identifierValue(payload, "entity", "type", "id");
        if (identifier == null) {
            return null;
        }

        if (PLAYER_IDENTIFIER.equals(identifier)) {
            return McClient.mc().player;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
        if (entityType == null) {
            return null;
        }

        return entitiesByIdentifier.computeIfAbsent(identifier, ignored -> entityType.create(level, EntitySpawnReason.COMMAND));
    }

    private Entity player(JsonObject payload, ClientLevel level) {
        UUID playerUuid = GrapheneNativeSlotJson.uuidValue(payload, "playerUuid", "player");
        if (playerUuid != null) {
            return level.getPlayerByUUID(playerUuid);
        }

        String player = GrapheneNativeSlotJson.stringValue(payload, null, "player");
        if ("self".equalsIgnoreCase(player) || "local".equalsIgnoreCase(player)) {
            return McClient.mc().player;
        }

        return null;
    }
}
