package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class GrapheneCefComponentCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void pruneRemovesComponentDirectoriesAndKeepsProfileAndGpuCaches() throws IOException {
        Path cacheDirectory = Files.createDirectories(tempDir.resolve("cache"));
        writeFile(cacheDirectory.resolve("WidevineCdm/4.10.2934.0/manifest.json"), 10);
        writeFile(cacheDirectory.resolve("component_crx_cache/payload.crx3"), 20);
        writeFile(cacheDirectory.resolve("Subresource Filter/Unindexed Rules/9.65.0/Filtering Rules"), 30);
        writeFile(cacheDirectory.resolve("Default/Preferences"), 7);
        writeFile(cacheDirectory.resolve("GrShaderCache/data_0"), 7);
        writeFile(cacheDirectory.resolve("first_party_sets.db"), 7);

        long removedBytes = GrapheneCefComponentCache.prune(cacheDirectory);

        assertEquals(60L, removedBytes);
        assertFalse(Files.exists(cacheDirectory.resolve("WidevineCdm")));
        assertFalse(Files.exists(cacheDirectory.resolve("component_crx_cache")));
        assertFalse(Files.exists(cacheDirectory.resolve("Subresource Filter")));
        assertTrue(Files.isRegularFile(cacheDirectory.resolve("Default/Preferences")));
        assertTrue(Files.isRegularFile(cacheDirectory.resolve("GrShaderCache/data_0")));
        assertTrue(Files.isRegularFile(cacheDirectory.resolve("first_party_sets.db")));
    }

    @Test
    void pruneIsANoOpWhenNoComponentDirectoriesExist() throws IOException {
        Path cacheDirectory = Files.createDirectories(tempDir.resolve("cache"));
        writeFile(cacheDirectory.resolve("Default/Preferences"), 7);

        assertEquals(0L, GrapheneCefComponentCache.prune(cacheDirectory));
        assertEquals(0L, GrapheneCefComponentCache.prune(tempDir.resolve("missing-cache")));
        assertTrue(Files.isRegularFile(cacheDirectory.resolve("Default/Preferences")));
    }

    @Test
    void pruneNeverFollowsSymbolicLinksOutOfTheCache() throws IOException {
        Path cacheDirectory = Files.createDirectories(tempDir.resolve("cache"));
        Path outsideFile = writeFile(tempDir.resolve("outside/keep.bin"), 5);
        writeFile(cacheDirectory.resolve("ZxcvbnData/3/ranked_dicts"), 4);
        Path linkedFile = cacheDirectory.resolve("ZxcvbnData/3/linked.bin");
        assumeTrue(tryCreateSymbolicLink(cacheDirectory.resolve("WidevineCdm"), outsideFile.getParent()));
        assumeTrue(tryCreateSymbolicLink(linkedFile, outsideFile));

        GrapheneCefComponentCache.prune(cacheDirectory);

        assertTrue(Files.isRegularFile(outsideFile));
        assertFalse(Files.exists(cacheDirectory.resolve("ZxcvbnData")));
        assertTrue(Files.isSymbolicLink(cacheDirectory.resolve("WidevineCdm")));
    }

    private static Path writeFile(Path file, int size) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.write(file, new byte[size]);
    }

    private static boolean tryCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException ignored) {
            // Symbolic links need extra privileges on some platforms; the test is skipped there.
            return false;
        }
    }
}
