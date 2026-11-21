package tytoo.grapheneui.internal.cef;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.trethore.jcefgithub.EnumProgress;
import io.github.trethore.jcefgithub.IProgressHandler;
import io.github.trethore.jcefgithub.shaded.commons.compress.archivers.tar.TarArchiveEntry;
import io.github.trethore.jcefgithub.shaded.commons.compress.archivers.tar.TarArchiveOutputStream;
import io.github.trethore.jcefgithub.shaded.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GrapheneCefNativeBundleInstallerTest {
    private static final String NATIVE_FILE = "libjcef.so";
    private static final byte[] NATIVE_BYTES = nativeBytes();
    private static final IProgressHandler IGNORED_PROGRESS = (state, percent) -> {
    };

    private static final char[] STORE_PASSWORD = "graphene-test".toCharArray();
    private static SSLContext tls;
    private static SSLSocketFactory previousSocketFactory;

    private final List<String> rangeHeaders = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger plainRequests = new AtomicInteger();
    private HttpsServer server;
    private HttpServer plainServer;
    private byte[] bundle;
    private byte[] pinnedBundle;
    private int requestsToCut;
    private boolean honourRanges = true;

    @TempDir
    Path tempDir;

    // The installer only speaks https, so the mirror is a real local TLS server with a throwaway certificate.
    @BeforeAll
    static void trustLocalCertificate(@TempDir Path keyDir) throws Exception {
        Path keyStore = keyDir.resolve("mirror.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "mirror", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=127.0.0.1", "-ext", "san=ip:127.0.0.1", "-validity", "2",
                "-storetype", "PKCS12", "-keystore", keyStore.toString(),
                "-storepass", new String(STORE_PASSWORD), "-keypass", new String(STORE_PASSWORD))
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keyStore)) {
            store.load(in, STORE_PASSWORD);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, STORE_PASSWORD);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
    }

    @AfterAll
    static void restoreTrust() {
        HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory);
    }

    @BeforeEach
    void startServer() throws IOException {
        bundle = nativesJar(NATIVE_BYTES);
        pinnedBundle = bundle.clone();
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/natives.jar", this::serveBundle);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/natives.jar");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        plainServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        plainServer.createContext("/", exchange -> {
            plainRequests.incrementAndGet();
            serveBundle(exchange);
        });
        plainServer.start();
        server.createContext("/downgrade", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://127.0.0.1:" + plainServer.getAddress().getPort() + "/natives.jar");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        plainServer.stop(0);
    }

    @Test
    void wellFormedArchiveWithOtherNativesFailsThePinBeforeExtraction() throws Exception {
        // A compromised mirror serves a valid bundle of the pinned size with different native code.
        byte[] other = Arrays.copyOf(NATIVE_BYTES, NATIVE_BYTES.length / 2);
        other[0] ^= 0x01;
        byte[] forged = nativesJar(other);
        assertTrue(forged.length <= pinnedBundle.length, "forged bundle must fit the pinned size");
        bundle = Arrays.copyOf(forged, pinnedBundle.length);
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");

        IOException failure = assertThrows(IOException.class, installer::ensureInstalled);

        assertTrue(String.valueOf(failure.getCause()).contains("SHA-256"), String.valueOf(failure.getCause()));
        assertFalse(Files.exists(installDir.resolve(NATIVE_FILE)));
        assertFalse(Files.exists(installDir.resolve("install.lock")));
        assertFalse(Files.exists(installer.partialFile()));
    }

    @Test
    void redirectToPlainHttpIsRefusedWithoutARequest() throws Exception {
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/downgrade");

        assertThrows(IOException.class, installer::ensureInstalled);

        assertEquals(0, plainRequests.get());
        assertFalse(Files.exists(installDir.resolve("install.lock")));
    }

    @Test
    void plainHttpMirrorIsRejected() {
        String mirror = "http://127.0.0.1:" + plainServer.getAddress().getPort() + "/natives.jar";

        assertThrows(IllegalArgumentException.class, () -> new GrapheneCefNativeBundleInstaller(
                installDir(), List.of(mirror), pin(), IGNORED_PROGRESS, false, 1L, 500));

        assertEquals(0, plainRequests.get());
    }

    @Test
    void mirrorServingAnotherSizeIsSkipped() throws Exception {
        bundle = Arrays.copyOf(pinnedBundle, pinnedBundle.length + 4096);
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");

        assertThrows(IOException.class, installer::ensureInstalled);

        assertFalse(Files.exists(installer.partialFile()) && Files.size(installer.partialFile()) > pinnedBundle.length);
        assertFalse(Files.exists(installDir.resolve("install.lock")));
    }

    @Test
    void cutDownloadResumesWithARangeRequestAndInstalls() throws Exception {
        requestsToCut = 1;
        Path installDir = installDir();

        installer(installDir, "/redirect").ensureInstalled();

        assertEquals(2, rangeHeaders.size());
        assertNull(rangeHeaders.get(0));
        assertTrue(rangeHeaders.get(1).matches("bytes=[1-9]\\d*-"), rangeHeaders.get(1));
        assertInstalled(installDir);
    }

    @Test
    void partialFileFromAnEarlierLaunchIsResumed() throws Exception {
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");
        Files.createDirectories(installDir.getParent());
        Files.write(installer.partialFile(), Arrays.copyOf(bundle, bundle.length / 3));

        installer.ensureInstalled();

        assertEquals(List.of("bytes=" + bundle.length / 3 + "-"), rangeHeaders);
        assertInstalled(installDir);
    }

    @Test
    void mirrorIgnoringRangesRestartsFromTheBeginning() throws Exception {
        honourRanges = false;
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");
        Files.createDirectories(installDir.getParent());
        Files.write(installer.partialFile(), Arrays.copyOf(bundle, bundle.length / 2));

        installer.ensureInstalled();

        assertInstalled(installDir);
    }

    @Test
    void completePartialFromAnEarlierLaunchInstallsWithoutRedownloading() throws Exception {
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");
        Files.createDirectories(installDir.getParent());
        Files.write(installer.partialFile(), bundle);

        installer.ensureInstalled();

        assertEquals(List.of("bytes=" + bundle.length + "-"), rangeHeaders);
        assertInstalled(installDir);
    }

    @Test
    void damagedBytesFailTheChecksumBeforeAnythingIsInstalled() throws Exception {
        bundle[bundle.length / 2] ^= 0x5A;
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");

        assertThrows(IOException.class, installer::ensureInstalled);

        assertFalse(Files.exists(installer.partialFile()));
        assertFalse(Files.exists(installDir.resolve("install.lock")));
    }

    @Test
    void corruptArchiveIsDiscardedSoTheNextStartDownloadsAgain() throws Exception {
        bundle = "not a jar".getBytes(StandardCharsets.UTF_8);
        pinnedBundle = bundle;
        Path installDir = installDir();
        GrapheneCefNativeBundleInstaller installer = installer(installDir, "/natives.jar");

        assertThrows(IOException.class, installer::ensureInstalled);

        assertFalse(Files.exists(installer.partialFile()));
        assertFalse(Files.exists(installDir.resolve("install.lock")));
    }

    @Test
    void downloadProgressIsReportedAgainstTheWholeBundle() throws Exception {
        requestsToCut = 1;
        List<Float> progress = Collections.synchronizedList(new ArrayList<>());
        IProgressHandler recorder = (state, percent) -> {
            if (state == EnumProgress.DOWNLOADING && percent >= 0) {
                progress.add(percent);
            }
        };
        Path installDir = installDir();

        new GrapheneCefNativeBundleInstaller(installDir, List.of(url("/natives.jar")), pin(), recorder, false, 1L, 500).ensureInstalled();

        assertEquals(100.0F, progress.getLast(), 0.001F);
        for (int index = 1; index < progress.size(); index++) {
            assertTrue(progress.get(index) >= progress.get(index - 1), "progress went backwards: " + progress);
        }
    }

    private void serveBundle(HttpExchange exchange) throws IOException {
        String range = exchange.getRequestHeaders().getFirst("Range");
        rangeHeaders.add(range);
        int offset = 0;
        if (honourRanges && range != null && range.startsWith("bytes=") && range.endsWith("-")) {
            offset = Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
            if (offset >= bundle.length) {
                exchange.getResponseHeaders().add("Content-Range", "bytes */" + bundle.length);
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Range", "bytes " + offset + "-" + (bundle.length - 1) + "/" + bundle.length);
            exchange.sendResponseHeaders(206, bundle.length - offset);
        } else {
            exchange.sendResponseHeaders(200, bundle.length);
        }

        boolean cut = requestsToCut > 0;
        if (cut) {
            requestsToCut--;
        }
        int end = cut ? offset + (bundle.length - offset) / 2 : bundle.length;
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bundle, offset, end - offset);
            body.flush();
        } catch (IOException ignored) {
            // The client may abandon a deliberately truncated response.
        }
        if (cut) {
            exchange.close();
        }
    }

    private GrapheneCefNativeBundleInstaller installer(Path installDir, String path) {
        return new GrapheneCefNativeBundleInstaller(installDir, List.of(url(path)), pin(), IGNORED_PROGRESS, false, 1L, 500);
    }

    private GrapheneCefNativeBundleDigests.Pin pin() {
        try {
            return new GrapheneCefNativeBundleDigests.Pin(
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pinnedBundle)), pinnedBundle.length);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private String url(String path) {
        return "https://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private Path installDir() {
        return tempDir.resolve("jcef").resolve("143.0.14.15").resolve("linux-amd64");
    }

    private void assertInstalled(Path installDir) throws IOException {
        assertArrayEquals(NATIVE_BYTES, Files.readAllBytes(installDir.resolve(NATIVE_FILE)));
        assertTrue(Files.exists(installDir.resolve("install.lock")));
        assertFalse(Files.exists(installDir.resolveSibling(installDir.getFileName() + ".natives.part")));
    }

    private static byte[] nativeBytes() {
        byte[] bytes = new byte[512 * 1024];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 31 + 7);
        }
        return bytes;
    }

    private static byte[] nativesJar(byte[] nativeBytes) throws IOException {
        ByteArrayOutputStream tarball = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(tarball))) {
            TarArchiveEntry entry = new TarArchiveEntry(NATIVE_FILE);
            entry.setSize(nativeBytes.length);
            tar.putArchiveEntry(entry);
            tar.write(nativeBytes);
            tar.closeArchiveEntry();
        }

        ByteArrayOutputStream jar = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(jar)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("linux-amd64.tar.gz"));
            zip.write(tarball.toByteArray());
            zip.closeEntry();
        }
        return jar.toByteArray();
    }
}
