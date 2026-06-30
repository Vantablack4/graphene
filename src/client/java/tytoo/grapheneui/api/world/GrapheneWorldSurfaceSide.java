package tytoo.grapheneui.api.world;

@SuppressWarnings("unused")
public enum GrapheneWorldSurfaceSide {
    FRONT_ONLY,
    BACK_ONLY,
    /**
     * @deprecated Use {@link #DOUBLE_SIDED_READABLE}. This compatibility alias no longer renders mirrored text.
     */
    @Deprecated(forRemoval = true)
    DOUBLE_SIDED_MIRRORED,
    DOUBLE_SIDED_READABLE
}
