package tytoo.grapheneui.internal.url;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.config.GrapheneContainerConfig;
import tytoo.grapheneui.api.config.GrapheneProxiedAssets;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBundledPagesTest {
    private static final String SITE = "https://manuscript.graphene.invalid";
    private static final String PREFIX = SITE + "/editor/";
    private static final String PROXY_PREFIX = SITE + "/cdn/";
    private static final String DIRECTORY = "assets/mod_manuscript/web/manuscript/";
    private static final String UPSTREAM = "https://cdn.vantablack4.com/webassets/manuscript/";
    private static final GrapheneBundledPages PAGES = GrapheneBundledPages.of(
            Map.of(PREFIX, DIRECTORY),
            Map.of(PROXY_PREFIX, new GrapheneProxiedAssets(UPSTREAM, Set.of("json", "webp", "png", "otf", "ttf")))
    );

    @Test
    void urlsUnderThePrefixComeFromTheJarDirectory() {
        assertEquals(DIRECTORY + "index.html", path(PAGES, PREFIX + "index.html?mode=editor&assetBase=x"));
        assertEquals(DIRECTORY + "assets/index.js", path(PAGES, PREFIX + "assets/index.js"));
        assertEquals(DIRECTORY + "fonts/knightwood.woff2", path(PAGES, PREFIX + "fonts/knightwood.woff2#x"));
        assertEquals(DIRECTORY + "index.html", path(PAGES, PREFIX));
        assertEquals(DIRECTORY + "index.html", path(PAGES, "HTTPS://MANUSCRIPT.graphene.invalid:443/editor/index.html"));
    }

    @Test
    void proxiedAssetsMapToTheFixedUpstream() {
        assertEquals(UPSTREAM + "game-assets/manifest.json", upstream(PROXY_PREFIX + "game-assets/manifest.json"));
        assertEquals(
                UPSTREAM + "game-assets/sandbox-ui/parchment-frame-idle.webp",
                upstream(PROXY_PREFIX + "game-assets/sandbox-ui/parchment-frame-idle.webp")
        );
        assertEquals(
                UPSTREAM + "game-assets/text-system/atlases/atlas-548-inkulinatiyaza-regular-sdf-atlas.png",
                upstream(PROXY_PREFIX + "game-assets/text-system/atlases/atlas-548-inkulinatiyaza-regular-sdf-atlas.png")
        );
        assertEquals(UPSTREAM + "game-assets/fonts/inter-regular.otf", upstream(PROXY_PREFIX + "game-assets/fonts/inter-regular.otf"));
    }

    @Test
    void proxiedPathsAndTypesOutsideTheAllowListAreRefusedLocally() {
        for (String url : new String[]{
                PROXY_PREFIX + "game-assets/page.html",
                PROXY_PREFIX + "game-assets/icon.svg",
                PROXY_PREFIX + "game-assets/run.js",
                PROXY_PREFIX + "game-assets/manifest.json.html",
                PROXY_PREFIX + "game-assets/noextension",
                PROXY_PREFIX + "game-assets/manifest.JSON",
                PROXY_PREFIX + "game-assets/manifest.json?v=1",
                PROXY_PREFIX + "game-assets/.hidden.json",
                PROXY_PREFIX + "game-assets/a%20b.json",
                PROXY_PREFIX + "game-assets/a%252e%252e/b.json",
                PROXY_PREFIX + "game-assets/%2e%2e/%2e%2e/secret.json",
                PROXY_PREFIX + "game-assets/a%5Cb.json",
                PROXY_PREFIX + "game-assets/a%3Fb.json",
                PROXY_PREFIX + "game-assets//manifest.json",
                PROXY_PREFIX + "/game-assets/manifest.json",
                PROXY_PREFIX
        }) {
            assertTrue(PAGES.resolve(url).refused(), url + " -> " + PAGES.resolve(url));
        }
    }

    @Test
    void theOwnedOriginNeverFallsThroughToTheNetwork() {
        for (String url : new String[]{
                SITE + "/",
                SITE + "/favicon.ico",
                SITE + "/outside/network.html",
                SITE + "/editor",
                SITE + "/cdnx/manifest.json",
                SITE + "/editor/a%zz.js",
                PREFIX + "../../../mod_characters/web/index.html",
                PREFIX + "%2e%2e/%2e%2e/secret.json",
                PREFIX + "assets/..%2F..%2Fx.js",
                PREFIX + "assets/%5c..%5cx.js",
                PREFIX + "refused%5Cpath.js",
                PREFIX + "a//b.js"
        }) {
            GrapheneBundledPages.Resolution resolution = PAGES.resolve(url);
            assertTrue(resolution.owned() && resolution.refused(), url + " -> " + resolution);
        }
    }

    @Test
    void otherOriginsStayOnTheNetwork() {
        for (String url : new String[]{
                "https://cdn.vantablack4.com/webassets/manuscript/game-assets/manifest.json",
                "http://manuscript.graphene.invalid/editor/index.html",
                "https://manuscript.graphene.invalid:8443/editor/index.html",
                "https://evil.example/editor/index.html",
                "app://assets/mod_manuscript/web/manuscript/index.html",
                "about:blank"
        }) {
            assertEquals(GrapheneBundledPages.Resolution.NOT_OWNED, PAGES.resolve(url), url);
        }
        assertEquals(GrapheneBundledPages.Resolution.NOT_OWNED, PAGES.resolve(null));
        assertEquals(GrapheneBundledPages.Resolution.NOT_OWNED, GrapheneBundledPages.empty().resolve(PREFIX + "index.html"));
    }

    @Test
    void ownedOriginsAreKnown() {
        assertTrue(PAGES.ownsOriginOf(SITE + "/anything"));
        assertTrue(PAGES.ownsOriginOf("HTTPS://Manuscript.Graphene.Invalid:443/"));
        assertFalse(PAGES.ownsOriginOf("https://cdn.vantablack4.com/webassets/manuscript/game-assets/evil.html"));
        assertFalse(PAGES.ownsOriginOf("about:blank"));
        assertFalse(GrapheneBundledPages.empty().ownsOriginOf(PREFIX));
    }

    @Test
    void theLongestPrefixWins() {
        GrapheneBundledPages pages = GrapheneBundledPages.of(Map.of(
                "https://site.example.invalid/site/", "assets/a/site/",
                "https://site.example.invalid/site/editor/", "assets/b/editor/"
        ));

        assertEquals("assets/b/editor/index.html", path(pages, "https://site.example.invalid/site/editor/index.html"));
        assertEquals("assets/a/site/other.html", path(pages, "https://site.example.invalid/site/other.html"));
    }

    @Test
    void prefixesDirectoriesUpstreamsAndExtensionsAreValidated() {
        for (String prefix : new String[]{
                "https://site.example.invalid/site",
                "ftp://site.example.invalid/site/",
                "https:///site/",
                "https://user@site.example.invalid/site/",
                "https://site.example.invalid/site/?x=1",
                "https://site.example.invalid/site/../",
                "https://fa\u00df.invalid/site/",
                "not a url/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateUrlPrefix(prefix), prefix);
        }

        for (String directory : new String[]{"assets/mod/web", "mod/web/", "assets/../mod/", "assets/mod\\web/", "assets//mod/"}) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateResourceDirectory(directory), directory);
        }

        for (String upstream : new String[]{
                "https://cdn_assets.vantablack4.com/webassets/manuscript/",
                "http://cdn.vantablack4.com/webassets/manuscript/",
                "https://cdn.vantablack4.com/webassets/manuscript",
                "https://user@cdn.vantablack4.com/x/",
                "https://cdn.vantablack4.com/x/?a=1",
                "file:///C:/x/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateUpstreamBase(upstream), upstream);
        }
        assertEquals("http://127.0.0.1:20500/assets/", GrapheneBundledPages.validateUpstreamBase("http://127.0.0.1:20500/assets/"));

        for (String extension : new String[]{"html", "svg", "js", "mjs", "css", "xml", "wasm", "pdf", "JSON", "", "a.b"}) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateExtensions(Set.of(extension)), extension);
        }
        assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateExtensions(Set.of()));
    }

    @Test
    void anOwnedOriginMustBeAReservedHttpsHost() {
        for (String publicPrefix : new String[]{
                "https://cdn.vantablack4.com/webassets/manuscript/bundled/",
                "https://cdn.example/site/",
                "https://graphene.invalid.example.com/page/",
                "https://127.0.0.1/page/",
                "https://localhost/page/"
        }) {
            IllegalArgumentException refused = assertThrows(
                    IllegalArgumentException.class,
                    () -> GrapheneBundledPages.validateUrlPrefix(publicPrefix),
                    publicPrefix
            );
            assertTrue(refused.getMessage().contains(".invalid"), refused.getMessage());
            assertThrows(IllegalArgumentException.class, () -> GrapheneContainerConfig.builder().bundledPages(publicPrefix, DIRECTORY));
            assertThrows(IllegalArgumentException.class, () -> GrapheneContainerConfig.builder().proxiedAssets(publicPrefix, UPSTREAM, Set.of("json")));
        }

        for (String notReserved : new String[]{
                "http://manuscript.graphene.invalid/editor/",
                "https://manuscript.graphene.invalid:8443/editor/",
                "https://.invalid/editor/",
                "https://invalid/editor/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateUrlPrefix(notReserved), notReserved);
        }

        assertEquals(PREFIX, GrapheneBundledPages.validateUrlPrefix(PREFIX));
        assertEquals("https://Manuscript.Graphene.INVALID/x/", GrapheneBundledPages.validateUrlPrefix("https://Manuscript.Graphene.INVALID/x/"));
    }

    @Test
    void proxiedPathsWithAnyPercentEscapeAreRefused() {
        for (String url : new String[]{
                PROXY_PREFIX + "game-assets/%6danifest.json",
                PROXY_PREFIX + "game-assets%2Fmanifest.json",
                PROXY_PREFIX + "game-assets/manifest%2Ejson",
                PROXY_PREFIX + "game-assets/manifest.js%6Fn",
                PROXY_PREFIX + "%67ame-assets/manifest.json"
        }) {
            assertTrue(PAGES.resolve(url).refused(), url + " -> " + PAGES.resolve(url));
        }
        assertEquals(UPSTREAM + "game-assets/manifest.json", upstream(PROXY_PREFIX + "game-assets/manifest.json"));
    }

    @Test
    void containerConfigCarriesItsBundledPagesAndProxies() {
        GrapheneContainerConfig config = GrapheneContainerConfig.builder()
                .bundledPages(PREFIX, DIRECTORY)
                .proxiedAssets(PROXY_PREFIX, UPSTREAM, Set.of("json"))
                .build();

        assertEquals(Map.of(PREFIX, DIRECTORY), config.bundledPages());
        assertEquals(Map.of(PROXY_PREFIX, new GrapheneProxiedAssets(UPSTREAM, Set.of("json"))), config.proxiedAssets());
        assertTrue(GrapheneContainerConfig.defaults().bundledPages().isEmpty());
        assertTrue(GrapheneContainerConfig.defaults().proxiedAssets().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> GrapheneContainerConfig.builder().bundledPages(PREFIX, "web/"));
        assertThrows(IllegalArgumentException.class, () -> GrapheneContainerConfig.builder().proxiedAssets(PROXY_PREFIX, UPSTREAM, Set.of("html")));
    }

    private static String path(GrapheneBundledPages pages, String url) {
        return pages.resolve(url).resourcePath();
    }

    private static String upstream(String url) {
        return PAGES.resolve(url).upstreamUrl();
    }
}
