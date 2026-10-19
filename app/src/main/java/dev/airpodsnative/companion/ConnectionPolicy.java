package dev.airpodsnative.companion;

import java.util.Locale;

/** Pure JVM / Android-independent session policy, testable without hardware. */
public final class ConnectionPolicy {
    private String requested;
    private long generation;
    private int failures;
    private static final long[] DELAYS_MS = {1000, 2000, 4000, 8000, 16000, 30000};
    public synchronized long select(String address) {
        if (!DeviceAddress.valid(address)) throw new IllegalArgumentException("Invalid address");
        if (address.equalsIgnoreCase(requested)) return generation;
        requested = address.toUpperCase(Locale.ROOT);
        failures = 0;
        return ++generation;
    }
    public synchronized long stop() {
        requested = null;
        failures = 0;
        return ++generation;
    }
    public synchronized boolean current(long token, String address) {
        return token == generation && requested != null && requested.equalsIgnoreCase(address);
    }
    public synchronized long nextDelay(long token, String address) {
        if (!current(token, address)) return -1;
        long wait = DELAYS_MS[Math.min(failures, DELAYS_MS.length - 1)];
        failures++;
        return wait;
    }
    public synchronized void succeeded(long token, String address) {
        if (current(token, address)) failures = 0;
    }
    public synchronized long generation() { return generation; }
    public synchronized String target() { return requested; }
    public synchronized int failures() { return failures; }

    public static final class DeviceAddress {
        private DeviceAddress() {}
        public static boolean valid(String address) {
            return address != null && address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}");
        }
    }
}
