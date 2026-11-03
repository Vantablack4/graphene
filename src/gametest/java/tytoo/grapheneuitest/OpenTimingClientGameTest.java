package tytoo.grapheneuitest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import tytoo.grapheneui.api.GrapheneCore;
import tytoo.grapheneui.internal.browser.GrapheneOpenTrace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Measures, with real CEF in a live world, how long a Graphene screen shows nothing after it opens. One cold open,
 * then warm opens with the page gone in between. All times are milliseconds after {@code setScreen} on the client.
 */
@SuppressWarnings("UnstableApiUsage")
public final class OpenTimingClientGameTest implements FabricClientGameTest {
    private static final String KARAKTER_PAGE = "web/karakter/index.html";
    private static final String MINIMAL_PAGE = "web/minimal/index.html";
    private static final long OPEN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final int WARM_OPENS = 7;
    private static final int MINIMAL_OPENS = 5;
    private static final int TICKS_BETWEEN_OPENS = 60;
    private static final int MAIN_THREAD_SETTLE_TICKS = 30;
    private static final int IDLE_TICKS = 600;
    private static final double MARK_TOLERANCE_MILLIS = 5.0D;
    private static final double HOLD_LIMIT_MILLIS = 800.0D;
    private static final double CSS_PIXELS_PER_GUI_PIXEL = 2.0D;
    private static final List<String> ROW_MARKS = List.of(
            "screen.frame",
            "background.first",
            "surface.create.end",
            "browser.created",
            "load.start.cef",
            "load.end.cef",
            "load.end.main",
            "surface.css",
            "bridge.inject",
            "bridge.ready",
            "paint.first",
            "paint.content",
            "frame.first",
            "frame.content"
    );
    private static final String CARD_JSON = """
            {"name":"Teressa Valdemar","race":"Sanctarian","gender":"Kadın","age":38,
             "alignment":"Kuralcı Tarafsız","socialRank":"İmparatoriçe",
             "stats":{"str":4,"end":4,"agi":4,"will":2,"wis":2,"int":3,"cha":2},
             "sections":{
               "currency":{"amount":1250,"display":"1.250,40"},
               "profession":{"professionId":"carpenter","label":"Marangoz, 75. seviye","name":"Marangoz",
                             "level":75,"levelLabel":"75. seviye","rankLabel":"Kalfa",
                             "xp":99950,"nextRankXp":100000,"nextRankLabel":"Usta",
                             "sideProfessionId":"clothier","sideLabel":"Dokumacı","sideLevel":45},
               "vigor":{"current":64,"cap":100,"renewalSeconds":86340}}}
            """;
    private static final String PAGE_TIMING_SCRIPT = """
            const navigation = performance.getEntriesByType("navigation")[0];
            const paints = Object.fromEntries(performance.getEntriesByType("paint").map(entry => [entry.name, entry.startTime]));
            return {
                origin: performance.timeOrigin,
                responseEnd: navigation ? navigation.responseEnd : -1,
                domInteractive: navigation ? navigation.domInteractive : -1,
                domContentLoaded: navigation ? navigation.domContentLoadedEventEnd : -1,
                loadEnd: navigation ? navigation.loadEventEnd : -1,
                marks: globalThis.__openTimingMarks || {},
                paints
            };
            """;

    private final List<String> report = new ArrayList<>();
    private final Map<String, List<Open>> opensByLabel = new LinkedHashMap<>();
    private final List<Mark> marks = new CopyOnWriteArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        Path evidence = FabricLoader.getInstance().getGameDir().resolve("../../test-evidence").normalize().toAbsolutePath();
        Path screenshots = evidence.resolve("screenshots");
        Path output = evidence.resolve("open-timing");
        try {
            Files.createDirectories(screenshots);
            Files.createDirectories(output);
        } catch (IOException failure) {
            throw new AssertionError("Could not prepare the evidence directory", failure);
        }

        boolean karakterAvailable = OpenTimingClientGameTest.class.getClassLoader()
                .getResource("assets/graphene_gametest/" + KARAKTER_PAGE) != null;
        String primaryPage = karakterAvailable ? KARAKTER_PAGE : MINIMAL_PAGE;
        String firstPage = Boolean.getBoolean("graphene.openTiming.minimalFirst") ? MINIMAL_PAGE : primaryPage;
        context.waitFor(client -> GrapheneCore.isInitialized(), 2400);
        context.runOnClient(client -> {
            client.options.guiScale().set(3);
            client.getWindow().setWindowed(1920, 1080);
            client.resizeGui();
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.waitTicks(TICKS_BETWEEN_OPENS);
            report.add("memory before first open:");
            report.addAll(processMemory());

            GrapheneOpenTrace.install(this::onMark);
            Open cold = measureOpen(context, "cold", firstPage);
            report.add("memory with the page open:");
            report.addAll(processMemory());
            closeScreen(context);
            report.add("memory after closing it:");
            report.addAll(processMemory());
            record("cold", cold);

            for (int index = 0; index < WARM_OPENS; index++) {
                record("karakter warm", measureOpen(context, "warm" + index, primaryPage));
                closeScreen(context);
            }

            context.waitTicks(IDLE_TICKS);
            record("karakter after 30 s idle", measureOpen(context, "idle", primaryPage));
            closeScreen(context);

            for (int index = 0; index < MINIMAL_OPENS; index++) {
                record("minimal warm", measureOpen(context, "minimal" + index, MINIMAL_PAGE));
                closeScreen(context);
            }

            captureFrames(context, screenshots, primaryPage);
            if (karakterAvailable) {
                checkScreenOverScreenKeepsItsBackground(context, primaryPage);
                checkLayoutFollowsTheWindow(context, screenshots);
            }
            closeScreen(context);
            GrapheneOpenTrace.install(null);
            report.add("memory at the end:");
            report.addAll(processMemory());
        } finally {
            GrapheneOpenTrace.install(null);
        }

        writeSummary(output.resolve("summary.txt"));
    }

    private void onMark(Object browser, String name, String detail, long nanos) {
        marks.add(new Mark(browser, name, detail, nanos));
    }

    private Open measureOpen(ClientGameTestContext context, String label, String page) {
        marks.clear();
        GrapheneOpenTrace.install(this::onMark);
        long[] start = new long[2];
        long[] setScreenEnd = new long[1];
        context.runOnClient(client -> {
            // A player who opens a screen has just pressed a key; without input the game drops to 30 FPS after a minute.
            client.getFramerateLimitTracker().onInputReceived();
            start[1] = System.currentTimeMillis();
            start[0] = System.nanoTime();
            client.setScreen(new OpenTimingScreen(page, CARD_JSON));
            setScreenEnd[0] = System.nanoTime();
        });

        while (firstMarkFor("frame.content") == null) {
            if (System.nanoTime() - start[0] > OPEN_TIMEOUT_NANOS) {
                throw new AssertionError("No content frame after 15 s for " + label + "; marks: " + marks);
            }

            context.waitTick();
        }

        long frameContent = firstMarkFor("frame.content").nanos();
        int renderedFrames = 0;
        for (Mark mark : marks) {
            if (mark.name().equals("screen.frame") && mark.nanos() <= frameContent) {
                renderedFrames++;
            }
        }
        double frameMillis = (frameContent - start[0]) / 1_000_000.0D / Math.max(1, renderedFrames);
        context.waitTicks(MAIN_THREAD_SETTLE_TICKS);

        Open open = new Open(label, page, start[0], start[1], setScreenEnd[0], renderedFrames, frameMillis);
        Mark drawn = firstMarkFor("frame.content");
        for (Mark mark : marks) {
            if (mark.browser() == null || mark.browser() == drawn.browser()) {
                open.markTimes().putIfAbsent(mark.name(), (mark.nanos() - start[0]) / 1_000_000.0D);
            }
            if (mark.name().equals("resource.read")) {
                open.resources().add(String.format(Locale.ROOT, "%.1f %s", (mark.nanos() - start[0]) / 1_000_000.0D, mark.detail()));
            }
            if (mark.name().equals("screen.frame") && mark.nanos() <= frameContent) {
                open.frameTimes().add((long) ((mark.nanos() - start[0]) / 1_000_000.0D));
            }
        }
        open.markTimes().put("frame.content", (frameContent - start[0]) / 1_000_000.0D);

        JsonObject pageTiming = pageTiming(context);
        if (pageTiming != null) {
            double origin = pageTiming.get("origin").getAsDouble() - start[1];
            open.pageTimes().put("timeOrigin", origin);
            for (String key : List.of("responseEnd", "domInteractive", "domContentLoaded", "loadEnd")) {
                open.pageTimes().put(key, origin + pageTiming.get(key).getAsDouble());
            }
            for (Map.Entry<String, JsonElement> entry : pageTiming.getAsJsonObject("marks").entrySet()) {
                open.pageTimes().put(entry.getKey(), origin + entry.getValue().getAsDouble());
            }
            for (Map.Entry<String, JsonElement> entry : pageTiming.getAsJsonObject("paints").entrySet()) {
                open.pageTimes().put(entry.getKey(), origin + entry.getValue().getAsDouble());
            }
        }

        report.add(open.describe());
        System.out.println("OPEN_TIMING " + open.describe());
        assertWorldNeverBlursBeforeThePage(open);
        return open;
    }

    /** Over the world the blur comes with the page, on the same frame, unless the page took longer than the limit. */
    private static void assertWorldNeverBlursBeforeThePage(Open open) {
        double background = open.markTimes().get("background.first");
        double content = open.markTimes().get("paint.content");
        if (background + MARK_TOLERANCE_MILLIS < content && content < HOLD_LIMIT_MILLIS) {
            throw new AssertionError("The world blurred " + (content - background) + " ms before the page painted: " + open.describe());
        }
    }

    /** A screen opened from another screen must not flash the sharp world between the two. */
    private void checkScreenOverScreenKeepsItsBackground(ClientGameTestContext context, String page) {
        marks.clear();
        GrapheneOpenTrace.install(this::onMark);
        long[] start = new long[1];
        context.runOnClient(client -> {
            start[0] = System.nanoTime();
            client.setScreen(new OpenTimingScreen(page, CARD_JSON));
        });
        for (int tick = 0; tick < 4 && firstMarkFor("background.first") == null; tick++) {
            context.waitTick();
        }

        Mark background = firstMarkFor("background.first");
        if (background == null || background.nanos() - start[0] > TimeUnit.MILLISECONDS.toNanos(200)) {
            throw new AssertionError("A screen opened over a screen held its background back: " + marks);
        }

        Mark content = firstMarkFor("paint.content");
        report.add("screen over screen: background at " + (background.nanos() - start[0]) / 1_000_000L + " ms, page painted "
                + (content == null ? "later" : "at " + (content.nanos() - start[0]) / 1_000_000L + " ms"));
    }

    /** Resizing and GUI Scale still reach the open page: zoom, grid, text and the native portrait all follow. */
    private void checkLayoutFollowsTheWindow(ClientGameTestContext context, Path screenshots) {
        context.getInput().setCursorPos(0.0D, 0.0D);
        record LayoutCase(String name, int width, int height, int guiScale) {
        }

        for (LayoutCase layoutCase : List.of(
                new LayoutCase("gui3", 1920, 1080, 3),
                new LayoutCase("gui2", 1920, 1080, 2),
                new LayoutCase("small-auto", 1254, 646, 0)
        )) {
            context.runOnClient(client -> {
                client.options.guiScale().set(layoutCase.guiScale());
                client.getWindow().setWindowed(layoutCase.width(), layoutCase.height());
                client.resizeGui();
            });
            String[] last = new String[1];
            try {
                context.waitFor(client -> {
                    if (!(client.screen instanceof OpenTimingScreen screen) || screen.lastLayoutJson() == null) {
                        return false;
                    }

                    last[0] = screen.lastLayoutJson();
                    JsonObject layout = JsonParser.parseString(last[0]).getAsJsonObject();
                    return client.getWindow().getScreenWidth() == layoutCase.width()
                            && layout.get("surfaceWidth").getAsDouble() == screen.width * CSS_PIXELS_PER_GUI_PIXEL
                            && Math.abs(layout.get("zoom").getAsDouble() - client.getWindow().getGuiScale() / CSS_PIXELS_PER_GUI_PIXEL) < 0.001D
                            && Math.abs(layout.get("viewportWidth").getAsDouble() - client.getWindow().getWidth()) <= 1.0D
                            && layout.get("fontsReady").getAsBoolean()
                            && layout.has("textSettled") && layout.get("textSettled").getAsBoolean();
                }, 200);
            } catch (AssertionError timeout) {
                throw new AssertionError("Layout never followed the window at " + layoutCase.name() + "; last report: " + last[0], timeout);
            }

            JsonObject layout = JsonParser.parseString(last[0]).getAsJsonObject();
            if (!layout.get("cardOnGrid").getAsBoolean() || layout.get("textCut").getAsInt() > 0 || layout.get("portraitScale").getAsDouble() <= 0.0D) {
                throw new AssertionError("Wrong layout at " + layoutCase.name() + ": " + last[0]);
            }

            context.waitTicks(10);
            context.takeScreenshot(
                    TestScreenshotOptions.of("open-timing-layout-" + layoutCase.name())
                            .disableCounterPrefix()
                            .withDestinationDir(screenshots)
            );
            report.add("layout " + layoutCase.name() + ": " + last[0]);
        }
    }

    private JsonObject pageTiming(ClientGameTestContext context) {
        CompletableFuture<String> future = context.computeOnClient(client -> {
            if (!(client.screen instanceof OpenTimingScreen screen) || !screen.webView().bridge().isReady()) {
                return null;
            }

            return screen.webView().getSurface().evaluate(PAGE_TIMING_SCRIPT, Duration.ofSeconds(5));
        });
        if (future == null) {
            return null;
        }

        for (int tick = 0; tick < 400 && !future.isDone(); tick++) {
            context.waitTick();
        }

        if (!future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        return JsonParser.parseString(future.join()).getAsJsonObject();
    }

    private void closeScreen(ClientGameTestContext context) {
        context.setScreen(() -> null);
        context.waitTicks(TICKS_BETWEEN_OPENS);
    }

    private void captureFrames(ClientGameTestContext context, Path screenshots, String page) {
        context.runOnClient(client -> client.setScreen(new OpenTimingScreen(page, CARD_JSON)));
        for (int frame = 0; frame < 6; frame++) {
            context.takeScreenshot(
                    TestScreenshotOptions.of("open-timing-frame-" + frame)
                            .disableCounterPrefix()
                            .withDestinationDir(screenshots)
            );
        }

        context.waitTicks(40);
        context.takeScreenshot(
                TestScreenshotOptions.of("open-timing-settled")
                        .disableCounterPrefix()
                        .withDestinationDir(screenshots)
        );
    }

    private Mark firstMarkFor(String name) {
        for (Mark mark : marks) {
            if (mark.name().equals(name)) {
                return mark;
            }
        }

        return null;
    }

    private void record(String group, Open open) {
        opensByLabel.computeIfAbsent(group, ignoredGroup -> new ArrayList<>()).add(open);
    }

    private void writeSummary(Path file) {
        List<String> lines = new ArrayList<>(report);
        lines.add("");
        lines.add("median per group (ms after setScreen):");
        for (Map.Entry<String, List<Open>> group : opensByLabel.entrySet()) {
            StringBuilder line = new StringBuilder(group.getKey()).append(" n=").append(group.getValue().size());
            for (String name : ROW_MARKS) {
                line.append(String.format(Locale.ROOT, " %s=%.0f", name, median(group.getValue(), open -> open.markTimes().get(name))));
            }
            line.append(String.format(Locale.ROOT, " setScreen=%.1f", median(group.getValue(), Open::setScreenMillis)));
            line.append(String.format(Locale.ROOT, " frameMs=%.1f", median(group.getValue(), Open::frameMillis)));
            for (String name : List.of("timeOrigin", "responseEnd", "domInteractive", "domContentLoaded", "fonts.loading1", "fonts.done1", "fonts.loading2", "fonts.done2", "fonts.ready", "loadEnd", "attr.surface", "attr.ready", "panel.visible", "first-paint", "first-contentful-paint", "pixeltext.settled")) {
                line.append(String.format(Locale.ROOT, " page.%s=%.0f", name, median(group.getValue(), open -> open.pageTimes().get(name))));
            }
            lines.add(line.toString());
        }

        lines.forEach(line -> System.out.println("OPEN_TIMING " + line));
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AssertionError("Could not write " + file, failure);
        }
    }

    private static double median(List<Open> opens, Function<Open, Double> value) {
        List<Double> values = new ArrayList<>();
        for (Open open : opens) {
            Double measured = value.apply(open);
            if (measured != null) {
                values.add(measured);
            }
        }

        if (values.isEmpty()) {
            return Double.NaN;
        }

        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    private static List<String> processMemory() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return List.of("  (only measured on Windows)");
        }

        String script = """
                $all = Get-CimInstance Win32_Process; $ids = @(%d); $found = $true;
                while ($found) { $found = $false; foreach ($p in $all) { if (($ids -contains $p.ParentProcessId) -and -not ($ids -contains $p.ProcessId)) { $ids += $p.ProcessId; $found = $true } } };
                foreach ($p in $all) { if ($ids -contains $p.ProcessId) {
                    $type = if ($p.CommandLine -match '--type=([a-z-]+)') { $matches[1] } else { 'main' };
                    $sub = if ($p.CommandLine -match '--utility-sub-type=([a-zA-Z._-]+)') { ' ' + $matches[1] } else { '' };
                    '  {0} {1}{2} private={3:N0}MB workingSet={4:N0}MB' -f $p.Name, $type, $sub, ($p.PrivatePageCount / 1MB), ($p.WorkingSetSize / 1MB)
                } }
                """.formatted(ProcessHandle.current().pid());
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(lines::add);
            }
            process.waitFor(30, TimeUnit.SECONDS);
            return lines;
        } catch (IOException | InterruptedException failure) {
            return List.of("  (memory read failed: " + failure + ")");
        }
    }

    private record Mark(Object browser, String name, String detail, long nanos) {
    }

    private record Open(
            String label,
            String page,
            long startNanos,
            long startMillis,
            long setScreenEndNanos,
            int frames,
            double frameMillis,
            Map<String, Double> markTimes,
            Map<String, Double> pageTimes,
            List<String> resources,
            List<Long> frameTimes
    ) {
        private Open(String label, String page, long startNanos, long startMillis, long setScreenEndNanos, int frames, double frameMillis) {
            this(label, page, startNanos, startMillis, setScreenEndNanos, frames, frameMillis, new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>());
        }

        private double setScreenMillis() {
            return (setScreenEndNanos - startNanos) / 1_000_000.0D;
        }

        private String describe() {
            StringBuilder line = new StringBuilder(label).append(' ').append(page);
            line.append(String.format(Locale.ROOT, " setScreen=%.1f frames=%d frameMs=%.1f", setScreenMillis(), frames, frameMillis));
            markTimes.forEach((name, time) -> line.append(String.format(Locale.ROOT, " %s=%.1f", name, time)));
            pageTimes.forEach((name, time) -> line.append(String.format(Locale.ROOT, " page.%s=%.1f", name, time)));
            line.append(" frameTimes=").append(frameTimes);
            if (!resources.isEmpty()) {
                line.append(" resources=").append(resources);
            }
            return line.toString();
        }
    }
}
