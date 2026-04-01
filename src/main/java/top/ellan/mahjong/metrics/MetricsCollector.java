package top.ellan.mahjong.metrics;

public interface MetricsCollector {
    default void incrementCounter(String name) {
        this.incrementCounter(name, 1L);
    }

    void incrementCounter(String name, long delta);

    void recordTimerNanos(String name, long nanos);

    void recordGauge(String name, long value);
}
