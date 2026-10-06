package tytoo.grapheneui.internal.cef.startup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;
import tytoo.grapheneui.internal.mc.McClient;

import java.util.Objects;

public final class GrapheneNativeDownloadToast implements Toast {
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/system");
    private static final Object TOKEN = new Object();
    private static final int WIDTH = 180;
    private static final int HEIGHT = 40;
    private static final int TEXT_LEFT = 18;
    private static final int RIGHT_PADDING = 10;
    private static final int TITLE_TOP = 7;
    private static final int DETAIL_TOP = 18;
    private static final int BAR_TOP = 30;
    private static final int BAR_HEIGHT = 3;
    private static final int TITLE_COLOR = ARGB.color(255, 255, 255, 0);
    private static final int DETAIL_COLOR = ARGB.color(255, 255, 255, 255);
    private static final int BAR_BACKGROUND_COLOR = ARGB.color(255, 40, 40, 46);
    private static final int BAR_FILL_COLOR = ARGB.color(255, 76, 175, 80);

    private final GrapheneNativeDownloadState state;
    private Visibility wantedVisibility = Visibility.SHOW;

    private GrapheneNativeDownloadToast(GrapheneNativeDownloadState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public static void show(GrapheneNativeDownloadState state) {
        if (!state.isActive()) {
            return;
        }

        ToastManager toastManager = McClient.mc().getToastManager();
        if (toastManager.getToast(GrapheneNativeDownloadToast.class, TOKEN) == null) {
            toastManager.addToast(new GrapheneNativeDownloadToast(state));
        }
    }

    @Override
    public @NonNull Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(@NonNull ToastManager manager, long fullyVisibleForMs) {
        wantedVisibility = state.isActive() ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, long fullyVisibleForMs) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, WIDTH, HEIGHT);
        graphics.text(font, GrapheneStartupText.DOWNLOADING, TEXT_LEFT, TITLE_TOP, TITLE_COLOR, false);
        graphics.text(font, GrapheneStartupText.downloadProgress(state), TEXT_LEFT, DETAIL_TOP, DETAIL_COLOR, false);

        int barRight = WIDTH - RIGHT_PADDING;
        int filledWidth = Math.round((barRight - TEXT_LEFT) * state.progress());
        graphics.fill(TEXT_LEFT, BAR_TOP, barRight, BAR_TOP + BAR_HEIGHT, BAR_BACKGROUND_COLOR);
        if (filledWidth > 0) {
            graphics.fill(TEXT_LEFT, BAR_TOP, TEXT_LEFT + filledWidth, BAR_TOP + BAR_HEIGHT, BAR_FILL_COLOR);
        }
    }

    @Override
    public @NonNull Object getToken() {
        return TOKEN;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }
}
