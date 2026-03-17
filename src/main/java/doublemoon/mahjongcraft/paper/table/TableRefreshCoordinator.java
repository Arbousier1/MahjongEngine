package doublemoon.mahjongcraft.paper.table;

import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

final class TableRefreshCoordinator {
    private static final int CHUNK_REFRESH_BATCH_SIZE = 1;

    private final MahjongPaperPlugin plugin;
    private final MahjongTableManager tableManager;
    private final TableSeatCoordinator seatCoordinator;
    private final int startupRebuildBatchSize;
    private final Set<String> pendingArtifactCleanupTableIds = new HashSet<>();
    private final StartupRefreshQueue startupRefreshQueue = new StartupRefreshQueue();
    private final StartupRefreshQueue chunkRefreshQueue = new StartupRefreshQueue();
    private BukkitTask startupRefreshTask;
    private BukkitTask chunkRefreshTask;

    TableRefreshCoordinator(MahjongPaperPlugin plugin, MahjongTableManager tableManager, TableSeatCoordinator seatCoordinator, int startupRebuildBatchSize) {
        this.plugin = plugin;
        this.tableManager = tableManager;
        this.seatCoordinator = seatCoordinator;
        this.startupRebuildBatchSize = Math.max(1, startupRebuildBatchSize);
    }

    void shutdown() {
        if (this.startupRefreshTask != null) {
            this.startupRefreshTask.cancel();
            this.startupRefreshTask = null;
        }
        if (this.chunkRefreshTask != null) {
            this.chunkRefreshTask.cancel();
            this.chunkRefreshTask = null;
        }
        this.pendingArtifactCleanupTableIds.clear();
        this.startupRefreshQueue.clear();
        this.chunkRefreshQueue.clear();
    }

    void markPendingArtifactCleanup(String tableId) {
        if (tableId == null) {
            return;
        }
        this.pendingArtifactCleanupTableIds.add(tableId);
    }

    void clearPendingArtifactCleanup(String tableId) {
        if (tableId == null) {
            return;
        }
        this.pendingArtifactCleanupTableIds.remove(tableId);
    }

    void cleanupLoadedTableArtifactsIfNeeded(MahjongTableSession session) {
        if (session == null || !this.pendingArtifactCleanupTableIds.remove(session.id())) {
            return;
        }
        this.refreshPersistentTableArtifacts(session);
        this.plugin.debug().log("table", "Deferred startup cleanup finished for persistent table " + session.id());
    }

    void enqueueStartupRefresh(String tableId) {
        this.startupRefreshQueue.enqueue(tableId);
    }

    void enqueueStartupRefresh(Collection<MahjongTableSession> sessions) {
        if (sessions == null) {
            return;
        }
        for (MahjongTableSession session : sessions) {
            if (session != null && session.isPersistentRoom()) {
                this.enqueueStartupRefresh(session.id());
            }
        }
    }

    void scheduleStartupRefreshTask() {
        if (this.startupRefreshQueue.isEmpty()) {
            return;
        }
        if (this.startupRefreshTask != null && !this.startupRefreshTask.isCancelled()) {
            return;
        }
        this.startupRefreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::processStartupRefreshBatch, 1L, 1L);
    }

    void handleChunkLoad(Chunk chunk, Collection<String> candidateTableIds) {
        if (chunk == null || candidateTableIds == null || candidateTableIds.isEmpty()) {
            return;
        }
        for (String tableId : candidateTableIds) {
            MahjongTableSession session = this.tableManager.resolveTableById(tableId);
            if (session == null || !this.affectsTableArea(chunk, session)) {
                continue;
            }
            this.chunkRefreshQueue.enqueue(tableId);
        }
        this.scheduleChunkRefreshTask();
    }

    private void scheduleChunkRefreshTask() {
        if (this.chunkRefreshQueue.isEmpty()) {
            return;
        }
        if (this.chunkRefreshTask != null && !this.chunkRefreshTask.isCancelled()) {
            return;
        }
        this.chunkRefreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::processChunkRefreshBatch, 1L, 1L);
    }

    private void processStartupRefreshBatch() {
        int attempts = 0;
        while (attempts < this.startupRebuildBatchSize && !this.startupRefreshQueue.isEmpty()) {
            String tableId = this.startupRefreshQueue.poll();
            MahjongTableSession session = this.tableManager.resolveTableById(tableId);
            if (session == null || !session.isPersistentRoom()) {
                continue;
            }
            if (!this.isTableAreaLoaded(session)) {
                attempts++;
                continue;
            }
            this.refreshPersistentTableArtifacts(session);
            session.render();
            attempts++;
        }
        if (this.startupRefreshQueue.isEmpty() && this.startupRefreshTask != null) {
            this.startupRefreshTask.cancel();
            this.startupRefreshTask = null;
        }
    }

    private void processChunkRefreshBatch() {
        int attempts = 0;
        while (attempts < CHUNK_REFRESH_BATCH_SIZE && !this.chunkRefreshQueue.isEmpty()) {
            String tableId = this.chunkRefreshQueue.poll();
            MahjongTableSession session = this.tableManager.resolveTableById(tableId);
            if (session == null) {
                continue;
            }
            if (!this.isTableAreaLoaded(session)) {
                attempts++;
                continue;
            }
            this.cleanupLoadedTableArtifactsIfNeeded(session);
            session.render();
            this.seatCoordinator.startSeatWatchdog(session);
            attempts++;
        }
        if (this.chunkRefreshQueue.isEmpty() && this.chunkRefreshTask != null) {
            this.chunkRefreshTask.cancel();
            this.chunkRefreshTask = null;
        }
    }

    private void refreshPersistentTableArtifacts(MahjongTableSession session) {
        if (session == null) {
            return;
        }
        this.pendingArtifactCleanupTableIds.remove(session.id());
        Location center = session.center();
        session.clearDisplays();
        this.tableManager.cleanupTableArtifactsAt(center);
    }

    private boolean affectsTableArea(Chunk chunk, MahjongTableSession session) {
        Location center = session.center();
        World world = center.getWorld();
        if (world == null || !world.equals(chunk.getWorld())) {
            return false;
        }
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        return Math.abs(centerChunkX - chunk.getX()) <= 1 && Math.abs(centerChunkZ - chunk.getZ()) <= 1;
    }

    private boolean isTableAreaLoaded(MahjongTableSession session) {
        if (session == null) {
            return false;
        }
        Location center = session.center();
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        Set<ChunkNeighborhood.ChunkKey> loadedChunks = new HashSet<>();
        for (ChunkNeighborhood.ChunkKey chunkKey : ChunkNeighborhood.around(world.getUID(), centerChunkX, centerChunkZ)) {
            if (world.isChunkLoaded(chunkKey.chunkX(), chunkKey.chunkZ())) {
                loadedChunks.add(chunkKey);
            }
        }
        return TableAreaChunks.allLoaded(world.getUID(), centerChunkX, centerChunkZ, loadedChunks);
    }
}
