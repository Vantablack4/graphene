package tytoo.grapheneui.internal.browser;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;
import tytoo.grapheneui.api.surface.BrowserSurface;
import tytoo.grapheneui.api.surface.GrapheneLoadListener;
import tytoo.grapheneui.internal.event.GrapheneLoadEventBus;

import java.util.*;

public final class BrowserSurfaceLoadListenerScope implements AutoCloseable {
    private static final String LOAD_LISTENER_NAME = "loadListener";

    private final GrapheneLoadEventBus loadEventBus;
    private final Map<GrapheneLoadListener, GrapheneLoadListener> wrappedListenersByListener = new IdentityHashMap<>();
    private volatile GrapheneBrowser browser;
    private boolean closed;

    public BrowserSurfaceLoadListenerScope(GrapheneBrowser browser, GrapheneLoadEventBus loadEventBus) {
        this(loadEventBus);
        this.browser = Objects.requireNonNull(browser, "browser");
    }

    public BrowserSurfaceLoadListenerScope(GrapheneLoadEventBus loadEventBus) {
        this.loadEventBus = Objects.requireNonNull(loadEventBus, "loadEventBus");
    }

    public void bindBrowser(GrapheneBrowser browser) {
        this.browser = Objects.requireNonNull(browser, "browser");
    }

    public BrowserSurface.Subscription subscribe(GrapheneLoadListener loadListener) {
        add(loadListener);
        return () -> remove(loadListener);
    }

    public void add(GrapheneLoadListener loadListener) {
        Objects.requireNonNull(loadListener, LOAD_LISTENER_NAME);

        GrapheneLoadListener wrappedListener = createScopedLoadListener(loadListener);
        GrapheneLoadListener previousWrappedListener;
        synchronized (wrappedListenersByListener) {
            if (closed) {
                throw new IllegalStateException("Cannot add load listener to a closed BrowserSurface");
            }

            previousWrappedListener = wrappedListenersByListener.put(loadListener, wrappedListener);
        }

        if (previousWrappedListener != null) {
            loadEventBus.unregister(previousWrappedListener);
        }

        loadEventBus.register(wrappedListener);
    }

    public void remove(GrapheneLoadListener loadListener) {
        Objects.requireNonNull(loadListener, LOAD_LISTENER_NAME);

        GrapheneLoadListener wrappedListener;
        synchronized (wrappedListenersByListener) {
            wrappedListener = wrappedListenersByListener.remove(loadListener);
        }

        if (wrappedListener != null) {
            loadEventBus.unregister(wrappedListener);
        }
    }

    @Override
    public void close() {
        List<GrapheneLoadListener> listenersToUnregister;
        synchronized (wrappedListenersByListener) {
            if (closed) {
                return;
            }

            closed = true;
            listenersToUnregister = new ArrayList<>(wrappedListenersByListener.values());
            wrappedListenersByListener.clear();
        }

        for (GrapheneLoadListener listener : listenersToUnregister) {
            loadEventBus.unregister(listener);
        }
    }

    private GrapheneLoadListener createScopedLoadListener(GrapheneLoadListener loadListener) {
        return new GrapheneLoadListener() {
            @Override
            public void onLoadingStateChange(
                    CefBrowser eventBrowser,
                    boolean isLoading,
                    boolean canGoBack,
                    boolean canGoForward
            ) {
                if (eventBrowser == browser && eventBrowser != null) {
                    loadListener.onLoadingStateChange(eventBrowser, isLoading, canGoBack, canGoForward);
                }
            }

            @Override
            public void onLoadStart(
                    CefBrowser eventBrowser,
                    CefFrame frame,
                    CefRequest.TransitionType transitionType
            ) {
                if (eventBrowser == browser && eventBrowser != null) {
                    loadListener.onLoadStart(eventBrowser, frame, transitionType);
                }
            }

            @Override
            public void onLoadEnd(CefBrowser eventBrowser, CefFrame frame, int httpStatusCode) {
                if (eventBrowser == browser && eventBrowser != null) {
                    loadListener.onLoadEnd(eventBrowser, frame, httpStatusCode);
                }
            }

            @Override
            public void onLoadError(
                    CefBrowser eventBrowser,
                    CefFrame frame,
                    CefLoadHandler.ErrorCode errorCode,
                    String errorText,
                    String failedUrl
            ) {
                if (eventBrowser == browser && eventBrowser != null) {
                    loadListener.onLoadError(eventBrowser, frame, errorCode, errorText, failedUrl);
                }
            }
        };
    }
}
