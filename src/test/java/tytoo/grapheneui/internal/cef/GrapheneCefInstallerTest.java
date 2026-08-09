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
    void linuxCompatibilityArgsKeepExistingSandboxAndNetworkHardening() {
        List<String> args = GrapheneCefInstaller.platformCompatibilityArgs(false, true, false);

        assertTrue(args.contains("--no-sandbox"));
        assertTrue(args.contains("--password-store=basic"));
        assertTrue(args.contains("--disable-background-networking"));
        assertTrue(args.stream().noneMatch("--ozone-platform=x11"::equals));
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
