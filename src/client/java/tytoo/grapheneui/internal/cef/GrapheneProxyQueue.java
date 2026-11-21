package tytoo.grapheneui.internal.cef;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Runs at most {@code maxRunning} proxied fetches at once and lets up to {@code maxQueued} more wait their turn in
 * arrival order. Only a request that finds the queue full is turned away.
 */
final class GrapheneProxyQueue {
    private final int maxRunning;
    private final int maxQueued;
    private final Executor starter;
    private final ArrayDeque<Runnable> waiting = new ArrayDeque<>();
    private int running;
    private int peakRunning;
    private int peakWaiting;

    GrapheneProxyQueue(int maxRunning, int maxQueued, Executor starter) {
        if (maxRunning < 1 || maxQueued < 0) {
            throw new IllegalArgumentException("maxRunning must be > 0 and maxQueued >= 0");
        }

        this.maxRunning = maxRunning;
        this.maxQueued = maxQueued;
        this.starter = Objects.requireNonNull(starter, "starter");
    }

    /**
     * Starts {@code fetch} now when a slot is free, queues it otherwise, and returns {@code false} only when the queue
     * is full too. A started fetch owns a slot until {@link #release()}.
     */
    boolean submit(Runnable fetch) {
        Objects.requireNonNull(fetch, "fetch");
        synchronized (this) {
            if (running >= maxRunning) {
                if (waiting.size() >= maxQueued) {
                    return false;
                }

                waiting.addLast(fetch);
                peakWaiting = Math.max(peakWaiting, waiting.size());
                return true;
            }

            running++;
            peakRunning = Math.max(peakRunning, running);
        }

        fetch.run();
        return true;
    }

    /**
     * Takes a fetch that has not started out of the queue. Returns {@code false} when it was not waiting.
     */
    synchronized boolean remove(Runnable fetch) {
        return waiting.remove(fetch);
    }

    /**
     * Frees a slot and starts the next waiting fetch, if any, on the starter executor.
     */
    void release() {
        Runnable next;
        synchronized (this) {
            next = waiting.pollFirst();
            if (next == null) {
                running = Math.max(0, running - 1);
                return;
            }
        }

        starter.execute(next);
    }

    synchronized int running() {
        return running;
    }

    synchronized int waiting() {
        return waiting.size();
    }

    synchronized int peakRunning() {
        return peakRunning;
    }

    synchronized int peakWaiting() {
        return peakWaiting;
    }
}
