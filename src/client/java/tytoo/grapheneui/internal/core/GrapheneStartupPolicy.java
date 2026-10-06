package tytoo.grapheneui.internal.core;

import tytoo.grapheneui.internal.mc.McClient;

public final class GrapheneStartupPolicy {
    public static final String AWAIT_STARTUP_PROPERTY = "graphene.surface.awaitStartup";
    private static final String CLIENT_GAMETEST_PROPERTY = "fabric.client.gametest";

    private GrapheneStartupPolicy() {
    }

    public static boolean awaitsStartupOnCurrentThread() {
        String configured = System.getProperty(AWAIT_STARTUP_PROPERTY);
        if (configured != null) {
            return Boolean.parseBoolean(configured);
        }

        return System.getProperty(CLIENT_GAMETEST_PROPERTY) != null || !McClient.isOnMainThread();
    }
}
