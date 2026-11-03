package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.internal.url.GrapheneAppUrls;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GrapheneSurfaceWarmupPageTest {
    @Test
    void theWarmupUrlResolvesToABundledPage() {
        String url = GrapheneAppUrls.asset(GrapheneCore.ID, GrapheneSurfaceWarmup.PAGE);
        String resourcePath = GrapheneAppUrls.normalizeResourcePath(url);

        assertNotNull(GrapheneSurfaceWarmupPageTest.class.getClassLoader().getResource(resourcePath), resourcePath);
    }
}
