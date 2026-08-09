# Testing

Graphene has two practical validation layers:

- unit tests under `src/test/java/...`
- in-game debug flows in the debug module

## Unit Test Coverage

Current test classes include:

- `GrapheneBridgeJsonApiTest`
- `GrapheneBridgeMessageCodecTest`
- `GrapheneBridgeOutboundQueueTest`
- `GrapheneAppUrlsTest`
- `GrapheneClasspathUrlsTest`
- `GrapheneHttpUrlsTest`
- `GrapheneHttpConfigTest`
- `GrapheneConfigTest`
- `GrapheneHttpServerRuntimeTest`
- `GrapheneMimeTypesTest`
- `BrowserSurfaceViewportMapperTest`
- `GrapheneWebViewShortcutPolicyTest`
- `GrapheneDirtyRectHistoryTest`
- `GraphenePaintBufferTest`
- `GrapheneBrowserFrameUploaderTest`
- `GrapheneNativeSlotBoundsMapperTest`
- `GrapheneDebugLogSelectorTest`
- `GrapheneLinuxKeyEventPlatformResolverTest`

These cover bridge serialization and routing behavior, URL/path normalization, HTTP server behavior, MIME detection, viewport/input/native-slot mapping, and debug selector parsing.

## In-Game Debug Validation

Use the debug client and bundled pages to validate end-to-end behavior:

1. Run `./gradlew runDebugClient`.
2. Press `F10` to open `GrapheneBrowserDebugScreen`.
3. Visit `graphene_test/pages/tests.html` and `graphene_test/pages/automated-tests.html`.
4. Trigger bridge interactions and automated test runs from the page UI.

`automated-tests.html` calls the Java-side debug runner over the bridge (`debug:tests:run`) and renders pass/fail results.

The debug browser surface runs with render-driven frames and performance metrics enabled. From its DevTools console,
inspect the live cumulative transport counters and shared-texture fallback reason with:

```js
await globalThis.grapheneBridge.request("debug:performance:snapshot", {});
```

For animated/WebGL changes, keep the browser visible while interacting with the scene, sample the counters before and
after the motion, and verify that `partialFrameCopies` and `partialFrameUploads` rise without a matching stream of
`dirtyHistoryFallbacks`. Also inspect Minecraft frame timing; the snapshot measures CPU submission, not GPU completion.

## Commands

Run from repository root:

```bash
./gradlew compileJava
./gradlew test
./gradlew build
./gradlew runDebugClient
./gradlew runDebugClient -PgrapheneDebug=*
./gradlew runDebugClient -PgrapheneDebug=tytoo.grapheneui.internal.bridge
./gradlew runDebugClient -PgrapheneDebug=tytoo.grapheneui.internal.bridge.GrapheneBridgeRuntime
```

For logging checks, run one pass without `-PgrapheneDebug` and one with a selector. Remove `-PgrapheneDebug` again to disable Graphene debug logs.

Linux/Xvfb lanes without a hardware GPU may opt into Chromium's lower-security
SwiftShader WebGL fallback with the JVM property
`graphene.cef.allowUnsafeSoftwareWebGl=true`. Keep this disabled for ordinary
clients and only use it for trusted embedded content in isolated test runs.
The opt-in applies Chromium's documented SwANGLE WebGL fallback flags rather
than changing the normal hardware rendering path.

## When Adding Features

Keep tests focused and deterministic:

- bridge protocol changes: codec/router/request lifecycle tests
- rendering/input math: mapper/state tests
- lifecycle behavior: navigation/close/pending-request edge cases

For bridge-facing features, prefer both:

1. unit tests for core logic
2. debug-page/manual verification for integration behavior

For native slots, unit-test coordinate and state mapping in JUnit, then verify visual behavior in the debug client with `graphene_test/pages/native-slots.html`.

---

Next: [Overview](overview.md)
