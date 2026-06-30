package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

final class GrapheneVanillaItemSlotRenderer implements GrapheneNativeSlotRenderer {
    @Override
    public Set<String> kinds() {
        return Set.of("item", "minecraft:item", "vanilla:item");
    }

    @Override
    public void render(GrapheneNativeSlotRenderContext context) {
        ItemStack stack = stack(context.payload());
        GrapheneNativeItemStackRenderer.render(context, stack);
    }

    private ItemStack stack(JsonObject payload) {
        Identifier identifier = GrapheneNativeSlotJson.identifierValue(payload, "item", "id");
        if (identifier == null) {
            return ItemStack.EMPTY;
        }

        int count = Math.clamp(GrapheneNativeSlotJson.intValue(payload, 1, "count"), 1, 999);
        return BuiltInRegistries.ITEM.getOptional(identifier)
                .map(item -> createStack(item, count))
                .orElse(ItemStack.EMPTY);
    }

    private ItemStack createStack(Item item, int count) {
        return new ItemStack(item, count);
    }
}
