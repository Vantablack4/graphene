# Lifecycle

Understanding Graphene lifecycle rules prevents stale bridge state and browser leaks.

## Runtime Lifecycle

- Register every consumer with `GrapheneCore.register(...)` from `onInitializeClient()`.
- Registration uses an anchor class, and Graphene closes registration before the first client tick.
- Re-registering the same consumer is allowed only when config is identical.
- Different config for the same consumer throws `IllegalStateException`.
- Runtime initializes automatically before the first client tick when at least one consumer is registered.
- Runtime can also initialize lazily on first `GrapheneCore.runtime()`, `GrapheneCore.startup()`, HTTP URL lookup, or
  first surface creation.
- `GrapheneCore.startup()` starts Graphene in the background and returns a future; never join that future on the
  render thread.
- `GrapheneCore.runtime()` blocks until startup finishes off the render thread and in Fabric client GameTests. On the
  render thread it never blocks: while Graphene is starting it starts it in the background and throws
  `IllegalStateException`. Check `GrapheneCore.isInitialized()` first.
- Any of these calls closes consumer registration, including an HTTP URL lookup. Do not build URLs from
  `onInitializeClient()`.
- The HTTP server starts before the CEF natives download, so `httpAssets()` and `httpUrl(...)` resolve immediately and
  keep the same base URL across startup retries.
- While the CEF natives download on first launch, Graphene shows a progress toast. It never blocks input.
- On macOS, Graphene loads the CEF framework library with one short render-thread task (a few tens of milliseconds)
  before CEF initializes in the background. Loading the framework briefly swaps the process malloc zone, and a
  `free()` on another thread during the swap crashes the game. The render thread frees memory constantly while it
  pumps window events, so the load runs on the render thread itself, and it waits until Minecraft's loading screen
  is gone (at most two minutes) because the window-open animation, resource reload workers and the JIT free memory
  constantly while it shows. CEF therefore finishes starting shortly after the title screen appears. A CEF helper
  run first warms the system's library validation so the render-thread task stays short even on a cold start.

If no consumer is registered, first Graphene usage fails with `IllegalStateException`.

## Starting Surfaces

A `BrowserSurface` built on the render thread while Graphene is still starting does not wait for it:

- `isStarting()` is `true` and the surface renders nothing; `prepareTextureFrame()` returns `null`.
- `bridge()` already accepts `onReady`, `onEvent`, and `onRequest` handlers, and `emit(...)` messages queue until the
  page is ready.
- `loadUrl(...)` replaces the pending URL; `goBack()`, `goForward()`, and `reload()` do nothing yet.
- Sizes and load listeners apply to the browser when it is created.
- The browser is created on the first `render(...)`, `prepareTextureFrame()`, or `tryCreateBrowser()` call after
  Graphene is ready. After a failed startup these calls retry it every 15 seconds.
- Java-side `request(...)` calls made while starting stay pending through the first page load and are answered once the
  page is ready, unless they time out first.
- If the browser cannot be created after startup, `hasFailed()` turns `true` and the surface never renders.

`GrapheneWebViewWidget` draws a native "Arayüz hazırlanıyor" placeholder with download progress while its surface is
starting, or "Arayüz açılamadı" after a failure (override `drawStartupPlaceholder(...)` to customize it), and ignores
input. When every web view on a screen is starting or failed, Escape closes the screen through `onClose()` even if the
screen normally routes Escape to its page, as long as `shouldCloseOnEsc()` is `true`.

Set `-Dgraphene.surface.awaitStartup=true` to make surface creation block until Graphene is ready, as it did before.
Fabric client GameTest runs and surfaces built off the render thread use that blocking mode by default; set the
property to `false` to test the starting state.

## Shared Config Merge Lifecycle

Before runtime initialization, Graphene merges all registered global config contributions.

- Conflicting explicit `jcefDownloadPath` or `remoteDebugging` configs fail startup.
- `extensionFolder` values are merged.
- `fileSystemAccessMode` resolves to `ALLOW` if any consumer requests `ALLOW`; otherwise `DENY`.

HTTP server settings are merged from container configs:

- `bindHost`, `baseUrlScheme`, and port binding must match when multiple consumers enable HTTP
- `fileRoot` and `spaFallback` remain isolated per consumer mount

## Surface And Widget Lifecycle

- `GrapheneWebViewWidget` owns a `BrowserSurface`.
- Widget creation registers ownership for automatic cleanup.
- `close()` removes widget tracking and closes all surfaces owned by that widget owner key.

`BrowserSurface.close()` performs:

1. owner unregistration
2. native slot cleanup
3. load listener scope cleanup
4. bridge detach
5. browser close

Native page slots are also cleared when `BrowserSurface` starts navigation or receives a CEF load-start event.

## World Surface Lifecycle

`GrapheneWorldSurface` owns one transparent `BrowserSurface` and registers itself with Fabric level rendering. Close it
directly or through the owner key:

```java
surface.close();
GrapheneCore.closeOwnedSurfaces(owner);
```

`GrapheneCore.closeOwnedSurfaces(owner)` closes owned world surfaces and then any remaining browser surfaces for the
same owner.

## Screen Auto-Close

`ScreenMixin` tracks Graphene web views and closes them by default on `Screen.onClose()`.

On close with auto-close enabled:

1. tracked `GrapheneWebViewWidget` instances are closed
2. surfaces owned by the screen owner key are closed
3. widget tracking is cleared

## Opt Out Of Auto-Close

If you need long-lived surfaces across screen transitions:

```java
import tytoo.grapheneui.api.screen.GrapheneScreens;

GrapheneScreens.setWebViewAutoCloseEnabled(screen, false);
```

When disabled, you must close widgets and surfaces manually.

## Bridge Lifecycle

- On navigation or load start, bridge readiness resets.
- Pending Java requests fail when page changes.
- On load end, Graphene injects bridge scripts.
- During render, Graphene retries bootstrap injection when needed.
- On JS `ready`, queued outbound Java messages flush and `onReady` listeners run.
- On close, bridge listeners/handlers/queue/pending requests are cleared.

## Native Slot Lifecycle

- The page registers slots through `globalThis.grapheneNativeSlots`.
- Slots are scoped to the browser surface and current page id.
- The JS helper sends full frame snapshots, removed slot ids, and page resets over the bridge.
- Java clears page-owned slots on navigation, load start, reset, and surface close.
- Native slots do not own keyboard focus or mouse clicks; browser input remains the source of interaction.

## Subscription Lifecycle

Use explicit cleanup for:

- `GrapheneBridgeSubscription`
- `BrowserSurface.Subscription` (load listeners)

Try-with-resources works for both.

---

Next: [Debugging](debugging.md)
