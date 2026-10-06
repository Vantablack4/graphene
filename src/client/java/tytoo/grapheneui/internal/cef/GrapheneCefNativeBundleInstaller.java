package tytoo.grapheneui.internal.cef;

import io.github.trethore.jcefgithub.CefBuildInfo;
import io.github.trethore.jcefgithub.EnumPlatform;
import io.github.trethore.jcefgithub.EnumProgress;
import io.github.trethore.jcefgithub.IProgressHandler;
import io.github.trethore.jcefgithub.UnsupportedPlatformException;
import io.github.trethore.jcefgithub.impl.step.check.CefInstallationChecker;
import io.github.trethore.jcefgithub.impl.step.extract.TarGzExtractor;
import io.github.trethore.jcefgithub.impl.util.FileUtils;
import io.github.trethore.jcefgithub.impl.util.macos.UnquarantineUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class GrapheneCefNativeBundleInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrapheneCefNativeBundleInstaller.class);
    private static final String PARTIAL_SUFFIX = ".natives.part";
    private static final String INSTALL_LOCK = "install.lock";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final long INITIAL_BACKOFF_MILLIS = 2_000L;
    private static final int MAX_REDIRECTS = 5;
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    private static final int MAX_ATTEMPTS_WITHOUT_PROGRESS = 5;
    private static final long MAX_BACKOFF_MILLIS = 30_000L;

    private final Path installDir;
    private final List<String> mirrorUrls;
    private final IProgressHandler progressHandler;
    private final boolean macOs;
    private final long initialBackoffMillis;
    private final int readTimeoutMillis;

    GrapheneCefNativeBundleInstaller(
            Path installDir,
            List<String> mirrorUrls,
            IProgressHandler progressHandler,
            boolean macOs,
            long initialBackoffMillis,
            int readTimeoutMillis
    ) {
        this.installDir = Objects.requireNonNull(installDir, "installDir");
        this.mirrorUrls = List.copyOf(Objects.requireNonNull(mirrorUrls, "mirrorUrls"));
        this.progressHandler = Objects.requireNonNull(progressHandler, "progressHandler");
        this.macOs = macOs;
        this.initialBackoffMillis = initialBackoffMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        if (this.mirrorUrls.isEmpty()) {
            throw new IllegalArgumentException("mirrorUrls must not be empty");
        }
    }

    static GrapheneCefNativeBundleInstaller forCurrentPlatform(
            Path installDir,
            Collection<String> mirrorTemplates,
            String jcefMavenVersion,
            IProgressHandler progressHandler
    ) throws IOException, UnsupportedPlatformException {
        CefBuildInfo buildInfo = CefBuildInfo.fromClasspath();
        EnumPlatform platform = EnumPlatform.getCurrentPlatform();
        List<String> mirrorUrls = mirrorTemplates.stream()
                .map(template -> resolveMirrorUrl(template, platform.getIdentifier(), buildInfo.getReleaseTag(), jcefMavenVersion))
                .toList();
        return new GrapheneCefNativeBundleInstaller(
                installDir,
                mirrorUrls,
                progressHandler,
                platform.getOs().isMacOSX(),
                INITIAL_BACKOFF_MILLIS,
                READ_TIMEOUT_MILLIS
        );
    }

    static String resolveMirrorUrl(String template, String platformIdentifier, String releaseTag, String jcefMavenVersion) {
        return template
                .replace("{platform}", platformIdentifier)
                .replace("{tag}", releaseTag)
                .replace("{mvn_version}", jcefMavenVersion);
    }

    static boolean isInstalled(Path installDir) {
        try {
            return CefInstallationChecker.checkInstallation(installDir.toFile());
        } catch (UnsupportedPlatformException exception) {
            throw new IllegalStateException("Failed to resolve current jcefgithub platform", exception);
        }
    }

    void ensureInstalled() throws IOException, InterruptedException {
        if (isInstalled(installDir)) {
            return;
        }

        Path partialFile = partialFile();
        download(partialFile);
        install(partialFile);
    }

    Path partialFile() {
        return installDir.resolveSibling(installDir.getFileName() + PARTIAL_SUFFIX);
    }

    private void download(Path partialFile) throws IOException, InterruptedException {
        Files.createDirectories(partialFile.getParent());
        progressHandler.handleProgress(EnumProgress.DOWNLOADING, EnumProgress.NO_ESTIMATION);

        int attemptsWithoutProgress = 0;
        long backoffMillis = initialBackoffMillis;
        IOException lastFailure = null;
        while (attemptsWithoutProgress < MAX_ATTEMPTS_WITHOUT_PROGRESS) {
            for (int mirrorIndex = 0; mirrorIndex < mirrorUrls.size(); mirrorIndex++) {
                String mirrorUrl = mirrorUrls.get(mirrorIndex);
                boolean lastMirror = mirrorIndex == mirrorUrls.size() - 1;
                long sizeBefore = existingSize(partialFile);
                try {
                    if (downloadFrom(mirrorUrl, partialFile, lastMirror)) {
                        return;
                    }
                } catch (IOException exception) {
                    lastFailure = exception;
                    LOGGER.warn(
                            "CEF native download from {} stopped at {} bytes; it resumes from there",
                            mirrorUrl,
                            existingSize(partialFile),
                            exception
                    );
                }

                if (existingSize(partialFile) > sizeBefore) {
                    attemptsWithoutProgress = 0;
                    backoffMillis = initialBackoffMillis;
                }
            }

            attemptsWithoutProgress++;
            Thread.sleep(backoffMillis);
            backoffMillis = Math.min(MAX_BACKOFF_MILLIS, backoffMillis * 2);
        }

        throw new IOException("CEF native download made no progress on any mirror", lastFailure);
    }

    private boolean downloadFrom(String mirrorUrl, Path partialFile, boolean lastMirror) throws IOException {
        long offset = existingSize(partialFile);
        HttpURLConnection connection = open(mirrorUrl, offset);
        try {
            int status = connection.getResponseCode();
            if (status == HTTP_RANGE_NOT_SATISFIABLE && offset > 0) {
                if (offset == unsatisfiedRangeTotal(connection)) {
                    return true;
                }
                LOGGER.warn("CEF native mirror {} rejected resuming at {} bytes; restarting the download", mirrorUrl, offset);
                Files.deleteIfExists(partialFile);
                return false;
            }

            boolean resumed = status == HttpURLConnection.HTTP_PARTIAL;
            if (status != HttpURLConnection.HTTP_OK && !resumed) {
                LOGGER.warn("CEF native mirror {} answered HTTP {}", mirrorUrl, status);
                return false;
            }

            if (resumed && rangeStart(connection) != offset) {
                LOGGER.warn("CEF native mirror {} resumed at the wrong offset; skipping it", mirrorUrl);
                return false;
            }

            if (!resumed && offset > 0 && !lastMirror) {
                LOGGER.warn("CEF native mirror {} cannot resume; trying the next mirror before restarting", mirrorUrl);
                return false;
            }

            if (!resumed) {
                offset = 0L;
            }

            long totalSize = totalSize(connection, offset, resumed);
            transfer(connection, partialFile, offset, totalSize, resumed);
            long downloadedSize = existingSize(partialFile);
            if (totalSize > 0 && downloadedSize != totalSize) {
                throw new IOException("CEF native download ended at " + downloadedSize + " of " + totalSize + " bytes");
            }

            return true;
        } finally {
            connection.disconnect();
        }
    }

    private void transfer(HttpURLConnection connection, Path partialFile, long offset, long totalSize, boolean resumed)
            throws IOException {
        StandardOpenOption mode = resumed ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(partialFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long written = offset;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (totalSize > 0) {
                    progressHandler.handleProgress(EnumProgress.DOWNLOADING, written * 100.0F / totalSize);
                }
            }
        }
    }

    private void install(Path partialFile) throws IOException {
        progressHandler.handleProgress(EnumProgress.EXTRACTING, EnumProgress.NO_ESTIMATION);
        try {
            verifyArchive(partialFile);
        } catch (IOException exception) {
            Files.deleteIfExists(partialFile);
            throw new IOException("Downloaded CEF natives are corrupt; the next start downloads them again", exception);
        }

        FileUtils.deleteDir(installDir.toFile());
        Files.createDirectories(installDir);
        try (ZipInputStream archive = new ZipInputStream(Files.newInputStream(partialFile))) {
            if (!seekNativeTarball(archive)) {
                throw new IOException("Downloaded CEF natives did not contain a .tar.gz archive");
            }
            TarGzExtractor.extractTarGZ(installDir.toFile(), archive);
        } catch (IOException exception) {
            Files.deleteIfExists(partialFile);
            FileUtils.deleteDir(installDir.toFile());
            throw new IOException("Downloaded CEF natives could not be extracted; the next start downloads them again", exception);
        }

        progressHandler.handleProgress(EnumProgress.INSTALL, EnumProgress.NO_ESTIMATION);
        if (macOs) {
            UnquarantineUtil.unquarantine(installDir.toFile());
        }
        Files.createFile(installDir.resolve(INSTALL_LOCK));
        Files.deleteIfExists(partialFile);
    }

    private static void verifyArchive(Path archiveFile) throws IOException {
        boolean tarballFound = false;
        try (ZipInputStream archive = new ZipInputStream(Files.newInputStream(archiveFile))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                tarballFound |= entry.getName().endsWith(".tar.gz");
                archive.transferTo(OutputStream.nullOutputStream());
            }
        }

        if (!tarballFound) {
            throw new IOException("Downloaded CEF natives did not contain a .tar.gz archive");
        }
    }

    private static boolean seekNativeTarball(ZipInputStream archive) throws IOException {
        ZipEntry entry;
        while ((entry = archive.getNextEntry()) != null) {
            if (entry.getName().endsWith(".tar.gz")) {
                return true;
            }
        }

        return false;
    }

    private HttpURLConnection open(String url, long offset) throws IOException {
        String currentUrl = url;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) URI.create(currentUrl).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
            }

            int status = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            if (status / 100 != 3 || location == null) {
                return connection;
            }

            connection.disconnect();
            currentUrl = URI.create(currentUrl).resolve(location).toString();
        }

        throw new IOException("Too many redirects while downloading CEF natives from " + url);
    }

    private static long rangeStart(HttpURLConnection connection) {
        String contentRange = connection.getHeaderField("Content-Range");
        if (contentRange == null || !contentRange.startsWith("bytes ")) {
            return -1L;
        }

        int dash = contentRange.indexOf('-');
        try {
            return dash < 0 ? -1L : Long.parseLong(contentRange.substring("bytes ".length(), dash).trim());
        } catch (NumberFormatException ignored) {
            // A malformed range is treated as a mismatch.
            return -1L;
        }
    }

    private static long unsatisfiedRangeTotal(HttpURLConnection connection) {
        String contentRange = connection.getHeaderField("Content-Range");
        if (contentRange == null) {
            return -1L;
        }

        int slash = contentRange.lastIndexOf('/');
        try {
            return slash < 0 ? -1L : Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException ignored) {
            // An unknown total cannot confirm the partial file is complete.
            return -1L;
        }
    }

    private static long totalSize(HttpURLConnection connection, long offset, boolean resumed) {
        String contentRange = connection.getHeaderField("Content-Range");
        if (resumed && contentRange != null) {
            int slash = contentRange.lastIndexOf('/');
            if (slash >= 0) {
                try {
                    return Long.parseLong(contentRange.substring(slash + 1).trim());
                } catch (NumberFormatException ignored) {
                    // An unknown total ("*") falls back to the content length below.
                }
            }
        }

        long contentLength = connection.getContentLengthLong();
        return contentLength < 0 ? -1L : contentLength + (resumed ? offset : 0L);
    }

    private static long existingSize(Path file) throws IOException {
        return Files.exists(file) ? Files.size(file) : 0L;
    }
}
