# Advanced Surface

`BrowserSurface` gives direct control over rendering, sizing, viewport cropping, navigation, and bridge access.

## Builder Options

- `url(String)` initial URL, default `about:blank`
- `transparent(boolean)` off-screen transparency, default `true`
- `surfaceSize(int, int)` logical render size
- `resolution(int, int)` explicit browser pixel resolution
- `autoResolution()` resolution follows surface size and window scale
- `viewBox(int, int, int, int)` crop source content region
- `client(CefClient)` custom CEF client
- `requestContext(CefRequestContext)` custom CEF request context
- `requestContextCustomizer(Consumer<CefRequestContext>)` mutate context before use
- `config(BrowserSurfaceConfig)` browser settings config
- `maxFps(int)` convenience setter for windowless frame rate
- `allowTextSelection(boolean)` opt into selecting non-editable page text, default `false`
- `allowZoom(boolean)` opt into Ctrl/Command-wheel and keyboard zoom, default `false`
- `allowAltF4Close(boolean)` opt into Alt+F4 closing Minecraft while the surface is attached to a `GrapheneWebViewWidget`, default `false`
- `frameScheduling(BrowserSurfaceFrameScheduling)` Chromium paint scheduling mode
- `performanceMetrics(boolean)` opt-in cumulative capture/upload counters
- `preferAcceleratedPaint(boolean)` request shared-texture acceleration with a safe software fallback
- `settingsCustomizer(Consumer<CefBrowserSettings>)` mutate low-level CEF settings
- `owner(Object)` register owner for lifecycle-managed cleanup

When `maxFps(...)` is applied multiple times on the same `BrowserSurfaceConfig` or `BrowserSurface.Builder`,
Graphene keeps the largest explicit value.

## Browser Interaction Defaults

Graphene surfaces behave like game UI by default:

- ordinary page text is not selectable;
- text inputs, text areas, and editable content remain selectable;
- Ctrl/Command-wheel and Ctrl/Command `+`, `-`, or `0` do not zoom the surface;
- Alt+F4 does not close Minecraft while a default-configured Graphene web view is present on the current screen.

Enable an interaction only for a surface that needs browser-like behavior:

```java
BrowserSurfaceConfig config = BrowserSurfaceConfig.builder()
        .allowTextSelection(true)
        .allowZoom(true)
        .allowAltF4Close(true)
        .build();
```

Page CSS can also opt specific elements back into text selection with `user-select: text`.

## Sizing Modes

Fixed resolution example:

```java
BrowserSurface surface = BrowserSurface.builder()
        .url("app://assets/my-mod-id/web/index.html")
        .surfaceSize(400, 240)
        .resolution(800, 480)
        .build();
```

Auto resolution example:

```java
surface.useAutoResolution();
surface.setSurfaceSize(600, 340);
```

## ViewBox Cropping

```java
surface.setViewBox(100, 50, 300, 200);
```

Reset to full frame:

```java
surface.resetViewBox();
```

## Rendering

If you are not using `GrapheneWebViewWidget`, call `render(...)` every frame.

```java
surface.render(guiGraphics, x, y, width, height);
```

`render(...)` also triggers bridge bootstrap fallback checks and submits the browser frame through Minecraft's GUI render pipeline.

## Animated And WebGL Surfaces

Graphene retains bounded dirty-region history and merges/clamps CEF damage before both CPU capture and GPU upload.
If Minecraft skips one or more Chromium paints, the next render uploads the cumulative damage instead of forcing a
whole-frame upload. After the four capture slots have warmed, Graphene also refreshes a slot from cumulative damage
instead of copying the entire CEF buffer when the retained history proves that partial work is safe.

For continuously animated pages, opt into render-driven Chromium frames:

```java
BrowserSurface surface = BrowserSurface.builder()
        .url("app://assets/my-mod-id/web/lockpick.html")
        .surfaceSize(640, 360)
        .autoResolution()
        .maxFps(60)
        .frameScheduling(BrowserSurfaceFrameScheduling.RENDER_DRIVEN)
        .build();
```

`RENDER_DRIVEN` enables CEF external begin frames and requests one frame for each `render(...)` or
`prepareTextureFrame()` call. `AUTOMATIC` remains the compatibility default. Use render-driven scheduling for a surface
that is rendered once per Minecraft frame; avoid calling both render methods for the same surface in one frame.

Auto resolution uses physical framebuffer scale. A full-screen browser can therefore be much larger than its logical
GUI dimensions. If a scene is fill-rate or upload-bandwidth limited, use an explicit `resolution(...)` chosen for the
content instead of rendering a 3D canvas at a needlessly high backing resolution.

## Performance Metrics

Metrics are disabled by default and allocate counters only when enabled:

```java
BrowserSurface surface = BrowserSurface.builder()
        .performanceMetrics(true)
        .build();

surface.performanceSnapshot().ifPresent(snapshot -> {
    long copied = snapshot.capturedBytes();
    long uploaded = snapshot.uploadedBytes();
    long coalesced = snapshot.coalescedPaintFrames();
});
```

`BrowserSurfacePerformanceSnapshot` is cumulative and safe to read while CEF paints. Capture and upload timing measures
Graphene's CPU-side work and texture submission; it does not wait for GPU completion. A high
`dirtyHistoryFallbacks()` value indicates the renderer or a capture slot fell farther behind than the retained damage
window, or otherwise could not prove a partial update safe.

## Shared-Texture Acceleration

The pinned JCEF exposes accelerated paint through platform-native handles, but those handles are valid only during the
CEF UI-thread callback. Minecraft texture creation/import must run on its render thread. Without a safe cross-thread
ownership and synchronization contract, retaining the handle is invalid and blocking the CEF callback risks deadlock.

Graphene therefore keeps `shared_texture_enabled` off and uses the optimized software OSR buffer path. Requesting a
preference does not claim acceleration:

```java
BrowserSurface surface = BrowserSurface.builder()
        .preferAcceleratedPaint(true)
        .build();

BrowserSurfaceAccelerationStatus status = surface.accelerationStatus();
// status.sharedTextureActive() is false; the blocker is in sharedTextureUnavailableReason().
```

Low-level settings customizers that force `shared_texture_enabled` are rejected, because CEF would stop delivering the
software paint buffer that Graphene can safely consume.

## Navigation And State

`BrowserSurface` exposes browser navigation and state:

- `loadUrl`, `reload`, `goBack`, `goForward`
- `currentUrl`, `canGoBack`, `canGoForward`, `isLoading`

## Input Adapter

Use `BrowserSurfaceInputAdapter` for custom input pipelines:

```java
BrowserSurfaceInputAdapter input = new BrowserSurfaceInputAdapter(surface);
input.setFocused(true);

input.mouseMoved(localMouseX, localMouseY, width, height);
input.mouseClicked(button, isDoubleClick, localMouseX, localMouseY, width, height);
input.mouseReleased(button, localMouseX, localMouseY, width, height);
input.mouseDragged(button, localMouseX, localMouseY, width, height);
input.mouseScrolled(localMouseX, localMouseY, scrollY, width, height);
```

Keyboard forwarding methods exist for both event objects and raw key values.

## Coordinate Mapping Helpers

For manual forwarding, use:

- `toBrowserPoint(...)`
- `toBrowserX(...)`
- `toBrowserY(...)`

These apply current viewBox and rendered dimensions.

## Native Slot Coordinates

Native slots use the same surface, resolution, and viewBox model.
The page-side helper measures DOM rectangles in CSS viewport coordinates, and `BrowserSurface` maps them into the rectangle passed to `render(...)`.
If a custom renderer or input adapter uses a different rendered size than `surface.render(...)`, native slots and browser input will not line up.

## Ownership And Cleanup

Owner-tracked lifecycle:

```java
BrowserSurface surface = BrowserSurface.builder()
        .owner(owner)
        .build();

surface.setOwner(otherOwner);
surface.clearOwner();

surface.close();
```

Always close surfaces you create.

---

Next: [Native Slots](native-slots.md)
