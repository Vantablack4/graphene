package tytoo.grapheneui.internal.cef;

/**
 * The bytes all proxied bodies may hold at once, while they are read and until CEF has taken them. A request reserves
 * its share before it reads anything and gives it back when its body is dropped.
 */
final class GrapheneProxyBodyBudget {
    private final long limitBytes;
    private long usedBytes;

    GrapheneProxyBodyBudget(long limitBytes) {
        if (limitBytes <= 0) {
            throw new IllegalArgumentException("limitBytes must be > 0");
        }

        this.limitBytes = limitBytes;
    }

    synchronized boolean tryReserve(long bytes) {
        if (bytes < 0 || usedBytes + bytes > limitBytes) {
            return false;
        }

        usedBytes += bytes;
        return true;
    }

    synchronized void release(long bytes) {
        usedBytes = Math.max(0, usedBytes - bytes);
    }

    synchronized long usedBytes() {
        return usedBytes;
    }
}
