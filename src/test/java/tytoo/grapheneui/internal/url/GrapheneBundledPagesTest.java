package tytoo.grapheneui.internal.url;

import org.junit.jupiter.api.Test;
import tytoo.grapheneui.api.config.GrapheneContainerConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneBundledPagesTest {
    private static final String PREFIX = "https://cdn.vantablack4.com/webassets/manuscript/bundled/";
    private static final String DIRECTORY = "assets/mod_manuscript/web/manuscript/";
    private static final GrapheneBundledPages PAGES = GrapheneBundledPages.of(Map.of(PREFIX, DIRECTORY));

    @Test
    void urlsUnderThePrefixComeFromTheJarDirectory() {
        assertEquals(DIRECTORY + "index.html", PAGES.resourcePathFor(PREFIX + "index.html?mode=editor&assetBase=x"));
        assertEquals(DIRECTORY + "assets/index.js", PAGES.resourcePathFor(PREFIX + "assets/index.js"));
        assertEquals(DIRECTORY + "fonts/knightwood.woff2", PAGES.resourcePathFor(PREFIX + "fonts/knightwood.woff2#x"));
        assertEquals(DIRECTORY + "index.html", PAGES.resourcePathFor(PREFIX));
        assertEquals(DIRECTORY + "index.html", PAGES.resourcePathFor("HTTPS://CDN.vantablack4.com:443/webassets/manuscript/bundled/index.html"));
    }

    @Test
    void urlsOutsideThePrefixStayOnTheNetwork() {
        assertNull(PAGES.resourcePathFor("https://cdn.vantablack4.com/webassets/manuscript/game-assets/manifest.json"));
        assertNull(PAGES.resourcePathFor("https://cdn.vantablack4.com/webassets/manuscript/v2/index.html"));
        assertNull(PAGES.resourcePathFor("https://cdn.vantablack4.com/webassets/manuscript/bundled"));
        assertNull(PAGES.resourcePathFor("http://cdn.vantablack4.com/webassets/manuscript/bundled/index.html"));
        assertNull(PAGES.resourcePathFor("https://cdn.vantablack4.com:8443/webassets/manuscript/bundled/index.html"));
        assertNull(PAGES.resourcePathFor("https://evil.example/webassets/manuscript/bundled/index.html"));
        assertNull(PAGES.resourcePathFor("app://assets/mod_manuscript/web/manuscript/index.html"));
        assertNull(PAGES.resourcePathFor(null));
        assertNull(GrapheneBundledPages.empty().resourcePathFor(PREFIX + "index.html"));
    }

    @Test
    void pathsCannotClimbOutOfTheDirectory() {
        assertNull(PAGES.resourcePathFor(PREFIX + "../../../mod_characters/web/index.html"));
        assertNull(PAGES.resourcePathFor(PREFIX + "%2e%2e/%2e%2e/secret.json"));
        assertNull(PAGES.resourcePathFor(PREFIX + "assets/..%2F..%2Fx.js"));
        assertNull(PAGES.resourcePathFor(PREFIX + "assets/%5c..%5cx.js"));
        assertNull(PAGES.resourcePathFor(PREFIX + "/etc/passwd"));
        assertNull(PAGES.resourcePathFor(PREFIX + "a//b.js"));
        assertNull(PAGES.resourcePathFor(PREFIX + "%zz"));
    }

    @Test
    void theLongestPrefixWins() {
        GrapheneBundledPages pages = GrapheneBundledPages.of(Map.of(
                "https://cdn.example/site/", "assets/a/site/",
                "https://cdn.example/site/editor/", "assets/b/editor/"
        ));

        assertEquals("assets/b/editor/index.html", pages.resourcePathFor("https://cdn.example/site/editor/index.html"));
        assertEquals("assets/a/site/other.html", pages.resourcePathFor("https://cdn.example/site/other.html"));
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
}
