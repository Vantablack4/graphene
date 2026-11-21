package tytoo.grapheneui.internal.bridge;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which documents may call a surface's bridge: only the main frame, and only while it shows a page from an
 * origin that Java itself loaded into the surface. Pages reached by the page's own navigation, popups and frames
 * from other origins get no bridge.
 */
public final class GrapheneBridgeCallerPolicy {
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_DEFAULT_PORT = "80";
    private static final String HTTPS_DEFAULT_PORT = "443";

    private final Set<String> trustedOrigins = ConcurrentHashMap.newKeySet();

    /**
     * Returns the origin of {@code url} as {@code scheme://host[:port]}, lower case and without default ports, or
     * {@code null} when the URL has no origin of its own (about:, data:, blob:, javascript: and malformed URLs).
     */
    public static String originOf(String url) {
        if (url == null) {
            return null;
        }

        String value = url.strip();
        int colon = value.indexOf(':');
        if (colon <= 0 || !isSchemeName(value, colon)) {
            return null;
        }

        String scheme = value.substring(0, colon).toLowerCase(Locale.ROOT);
        int authorityStart = colon + 3;
        if (!value.startsWith("//", colon + 1)) {
            return null;
        }

        int authorityEnd = authorityStart;
        while (authorityEnd < value.length() && "/?#\\".indexOf(value.charAt(authorityEnd)) < 0) {
            authorityEnd++;
        }

        String authority = value.substring(authorityStart, authorityEnd).toLowerCase(Locale.ROOT);
        if (authority.indexOf('@') >= 0) {
            return null;
        }

        return scheme + "://" + withoutDefaultPort(scheme, authority);
    }

    public void trust(String pageUrl) {
        String origin = originOf(pageUrl);
        if (origin != null) {
            trustedOrigins.add(origin);
        }
    }

    public Decision decide(boolean mainFrame, String frameUrl) {
        if (!mainFrame) {
            return Decision.REFUSED_SUBFRAME;
        }

        String origin = originOf(frameUrl);
        if (origin == null || !trustedOrigins.contains(origin)) {
            return Decision.REFUSED_ORIGIN;
        }

        return Decision.ALLOWED;
    }

    private static boolean isSchemeName(String value, int end) {
        if (!Character.isLetter(value.charAt(0))) {
            return false;
        }

        for (int index = 1; index < end; index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '+' && character != '-' && character != '.') {
                return false;
            }
        }

        return true;
    }

    private static String withoutDefaultPort(String scheme, String authority) {
        int portSeparator = authority.lastIndexOf(':');
        if (portSeparator < 0 || authority.indexOf(']', portSeparator) >= 0) {
            return authority;
        }

        String port = authority.substring(portSeparator + 1);
        boolean defaultPort = (scheme.equals(HTTP_SCHEME) && port.equals(HTTP_DEFAULT_PORT))
                || (scheme.equals(HTTPS_SCHEME) && port.equals(HTTPS_DEFAULT_PORT))
                || port.isEmpty();
        return defaultPort ? authority.substring(0, portSeparator) : authority;
    }

    public enum Decision {
        ALLOWED,
        REFUSED_SUBFRAME,
        REFUSED_ORIGIN;

        public boolean allowed() {
            return this == ALLOWED;
        }
    }
}
