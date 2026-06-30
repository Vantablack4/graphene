package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefInstallerTest {
    @Test
    void macCompatibilityArgsKeepGpuWorkInProcess() {
        List<String> args = GrapheneCefInstaller.platformCompatibilityArgs(true, false, false);

        assertEquals(List.of(
                "--disable-gpu",
                "--disable-gpu-compositing",
                "--in-process-gpu"
        ), args);
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
}
