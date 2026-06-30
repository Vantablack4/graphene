# World Surfaces

World surfaces render a Graphene `BrowserSurface` as a depth-tested textured quad in the Minecraft world. Use this
when the UI should physically sit on a crop, block, table, sign, kiosk, or entity attachment instead of floating in
screen space.

For projected HUD cards and dense labels, use [World Overlays](world-overlays.md). For physical in-world HTML panels,
use this API.

## Java API

```java
GrapheneHandle graphene = GrapheneCore.handle(MyModClient.class);

GrapheneWorldSurface surface = GrapheneWorldSurfaces.create(
        graphene,
        GrapheneWorldSurfaceConfig.builder(graphene.appAssets().asset("web/table.html"))
                .owner(this)
                .dimension(client.level.dimension())
                .position(Vec3.upFromBottomCenterOf(blockPos, 1.035D))
                .surfaceSize(768, 384)
                .resolution(768, 384)
                .worldSize(2.25F, 1.125F)
                .horizontalUp()
                .side(GrapheneWorldSurfaceSide.DOUBLE_SIDED_READABLE)
                .maxDistance(64.0D)
                .interactionReach(48.0D)
                .maxFps(30)
                .build()
);
```

The local surface plane is centered at `position`. Its local X axis maps to width, local Y maps to height, and
`orientation(...)` controls how the plane is oriented in world space. `horizontalUp()` lays the browser surface flat over
a block for table-top UI with its front side facing upward.

Common orientations:

```java
GrapheneWorldSurfaceConfig.builder(url).horizontalUp();                 // table-top, readable from above
GrapheneWorldSurfaceConfig.builder(url).horizontalDown();               // ceiling/downward panel
GrapheneWorldSurfaceConfig.builder(url).vertical(Direction.NORTH);      // wall/sign front faces north
GrapheneWorldSurfaceConfig.builder(url).blockFace(Direction.UP);        // front normal follows a block face
GrapheneWorldSurfaceConfig.builder(url).orientation(GrapheneWorldSurfaceOrientation.custom(rotation));
```

For nameplate-style in-world billboards, rotate the surface toward the camera:

```java
surface.setFacing(GrapheneWorldSurfaceFacing.CAMERA);      // full pitch/yaw billboard
surface.setFacing(GrapheneWorldSurfaceFacing.CAMERA_YAW);  // upright yaw-only billboard
```

Side behavior is explicit so text does not become accidentally mirrored when the camera moves behind a surface:

```java
surface.setSide(GrapheneWorldSurfaceSide.FRONT_ONLY);
surface.setSide(GrapheneWorldSurfaceSide.BACK_ONLY);
surface.setSide(GrapheneWorldSurfaceSide.DOUBLE_SIDED_READABLE);
```

Close surfaces directly or by owner:

```java
surface.close();
GrapheneCore.closeOwnedSurfaces(this);
```

## Bridge Updates

World surfaces use the normal Graphene bridge. Send focused state updates from Java:

```java
JsonObject payload = new JsonObject();
payload.addProperty("block", blockPos.toShortString());
surface.bridge().emitJson("my-mod:surface-frame", payload);
```

And receive them in the page:

```js
globalThis.grapheneBridge.on("my-mod:surface-frame", (frame) => {
  document.querySelector("#block").textContent = frame.block;
});
```

## Crosshair Interaction

Graphene automatically raycasts from the camera while the crosshair is over a visible world surface. Hover is routed as
browser mouse movement. When the player right-clicks, Graphene sends a primary browser button press, keeps that press
active while the use key is held, sends drag updates as the crosshair moves, and releases when the use key is released.
Normal HTML buttons, hover states, and drag handlers work without left-clicking and risking block breaking.

`interactionReach` controls the automatic crosshair interaction range. It defaults to
`GrapheneWorldSurfaceConfig.DEFAULT_INTERACTION_REACH` (`64.0D`) and is intentionally separate from render
`maxDistance`. Automatic interaction is capped by both values, so the effective reach is
`min(surface.interactionReach(), surface.maxDistance())`.

```java
surface.setInteractionReach(32.0D);
```

Use the same pick API when you need custom interaction policy:

```java
Vector3fc forward = camera.forwardVector();
Optional<GrapheneWorldSurfacePick> pick = GrapheneWorldSurfaces.pickNearestFromRay(
        client.level.dimension(),
        camera.position(),
        new Vec3(forward.x(), forward.y(), forward.z()),
        64.0D
);
```

The returned `surfaceX`/`surfaceY` values are logical Graphene surface coordinates, already adjusted for readable
two-sided surfaces.

## Current Limits

- World surfaces render the browser texture only; native slots are still a screen-space overlay feature.
- Crosshair interaction supports pointer-style hover, primary-button click, hold, release, and drag. Text entry and
  keyboard focus remain explicit UI flows that should be handled through a normal screen or a mod-specific input mode.
- `CAMERA` full billboards face the camera while keeping the surface's top edge as close to world-up as possible.
- `DOUBLE_SIDED_READABLE` chooses the camera-facing side each frame and flips the back-side UV mapping. This avoids
  drawing two coplanar translucent browser quads or exposing mirrored text.
- `DOUBLE_SIDED_MIRRORED` is retained only as a deprecated binary/source compatibility alias for readable two-sided
  rendering. It no longer exposes mirrored text and should not be used in new code.
- Use a small number of surfaces. For many crops or NPCs, aggregate into one world overlay layer or only create world
  surfaces for selected/high-value objects.

## Validation

Manual checks:

- Walk around the surface and confirm perspective changes with the world.
- Confirm blocks or terrain in front of the surface occlude it.
- Confirm the surface hides past `maxDistance` and in other dimensions.
- Confirm `GrapheneCore.closeOwnedSurfaces(owner)` releases the browser surface.

---

Next: [Native Slots](native-slots.md)
