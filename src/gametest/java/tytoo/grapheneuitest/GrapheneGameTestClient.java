package tytoo.grapheneuitest;

import net.fabricmc.api.ClientModInitializer;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.api.config.GrapheneConfig;
import tytoo.grapheneui.api.config.GrapheneContainerConfig;

import java.util.Set;

public final class GrapheneGameTestClient implements ClientModInitializer {
    static final String OWNED_SITE = "https://graphene-bundled.invalid";
    static final String BUNDLED_PAGE_PREFIX = OWNED_SITE + "/page/";
    static final String PROXY_PREFIX = OWNED_SITE + "/cdn/";
    static final UpstreamAssetServer UPSTREAM = UpstreamAssetServer.start();

    @Override
    public void onInitializeClient() {
        GrapheneCore.register(
                GrapheneGameTestClient.class,
                GrapheneConfig.builder()
                        .container(GrapheneContainerConfig.builder()
                                .bundledPages(BUNDLED_PAGE_PREFIX, "assets/graphene_gametest/web/bundled/")
                                .proxiedAssets(PROXY_PREFIX, UPSTREAM.baseUrl(), Set.of("json", "png"))
                                .build())
                        .build()
        );
    }
}
