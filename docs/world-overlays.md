# World Overlays

World overlays render a transparent Graphene browser surface over the HUD and position page UI from Minecraft world
coordinates. This is intended for nameplates, crop/resource labels, table-game controls, NPC prompts, and focused object
inspectors.

This is a screen-space feature. Graphene projects world anchors into GUI coordinates and sends those projected positions
to JavaScript. It does not render a Chromium texture as a depth-tested quad inside the 3D world.

If the browser UI should physically sit in the world and be occluded by terrain, use [World Surfaces](world-surfaces.md)
instead.

## Java API

Create one overlay layer per UI surface:

```java
GrapheneHandle graphene = GrapheneCore.handle(MyModClient.class);

GrapheneWorldOverlayLayer layer = GrapheneWorldOverlays.create(
        graphene,
        GrapheneWorldOverlayConfig.builder(graphene.appAssets().asset("web/world-overlays/index.html"))
                .maxAnchors(64)
                .maxFps(30)
                .defaultMaxDistance(32.0D)
                .owner(this)
                .build()
);
```

Publish immutable anchor snapshots from your client-side mod state:

```java
JsonObject payload = new JsonObject();
payload.addProperty("label", "Ready to harvest");
payload.addProperty("item", "minecraft:wheat");

GrapheneWorldAnchor anchor = GrapheneWorldAnchor.builder("crop:" + blockPos.asLong(), Vec3.upFromBottomCenterOf(blockPos, 1.15D))
        .kind("crop")
        .dimension(client.level.dimension())
        .maxDistance(18.0D)
        .screenOffset(0, -8)
        .occlusion(GrapheneWorldAnchorOcclusion.THROTTLED_RAYCAST)
        .payload(payload)
        .build();

layer.upsertAnchor(anchor);
```

When the owning object shuts down:

```java
GrapheneCore.closeOwnedSurfaces(this);
```

or close the layer directly:

```java
layer.close();
```

## JavaScript API

Graphene injects `globalThis.grapheneWorldOverlays` into every browser surface.

```js
const unsubscribe = globalThis.grapheneWorldOverlays.subscribe((frame) => {
  for (const anchor of frame.anchors) {
    console.log(anchor.id, anchor.x, anchor.y, anchor.payload);
  }
});

const latest = globalThis.grapheneWorldOverlays.snapshot();
const crop = globalThis.grapheneWorldOverlays.anchor("crop:123");

unsubscribe();
```

Frame anchors contain:

- `id`, `kind`, `priority`, `interactive`
- `x`, `y`, `depth`, `distance`, `scale`, `alpha`
- `worldX`, `worldY`, `worldZ`
- `payload`

## React Pattern

Keep React state keyed by anchor id and position cards with CSS transforms:

```tsx
function useGrapheneWorldAnchors() {
  const [anchors, setAnchors] = React.useState([]);

  React.useEffect(() => {
    const overlays = globalThis.grapheneWorldOverlays;
    if (!overlays) {
      return;
    }

    return overlays.subscribe((frame) => setAnchors(frame.anchors));
  }, []);

  return anchors;
}

function WorldOverlayRoot() {
  const anchors = useGrapheneWorldAnchors();

  return (
    <div className="world-overlay-root">
      {anchors.map((anchor) => (
        <div
          key={anchor.id}
          className="world-overlay-card"
          style={{
            transform: `translate3d(${anchor.x}px, ${anchor.y}px, 0) translate(-50%, -100%) scale(${anchor.scale})`,
            opacity: anchor.alpha,
            pointerEvents: anchor.interactive ? "auto" : "none",
          }}
        >
          {anchor.payload?.label}
        </div>
      ))}
    </div>
  );
}
```

Recommended CSS:

```css
html,
body,
#root,
.world-overlay-root {
  width: 100%;
  height: 100%;
  margin: 0;
  overflow: hidden;
  background: transparent;
}

.world-overlay-root {
  position: relative;
  pointer-events: none;
}

.world-overlay-card {
  position: absolute;
  left: 0;
  top: 0;
  will-change: transform, opacity;
}
```

## Native Slots

Native slots work inside world overlay cards. After every projection frame, Graphene asks `grapheneNativeSlots` to flush
so item, block, skin, head, and entity slots remeasure against the moved DOM rectangles.

```js
const slot = globalThis.grapheneNativeSlots.register({
  id: "crop-icon",
  kind: "item",
  element: document.querySelector("#crop-icon"),
  payload: { item: "minecraft:wheat", count: 1 },
  render: { decorations: true }
});
```

For non-React pages, `bindElement` can position an element directly:

```js
globalThis.grapheneWorldOverlays.bindElement("crop:123", document.querySelector("#crop-card"), {
  origin: "bottom-center",
  offsetY: -8
});
```

## Performance Rules

- Use one overlay layer for many anchors; do not create a browser surface per crop, NPC, table, or marker.
- Keep gameplay payload updates separate from high-frequency render state.
- Cap visible anchors with `maxAnchors`.
- Use `maxDistance` before expensive projection and raycast work.
- Use `THROTTLED_RAYCAST` only for selected or important anchors.
- Hide or aggregate dense content instead of rendering hundreds of React cards.
- Keep countdowns and animation client-side in JavaScript when possible.

## Input

World overlays are passive by default. The layer exposes its `BrowserSurfaceInputAdapter` for consumers that intentionally
route focused input:

```java
layer.inputAdapter().setFocused(true);
layer.inputAdapter().mouseClicked(button, false, mouseX, mouseY, layer.surface().getSurfaceWidth(), layer.surface().getSurfaceHeight());
```

Only forward input while the player is focused on an interactive overlay. Do not globally capture Minecraft input for
passive labels.

## Validation

Suggested validation commands:

```bash
./gradlew compileJava
./gradlew test
./gradlew runDebugClient
```

Manual checks:

- Confirm the overlay follows anchors while rotating the camera and changing GUI scale/FOV.
- Confirm anchors hide when out of range, behind the camera, in another dimension, or occluded when raycast occlusion is enabled.
- Confirm native slots inside overlay cards stay aligned while cards move.
- Confirm no overlay renders over menus unless `renderWhenScreenOpen(true)` is configured.
- Confirm `GrapheneCore.closeOwnedSurfaces(owner)` removes the HUD element and releases the browser surface.

---

Next: [Native Slots](native-slots.md)
