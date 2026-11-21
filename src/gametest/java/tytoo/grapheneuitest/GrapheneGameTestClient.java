package tytoo.grapheneuitest;

import net.fabricmc.api.ClientModInitializer;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.api.config.GrapheneConfig;
import tytoo.grapheneui.api.config.GrapheneContainerConfig;

public final class GrapheneGameTestClient implements ClientModInitializer {
    static final String BUNDLED_PAGE_PREFIX = "https://graphene-bundled.invalid/page/";

    @Override
    public void onInitializeClient() {
        GrapheneCore.register(
                GrapheneGameTestClient.class,
                GrapheneConfig.builder()
                        .container(GrapheneContainerConfig.builder()
                                .bundledPages(BUNDLED_PAGE_PREFIX, "assets/graphene_gametest/web/bundled/")
                                .build())
                        .build()
        );
    }
}
