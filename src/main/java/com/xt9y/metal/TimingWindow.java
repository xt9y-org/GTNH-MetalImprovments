package com.xt9y.metal;

public final class TimingWindow {

    private static final int WINDOW = 8192;
    private static final int BUCKETS = 2049;
    private static final long BUCKET_NANOS = 100_000L;

    public static final class Snapshot {
        public final int samples;
        public final double avgMillis;
        public final double p95Millis;
        public final double p99Millis;
        public final double maxMillis;
        public final int slowFrames;

        private Snapshot(int samples, double avgMillis, double p95Millis, double p99Millis,
            double maxMillis, int slowFrames) {
            this.samples = samples;
            this.avgMillis = avgMillis;
            this.p95Millis = p95Millis;
            this.p99Millis = p99Millis;
            this.maxMillis = maxMillis;
            this.slowFrames = slowFrames;
        }
    }

    private final long[] samples = new long[WINDOW];
    private final int[] buckets = new int[BUCKETS];
    private int count;
    private int next;
    private long sum;
    private int over33ms;

    public void record(long nanos) {
        if (nanos <= 0 || nanos > 1_000_000_000L) return;
        if (count == WINDOW) {
            final long old = samples[next];
            sum -= old;
            buckets[bucket(old)]--;
            if (old >= 33_333_333L) over33ms--;
        } else {
            count++;
        }
        samples[next] = nanos;
        next = (next + 1) % WINDOW;
        sum += nanos;
        buckets[bucket(nanos)]++;
        if (nanos >= 33_333_333L) over33ms++;
    }

    public int samples() {
        return count;
    }

    private static int bucket(long nanos) {
        return (int) Math.min(nanos / BUCKET_NANOS, BUCKETS - 1);
    }

    private double percentile(double fraction) {
        if (count == 0) return 0.0;
        final int rank = (int) Math.ceil(fraction * count);
        int accumulated = 0;
        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i];
            if (accumulated >= rank) return (i + 0.5) * (BUCKET_NANOS / 1_000_000.0);
        }
        return 0.0;
    }

    public Snapshot snapshot() {
        if (count == 0) return new Snapshot(0, 0, 0, 0, 0, 0);
        long maximum = 0L;
        for (int i = 0; i < count; i++) {
            maximum = Math.max(maximum, samples[i]);
        }
        return new Snapshot(count, sum / (count * 1_000_000.0), percentile(0.95),
            percentile(0.99), maximum / 1_000_000.0, over33ms);
    }
}
