package tytoo.grapheneui.internal.url;

import tytoo.grapheneui.api.config.GrapheneProxiedAssets;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The origins Graphene owns entirely. Every request on such an origin is answered by Graphene: from a jar directory
 * (bundled pages), through a read-only proxy to one upstream directory (proxied assets), or with a local error. None
 * ever reaches the network as itself, so only the mods' own content lives on these origins.
 */
public final class GrapheneBundledPages {
    private static final GrapheneBundledPages EMPTY = new GrapheneBundledPages(List.of());
    private static final String PATH_DELIMITER = "/";
    private static final String ASSETS_PREFIX = "assets" + PATH_DELIMITER;
    private static final String INDEX_PAGE = "index.html";
    private static final Pattern PROXIED_PATH = Pattern.compile(
            "(?:[A-Za-z0-9_-][A-Za-z0-9._-]*/)*[A-Za-z0-9_-][A-Za-z0-9._-]*"
    );
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,8}");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "htm", "html", "xhtml", "xht", "xml", "svg", "svgz", "js", "mjs", "css", "wasm", "pdf"
    );
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");

    private final List<Mount> mounts;
    private final Set<String> ownedOrigins;

    private GrapheneBundledPages(List<Mount> mounts) {
        this.mounts = List.copyOf(mounts);
        Set<String> origins = new HashSet<>();
        for (Mount mount : this.mounts) {
            origins.add(mount.origin());
        }
        this.ownedOrigins = Set.copyOf(origins);
    }

    public static GrapheneBundledPages empty() {
        return EMPTY;
    }

    public static GrapheneBundledPages of(Map<String, String> resourceDirectoriesByUrlPrefix) {
        return of(resourceDirectoriesByUrlPrefix, Map.of());
    }

    /**
     * Builds the owned origins from jar mounts ({@code urlPrefix -> resourceDirectory}) and proxy mounts
     * ({@code urlPrefix -> proxy}). The longest prefix wins.
     */
    public static GrapheneBundledPages of(
            Map<String, String> resourceDirectoriesByUrlPrefix,
            Map<String, GrapheneProxiedAssets> proxiesByUrlPrefix
    ) {
        List<Mount> mounts = new ArrayList<>();
        for (Map.Entry<String, String> entry : resourceDirectoriesByUrlPrefix.entrySet()) {
            mounts.add(mount(entry.getKey(), validateResourceDirectory(entry.getValue()), null));
        }

        for (Map.Entry<String, GrapheneProxiedAssets> entry : proxiesByUrlPrefix.entrySet()) {
            mounts.add(mount(entry.getKey(), null, Objects.requireNonNull(entry.getValue(), "proxy")));
        }

        mounts.sort((left, right) -> Integer.compare(right.path().length(), left.path().length()));
        return new GrapheneBundledPages(mounts);
    }

    /**
     * Checks a URL prefix: an absolute http or https URL with a lower-case ASCII host, no query, fragment or user
     * info, and a path that ends with a slash.
     */
    public static String validateUrlPrefix(String urlPrefix) {
        String value = Objects.requireNonNull(urlPrefix, "urlPrefix").strip();
        URI uri = parse(value);
        if (uri == null
                || uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))
                || uri.getHost() == null
                || GrapheneOrigins.of(value) == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() == null
                || !uri.getRawPath().endsWith(PATH_DELIMITER)
                || hasDotSegment(uri.getRawPath())) {
            throw new IllegalArgumentException("Bundled page prefix must be an http(s) URL whose path ends with '/': " + urlPrefix);
        }

        return value;
    }

    /**
     * Checks a resource directory: a classpath path under {@code assets/} that ends with a slash and has no dot
     * segments.
     */
    public static String validateResourceDirectory(String resourceDirectory) {
        String value = Objects.requireNonNull(resourceDirectory, "resourceDirectory").strip();
        if (!value.startsWith(ASSETS_PREFIX)
                || !value.endsWith(PATH_DELIMITER)
                || value.contains("\\")
                || hasDotSegment(value)
                || value.contains("//")) {
            throw new IllegalArgumentException("Bundled page directory must be a path under assets/ ending with '/': " + resourceDirectory);
        }

        return value;
    }

    /**
     * Checks a proxy upstream: an https URL (or http on a loopback host, for tests and development) with an ASCII
     * host, no query, fragment or user info, and a path that ends with a slash.
     */
    public static String validateUpstreamBase(String upstreamBase) {
        String value = Objects.requireNonNull(upstreamBase, "upstreamBase").strip();
        URI uri = parse(value);
        String origin = GrapheneOrigins.of(value);
        boolean https = uri != null && "https".equalsIgnoreCase(uri.getScheme());
        boolean loopbackHttp = uri != null && "http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
        if (uri == null
                || origin == null
                || !(https || loopbackHttp)
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() == null
                || !uri.getRawPath().endsWith(PATH_DELIMITER)
                || hasDotSegment(uri.getRawPath())) {
            throw new IllegalArgumentException("Proxy upstream must be an https URL whose path ends with '/': " + upstreamBase);
        }

        return value;
    }

    /**
     * Checks proxied file extensions: lower-case letters and digits, and never a type a browser runs or renders as a
     * document (HTML, SVG, XML, scripts, styles, WebAssembly, PDF).
     */
    public static Set<String> validateExtensions(Set<String> extensions) {
        Set<String> validated = new HashSet<>();
        for (String extension : Objects.requireNonNull(extensions, "extensions")) {
            String value = Objects.requireNonNull(extension, "extension").strip();
            if (!EXTENSION.matcher(value).matches() || DOCUMENT_EXTENSIONS.contains(value)) {
                throw new IllegalArgumentException("Proxied extension not allowed: " + extension);
            }

            validated.add(value);
        }

        if (validated.isEmpty()) {
            throw new IllegalArgumentException("Proxied assets need at least one extension");
        }

        return Set.copyOf(validated);
    }

    /**
     * Says how {@code url} is answered: on an origin Graphene does not own, it goes to the network; on an owned
     * origin it is served from the jar, fetched through the proxy, or refused with a local error. A refused URL never
     * reaches the network.
     */
    public Resolution resolve(String url) {
        if (mounts.isEmpty() || url == null) {
            return Resolution.NOT_OWNED;
        }

        String value = url.strip();
        if (!ownsOriginOf(value)) {
            return Resolution.NOT_OWNED;
        }

        URI uri = parse(value);
        if (uri == null || uri.getHost() == null || uri.getPath() == null) {
            return Resolution.REFUSED;
        }

        String path = uri.getPath();
        String origin = GrapheneOrigins.of(value);
        for (Mount mount : mounts) {
            if (!mount.origin().equals(origin) || !path.startsWith(mount.path())) {
                continue;
            }

            String remainder = path.substring(mount.path().length());
            if (remainder.contains("\\") || remainder.contains("//") || hasDotSegment(remainder)
                    || remainder.startsWith(PATH_DELIMITER)) {
                return Resolution.REFUSED;
            }

            return mount.proxy() == null ? jarResolution(mount, remainder) : proxyResolution(mount.proxy(), remainder, uri);
        }

        return Resolution.REFUSED;
    }

    /**
     * Returns whether Graphene owns the origin of {@code url}, so that nothing on it may come from the network.
     */
    public boolean ownsOriginOf(String url) {
        String origin = GrapheneOrigins.of(url);
        return origin != null && ownedOrigins.contains(origin);
    }

    public boolean isEmpty() {
        return mounts.isEmpty();
    }

    private static Resolution jarResolution(Mount mount, String remainder) {
        String resourcePath = remainder.isEmpty() || remainder.endsWith(PATH_DELIMITER) ? remainder + INDEX_PAGE : remainder;
        return new Resolution(true, mount.resourceDirectory() + resourcePath, null);
    }

    private static Resolution proxyResolution(GrapheneProxiedAssets proxy, String remainder, URI requestUri) {
        if (requestUri.getRawQuery() != null || !PROXIED_PATH.matcher(remainder).matches()) {
            return Resolution.REFUSED;
        }

        int dot = remainder.lastIndexOf('.');
        String extension = dot < 0 ? "" : remainder.substring(dot + 1);
        if (!proxy.extensions().contains(extension)) {
            return Resolution.REFUSED;
        }

        return new Resolution(true, null, proxy.upstreamBase() + remainder);
    }

    private static Mount mount(String urlPrefix, String resourceDirectory, GrapheneProxiedAssets proxy) {
        URI uri = Objects.requireNonNull(parse(validateUrlPrefix(urlPrefix)), "urlPrefix");
        return new Mount(
                Objects.requireNonNull(GrapheneOrigins.of(urlPrefix), "urlPrefix origin"),
                uri.getPath(),
                resourceDirectory,
                proxy
        );
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ignored) {
            // A URL Java cannot parse is never served or proxied.
            return null;
        }
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split(PATH_DELIMITER, -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }

        return false;
    }

    /**
     * How a request is answered. Not owned: the network. Owned with a resource path: the jar. Owned with an upstream
     * URL: the proxy. Owned with neither: refused locally.
     */
    public record Resolution(boolean owned, String resourcePath, String upstreamUrl) {
        public static final Resolution NOT_OWNED = new Resolution(false, null, null);
        public static final Resolution REFUSED = new Resolution(true, null, null);

        public boolean refused() {
            return owned && resourcePath == null && upstreamUrl == null;
        }
    }

    private record Mount(String origin, String path, String resourceDirectory, GrapheneProxiedAssets proxy) {
    }
}
