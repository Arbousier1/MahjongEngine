package top.ellan.mahjong.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AsyncService implements AutoCloseable {
    private final Logger logger;
    private final ExecutorService executor;

    public AsyncService(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "MahjongPaper-Async");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void execute(String taskName, Runnable task) {
        try {
            this.executor.execute(() -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    this.logger.log(Level.WARNING, "Async task failed: " + taskName, throwable);
                }
            });
        } catch (RejectedExecutionException ignored) {
            this.logger.fine("Ignoring async task after executor shutdown: " + taskName);
        }
    }

    @Override
    public void close() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }
    }

    /**
     * Blocks until all currently-queued tasks have STARTED execution (and any
     * previously-submitted task has either completed or is running), or the
     * timeout elapses — without shutting down the executor.
     *
     * Used during config reload to ensure pending DB writes (which reference
     * the OLD DatabaseService) have at least pulled their Connection from the
     * old DataSource before it is closed. Note: a task that has started but
     * not finished may still touch the closed DataSource; this is accepted
     * because (a) HikariCP tolerates in-flight connections on close, and
     * (b) the AsyncService error handler swallows the resulting SQLException
     * rather than crashing. The goal here is to eliminate the COMMON case of
     * "queued-but-not-started" tasks racing the close.
     *
     * Caller must NOT hold any lock that submitted tasks could need.
     *
     * @param timeoutSeconds max wait.
     * @return true if the sentinel ran in time, false on timeout.
     */
    public boolean awaitQuiescence(long timeoutSeconds) {
        // Submit a sentinel and wait for it to complete. When the sentinel's
        // get() returns, every task submitted before it has at least started
        // (executor preserves submission order for a single-threaded view of
        // FIFO queue; cached thread pool uses LinkedBlockingQueue which is
        // FIFO). Tasks that started but are still running when we proceed
        // will race the DB close — see method javadoc.
        java.util.concurrent.Future<?> sentinel = this.executor.submit(() -> { });
        try {
            sentinel.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            return true; // executor already shut down; nothing to wait for
        } catch (java.util.concurrent.TimeoutException timeout) {
            return false;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException ee) {
            // Sentinel body is empty, so this should not happen; treat as drained.
            return true;
        }
    }
}

