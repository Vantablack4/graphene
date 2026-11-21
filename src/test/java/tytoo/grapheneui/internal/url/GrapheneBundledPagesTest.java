package tytoo.grapheneui.internal.url;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.config.GrapheneContainerConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBundledPagesTest {
    private static final String PREFIX = "https://cdn.vantablack4.com/webassets/manuscript/bundled/";
    private static final String DIRECTORY = "assets/mod_manuscript/web/manuscript/";
    private static final GrapheneBundledPages PAGES = GrapheneBundledPages.of(Map.of(PREFIX, DIRECTORY));

    @Test
    void urlsUnderThePrefixComeFromTheJarDirectory() {
        assertEquals(DIRECTORY + "index.html", path(PAGES, PREFIX + "index.html?mode=editor&assetBase=x"));
        assertEquals(DIRECTORY + "assets/index.js", path(PAGES, PREFIX + "assets/index.js"));
        assertEquals(DIRECTORY + "fonts/knightwood.woff2", path(PAGES, PREFIX + "fonts/knightwood.woff2#x"));
        assertEquals(DIRECTORY + "index.html", path(PAGES, PREFIX));
        assertEquals(DIRECTORY + "index.html", path(PAGES, "HTTPS://CDN.vantablack4.com:443/webassets/manuscript/bundled/index.html"));
    }

    @Test
    void urlsOutsideThePrefixStayOnTheNetwork() {
        for (String url : new String[]{
                "https://cdn.vantablack4.com/webassets/manuscript/game-assets/manifest.json",
                "https://cdn.vantablack4.com/webassets/manuscript/v2/index.html",
                "https://cdn.vantablack4.com/webassets/manuscript/bundled",
                "http://cdn.vantablack4.com/webassets/manuscript/bundled/index.html",
                "https://cdn.vantablack4.com:8443/webassets/manuscript/bundled/index.html",
                "https://evil.example/webassets/manuscript/bundled/index.html",
                "https://cdn.vantablack4.com/webassets/manuscript/game-assets/a%zz.png",
                "app://assets/mod_manuscript/web/manuscript/index.html"
        }) {
            assertEquals(GrapheneBundledPages.Resolution.NOT_MOUNTED, PAGES.resolve(url), url);
        }
        assertEquals(GrapheneBundledPages.Resolution.NOT_MOUNTED, PAGES.resolve(null));
        assertEquals(GrapheneBundledPages.Resolution.NOT_MOUNTED, GrapheneBundledPages.empty().resolve(PREFIX + "index.html"));
    }

    @Test
    void pathsCannotClimbOutOfTheDirectoryAndNeverReachTheNetwork() {
        for (String url : new String[]{
                PREFIX + "../../../mod_characters/web/index.html",
                PREFIX + "%2e%2e/%2e%2e/secret.json",
                PREFIX + "assets/..%2F..%2Fx.js",
                PREFIX + "assets/%5c..%5cx.js",
                PREFIX + "refused%5Cpath.js",
                PREFIX + "/etc/passwd",
                PREFIX + "a//b.js",
                PREFIX + "%zz",
                PREFIX + "a b.js",
                "https://cdn.vantablack4.com/webassets/manuscript/bundled%2F..%2Fv2/index.html"
        }) {
            GrapheneBundledPages.Resolution resolution = PAGES.resolve(url);
            assertTrue(resolution.mounted() && resolution.refused(), url + " -> " + resolution);
        }
    }

    @Test
    void originsWithBundledPagesAreKnown() {
        assertTrue(PAGES.mountsOriginOf("https://cdn.vantablack4.com/webassets/manuscript/game-assets/evil.html"));
        assertTrue(PAGES.mountsOriginOf("HTTPS://CDN.vantablack4.com:443/"));
        assertFalse(PAGES.mountsOriginOf("http://cdn.vantablack4.com/webassets/manuscript/bundled/index.html"));
        assertFalse(PAGES.mountsOriginOf("app://assets/mod_manuscript/web/manuscript/index.html"));
        assertFalse(PAGES.mountsOriginOf("about:blank"));
        assertFalse(GrapheneBundledPages.empty().mountsOriginOf(PREFIX));
    }

    @Test
    void theLongestPrefixWins() {
        GrapheneBundledPages pages = GrapheneBundledPages.of(Map.of(
                "https://cdn.example/site/", "assets/a/site/",
                "https://cdn.example/site/editor/", "assets/b/editor/"
        ));

        assertEquals("assets/b/editor/index.html", path(pages, "https://cdn.example/site/editor/index.html"));
        assertEquals("assets/a/site/other.html", path(pages, "https://cdn.example/site/other.html"));
    }

    @Test
    void prefixesAndDirectoriesAreValidated() {
        for (String prefix : new String[]{
                "https://cdn.example/site",
                "ftp://cdn.example/site/",
                "https:///site/",
                "https://user@cdn.example/site/",
                "https://cdn.example/site/?x=1",
                "https://cdn.example/site/../",
                "not a url/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateUrlPrefix(prefix), prefix);
        }

        for (String directory : new String[]{
                "assets/mod/web",
                "mod/web/",
                "assets/../mod/",
                "assets/mod\\web/",
                "assets//mod/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> GrapheneBundledPages.validateResourceDirectory(directory), directory);
        }
    }

    @Test
    void containerConfigCarriesItsBundledPages() {
        GrapheneContainerConfig config = GrapheneContainerConfig.builder().bundledPages(PREFIX, DIRECTORY).build();

        assertEquals(Map.of(PREFIX, DIRECTORY), config.bundledPages());
        assertTrue(GrapheneContainerConfig.defaults().bundledPages().isEmpty());
        assertEquals(config, GrapheneContainerConfig.builder().bundledPages(PREFIX, DIRECTORY).build());
        assertThrows(IllegalArgumentException.class, () -> GrapheneContainerConfig.builder().bundledPages(PREFIX, "web/"));
    }

    private static String path(GrapheneBundledPages pages, String url) {
        return pages.resolve(url).resourcePath();
    }
}
