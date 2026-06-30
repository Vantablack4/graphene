package tytoo.grapheneui.api.nativeui;

/**
 * Runtime state for native Minecraft render slots owned by a browser surface.
 *
 * <p>Slots are normally created from JavaScript through {@code globalThis.grapheneNativeSlots}.
 * This API exposes lifecycle and pointer state needed by Java surfaces and widgets.</p>
 */
@SuppressWarnings("unused")
public interface GrapheneNativeSlots extends AutoCloseable {
    void clear();

    void clearPageSlots();

    int size();

    default boolean hasSlots() {
        return size() > 0;
    }

    void setPointer(int mouseX, int mouseY);

    void clearPointer();

    @Override
    void close();
}
