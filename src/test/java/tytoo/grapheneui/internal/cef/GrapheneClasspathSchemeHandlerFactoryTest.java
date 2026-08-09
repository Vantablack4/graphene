package tytoo.grapheneui.internal.cef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class GrapheneClasspathSchemeHandlerFactoryTest {
    @Test
    void foundAppResourcesAllowOnlyChromiumsOpaqueOrigin() {
        assertEquals(
                "null",
                GrapheneClasspathSchemeHandlerFactory.opaqueCorsOriginFor(
                        "app://assets/example/page.mjs",
                        true
                )
        );
    }

    @Test
    void missingAndClasspathResourcesDoNotReceiveOpaqueOriginCors() {
        assertNull(GrapheneClasspathSchemeHandlerFactory.opaqueCorsOriginFor(
                "app://assets/example/missing.mjs",
                false
        ));
        assertNull(GrapheneClasspathSchemeHandlerFactory.opaqueCorsOriginFor(
                "classpath://example/page.mjs",
                true
        ));
    }
}
