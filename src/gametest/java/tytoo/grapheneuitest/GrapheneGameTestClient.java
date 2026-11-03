package tytoo.grapheneuitest;

import net.fabricmc.api.ClientModInitializer;
import tytoo.grapheneui.api.GrapheneCore;

public final class GrapheneGameTestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GrapheneCore.register(GrapheneGameTestClient.class);
    }
}
