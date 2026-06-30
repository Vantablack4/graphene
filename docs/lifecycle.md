# Lifecycle

Understanding Graphene lifecycle rules prevents stale bridge state and browser leaks.

## Runtime Lifecycle

- Register every consumer with `GrapheneCore.register(...)` from `onInitializeClient()`.
- Registration uses an anchor class, and Graphene closes registration before the first client tick.
- Re-registering the same consumer is allowed only when config is identical.
- Different config for the same consumer throws `IllegalStateException`.
- Runtime initializes automatically before the first client tick when at least one consumer is registered.
- Runtime can also initialize lazily on first `GrapheneCore.runtime()` or first surface creation.

If no consumer is registered, first Graphene usage fails with `IllegalStateException`.

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

## World Overlay Lifecycle

`GrapheneWorldOverlayLayer` owns one transparent `BrowserSurface` plus a registered HUD element.

Close layers directly or through the owner key:

```java
layer.close();
GrapheneCore.closeOwnedSurfaces(owner);
```

`GrapheneCore.closeOwnedSurfaces(owner)` closes owned world overlays before closing browser surfaces for the same owner.
This removes the HUD element and releases the underlying browser surface together.

## World Surface Lifecycle

`GrapheneWorldSurface` owns one transparent `BrowserSurface` and registers itself with Fabric level rendering. Close it
directly or through the owner key:

```java
surface.close();
GrapheneCore.closeOwnedSurfaces(owner);
```

`GrapheneCore.closeOwnedSurfaces(owner)` closes owned world overlays, owned world surfaces, and then any remaining
browser surfaces for the same owner.

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
