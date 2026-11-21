package tytoo.grapheneui.internal.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps web URLs under registered prefixes to resources inside the mod jars, so a page can keep a web origin (and
 * reach that origin's data without CORS) while its HTML, scripts and styles come from the jar instead of the network.
 */
public final class GrapheneBundledPages {
    private static final GrapheneBundledPages EMPTY = new GrapheneBundledPages(List.of());
    private static final String PATH_DELIMITER = "/";
    private static final String ASSETS_PREFIX = "assets" + PATH_DELIMITER;
    private static final String INDEX_PAGE = "index.html";

    private final List<Mount> mounts;
    private final Set<String> mountOrigins;

    private GrapheneBundledPages(List<Mount> mounts) {
        this.mounts = List.copyOf(mounts);
        Set<String> origins = new HashSet<>();
        for (Mount mount : this.mounts) {
            origins.add(mount.origin());
        }
        this.mountOrigins = Set.copyOf(origins);
    }

    public static GrapheneBundledPages empty() {
        return EMPTY;
    }

    public static GrapheneBundledPages of(Map<String, String> resourceDirectoriesByUrlPrefix) {
        List<Mount> mounts = new ArrayList<>();
        for (Map.Entry<String, String> entry : resourceDirectoriesByUrlPrefix.entrySet()) {
            mounts.add(mount(entry.getKey(), entry.getValue()));
        }

        mounts.sort((left, right) -> Integer.compare(right.path().length(), left.path().length()));
        return new GrapheneBundledPages(mounts);
    }

    /**
     * Checks a URL prefix: an absolute http or https URL with a host, no query, fragment or user info, and a path
     * that ends with a slash.
     */
    public static String validateUrlPrefix(String urlPrefix) {
        String value = Objects.requireNonNull(urlPrefix, "urlPrefix").strip();
        URI uri = parse(value);
        if (uri == null
                || uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))
                || uri.getHost() == null
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
     * Says how {@code url} is answered: not covered by any prefix (it goes to the network), covered and served from
     * the jar resource {@link Resolution#resourcePath()}, or covered but refused because its path tries to leave the
     * directory or is malformed. A refused URL is answered locally and never reaches the network.
     */
    public Resolution resolve(String url) {
        if (mounts.isEmpty() || url == null) {
            return Resolution.NOT_MOUNTED;
        }

        String value = url.strip();
        URI uri = parse(value);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null || uri.getPath() == null) {
            return underSomePrefix(value) ? Resolution.REFUSED : Resolution.NOT_MOUNTED;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = effectivePort(scheme, uri.getPort());
        String path = uri.getPath();
        String rawPath = uri.getRawPath() == null ? path : uri.getRawPath();
        for (Mount mount : mounts) {
            if (!mount.scheme().equals(scheme) || !mount.host().equals(host) || mount.port() != port) {
                continue;
            }

            if (!path.startsWith(mount.path())) {
                if (rawPath.startsWith(mount.rawPath())) {
                    return Resolution.REFUSED;
                }

                continue;
            }

            String remainder = path.substring(mount.path().length());
            if (remainder.contains("\\") || remainder.contains("//") || hasDotSegment(remainder)
                    || remainder.startsWith(PATH_DELIMITER)) {
                return Resolution.REFUSED;
            }

            if (remainder.isEmpty() || remainder.endsWith(PATH_DELIMITER)) {
                remainder = remainder + INDEX_PAGE;
            }

            return new Resolution(true, mount.resourceDirectory() + remainder);
        }

        return Resolution.NOT_MOUNTED;
    }

    /**
     * Returns whether some prefix lives on the origin of {@code url}. Documents on such an origin get the bridge only
     * when Graphene served them from the jar.
     */
    public boolean mountsOriginOf(String url) {
        String origin = GrapheneOrigins.of(url);
        return origin != null && mountOrigins.contains(origin);
    }

    public boolean isEmpty() {
        return mounts.isEmpty();
    }

    private boolean underSomePrefix(String url) {
        for (Mount mount : mounts) {
            String prefix = mount.origin() + mount.rawPath();
            if (url.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }

        return false;
    }

    private static Mount mount(String urlPrefix, String resourceDirectory) {
        URI uri = Objects.requireNonNull(parse(validateUrlPrefix(urlPrefix)), "urlPrefix");
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return new Mount(
                scheme,
                uri.getHost().toLowerCase(Locale.ROOT),
                effectivePort(scheme, uri.getPort()),
                uri.getPath(),
                uri.getRawPath(),
                validateResourceDirectory(resourceDirectory),
                Objects.requireNonNull(GrapheneOrigins.of(urlPrefix), "urlPrefix origin")
        );
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }

        return scheme.equals("https") ? 443 : 80;
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ignored) {
            // A URL Java cannot parse is never served from the jar.
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

    public record Resolution(boolean mounted, String resourcePath) {
        public static final Resolution NOT_MOUNTED = new Resolution(false, null);
        public static final Resolution REFUSED = new Resolution(true, null);

        public boolean refused() {
            return mounted && resourcePath == null;
        }
    }

    private record Mount(String scheme, String host, int port, String path, String rawPath, String resourceDirectory, String origin) {
    }
}
