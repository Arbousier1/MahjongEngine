package top.ellan.mahjong.metrics;

public final class NoopMetricsCollector implements MetricsCollector {
    private static final NoopMetricsCollector INSTANCE = new NoopMetricsCollector();

    private NoopMetricsCollector() {
    }

    public static NoopMetricsCollector instance() {
        return INSTANCE;
    }

    @Override
    public void incrementCounter(String name, long delta) {
    }

    @Override
    public void recordTimerNanos(String name, long nanos) {
    }

    @Override
    public void recordGauge(String name, long value) {
    }
}
