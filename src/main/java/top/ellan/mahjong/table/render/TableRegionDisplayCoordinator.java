package top.ellan.mahjong.table.render;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.display.DisplayVisibilityRegistry;
import top.ellan.mahjong.render.display.TableDisplayRegistry;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.TableRenderPrecomputeResult;
import top.ellan.mahjong.table.core.TableRenderSnapshot;
import top.ellan.mahjong.table.core.TableSeatRenderSnapshot;
import top.ellan.mahjong.table.core.TableViewerOverlaySnapshot;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Entity;

public final class TableRegionDisplayCoordinator {
    private static final String REGION_TABLE = "table";
    private static final String REGION_WALL = "wall";
    private static final String REGION_DORA = "dora";
    private static final String REGION_CENTER = "center";
    private static final int MAX_WALL_TILE_REGIONS = 136;
    private static final int MAX_HAND_TILE_REGIONS = 14;
    private static final int MAX_DISCARD_TILE_REGIONS = 24;
    private static final int MAX_REGION_UPDATES_PER_APPLY = 64;
    private static final int MAX_ENTITY_SPAWNS_PER_APPLY = 192;

    private final MahjongTableSession session;
    private final TableRegionFingerprintService fingerprintService;
    private final Map<String, List<Entity>> regionDisplays = new LinkedHashMap<>();
    private final Map<String, String> regionFingerprints = new HashMap<>();

    public TableRegionDisplayCoordinator(MahjongTableSession session, TableRegionFingerprintService fingerprintService) {
        this.session = session;
        this.fingerprintService = fingerprintService;
    }

    public boolean applyRenderPrecompute(TableRenderPrecomputeResult result) {
        ApplyBudget budget = new ApplyBudget(MAX_REGION_UPDATES_PER_APPLY, MAX_ENTITY_SPAWNS_PER_APPLY);
        TableRenderSnapshot snapshot = result.snapshot();
        TableRenderLayout.LayoutPlan plan = result.layout();
        Map<String, String> fingerprints = result.regionFingerprints();

        if (!this.updateRegion(REGION_TABLE, fingerprints.get(REGION_TABLE), budget, () -> this.session.renderer().renderTableStructure(this.session, plan))) {
            return true;
        }
        if (!this.updateWallRegions(plan, budget)) {
            return true;
        }
        if (!this.updateRegionWithSpecs(REGION_DORA, fingerprints.get(REGION_DORA), budget, () -> this.session.renderer().renderDoraSpecs(this.session, plan))) {
            return true;
        }
        if (!this.updateRegionWithSpecs(
            REGION_CENTER,
            fingerprints.get(REGION_CENTER),
            budget,
            () -> this.session.renderer().renderCenterLabelSpecs(this.session, snapshot, plan)
        )) {
            return true;
        }
        for (SeatWind wind : SeatWind.values()) {
            TableSeatRenderSnapshot seat = snapshot.seat(wind);
            TableRenderLayout.SeatLayoutPlan seatPlan = plan.seat(wind);
            String visualRegionKey = this.seatRegionKey("visual", wind);
            String labelsRegionKey = this.seatRegionKey("labels", wind);
            String sticksRegionKey = this.seatRegionKey("sticks", wind);
            String meldsRegionKey = this.seatRegionKey("melds", wind);
            if (!this.updateRegion(visualRegionKey, fingerprints.get(visualRegionKey), budget, () -> this.session.renderer().renderSeatVisual(this.session, wind))) {
                return true;
            }
            if (!this.updateRegionWithSpecs(
                labelsRegionKey,
                fingerprints.get(labelsRegionKey),
                budget,
                () -> this.session.renderer().renderSeatLabelSpecs(this.session, seat, seatPlan)
            )) {
                return true;
            }
            if (!this.updateRegion(sticksRegionKey, fingerprints.get(sticksRegionKey), budget, () -> this.session.renderer().renderSticks(this.session, seat, seatPlan))) {
                return true;
            }
            if (!this.updatePublicHandRegions(snapshot, seat, seatPlan, budget)) {
                return true;
            }
            if (!this.updatePrivateHandRegions(seat, seatPlan, budget)) {
                return true;
            }
            if (!this.updateDiscardRegions(seat, seatPlan, budget)) {
                return true;
            }
            if (!this.updateRegionWithSpecs(
                meldsRegionKey,
                fingerprints.get(meldsRegionKey),
                budget,
                () -> this.session.renderer().renderMeldSpecs(this.session, seat, seatPlan)
            )) {
                return true;
            }
        }
        return false;
    }

    public void refreshPrivateHandRegions(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan) {
        this.updatePrivateHandRegions(seat, plan, ApplyBudget.unlimited());
    }

    public void updateViewerOverlayRegion(TableViewerOverlaySnapshot snapshot) {
        this.updateRegionWithSpecs(
            snapshot.regionKey(),
            snapshot.fingerprint(),
            ApplyBudget.unlimited(),
            () -> this.session.renderer().renderViewerOverlaySpecs(this.session, snapshot)
        );
    }

    public List<String> regionKeys() {
        return List.copyOf(this.regionDisplays.keySet());
    }

    public void removeManagedRegionDisplays(String regionKey) {
        this.removeRegionDisplays(regionKey);
    }

    public void clearRenderDisplays() {
        this.regionFingerprints.clear();
        this.removeAllDisplays();
    }

    public void invalidateFingerprints() {
        this.regionFingerprints.clear();
    }

    public boolean hasRegionDisplays() {
        return !this.regionDisplays.isEmpty();
    }

    public boolean hasStaleDisplayRegions() {
        for (List<Entity> entities : this.regionDisplays.values()) {
            if (this.hasInvalidDisplayEntity(entities)) {
                return true;
            }
        }
        return false;
    }

    private boolean updatePrivateHandRegions(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, ApplyBudget budget) {
        this.clearRegion(this.seatRegionKey("hand-private", seat.wind()));
        int handSize = seat.playerId() == null ? 0 : seat.hand().size();
        for (int tileIndex = 0; tileIndex < MAX_HAND_TILE_REGIONS; tileIndex++) {
            String displayRegionKey = this.handPrivateDisplayRegionKey(seat.wind(), tileIndex);
            String hitboxRegionKey = this.handPrivateHitboxRegionKey(seat.wind(), tileIndex);
            if (tileIndex >= handSize) {
                this.clearRegion(displayRegionKey);
                this.clearRegion(hitboxRegionKey);
                continue;
            }
            int index = tileIndex;
            if (!this.updateRegionWithSpecs(
                displayRegionKey,
                this.fingerprintService.handPrivateDisplayTileFingerprint(seat, plan, tileIndex),
                budget,
                () -> this.session.renderer().renderHandPrivateDisplayTileSpecs(this.session, seat, plan, index)
            )) {
                return false;
            }
            if (!this.updateRegionWithSpecs(
                hitboxRegionKey,
                this.fingerprintService.handPrivateHitboxTileFingerprint(seat, plan, tileIndex),
                budget,
                () -> this.session.renderer().renderHandPrivateHitboxTileSpecs(this.session, seat, plan, index)
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean updatePublicHandRegions(
        TableRenderSnapshot snapshot,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan,
        ApplyBudget budget
    ) {
        this.clearRegion(this.seatRegionKey("hand-public", seat.wind()));
        int handSize = seat.playerId() == null ? 0 : seat.hand().size();
        for (int tileIndex = 0; tileIndex < MAX_HAND_TILE_REGIONS; tileIndex++) {
            String regionKey = this.handPublicRegionKey(seat.wind(), tileIndex);
            if (tileIndex >= handSize) {
                this.clearRegion(regionKey);
                continue;
            }
            int index = tileIndex;
            if (!this.updateRegionWithSpecs(
                regionKey,
                this.fingerprintService.handPublicTileFingerprint(snapshot, seat, plan, tileIndex),
                budget,
                () -> this.session.renderer().renderHandPublicTileSpecs(this.session, snapshot, seat, plan, index)
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean updateDiscardRegions(TableSeatRenderSnapshot seat, TableRenderLayout.SeatLayoutPlan plan, ApplyBudget budget) {
        this.clearRegion(this.seatRegionKey("discards", seat.wind()));
        int discardCount = seat.playerId() == null ? 0 : plan.discardPlacements().size();
        for (int discardIndex = 0; discardIndex < MAX_DISCARD_TILE_REGIONS; discardIndex++) {
            String regionKey = this.discardRegionKey(seat.wind(), discardIndex);
            if (discardIndex >= discardCount) {
                this.clearRegion(regionKey);
                continue;
            }
            int index = discardIndex;
            if (!this.updateRegionWithSpecs(
                regionKey,
                this.fingerprintService.discardTileFingerprint(seat, plan, discardIndex),
                budget,
                () -> this.session.renderer().renderDiscardTileSpecs(this.session, seat, plan, index)
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean updateWallRegions(TableRenderLayout.LayoutPlan plan, ApplyBudget budget) {
        this.clearRegion(REGION_WALL);
        int wallTileCount = plan.wallTiles().size();
        for (int wallIndex = 0; wallIndex < MAX_WALL_TILE_REGIONS; wallIndex++) {
            String regionKey = this.wallRegionKey(wallIndex);
            if (wallIndex >= wallTileCount) {
                this.clearRegion(regionKey);
                continue;
            }
            int index = wallIndex;
            if (!this.updateRegionWithSpecs(
                regionKey,
                this.fingerprintService.wallTileFingerprint(plan, wallIndex),
                budget,
                () -> this.session.renderer().renderWallTileSpecs(this.session, plan, index)
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean updateRegion(String regionKey, String fingerprint, ApplyBudget budget, RegionRenderer renderer) {
        String previousFingerprint = this.regionFingerprints.get(regionKey);
        List<Entity> currentEntities = this.regionDisplays.get(regionKey);
        if (this.hasInvalidDisplayEntity(currentEntities)) {
            this.removeRegionDisplays(regionKey);
            previousFingerprint = null;
            currentEntities = null;
        }
        if (Objects.equals(previousFingerprint, fingerprint) && (currentEntities != null || this.regionFingerprints.containsKey(regionKey))) {
            return true;
        }
        if (!budget.tryConsumeRegionUpdate(1)) {
            return false;
        }
        this.removeRegionDisplays(regionKey);
        List<Entity> entities = renderer.render();
        if (!entities.isEmpty()) {
            this.regionDisplays.put(regionKey, entities);
        }
        this.regionFingerprints.put(regionKey, fingerprint);
        return true;
    }

    private boolean updateRegionWithSpecs(String regionKey, String fingerprint, ApplyBudget budget, RegionSpecRenderer renderer) {
        String previousFingerprint = this.regionFingerprints.get(regionKey);
        List<Entity> currentEntities = this.regionDisplays.get(regionKey);
        if (this.hasInvalidDisplayEntity(currentEntities)) {
            this.removeRegionDisplays(regionKey);
            previousFingerprint = null;
            currentEntities = null;
        }
        if (Objects.equals(previousFingerprint, fingerprint) && (currentEntities != null || this.regionFingerprints.containsKey(regionKey))) {
            return true;
        }
        List<DisplayEntities.EntitySpec> specs = renderer.render();
        if (currentEntities != null && DisplayEntities.reconcile(this.session.plugin(), currentEntities, specs)) {
            this.regionFingerprints.put(regionKey, fingerprint);
            return true;
        }
        if (!budget.tryConsumeRegionUpdate(1) || !budget.tryConsumeEntitySpawns(specs.size())) {
            return false;
        }
        this.removeRegionDisplays(regionKey);
        List<Entity> entities = DisplayEntities.spawnAll(this.session.plugin(), specs);
        if (!entities.isEmpty()) {
            this.regionDisplays.put(regionKey, entities);
        }
        this.regionFingerprints.put(regionKey, fingerprint);
        return true;
    }

    private void clearRegion(String regionKey) {
        this.removeRegionDisplays(regionKey);
        this.regionFingerprints.remove(regionKey);
    }

    private void removeAllDisplays() {
        for (String regionKey : List.copyOf(this.regionDisplays.keySet())) {
            this.removeRegionDisplays(regionKey);
        }
        this.regionDisplays.clear();
    }

    private void removeRegionDisplays(String regionKey) {
        List<Entity> entities = this.regionDisplays.remove(regionKey);
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            TableDisplayRegistry.unregister(entity.getEntityId());
            DisplayVisibilityRegistry.unregister(entity.getEntityId());
            if (this.session.plugin().craftEngine() != null) {
                this.session.plugin().craftEngine().unregisterCullableEntity(entity);
            }
            boolean removedByCraftEngine = this.session.plugin().craftEngine() != null
                && this.session.plugin().craftEngine().removeFurniture(entity);
            if (!removedByCraftEngine && !entity.isDead()) {
                this.session.plugin().scheduler().removeEntity(entity);
            }
        }
        this.regionFingerprints.remove(regionKey);
    }

    private boolean hasInvalidDisplayEntity(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        for (Entity entity : entities) {
            if (entity == null || entity.isDead() || !entity.isValid()) {
                return true;
            }
        }
        return false;
    }

    private String seatRegionKey(String region, SeatWind wind) {
        return region + ":" + wind.name();
    }

    private String handPrivateDisplayRegionKey(SeatWind wind, int tileIndex) {
        return this.seatRegionKey("hand-private-display-" + tileIndex, wind);
    }

    private String handPrivateHitboxRegionKey(SeatWind wind, int tileIndex) {
        return this.seatRegionKey("hand-private-hitbox-" + tileIndex, wind);
    }

    private String handPublicRegionKey(SeatWind wind, int tileIndex) {
        return this.seatRegionKey("hand-public-" + tileIndex, wind);
    }

    private String discardRegionKey(SeatWind wind, int discardIndex) {
        return this.seatRegionKey("discards-" + discardIndex, wind);
    }

    private String wallRegionKey(int wallIndex) {
        return REGION_WALL + "-" + wallIndex;
    }

    @FunctionalInterface
    private interface RegionRenderer {
        List<Entity> render();
    }

    @FunctionalInterface
    private interface RegionSpecRenderer {
        List<DisplayEntities.EntitySpec> render();
    }

    private static final class ApplyBudget {
        private int remainingRegionUpdates;
        private int remainingEntitySpawns;

        private ApplyBudget(int remainingRegionUpdates, int remainingEntitySpawns) {
            this.remainingRegionUpdates = remainingRegionUpdates;
            this.remainingEntitySpawns = remainingEntitySpawns;
        }

        static ApplyBudget unlimited() {
            return new ApplyBudget(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        boolean tryConsumeRegionUpdate(int amount) {
            if (this.remainingRegionUpdates < amount) {
                return false;
            }
            this.remainingRegionUpdates -= amount;
            return true;
        }

        boolean tryConsumeEntitySpawns(int amount) {
            if (this.remainingEntitySpawns < amount) {
                return false;
            }
            this.remainingEntitySpawns -= amount;
            return true;
        }
    }
}



