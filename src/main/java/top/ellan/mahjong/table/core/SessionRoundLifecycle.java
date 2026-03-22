package top.ellan.mahjong.table.core;

final class SessionRoundLifecycle {
    private static final long NEXT_ROUND_DELAY_MILLIS = 8000L;
    private long nextRoundDeadlineMillis;

    void scheduleNextRoundCountdown() {
        this.nextRoundDeadlineMillis = System.currentTimeMillis() + NEXT_ROUND_DELAY_MILLIS;
    }

    void cancelNextRoundCountdown() {
        this.nextRoundDeadlineMillis = 0L;
    }

    boolean hasNextRoundCountdown() {
        return this.nextRoundDeadlineMillis > 0L;
    }

    long nextRoundSecondsRemaining() {
        if (this.nextRoundDeadlineMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (long) Math.ceil((this.nextRoundDeadlineMillis - System.currentTimeMillis()) / 1000.0D));
    }
}

