package top.ellan.mahjong.gb.jni;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class GbNativeWarmupService {
    private static final Logger LOGGER = Logger.getLogger(GbNativeWarmupService.class.getName());
    private static volatile WarmupReport lastReport;

    private GbNativeWarmupService() {
    }

    public static synchronized WarmupReport warmupOnce(Logger logger) {
        if (lastReport != null) {
            return lastReport;
        }
        Logger targetLogger = logger == null ? LOGGER : logger;
        WarmupReport report = warmup(new GbMahjongNativeBridge());
        lastReport = report;
        if (!report.available()) {
            targetLogger.warning("[GB-Native] Warmup skipped: " + report.detail());
            return report;
        }
        targetLogger.info(
            "[GB-Native] Warmup completed totalNanos=" + report.totalNanos()
                + " versionNanos=" + report.versionNanos()
                + " pingNanos=" + report.pingNanos()
        );
        targetLogger.info(
            "[GB-Native] First-call benchmark(ns) fan=" + report.fanFirstNanos()
                + " ting=" + report.tingFirstNanos()
                + " win=" + report.winFirstNanos()
                + " | warm-call(ns) fan=" + report.fanWarmNanos()
                + " ting=" + report.tingWarmNanos()
                + " win=" + report.winWarmNanos()
        );
        if (!report.fanSuccess() || !report.tingSuccess() || !report.winSuccess()) {
            targetLogger.warning("[GB-Native] Warmup call failures: " + report.errorSummary());
        }
        return report;
    }

    public static WarmupReport lastReport() {
        return lastReport;
    }

    static WarmupReport warmup(GbMahjongNativeBridge bridge) {
        Objects.requireNonNull(bridge, "bridge");
        long startedAt = System.nanoTime();
        boolean available = bridge.isAvailable();
        String detail = bridge.availabilityDetail();
        if (!available) {
            return new WarmupReport(
                false,
                detail,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                false,
                false,
                false,
                "",
                System.nanoTime() - startedAt
            );
        }

        TimedCall versionCall = timedCall(bridge::libraryVersion);
        TimedCall pingCall = timedCall(bridge::ping);

        PairedCall fanCall = pairedCall(() -> bridge.evaluateFan(sampleFanRequest()));
        PairedCall tingCall = pairedCall(() -> bridge.evaluateTing(sampleTingRequest()));
        PairedCall winCall = pairedCall(() -> bridge.evaluateWin(sampleWinRequest()));

        String errorSummary = mergeErrors(fanCall.errorSummary(), tingCall.errorSummary(), winCall.errorSummary());
        return new WarmupReport(
            true,
            detail,
            versionCall.nanos(),
            pingCall.nanos(),
            fanCall.firstNanos(),
            fanCall.warmNanos(),
            tingCall.firstNanos(),
            tingCall.warmNanos(),
            winCall.firstNanos(),
            winCall.warmNanos(),
            versionCall.nanos() + pingCall.nanos() + fanCall.firstNanos() + fanCall.warmNanos() + tingCall.firstNanos() + tingCall.warmNanos() + winCall.firstNanos() + winCall.warmNanos(),
            fanCall.success(),
            tingCall.success(),
            winCall.success(),
            errorSummary,
            System.nanoTime() - startedAt
        );
    }

    private static TimedCall timedCall(ThrowingRunnable runnable) {
        long startedAt = System.nanoTime();
        try {
            runnable.run();
            return new TimedCall(true, System.nanoTime() - startedAt, null);
        } catch (RuntimeException ex) {
            return new TimedCall(false, System.nanoTime() - startedAt, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static PairedCall pairedCall(ThrowingRunnable runnable) {
        TimedCall first = timedCall(runnable);
        TimedCall warm = timedCall(runnable);
        return new PairedCall(
            first.nanos(),
            warm.nanos(),
            first.success() && warm.success(),
            mergeErrors(first.error(), warm.error())
        );
    }

    private static String mergeErrors(String... errors) {
        StringBuilder summary = new StringBuilder();
        for (String error : errors) {
            if (error == null || error.isBlank()) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(" | ");
            }
            summary.append(error);
        }
        return summary.toString();
    }

    private static GbFanRequest sampleFanRequest() {
        return new GbFanRequest(
            "GB_MAHJONG",
            List.of("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "B1", "B2", "B3", "T1"),
            List.of(),
            "T1",
            "SELF_DRAW",
            "EAST",
            "EAST",
            List.of(),
            List.of()
        );
    }

    private static GbTingRequest sampleTingRequest() {
        return new GbTingRequest(
            "GB_MAHJONG",
            List.of("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "B1", "B2", "B3", "T1"),
            List.of(),
            "EAST",
            "EAST",
            List.of(),
            List.of()
        );
    }

    private static GbWinRequest sampleWinRequest() {
        return new GbWinRequest(
            "GB_MAHJONG",
            List.of("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "B1", "B2", "B3", "T1"),
            List.of(),
            "T1",
            "SELF_DRAW",
            "EAST",
            null,
            "EAST",
            "EAST",
            List.of(),
            List.of(),
            List.of()
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record TimedCall(boolean success, long nanos, String error) {
    }

    private record PairedCall(long firstNanos, long warmNanos, boolean success, String errorSummary) {
    }

    public record WarmupReport(
        boolean available,
        String detail,
        long versionNanos,
        long pingNanos,
        long fanFirstNanos,
        long fanWarmNanos,
        long tingFirstNanos,
        long tingWarmNanos,
        long winFirstNanos,
        long winWarmNanos,
        long benchmarkTotalNanos,
        boolean fanSuccess,
        boolean tingSuccess,
        boolean winSuccess,
        String errorSummary,
        long totalNanos
    ) {
    }
}
