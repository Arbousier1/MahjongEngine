package top.ellan.mahjong.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class InMemoryMetricsCollector implements MetricsCollector {
    private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TimerStats> timers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    @Override
    public void incrementCounter(String name, long delta) {
        if (name == null || name.isBlank() || delta == 0L) {
            return;
        }
        this.counters.computeIfAbsent(name, ignored -> new LongAdder()).add(delta);
    }

    @Override
    public void recordTimerNanos(String name, long nanos) {
        if (name == null || name.isBlank() || nanos < 0L) {
            return;
        }
        this.timers.computeIfAbsent(name, ignored -> new TimerStats()).record(nanos);
    }

    @Override
    public void recordGauge(String name, long value) {
        if (name == null || name.isBlank()) {
            return;
        }
        this.gauges.computeIfAbsent(name, ignored -> new AtomicLong()).set(value);
    }

    public long counterValue(String name) {
        LongAdder counter = this.counters.get(name);
        return counter == null ? 0L : counter.sum();
    }

    public long timerCount(String name) {
        TimerStats timer = this.timers.get(name);
        return timer == null ? 0L : timer.count();
    }

    public long timerTotalNanos(String name) {
        TimerStats timer = this.timers.get(name);
        return timer == null ? 0L : timer.totalNanos();
    }

    public long timerMaxNanos(String name) {
        TimerStats timer = this.timers.get(name);
        return timer == null ? 0L : timer.maxNanos();
    }

    public long gaugeValue(String name) {
        AtomicLong gauge = this.gauges.get(name);
        return gauge == null ? 0L : gauge.get();
    }

    private static final class TimerStats {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private void record(long nanos) {
            this.count.increment();
            this.totalNanos.add(nanos);
            this.maxNanos.accumulateAndGet(nanos, Math::max);
        }

        private long count() {
            return this.count.sum();
        }

        private long totalNanos() {
            return this.totalNanos.sum();
        }

        private long maxNanos() {
            return this.maxNanos.get();
        }
    }
}
