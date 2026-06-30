# Native Slots

Native slots let a web UI reserve DOM rectangles where Graphene draws vanilla Minecraft GUI render state after the Chromium frame.
Use them when HTML/CSS should own layout, but the content must come from Minecraft's renderer, such as item stacks, blocks, player heads, skins, or entities.

Native slots do not create native input widgets.
The browser still owns clicks, typing, focus, drag, and scroll.
Graphene only uses the current widget mouse position for hover effects such as item tooltips.

## JavaScript API

Graphene injects `globalThis.grapheneNativeSlots` with the bridge bootstrap.

```js
const slot = grapheneNativeSlots.register({
    id: "inventory-diamond",
    kind: "item",
    payload: {item: "minecraft:diamond", count: 3},
    render: {decorations: true},
    handoff: {tooltip: true},
    element: document.getElementById("inventory-diamond")
});
```

For React, attach the element from a ref:

```jsx
function NativeItemSlot() {
    const slotRef = React.useRef(null);

    React.useEffect(() => {
        const slot = globalThis.grapheneNativeSlots.register({
            id: "react-diamond",
            kind: "item",
            payload: {item: "minecraft:diamond_sword"},
            handoff: {tooltip: true}
        });

        slot.attach(slotRef.current);
        return () => slot.unregister();
    }, []);

    return <div ref={slotRef} className="native-slot" />;
}
```

`register(...)` returns a handle with `attach(element)`, `update(options)`, `unregister()`, `flush(reason)`, and `measureNow()`.
Graphene watches resize, scroll, intersection, and viewport changes and batches measurements with `requestAnimationFrame`.

## Built-In Kinds

- `item`, `minecraft:item`, `vanilla:item`: payload `{item: "minecraft:diamond", count: 1}`
- `block`, `minecraft:block`, `vanilla:block`: payload `{block: "minecraft:grass_block"}`
- `head`, `player-head`, `minecraft:head`: payload `{uuid: "..."}` or `{texture: "minecraft:textures/entity/player/wide/steve.png"}`
- `skin`, `player-skin`, `minecraft:skin`: payload `{uuid: "..."}` or `{texture: "...", model: "slim"}`
- `entity`, `minecraft:entity`, `vanilla:entity`: payload `{entity: "minecraft:zombie"}`

Common render options:

- `zIndex`: render order among native slots
- `visible`: false skips rendering without unregistering
- `scale` or `size`: renderer-specific size override
- `rotationX`, `rotationY`, `pivotY`: skin/entity orientation options
- `decorations`, `fake`, `seed`, `countText`: item/block options

Common handoff options:

- `tooltip: true`: item/block slot asks Minecraft to render the stack tooltip while hovered

## Coordinate Model

The JS helper measures `element.getBoundingClientRect()` in CSS viewport coordinates.
Java maps that rectangle through the current `BrowserSurface` resolution and `viewBox`, then clamps it to the rendered surface rectangle.

If you render the same surface at a different size, use the same size for browser input forwarding and for `surface.render(...)`.
If you crop with `setViewBox(...)`, only the visible part of a slot inside that viewBox is rendered.

## Lifecycle

- Register slots after the page creates the placeholder elements.
- Call `slot.unregister()` when a component unmounts.
- Graphene clears page-owned slots on surface navigation, CEF load start, and surface close.
- `pagehide` and `beforeunload` also send a reset from the JS helper.

## Limits

- Native slots are render overlays, not native interactive controls.
- Entity slots require a loaded client level; menus shown before a world is joined cannot instantiate arbitrary entities.
- Payloads intentionally accept identifiers and simple renderer options, not arbitrary NBT or item data components.

---

Next: [Troubleshooting](troubleshooting.md)
