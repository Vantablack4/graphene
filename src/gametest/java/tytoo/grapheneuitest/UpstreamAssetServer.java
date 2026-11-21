package tytoo.grapheneuitest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the CDN behind Graphene's asset proxy: a loopback HTTP server with a catalog, a red PNG, a page that
 * must never be fetched, an error and a redirect. It records every request it gets.
 */
final class UpstreamAssetServer {
    static final String CATALOG_JSON = "{\"items\":[\"pixel.png\"]}";
    private static final String BASE_PATH = "/upstream/";

    private final HttpServer server;
    private final byte[] pixelPng;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private UpstreamAssetServer(HttpServer server, byte[] pixelPng) {
        this.server = server;
        this.pixelPng = pixelPng;
    }

    static UpstreamAssetServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            UpstreamAssetServer upstream = new UpstreamAssetServer(server, redPng());
            server.createContext(BASE_PATH, upstream::handle);
            server.start();
            return upstream;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + BASE_PATH;
    }

    List<String> requests() {
        return requests;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath().substring(BASE_PATH.length());
        String query = exchange.getRequestURI().getRawQuery();
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        requests.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query) + (cookie == null ? "" : " cookie=" + cookie));
        switch (path) {
            case "catalog.json" -> send(exchange, 200, "text/html", CATALOG_JSON.getBytes(StandardCharsets.UTF_8));
            case "pixel.png" -> send(exchange, 200, "text/html", pixelPng);
            case "page.html" -> send(exchange, 200, "text/html", "<script>cefQuery({})</script>".getBytes(StandardCharsets.UTF_8));
            case "moved.png" -> {
                exchange.getResponseHeaders().add("Location", "/upstream/pixel.png");
                send(exchange, 302, "text/plain", new byte[0]);
            }
            default -> send(exchange, 500, "text/plain", "upstream failure".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static byte[] redPng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                image.setRGB(x, y, 0xFFFF0000);
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
