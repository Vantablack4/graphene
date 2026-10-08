package tytoo.grapheneui.internal.cef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

final class GrapheneCefComponentCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrapheneCefComponentCache.class);
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;
    private static final List<String> COMPONENT_DIRECTORY_NAMES = List.of(
            "ActorSafetyLists",
            "AmountExtractionHeuristicRegexes",
            "AutofillStates",
            "CertificateRevocation",
            "CommerceHeuristics",
            "component_crx_cache",
            "CookieReadinessList",
            "Crowd Deny",
            "FileTypePolicies",
            "FirstPartySetsPreloaded",
            "HistorySearch",
            "hyphen-data",
            "MaskedDomainListPreloaded",
            "MediaFoundationWidevineCdm",
            "MEIPreload",
            "OnDeviceHeadSuggestModel",
            "OpenCookieDatabase",
            "OptimizationHints",
            "OriginTrials",
            "PKIMetadata",
            "PlusAddressBlocklist",
            "PrivacySandboxAttestationsPreloaded",
            "ProbabilisticRevealTokenRegistry",
            "RealTimeUrlChecksAllowlist",
            "RecoveryImproved",
            "SafetyTips",
            "SSLErrorAssistant",
            "Subresource Filter",
            "TpcdMetadata",
            "TrustTokenKeyCommitments",
            "WasmTtsEngine",
            "WidevineCdm",
            "ZxcvbnData"
    );

    private GrapheneCefComponentCache() {
    }

    static long prune(Path cacheDirectory) {
        long removedBytes = 0L;
        int removedDirectories = 0;
        for (String directoryName : COMPONENT_DIRECTORY_NAMES) {
            Path componentDirectory = cacheDirectory.resolve(directoryName);
            if (!Files.isDirectory(componentDirectory, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }

            try {
                removedBytes += deleteRecursively(componentDirectory);
                removedDirectories++;
            } catch (IOException exception) {
                LOGGER.warn("Failed to remove unused Chromium component directory {}", componentDirectory, exception);
            }
        }

        if (removedDirectories > 0) {
            LOGGER.info(
                    "Removed {} unused Chromium component directories ({} MiB) from {}",
                    removedDirectories,
                    removedBytes / BYTES_PER_MEBIBYTE,
                    cacheDirectory
            );
        }

        return removedBytes;
    }

    private static long deleteRecursively(Path directory) throws IOException {
        DeletingFileVisitor visitor = new DeletingFileVisitor();
        Files.walkFileTree(directory, visitor);
        return visitor.deletedBytes;
    }

    private static final class DeletingFileVisitor extends SimpleFileVisitor<Path> {
        private long deletedBytes = 0L;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.delete(file);
            deletedBytes += attributes.size();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
            if (exception != null) {
                throw exception;
            }

            Files.delete(directory);
            return FileVisitResult.CONTINUE;
        }
    }
}
