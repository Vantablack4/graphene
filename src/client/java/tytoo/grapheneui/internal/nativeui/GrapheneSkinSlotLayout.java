package tytoo.grapheneui.internal.nativeui;

record GrapheneSkinSlotLayout(float scale, int top, int bottom) {
    private static final float PLAYER_MODEL_CENTER_FROM_BOTTOM = 1.101F;
    private static final float PLAYER_MODEL_FIT_WIDTH = 1.8F;
    private static final float PLAYER_MODEL_FIT_HEIGHT = 2.4F;

    static GrapheneSkinSlotLayout contain(GrapheneNativeSlotScreenRect bounds, float alignY) {
        return contain(bounds, alignY, containScale(bounds));
    }

    static GrapheneSkinSlotLayout contain(GrapheneNativeSlotScreenRect bounds, float alignY, float scale) {
        float clampedScale = Math.max(1.0F, scale);
        float clampedAlignY = Math.clamp(alignY, 0.0F, 1.0F);
        float modelCenter = bounds.y() + bounds.height() * clampedAlignY;
        int bottom = Math.round(modelCenter + clampedScale * PLAYER_MODEL_CENTER_FROM_BOTTOM);
        return new GrapheneSkinSlotLayout(clampedScale, bottom - bounds.height(), bottom);
    }

    static float containScale(GrapheneNativeSlotScreenRect bounds) {
        return Math.max(
                1.0F,
                Math.min(bounds.width() / PLAYER_MODEL_FIT_WIDTH, bounds.height() / PLAYER_MODEL_FIT_HEIGHT)
        );
    }

    static GrapheneSkinSlotLayout legacy(GrapheneNativeSlotScreenRect bounds) {
        float scale = Math.max(1.0F, Math.min(bounds.width(), bounds.height()) * 0.55F);
        return new GrapheneSkinSlotLayout(scale, bounds.y(), bounds.bottom());
    }
}
