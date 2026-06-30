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
                .rotationDegrees(90.0F, 0.0F, 0.0F)
                .maxDistance(64.0D)
                .maxFps(30)
                .build()
);
```

The local surface plane is centered at `position`. Its local X axis maps to width, local Y maps to height, and
`rotationDegrees(pitch, yaw, roll)` controls how the plane is oriented in world space. A pitch of `90` lays the browser
surface flat over a block for table-top UI.

For nameplate-style in-world billboards:

```java
surface.setFacing(GrapheneWorldSurfaceFacing.CAMERA);
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

## Current Limits

- World surfaces render the browser texture only; native slots are still a screen-space overlay feature.
- Input picking is intentionally not automatic yet. Consumers can still use `surface.inputAdapter()` when they have their
  own raycast-to-surface mapping.
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
