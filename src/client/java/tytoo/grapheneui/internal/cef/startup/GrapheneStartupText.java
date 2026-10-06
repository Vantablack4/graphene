package tytoo.grapheneui.internal.cef.startup;

import net.minecraft.network.chat.Component;

public final class GrapheneStartupText {
    public static final Component PREPARING = Component.translatableWithFallback(
            "graphene-ui.startup.preparing",
            "Arayüz hazırlanıyor"
    );
    public static final Component DOWNLOADING = Component.translatableWithFallback(
            "graphene-ui.startup.downloading",
            "Arayüz dosyaları indiriliyor"
    );
    public static final Component INSTALLING = Component.translatableWithFallback(
            "graphene-ui.startup.installing",
            "Arayüz kuruluyor"
    );
    public static final Component RETRYING = Component.translatableWithFallback(
            "graphene-ui.startup.retrying",
            "Arayüz yüklenemedi, yeniden deneniyor"
    );
    public static final Component FAILED = Component.translatableWithFallback(
            "graphene-ui.startup.failed",
            "Arayüz açılamadı"
    );

    private GrapheneStartupText() {
    }

    public static Component downloadProgress(GrapheneNativeDownloadState state) {
        if (state.isPostDownloadWork()) {
            return INSTALLING;
        }

        return Component.translatableWithFallback(
                "graphene-ui.startup.downloading.percent",
                "%%%s",
                Math.round(state.progress() * 100.0F)
        );
    }
}
