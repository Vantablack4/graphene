package tytoo.grapheneui.internal.url;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GrapheneOriginsTest {
    @Test
    void webOriginsAreCanonical() {
        assertEquals("https://cdn.vantablack4.com", GrapheneOrigins.of("https://cdn.vantablack4.com/webassets/manuscript/bundled/index.html?mode=editor"));
        assertEquals("https://cdn.vantablack4.com", GrapheneOrigins.of("HTTPS://CDN.Vantablack4.COM:443/x"));
        assertEquals("https://cdn.vantablack4.com", GrapheneOrigins.of("https://cdn.vantablack4.com:/x"));
        assertEquals("https://cdn.vantablack4.com", GrapheneOrigins.of("https://cdn.vantablack4.com#frag"));
        assertEquals("https://cdn.vantablack4.com:8443", GrapheneOrigins.of("https://cdn.vantablack4.com:8443/x"));
        assertEquals("http://example.com", GrapheneOrigins.of("http://example.com:80"));
        assertEquals("http://example.com:443", GrapheneOrigins.of("http://example.com:443/"));
        assertEquals("http://127.0.0.1:20417", GrapheneOrigins.of("http://127.0.0.1:20417/mods/mod_manuscript/index.html"));
        assertEquals("http://[::1]:8080", GrapheneOrigins.of("http://[::1]:8080/x"));
        assertEquals("http://[::1]", GrapheneOrigins.of("http://[::1]/x"));
        assertEquals("https://xn--bcher-kva.example", GrapheneOrigins.of("https://bücher.example/x"));
        assertEquals(GrapheneOrigins.of("https://xn--bcher-kva.example/"), GrapheneOrigins.of("https://BÜCHER.example/"));
    }

    @Test
    void graphenesOwnSchemesHaveOrigins() {
        assertEquals("app://assets", GrapheneOrigins.of("app://assets/mod_characters/web/character-card/index.html"));
        assertEquals("app://assets", GrapheneOrigins.of("APP://Assets/mod_characters/web/index.html"));
        assertEquals("classpath://", GrapheneOrigins.of("classpath:///assets/graphene-ui/page.html"));
    }

    @Test
    void everyOtherSchemeHasNoOrigin() {
        for (String url : new String[]{
                null,
                "",
                "about:blank",
                "about://blank",
                "data:text/html,<script>cefQuery({})</script>",
                "data://text/html,x",
                "blob:app://assets/4f1c",
                "blob://assets/4f1c",
                "javascript:alert(1)",
                "javascript://evil.example/%0aalert(1)",
                "file:///C:/Users/x/page.html",
                "file://server/share/page.html",
                "ws://evil.example/",
                "chrome://settings/",
                "devtools://devtools/bundled/inspector.html",
                "view-source:https://cdn.vantablack4.com/",
                "filesystem:https://cdn.vantablack4.com/temporary/x",
                "foo://cdn.vantablack4.com/",
                "//evil.example/x",
                "1http://evil.example/x"
        }) {
            assertNull(GrapheneOrigins.of(url), url);
        }
    }

    @Test
    void malformedWebAuthoritiesHaveNoOrigin() {
        for (String url : new String[]{
                "https://app.assets@evil.example/",
                "https:///x",
                "https://:443/x",
                "https://cdn.example:99999/x",
                "https://cdn.example:44a/x",
                "https://cdn%2eexample/x",
                "https://[::1/x",
                "app:///x",
                "classpath://assets/x"
        }) {
            assertNull(GrapheneOrigins.of(url), url);
        }
    }
}
