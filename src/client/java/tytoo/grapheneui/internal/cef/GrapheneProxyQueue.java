package tytoo.grapheneui.internal.cef;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Hands out at most {@code maxRunning} slots for proxied fetches and lets up to {@code maxQueued} more requests wait
 * in arrival order. Only a request that finds the queue full is turned away. Every grant runs on the starter
 * executor, never on the caller's thread, and each slot has exactly one owner: the {@link Ticket} it was granted to,
 * until that ticket is released.
 */
final class GrapheneProxyQueue {
    private final int maxRunning;
    private final int maxQueued;
    private final Executor starter;
    private final ArrayDeque<Ticket> waiting = new ArrayDeque<>();
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
     * Asks for a slot. When one is free, or later when one frees up, {@code onGranted} runs on the starter executor
     * with the ticket that now owns the slot. Returns that ticket, or {@code null} when the queue is full.
     */
    Ticket submit(Consumer<Ticket> onGranted) {
        Ticket ticket = new Ticket(Objects.requireNonNull(onGranted, "onGranted"));
        synchronized (this) {
            if (running < maxRunning) {
                running++;
                peakRunning = Math.max(peakRunning, running);
                ticket.granted = true;
            } else if (waiting.size() < maxQueued) {
                waiting.addLast(ticket);
                peakWaiting = Math.max(peakWaiting, waiting.size());
                return ticket;
            } else {
                return null;
            }
        }

        starter.execute(ticket::grant);
        return ticket;
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

    private void release(Ticket ticket) {
        Ticket next;
        synchronized (this) {
            if (ticket.released) {
                return;
            }

            ticket.released = true;
            if (!ticket.granted) {
                waiting.remove(ticket);
                return;
            }

            next = waiting.pollFirst();
            if (next == null) {
                running--;
                return;
            }

            next.granted = true;
        }

        starter.execute(next::grant);
    }

    /**
     * One request's place: waiting, or holding a slot. {@link #release()} gives it up exactly once, from any thread;
     * a waiting ticket simply leaves the queue, a granted one hands its slot to the next waiting request.
     */
    final class Ticket {
        private final Consumer<Ticket> onGranted;
        private boolean granted;
        private boolean released;

        private Ticket(Consumer<Ticket> onGranted) {
            this.onGranted = onGranted;
        }

        void release() {
            GrapheneProxyQueue.this.release(this);
        }

        private void grant() {
            onGranted.accept(this);
        }
    }
}
