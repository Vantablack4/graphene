package tytoo.grapheneui.api.config;

import tytoo.grapheneui.internal.url.GrapheneBundledPages;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unused")
public final class GrapheneContainerConfig {
    private static final GrapheneContainerConfig DEFAULT = builder().build();
    private static final String HTTP_CONFIG_NAME = "httpConfig";

    private final GrapheneHttpConfig httpConfig;
    private final Map<String, String> bundledPages;

    private GrapheneContainerConfig(Builder builder) {
        this.httpConfig = builder.httpConfig;
        this.bundledPages = Map.copyOf(builder.bundledPages);
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof GrapheneContainerConfig other)) {
            return false;
        }

        return Objects.equals(httpConfig, other.httpConfig) && bundledPages.equals(other.bundledPages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpConfig, bundledPages);
    }

    public static final class Builder {
        private final Map<String, String> bundledPages = new LinkedHashMap<>();
        private GrapheneHttpConfig httpConfig;

        private Builder() {
        }

        public Builder http(GrapheneHttpConfig httpConfig) {
            this.httpConfig = Objects.requireNonNull(httpConfig, HTTP_CONFIG_NAME);
            return this;
        }

        /**
         * Serves every request under {@code urlPrefix} (for example {@code https://cdn.example.com/site/page/}) from
         * the jar directory {@code resourceDirectory} (for example {@code assets/my_mod/web/page/}) instead of the
         * network. The page keeps the prefix's origin, so it can read that origin's other files without CORS, while
         * its HTML, scripts and styles cannot be changed by whoever serves the origin. Paths outside the prefix still
         * go to the network.
         */
        public Builder bundledPages(String urlPrefix, String resourceDirectory) {
            bundledPages.put(
                    GrapheneBundledPages.validateUrlPrefix(urlPrefix),
                    GrapheneBundledPages.validateResourceDirectory(resourceDirectory)
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
