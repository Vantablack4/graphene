package tytoo.grapheneui.internal.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GrapheneBridgeCallerPolicyTest {
    private static final String CARD_PAGE = "app://assets/mod_characters/web/character-card/index.html";
    private static final String LOCAL_PAGE = "http://127.0.0.1:20417/mods/mod_manuscript/index.html?mode=editor";
    private static final String CDN_PAGE = "https://cdn.vantablack4.com/webassets/manuscript/bundled/index.html?mode=editor";

    @Test
    void originsKeepSchemeHostAndPortOnly() {
        assertEquals("app://assets", GrapheneBridgeCallerPolicy.originOf(CARD_PAGE));
        assertEquals("http://127.0.0.1:20417", GrapheneBridgeCallerPolicy.originOf(LOCAL_PAGE));
        assertEquals("https://cdn.vantablack4.com", GrapheneBridgeCallerPolicy.originOf(CDN_PAGE));
        assertEquals("https://cdn.vantablack4.com", GrapheneBridgeCallerPolicy.originOf("HTTPS://CDN.Vantablack4.com:443/x"));
        assertEquals("http://example.com", GrapheneBridgeCallerPolicy.originOf("http://example.com:80"));
        assertEquals("http://[::1]:8080", GrapheneBridgeCallerPolicy.originOf("http://[::1]:8080/x"));
        assertEquals("http://[::1]", GrapheneBridgeCallerPolicy.originOf("http://[::1]/x"));
        assertEquals("classpath://", GrapheneBridgeCallerPolicy.originOf("classpath:///assets/graphene-ui/page.html"));
        assertEquals("https://cdn.vantablack4.com", GrapheneBridgeCallerPolicy.originOf("https://cdn.vantablack4.com#frag"));
    }

    @Test
    void documentsWithoutAnOriginOfTheirOwnHaveNone() {
        assertNull(GrapheneBridgeCallerPolicy.originOf(null));
        assertNull(GrapheneBridgeCallerPolicy.originOf(""));
        assertNull(GrapheneBridgeCallerPolicy.originOf("about:blank"));
        assertNull(GrapheneBridgeCallerPolicy.originOf("data:text/html,<script>cefQuery({})</script>"));
        assertNull(GrapheneBridgeCallerPolicy.originOf("blob:app://assets/4f1c"));
        assertNull(GrapheneBridgeCallerPolicy.originOf("javascript:alert(1)"));
        assertNull(GrapheneBridgeCallerPolicy.originOf("https://app.assets@evil.example/"));
        assertNull(GrapheneBridgeCallerPolicy.originOf("//evil.example/x"));
        assertNull(GrapheneBridgeCallerPolicy.originOf("1http://evil.example/x"));
    }

    @Test
    void theMainFrameOfALoadedPageMayCall() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CARD_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CARD_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, "app://assets/mod_characters/web/character-card/index.html#tab"));
    }

    @Test
    void subframesAreRefusedEvenFromTheSameOrigin() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CARD_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_SUBFRAME, policy.decide(false, CARD_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_SUBFRAME, policy.decide(false, "https://evil.example/frame.html"));
    }

    @Test
    void aMainFrameThatNavigatedElsewhereIsRefused() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(LOCAL_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "https://evil.example/popup.html"));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "http://127.0.0.1:20418/mods/mod_manuscript/index.html"));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "about:blank"));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "data:text/html,x"));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, null));
    }

    @Test
    void nothingIsTrustedBeforeJavaLoadsAPage() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust("about:blank");

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, CARD_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "about:blank"));
    }

    @Test
    void everyPageJavaLoadsStaysTrusted() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CDN_PAGE);
        policy.trust(LOCAL_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CDN_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, LOCAL_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, CARD_PAGE));
    }
}
