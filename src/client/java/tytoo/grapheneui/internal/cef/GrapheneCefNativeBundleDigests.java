package tytoo.grapheneui.internal.cef;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pinned SHA-256 and size of every CEF native bundle Graphene may download.
 *
 * <p>The bundle is native code that runs on the player's machine, so a mirror's own answer is never trusted.
 * Each value was taken from Maven Central's published {@code .sha256} file and matched against GitHub's
 * server-computed digest of the same release asset (trethore/jcefgithub release 143.0.14.15).
 *
 * <p>Updating {@code jcefgithub_version} changes the release tag: add the new bundles here, from both sources,
 * or Graphene refuses to download them. {@code GrapheneCefNativeBundleDigestsTest} fails until it is done.
 */
final class GrapheneCefNativeBundleDigests {
    static final String RELEASE_TAG = "jcef-ceeefbe+cef-143.0.14+gdd46a37+chromium-143.0.7499.193";
    static final List<String> PLATFORMS = List.of(
            "linux-amd64", "linux-arm64", "macosx-amd64", "macosx-arm64", "windows-amd64", "windows-arm64");

    private static final Map<String, Pin> PINS = Map.of(
            key(RELEASE_TAG, "linux-amd64"), new Pin("8a9fd48ca6c81e547f929b3ee04c6bdfe728b066993ca9bb0d25498007357a5e", 141_587_891L),
            key(RELEASE_TAG, "linux-arm64"), new Pin("a31ea135b2a921a7ea9a32e3de3901d34b977ce1d17cf73eb02a825cd64a662f", 167_815_127L),
            key(RELEASE_TAG, "macosx-amd64"), new Pin("7d5e252d1708dbab76b7d93e8eeb085dda456e5d40847004ed0172ef55b3cede", 133_330_645L),
            key(RELEASE_TAG, "macosx-arm64"), new Pin("16867feb7c5b987dd79934d18b134762acc38124e79a7a68987c254ebc6641b0", 126_782_690L),
            key(RELEASE_TAG, "windows-amd64"), new Pin("2518a92d4e6b7d1aace4bab753f343dd9cfcbdbd78cb82ef675ad5249ff3d17e", 150_159_763L),
            key(RELEASE_TAG, "windows-arm64"), new Pin("76e3b3120079357f9e1ae862a26f4e3c8e8f19704c351428dccc13a956c7f84d", 151_247_548L)
    );

    private GrapheneCefNativeBundleDigests() {
    }

    static Optional<Pin> find(String releaseTag, String platformIdentifier) {
        return Optional.ofNullable(PINS.get(key(releaseTag, platformIdentifier)));
    }

    private static String key(String releaseTag, String platformIdentifier) {
        return releaseTag + "/" + platformIdentifier;
    }

    record Pin(String sha256, long size) {
        Pin {
            if (!sha256.matches("[0-9a-f]{64}") || size <= 0) {
                throw new IllegalArgumentException("invalid CEF native bundle pin");
            }
        }
    }
}
