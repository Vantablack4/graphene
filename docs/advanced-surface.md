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
