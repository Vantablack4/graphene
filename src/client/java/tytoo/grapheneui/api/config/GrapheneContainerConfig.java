package tytoo.grapheneui.api.config;

import tytoo.grapheneui.internal.url.GrapheneBundledPages;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("unused")
public final class GrapheneContainerConfig {
    private static final GrapheneContainerConfig DEFAULT = builder().build();
    private static final String HTTP_CONFIG_NAME = "httpConfig";

    private final GrapheneHttpConfig httpConfig;
    private final Map<String, String> bundledPages;
    private final Map<String, GrapheneProxiedAssets> proxiedAssets;

    private GrapheneContainerConfig(Builder builder) {
        this.httpConfig = builder.httpConfig;
        this.bundledPages = Map.copyOf(builder.bundledPages);
        this.proxiedAssets = Map.copyOf(builder.proxiedAssets);
    }

    public static GrapheneContainerConfig defaults() {
        return DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<GrapheneHttpConfig> http() {
        return Optional.ofNullable(httpConfig);
    }

    /**
     * The jar directories that serve web URL prefixes, by prefix. See {@link Builder#bundledPages(String, String)}.
     */
    public Map<String, String> bundledPages() {
        return bundledPages;
    }

    /**
     * The read-only proxy mounts, by URL prefix. See {@link Builder#proxiedAssets(String, String, Set)}.
     */
    public Map<String, GrapheneProxiedAssets> proxiedAssets() {
        return proxiedAssets;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof GrapheneContainerConfig other)) {
            return false;
        }

        return Objects.equals(httpConfig, other.httpConfig)
                && bundledPages.equals(other.bundledPages)
                && proxiedAssets.equals(other.proxiedAssets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpConfig, bundledPages, proxiedAssets);
    }

    public static final class Builder {
        private final Map<String, String> bundledPages = new LinkedHashMap<>();
        private final Map<String, GrapheneProxiedAssets> proxiedAssets = new LinkedHashMap<>();
        private GrapheneHttpConfig httpConfig;

        private Builder() {
        }

        public Builder http(GrapheneHttpConfig httpConfig) {
            this.httpConfig = Objects.requireNonNull(httpConfig, HTTP_CONFIG_NAME);
            return this;
        }

        /**
         * Serves every request under {@code urlPrefix} (for example {@code https://my-mod.graphene.invalid/page/})
         * from the jar directory {@code resourceDirectory} (for example {@code assets/my_mod/web/page/}). Graphene
         * then owns the prefix's whole origin: every request on it is answered by a bundled or proxied mount or with
         * a local error, and none reaches the network. The prefix must be https on a reserved host ending in
         * {@code .invalid} (RFC 6761), with no port; any other host is refused.
         */
        public Builder bundledPages(String urlPrefix, String resourceDirectory) {
            bundledPages.put(
                    GrapheneBundledPages.validateUrlPrefix(urlPrefix),
                    GrapheneBundledPages.validateResourceDirectory(resourceDirectory)
            );
            return this;
        }

        /**
         * Fetches requests under {@code urlPrefix} on a Graphene-owned origin from {@code upstreamBase} plus the same
         * relative path, so a bundled page on that origin reads remote data (JSON, images, fonts) as same-origin
         * files. Only GET requests for the listed extensions with a plain path (letters, digits, {@code . _ -}, no
         * dot segments, no percent escapes, no query) are fetched; the response type comes from the extension, never
         * from upstream, and any other request or upstream failure is a local error. The prefix follows the same
         * {@code .invalid} rule as {@link #bundledPages(String, String)}.
         */
        public Builder proxiedAssets(String urlPrefix, String upstreamBase, Set<String> extensions) {
            proxiedAssets.put(
                    GrapheneBundledPages.validateUrlPrefix(urlPrefix),
                    new GrapheneProxiedAssets(upstreamBase, extensions)
            );
            return this;
        }

        public Builder disableHttp() {
            this.httpConfig = null;
            return this;
        }

        public GrapheneContainerConfig build() {
            return new GrapheneContainerConfig(this);
        }
    }
}
