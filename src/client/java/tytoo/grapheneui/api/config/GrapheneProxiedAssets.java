package tytoo.grapheneui.api.config;

import tytoo.grapheneui.internal.url.GrapheneBundledPages;

import java.util.Set;

/**
 * A read-only proxy mount on a Graphene-owned origin: a request under the mount's prefix is fetched with GET from
 * {@code upstreamBase} plus the same relative path, for the listed file extensions only. See
 * {@link GrapheneContainerConfig.Builder#proxiedAssets(String, String, Set)}.
 *
 * @param upstreamBase an https URL whose path ends with {@code /} (http only on a loopback host)
 * @param extensions   the lower-case file extensions that may be fetched; never document or script types
 */
public record GrapheneProxiedAssets(String upstreamBase, Set<String> extensions) {
    public GrapheneProxiedAssets {
        upstreamBase = GrapheneBundledPages.validateUpstreamBase(upstreamBase);
        extensions = GrapheneBundledPages.validateExtensions(extensions);
    }
}
