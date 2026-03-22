package top.ellan.mahjong.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AsyncService implements AutoCloseable {
    private final Logger logger;
    private final ExecutorService executor;

    public AsyncService(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
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
        this.executor.close();
    }
}

