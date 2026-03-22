package top.ellan.mahjong.table.runtime;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class StartupRefreshQueue {
    private final Set<String> queuedIds = new HashSet<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();

    void enqueue(String tableId) {
        if (tableId == null) {
            return;
        }
        String normalizedId = tableId.toUpperCase(Locale.ROOT);
        if (!this.queuedIds.add(normalizedId)) {
            return;
        }
        this.queue.addLast(normalizedId);
    }

    String poll() {
        String next = this.queue.pollFirst();
        if (next != null) {
            this.queuedIds.remove(next);
        }
        return next;
    }

    boolean isEmpty() {
        return this.queue.isEmpty();
    }

    void clear() {
        this.queuedIds.clear();
        this.queue.clear();
    }
}


