package tytoo.grapheneui.internal.browser;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import java.util.Objects;

public final class GraphenePageDefaults {
    private static final String TEXT_SELECTION_STYLE_ID = "graphene-default-text-selection";
    private static final String DISABLE_TEXT_SELECTION_SCRIPT = """
            (() => {
                const styleId = "%s";
                if (document.getElementById(styleId)) {
                    return;
                }

                const style = document.createElement("style");
                style.id = styleId;
                style.textContent = `
                    :where(html, body, body *) {
                        -webkit-user-select: none;
                        user-select: none;
                    }

                    :where(input, textarea, [contenteditable]:not([contenteditable="false"]),
                           [contenteditable]:not([contenteditable="false"]) *) {
                        -webkit-user-select: text;
                        user-select: text;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            })();
            """.formatted(TEXT_SELECTION_STYLE_ID);

    private GraphenePageDefaults() {
    }

    public static void disableTextSelection(CefBrowser browser, CefFrame frame) {
        CefFrame targetFrame = frame == null ? browser.getMainFrame() : frame;
        if (targetFrame == null || !targetFrame.isValid()) {
            return;
        }

        String frameUrl = Objects.requireNonNullElse(targetFrame.getURL(), "about:blank");
        targetFrame.executeJavaScript(DISABLE_TEXT_SELECTION_SCRIPT, frameUrl, 0);
    }
}
