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

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CARD_PAGE, false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, "app://assets/mod_characters/web/character-card/index.html#tab", false, false));
    }

    @Test
    void javasSpellingAndChromiumsCanonicalUrlMatch() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust("HTTPS://CDN.Vantablack4.com:443/webassets/manuscript/bundled/index.html");

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CDN_PAGE, true, true));
    }

    @Test
    void subframesAreRefusedEvenFromTheSameOrigin() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CARD_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_SUBFRAME, policy.decide(false, CARD_PAGE, false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_SUBFRAME, policy.decide(false, "https://evil.example/frame.html", false, false));
    }

    @Test
    void aMainFrameThatNavigatedElsewhereIsRefused() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(LOCAL_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "https://evil.example/popup.html", false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "http://127.0.0.1:20418/mods/mod_manuscript/index.html", false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "about:blank", false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "data:text/html,x", false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, null, false, false));
    }

    @Test
    void nothingIsTrustedBeforeJavaLoadsAPage() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust("about:blank");
        policy.trust("data:text/html,x");
        policy.trust("foo://assets/x");

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, CARD_PAGE, false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "about:blank", false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, "foo://assets/x", false, false));
    }

    @Test
    void everyPageJavaLoadsStaysTrusted() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CDN_PAGE);
        policy.trust(LOCAL_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CDN_PAGE, true, true));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, LOCAL_PAGE, false, false));
        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_ORIGIN, policy.decide(true, CARD_PAGE, false, false));
    }

    @Test
    void onABundledOriginOnlyADocumentServedFromTheJarMayCall() {
        GrapheneBridgeCallerPolicy policy = new GrapheneBridgeCallerPolicy();
        policy.trust(CDN_PAGE);

        assertEquals(GrapheneBridgeCallerPolicy.Decision.REFUSED_NOT_FROM_JAR, policy.decide(true, CDN_PAGE, true, false));
        assertEquals(
                GrapheneBridgeCallerPolicy.Decision.REFUSED_NOT_FROM_JAR,
                policy.decide(true, "https://cdn.vantablack4.com/webassets/manuscript/game-assets/evil.html", true, false)
        );
        assertEquals(GrapheneBridgeCallerPolicy.Decision.ALLOWED, policy.decide(true, CDN_PAGE, true, true));
    }
}
