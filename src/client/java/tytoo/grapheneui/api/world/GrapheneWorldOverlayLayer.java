package tytoo.grapheneui.api.world;

import net.minecraft.resources.Identifier;
import tytoo.grapheneui.api.bridge.GrapheneBridge;
import tytoo.grapheneui.api.nativeui.GrapheneNativeSlots;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.BrowserSurfaceInputAdapter;

import java.util.Collection;

@SuppressWarnings("unused")
public interface GrapheneWorldOverlayLayer extends AutoCloseable {
    Identifier elementId();

    BrowserSurface surface();

    GrapheneBridge bridge();

    GrapheneNativeSlots nativeSlots();

    BrowserSurfaceInputAdapter inputAdapter();

    void updateAnchors(Collection<GrapheneWorldAnchor> anchors);

    void upsertAnchor(GrapheneWorldAnchor anchor);

    void removeAnchor(String id);

    void clearAnchors();

    int anchorCount();

    int visibleAnchorCount();

    boolean hasVisibleAnchors();

    @Override
    void close();
}
