package tytoo.grapheneui.internal.nativeui;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Set;

final class GrapheneVanillaBlockSlotRenderer implements GrapheneNativeSlotRenderer {
    @Override
    public Set<String> kinds() {
        return Set.of("block", "minecraft:block", "vanilla:block");
    }

    @Override
    public void render(GrapheneNativeSlotRenderContext context) {
        ItemStack stack = stack(context.payload());
        GrapheneNativeItemStackRenderer.render(context, stack);
    }

    private ItemStack stack(JsonObject payload) {
        Identifier identifier = GrapheneNativeSlotJson.identifierValue(payload, "block", "id");
        if (identifier == null) {
            return ItemStack.EMPTY;
        }

        return BuiltInRegistries.BLOCK.getOptional(identifier)
                .map(Block::asItem)
                .map(ItemStack::new)
                .filter(stack -> !stack.isEmpty())
                .orElse(ItemStack.EMPTY);
    }
}
