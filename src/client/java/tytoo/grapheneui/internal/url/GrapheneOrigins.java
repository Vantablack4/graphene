package tytoo.grapheneui.internal.url;

import java.net.IDN;
import java.util.Locale;

/**
 * Canonical origin keys, so a URL Java loads and the URL Chromium reports for the same page compare equal.
 */
public final class GrapheneOrigins {
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int HTTPS_DEFAULT_PORT = 443;
    private static final int MAX_PORT = 65_535;
    private static final String AUTHORITY_END = "/?#\\";

    private GrapheneOrigins() {
    }

    /**
     * Returns the origin of {@code url} as {@code scheme://host[:port]}: http and https with the scheme and host in
     * lower case (IDN hosts in their ASCII form) and the default port dropped, Graphene's {@code app} scheme with its
     * host, and Graphene's {@code classpath} scheme as {@code classpath://}. Every other URL, about:, data:, blob:,
     * javascript:, file: and malformed ones among them, has no origin and gives {@code null}.
     */
    public static String of(String url) {
        if (url == null) {
            return null;
        }

        String value = url.strip();
        int colon = value.indexOf(':');
        if (colon <= 0 || !value.startsWith("//", colon + 1)) {
            return null;
        }

        String scheme = value.substring(0, colon).toLowerCase(Locale.ROOT);
        int authorityStart = colon + 3;
        int authorityEnd = authorityStart;
        while (authorityEnd < value.length() && AUTHORITY_END.indexOf(value.charAt(authorityEnd)) < 0) {
            authorityEnd++;
        }

        String authority = value.substring(authorityStart, authorityEnd);
        if (authority.indexOf('@') >= 0) {
            return null;
        }

        return switch (scheme) {
            case HTTP_SCHEME -> webOrigin(scheme, authority, HTTP_DEFAULT_PORT);
            case HTTPS_SCHEME -> webOrigin(scheme, authority, HTTPS_DEFAULT_PORT);
            case GrapheneAppUrls.SCHEME -> isPlainHost(authority) && !authority.isEmpty()
                    ? scheme + "://" + authority.toLowerCase(Locale.ROOT)
                    : null;
            case "classpath" -> authority.isEmpty() ? "classpath://" : null;
            default -> null;
        };
    }

    private static String webOrigin(String scheme, String authority, int defaultPort) {
        String host = authority;
        int port = defaultPort;
        int portSeparator = authority.lastIndexOf(':');
        if (portSeparator >= 0 && authority.indexOf(']', portSeparator) < 0) {
            host = authority.substring(0, portSeparator);
            String portText = authority.substring(portSeparator + 1);
            if (!portText.isEmpty()) {
                port = parsePort(portText);
                if (port < 0) {
                    return null;
                }
            }
        }

        String canonicalHost = canonicalHost(host);
        if (canonicalHost == null) {
            return null;
        }

        return scheme + "://" + canonicalHost + (port == defaultPort ? "" : ":" + port);
    }

    private static String canonicalHost(String host) {
        if (host.isEmpty()) {
            return null;
        }

        if (host.startsWith("[")) {
            return host.endsWith("]") && host.length() > 2 ? host.toLowerCase(Locale.ROOT) : null;
        }

        if (!isPlainHost(host)) {
            return null;
        }

        try {
            String ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
            return ascii.isEmpty() ? null : ascii;
        } catch (IllegalArgumentException ignored) {
            // A host IDN cannot convert is not one Chromium would load either.
            return null;
        }
    }

    private static boolean isPlainHost(String host) {
        for (int index = 0; index < host.length(); index++) {
            char character = host.charAt(index);
            if (character <= ' ' || character == '%' || character == '[' || character == ']' || character == ':') {
                return false;
            }
        }

        return true;
    }

    private static int parsePort(String portText) {
        if (portText.length() > 5) {
            return -1;
        }

        int port = 0;
        for (int index = 0; index < portText.length(); index++) {
            char character = portText.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }

            port = port * 10 + (character - '0');
        }

        return port > MAX_PORT ? -1 : port;
    }
}
