package tytoo.grapheneui.internal.cef.startup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import tytoo.grapheneui.internal.cef.GrapheneCefRuntime;
import tytoo.grapheneui.internal.core.GrapheneCoreServices;
import tytoo.grapheneui.internal.mc.McClient;

public final class GrapheneStartupPlaceholder {
    private static final int BACKGROUND_COLOR = ARGB.color(168, 10, 10, 14);
    private static final int TITLE_COLOR = ARGB.color(255, 240, 240, 240);
    private static final int DETAIL_COLOR = ARGB.color(255, 170, 170, 176);
    private static final int FAILURE_COLOR = ARGB.color(255, 230, 120, 110);
    private static final int BAR_BACKGROUND_COLOR = ARGB.color(255, 46, 46, 54);
    private static final int BAR_FILL_COLOR = ARGB.color(255, 76, 175, 80);
    private static final int MAX_BAR_WIDTH = 160;
    private static final int SIDE_PADDING = 12;
    private static final int LINE_GAP = 4;
    private static final int BAR_GAP = 6;
    private static final int BAR_HEIGHT = 2;
    private static final long DOT_STEP_MILLIS = 400L;
    private static final String[] DOTS = {"", ".", "..", "..."};

    private GrapheneStartupPlaceholder() {
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean failed) {
        if (width <= 0 || height <= 0) {
            return;
        }

        Font font = McClient.mc().font;
        int centerX = x + width / 2;
        graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        if (failed) {
            graphics.centeredText(font, GrapheneStartupText.FAILED, centerX, y + (height - font.lineHeight) / 2, FAILURE_COLOR);
            return;
        }

        GrapheneCefRuntime runtime = GrapheneCoreServices.get().runtimeInternal();
        GrapheneNativeDownloadState downloadState = runtime.nativeDownloadState();
        Component detail = null;
        int detailColor = DETAIL_COLOR;
        boolean showBar = false;
        if (downloadState.isActive()) {
            detail = Component.empty()
                    .append(GrapheneStartupText.DOWNLOADING)
                    .append(" ")
                    .append(GrapheneStartupText.downloadProgress(downloadState));
            showBar = true;
        } else if (runtime.hasFailedStartup()) {
            detail = GrapheneStartupText.RETRYING;
            detailColor = FAILURE_COLOR;
        }

        int lineHeight = font.lineHeight;
        boolean fitsDetail = detail != null && height >= lineHeight * 2 + LINE_GAP + BAR_GAP + BAR_HEIGHT;
        int blockHeight = fitsDetail ? lineHeight * 2 + LINE_GAP + (showBar ? BAR_GAP + BAR_HEIGHT : 0) : lineHeight;
        int top = y + (height - blockHeight) / 2;
        drawTitle(graphics, font, centerX, top);
        if (!fitsDetail) {
            return;
        }

        int detailTop = top + lineHeight + LINE_GAP;
        graphics.centeredText(font, detail, centerX, detailTop, detailColor);
        if (!showBar) {
            return;
        }

        int barWidth = Math.min(MAX_BAR_WIDTH, width - SIDE_PADDING * 2);
        if (barWidth <= 0) {
            return;
        }

        int barLeft = centerX - barWidth / 2;
        int barTop = detailTop + lineHeight + BAR_GAP;
        int filledWidth = Math.round(barWidth * downloadState.progress());
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + BAR_HEIGHT, BAR_BACKGROUND_COLOR);
        if (filledWidth > 0) {
            graphics.fill(barLeft, barTop, barLeft + filledWidth, barTop + BAR_HEIGHT, BAR_FILL_COLOR);
        }
    }

    private static void drawTitle(GuiGraphicsExtractor graphics, Font font, int centerX, int top) {
        Component title = GrapheneStartupText.PREPARING;
        int titleLeft = centerX - font.width(title) / 2;
        String dots = DOTS[(int) ((System.currentTimeMillis() / DOT_STEP_MILLIS) % DOTS.length)];
        graphics.text(font, title, titleLeft, top, TITLE_COLOR, false);
        graphics.text(font, dots, titleLeft + font.width(title), top, TITLE_COLOR, false);
    }
}
