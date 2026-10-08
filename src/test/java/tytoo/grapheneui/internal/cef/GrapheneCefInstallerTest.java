package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefInstallerTest {
    @Test
    void macCompatibilityArgsKeepGpuEnabledAndInProcess() {
        List<String> args = GrapheneCefInstaller.platformCompatibilityArgs(true, false, false);

        assertEquals(List.of("--in-process-gpu"), args);
    }

    @Test
    void linuxCompatibilityArgsKeepSandboxAndPasswordStoreOverrides() {
        List<String> args = GrapheneCefInstaller.platformCompatibilityArgs(false, true, false);

        assertTrue(args.contains("--no-sandbox"));
        assertTrue(args.contains("--password-store=basic"));
        assertTrue(args.stream().noneMatch("--ozone-platform=x11"::equals));
    }

    @Test
    void backgroundServiceArgsDisableChromeComponentSyncAndPingTraffic() {
        List<String> args = GrapheneCefInstaller.backgroundServiceArgs();

        assertTrue(args.contains("--disable-background-networking"));
        assertTrue(args.contains("--disable-component-update"));
        assertTrue(args.contains("--disable-domain-reliability"));
        assertTrue(args.contains("--disable-sync"));
        assertTrue(args.contains("--no-pings"));
        assertEquals(
                List.of("--disable-features=AutofillServerCommunication,MediaRouter,OptimizationHints,OptimizationTargetPrediction"),
                args.stream().filter(arg -> arg.startsWith("--disable-features=")).toList()
        );
    }

    @Test
    void platformCompatibilityArgsNeverReplaceTheSharedDisabledFeatureList() {
        List<List<String>> platformArgs = List.of(
                GrapheneCefInstaller.platformCompatibilityArgs(true, false, false),
                GrapheneCefInstaller.platformCompatibilityArgs(false, false, false),
                GrapheneCefInstaller.platformCompatibilityArgs(false, true, false),
                GrapheneCefInstaller.platformCompatibilityArgs(false, true, true, true)
        );

        for (List<String> args : platformArgs) {
            assertTrue(args.stream().noneMatch(arg -> arg.startsWith("--disable-features")));
        }
    }

    @Test
    void waylandCompatibilityArgsForceX11() {
        List<String> args = GrapheneCefInstaller.platformCompatibilityArgs(false, true, true);

        assertTrue(args.contains("--ozone-platform=x11"));
    }

    @Test
    void linuxSoftwareWebGlFallbackRequiresExplicitUnsafeOptIn() {
        List<String> defaultArgs = GrapheneCefInstaller.platformCompatibilityArgs(
                false,
                true,
                false,
                false
        );
        List<String> optedInArgs = GrapheneCefInstaller.platformCompatibilityArgs(
                false,
                true,
                false,
                true
        );

        assertTrue(defaultArgs.stream().noneMatch("--enable-unsafe-swiftshader"::equals));
        assertTrue(defaultArgs.stream().noneMatch("--use-gl=angle"::equals));
        assertTrue(defaultArgs.stream().noneMatch("--use-angle=swiftshader-webgl"::equals));
        assertTrue(optedInArgs.contains("--use-gl=angle"));
        assertTrue(optedInArgs.contains("--use-angle=swiftshader-webgl"));
        assertTrue(optedInArgs.contains("--enable-unsafe-swiftshader"));
    }
}
