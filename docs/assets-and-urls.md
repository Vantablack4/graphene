# Assets And URLs

Graphene provides handle-scoped URL helpers for loading web assets from your mod namespace and, optionally, from the runtime HTTP server.

## URL Helpers

App scheme (`app://`):

- `grapheneHandle.appAssets().asset(path)`

Classpath scheme (`classpath:///`):

- `GrapheneClasspathUrls.asset(namespace, path)`
- `grapheneHandle.classpathAssets().asset(path)`

Runtime HTTP for shared classpath assets:

- `grapheneHandle.httpAssets().asset(path)`

Runtime HTTP for this mod's mounted container route:

- `grapheneHandle.httpUrl(path)`

Examples:

```java
String appUrl = grapheneHandle.appAssets().asset("web/index.html");
// app://assets/my-mod-id/web/index.html

String classpathUrl = GrapheneClasspathUrls.asset("my-mod-id", "web/index.html");
// classpath:///assets/my-mod-id/web/index.html

String classpathHttpUrl = grapheneHandle.httpAssets().asset("web/index.html");
// http://127.0.0.1:<port>/assets/my-mod-id/web/index.html

String mountedHttpUrl = grapheneHandle.httpUrl("web/index.html");
// http://127.0.0.1:<port>/mods/my-mod-id/web/index.html
```

## Resource Layout

```text
src/client/resources/
  assets/
    my-mod-id/
      web/
        index.html
        app.js
        styles.css
        images/logo.png
```

Load with:

```java
String url = grapheneHandle.appAssets().asset("web/index.html");
```

## Relative Paths Inside HTML

If your page is loaded from `app://assets/my-mod-id/web/index.html`, relative references resolve naturally:

```html
<link rel="stylesheet" href="styles.css">
<script src="app.js"></script>
<img src="images/logo.png" alt="logo">
```

## URL Normalization

Graphene normalizes `app://` and `classpath:///` asset requests before resolving classpath resources.
`GrapheneClasspathUrls.normalizeResourcePath(url)` is also available when you need to normalize a public classpath URL yourself:

- verify scheme
- strip query and fragment
- decode URL-encoded path segments
- return normalized classpath resource path

Example:

- input: `classpath:///assets/graphene-ui/a%20b.html?x=1#top`
- result: `assets/graphene-ui/a b.html`

## MIME Types

Graphene resolves common MIME types for HTML, CSS, JS, JSON, images, WebM/MP4 video, fonts, and WASM.
Unknown extensions default to `text/plain`.

## HTTP Mode

Enable HTTP mode with `GrapheneContainerConfig`:

```java
GrapheneConfig config = GrapheneConfig.builder()
        .container(GrapheneContainerConfig.builder()
                .http(GrapheneHttpConfig.builder()
                        .bindHost("127.0.0.1")
                        .randomPortInRange(20_000, 21_000)
                        .fileRoot("C:/dev/my-ui-dist")
                        .spaFallback("/assets/my-mod-id/web/index.html")
                        .build())
                .build())
        .build();

GrapheneCore.register(MyModClient.class, config);

GrapheneHandle graphene = GrapheneCore.handle(MyModClient.class);

String classpathHttpUrl = graphene.httpAssets().asset("web/index.html");
String mountedHttpUrl = graphene.httpUrl("web/index.html");
```

Important behavior:

- `graphene.httpAssets().asset(...)` and `graphene.httpUrl(...)` throw `IllegalStateException` when HTTP server is not running.
- `graphene.httpAssets().asset(...)` always targets shared classpath assets under `/assets/<mod-id>/...`.
- `graphene.httpUrl(...)` targets the consumer mount under `/mods/<mod-id>/...`.
- Mounted HTTP request resolution order is:
  1. filesystem (`fileRoot/request-path`)
  2. classpath fallback under `assets/<mod-id>/<request-path>`
  3. optional SPA fallback
- Successful HTTP assets advertise byte-range support. Single `bytes=` ranges return `206 Partial Content`, which lets
  Chromium seek and stream bundled media without downloading the whole file first.

## Bundled Pages On An Owned Origin

A page that needs a web origin (for example to read remote data without CORS and draw it on a canvas it can export)
can run on an origin Graphene owns entirely, with its code from the jar and its remote data through a read-only proxy:

```java
GrapheneCore.register(MyMod.class, GrapheneConfig.builder()
        .container(GrapheneContainerConfig.builder()
                .bundledPages("https://my-mod.graphene.invalid/page/", "assets/my_mod/web/page/")
                .proxiedAssets("https://my-mod.graphene.invalid/cdn/", "https://cdn.example.com/my-mod/",
                        Set.of("json", "webp", "png"))
                .build())
        .build());
```

- Every request under a `bundledPages` prefix is answered from the jar directory (a path ending in `/` serves
  `index.html`).
- Every request under a `proxiedAssets` prefix is fetched by Java from the upstream directory plus the same path:
  GET only, the listed extensions only (never HTML, SVG, XML, scripts, styles, WebAssembly or PDF), a plain path
  (letters, digits, `.`, `_`, `-`, no dot segments, no query), nothing forwarded from the page (no cookies, headers or
  query), no percent escapes, no redirects, at most 16 MiB, and the whole body within 30 seconds. At most 32 proxied
  requests are in flight; more get a local 503. The response type comes from the extension and carries
  `X-Content-Type-Options: nosniff` and `Content-Security-Policy: sandbox` (a subresource ignores that CSP, so
  fonts, images and JSON still load; a proxied file opened as a document cannot run anything). An upstream 404 is a
  local 404, a missed deadline a local 504, and any other upstream answer or failure a local 502. The fetch and its
  continuations run on Graphene's own small thread pool, never on CEF's IO thread.
- Every other request on the origin, and any path that tries to leave a mount, is answered with a local error and
  never reaches the network.
- The origin must be `https` on a host ending in `.invalid` (RFC 6761 reserves it; it never resolves), with no port.
  Registration refuses any other host. So a request Graphene never sees (one with no browser, a WebSocket, a
  service worker, which a reserved host can never have registered) fails on DNS instead of reaching a server.

Because only the mods' own content lives on the origin, the bridge trusts it like any other page origin (see
[Who May Call The Bridge](bridge.md#who-may-call-the-bridge)). Register before Graphene starts, like the HTTP mode.

## Recommendations

- Keep web assets under `assets/<mod-id>/web/...`.
- Use handle or `GrapheneClasspathUrls` helpers instead of hard-coded string concatenation.
- Keep filenames lowercase and extension-explicit.
- Prefer `grapheneHandle.httpUrl(...)` for consumer-mounted content and `grapheneHandle.httpAssets().asset(...)` for shared classpath assets.

---

Next: [Lifecycle](lifecycle.md)
