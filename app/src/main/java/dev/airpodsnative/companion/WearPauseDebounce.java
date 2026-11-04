package dev.airpodsnative.companion;

import java.util.function.LongConsumer;

/** Coordinate a pending wear deadline with a scheduler that can wake slightly early.
 * Handler uses uptime while wear policy deadlines use elapsed realtime. A callback
 * must retain a still-pending deadline, never consume it early or lose its retry. */
public final class WearPauseDebounce {
    private WearPauseDebounce() {}

    public static boolean consumeOrReschedule(WearPausePolicy policy, boolean active,
                                               long now, LongConsumer schedule) {
        if (!active) return false;
        if (policy.consume(now)) return true;
        long remaining = policy.delay(now);
        if (remaining >= 0) schedule.accept(Math.max(1, remaining));
        return false;
    }
}
