package tytoo.grapheneui.internal.cef;

import io.github.trethore.jcefgithub.CefBuildInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefNativeBundleDigestsTest {
    @Test
    void everyPlatformOfTheBundledCefBuildIsPinned() throws Exception {
        String releaseTag = CefBuildInfo.fromClasspath().getReleaseTag();

        // A jcefgithub update without new pins would make every player's first start fail closed.
        assertEquals(GrapheneCefNativeBundleDigests.RELEASE_TAG, releaseTag,
                "jcefgithub changed: pin the new CEF native bundles in GrapheneCefNativeBundleDigests");
        for (String platform : GrapheneCefNativeBundleDigests.PLATFORMS) {
            assertTrue(GrapheneCefNativeBundleDigests.find(releaseTag, platform).isPresent(), platform);
        }
    }
}
