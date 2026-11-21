package tytoo.grapheneui.internal.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GrapheneBridgeCallerPolicyTest {
    private static final String CARD_PAGE = "app://assets/mod_characters/web/character-card/index.html";
    private static final String LOCAL_PAGE = "http://127.0.0.1:20417/mods/mod_manuscript/index.html?mode=editor";
    private static final String CDN_PAGE = "https://cdn.vantablack4.com/webassets/manuscript/bundled/index.html?mode=editor";

    @Test
    void theMainFrameOfALoadedPageMayCall() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CARD_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CARD_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, "app://assets/mod_characters/web/character-card/index.html#tab"));
    }

    @Test
    void javasSpellingAndChromiumsCanonicalUrlMatch() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust("HTTPS://CDN.Vantablack4.com:443/webassets/manuscript/bundled/index.html");

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CDN_PAGE));
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
        policy.trust("data:text/html,x");
        policy.trust("foo://assets/x");

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, CARD_PAGE));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "about:blank"));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "foo://assets/x"));
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
