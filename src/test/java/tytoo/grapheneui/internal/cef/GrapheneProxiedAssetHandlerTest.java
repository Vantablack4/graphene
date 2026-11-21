package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GrapheneProxiedAssetHandlerTest {
    @Test
    void onlyAnUpstreamOkIsPassedOnEverythingElseIsALocalError() {
        assertEquals(200, GrapheneProxiedAssetHandler.localStatusFor(200));
        assertEquals(404, GrapheneProxiedAssetHandler.localStatusFor(404));
        for (int upstream : new int[]{201, 204, 206, 301, 302, 304, 307, 401, 403, 410, 500, 503}) {
            assertEquals(502, GrapheneProxiedAssetHandler.localStatusFor(upstream), "upstream " + upstream);
        }
    }

    @Test
    void bodiesAreCappedByTheirDeclaredAndTheirRealLength() throws IOException {
        byte[] small = {1, 2, 3};
        assertArrayEquals(small, GrapheneProxiedAssetHandler.readCapped(new ByteArrayInputStream(small), small.length));
        assertArrayEquals(small, GrapheneProxiedAssetHandler.readCapped(new ByteArrayInputStream(small), -1L));
        assertNull(GrapheneProxiedAssetHandler.readCapped(new ByteArrayInputStream(small), GrapheneProxiedAssetHandler.MAX_BODY_BYTES + 1L));
        assertNull(GrapheneProxiedAssetHandler.readCapped(endless(), -1L));
        assertNull(GrapheneProxiedAssetHandler.readCapped(endless(), 10L));
    }

    private static InputStream endless() {
        return new InputStream() {
            @Override
            public int read() {
                return 7;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                Arrays.fill(buffer, offset, offset + length, (byte) 7);
                return length;
            }
        };
    }
}
