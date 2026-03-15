package doublemoon.mahjongcraft.paper.render;

import doublemoon.mahjongcraft.paper.model.MahjongTile;
import doublemoon.mahjongcraft.paper.model.SeatWind;
import doublemoon.mahjongcraft.paper.table.MahjongTableSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

public final class TableRenderer {
    private static final double ONE_SIXTEENTH = 1.0D / 16.0D;
    private static final double TILE_WIDTH = 0.1125D;
    private static final double TILE_HEIGHT = 0.15D;
    private static final double TILE_DEPTH = 0.075D;
    private static final double TILE_PADDING = 0.0025D;
    private static final double TABLE_BOTTOM_SIZE = 14.0D * ONE_SIXTEENTH;
    private static final double TABLE_BOTTOM_HEIGHT = 2.0D * ONE_SIXTEENTH;
    private static final double TABLE_PILLAR_SIZE = 8.0D * ONE_SIXTEENTH;
    private static final double TABLE_PILLAR_HEIGHT = 12.0D * ONE_SIXTEENTH;
    private static final double TABLE_TOP_SIZE = 46.0D * ONE_SIXTEENTH;
    private static final double TABLE_TOP_THICKNESS = 2.0D * ONE_SIXTEENTH;
    private static final double TABLE_BORDER_THICKNESS = ONE_SIXTEENTH;
    private static final double TABLE_BORDER_HEIGHT = 3.0D * ONE_SIXTEENTH;
    private static final double TABLE_BORDER_SPAN = 47.0D * ONE_SIXTEENTH;
    private static final double TABLE_SHULKER_HITBOX_Y = -1.0D;
    private static final double[] TABLE_SHULKER_HITBOX_GRID = new double[] {-1.0D, 0.0D, 1.0D};
    private static final double WALL_DIRECTION_OFFSET = 1.0D;
    private static final double HAND_DIRECTION_OFFSET = WALL_DIRECTION_OFFSET + TILE_DEPTH + TILE_HEIGHT;
    private static final double HALF_TABLE_LENGTH_NO_BORDER = 0.5D + 15.0D / 16.0D;
    private static final double DORA_STEP = TILE_WIDTH + TILE_PADDING;
    private static final double DORA_RADIUS = 0.28D;
    private static final double UPRIGHT_TILE_Y = TILE_HEIGHT / 2.0D + 0.01D;
    private static final double FLAT_TILE_Y = 0.02D;
    private static final int DISCARDS_PER_ROW = 6;
    public List<Entity> renderTableStructure(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<Entity> spawned = new ArrayList<>(16);
        double borderCenterOffset = TABLE_TOP_SIZE / 2.0D + TABLE_BORDER_THICKNESS / 2.0D;

        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(0.0D, -1.0D, 0.0D), TABLE_BOTTOM_SIZE, TABLE_BOTTOM_HEIGHT, TABLE_BOTTOM_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TABLE_BOTTOM_SIZE,
            (float) TABLE_BOTTOM_HEIGHT,
            (float) TABLE_BOTTOM_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(0.0D, -(TABLE_TOP_THICKNESS + TABLE_PILLAR_HEIGHT), 0.0D), TABLE_PILLAR_SIZE, TABLE_PILLAR_HEIGHT, TABLE_PILLAR_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TABLE_PILLAR_SIZE,
            (float) TABLE_PILLAR_HEIGHT,
            (float) TABLE_PILLAR_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(0.0D, -TABLE_TOP_THICKNESS, 0.0D), TABLE_TOP_SIZE, TABLE_TOP_THICKNESS, TABLE_TOP_SIZE),
            Material.SMOOTH_STONE,
            (float) TABLE_TOP_SIZE,
            (float) TABLE_TOP_THICKNESS,
            (float) TABLE_TOP_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(0.0D, -TABLE_TOP_THICKNESS, -borderCenterOffset), TABLE_BORDER_SPAN, TABLE_BORDER_HEIGHT, TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_SPAN,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(0.0D, -TABLE_TOP_THICKNESS, borderCenterOffset), TABLE_BORDER_SPAN, TABLE_BORDER_HEIGHT, TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_SPAN,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(-borderCenterOffset, -TABLE_TOP_THICKNESS, 0.0D), TABLE_BORDER_THICKNESS, TABLE_BORDER_HEIGHT, TABLE_BORDER_SPAN),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_THICKNESS,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_SPAN
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.plugin(),
            centeredCuboid(center.clone().add(borderCenterOffset, -TABLE_TOP_THICKNESS, 0.0D), TABLE_BORDER_THICKNESS, TABLE_BORDER_HEIGHT, TABLE_BORDER_SPAN),
            Material.STRIPPED_OAK_WOOD,
            (float) TABLE_BORDER_THICKNESS,
            (float) TABLE_BORDER_HEIGHT,
            (float) TABLE_BORDER_SPAN
        ));
        spawned.addAll(this.renderTableHitboxes(session, center));
        return spawned;
    }

    public List<Entity> renderSeatLabels(MahjongTableSession session, SeatWind wind) {
        List<Entity> spawned = new ArrayList<>(2);
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        Location handBase = handDirectionBase(center, wind);
        boolean active = session.currentSeat() == wind;

        spawned.add(DisplayEntities.spawnLabel(
            session.plugin(),
            handBase.clone().add(0.0D, 0.45D, 0.0D),
            Component.text(session.publicSeatStatus(wind)),
            seatLabelColor(wind, active)
        ));
        if (playerId != null) {
            spawned.add(DisplayEntities.spawnLabel(
                session.plugin(),
                handBase.clone().add(0.0D, 0.26D, 0.0D),
                Component.text(session.displayName(playerId, session.publicLocale())),
                Color.fromARGB(100, 18, 18, 18)
            ));
        }
        return spawned;
    }

    public List<Entity> renderWall(MahjongTableSession session) {
        Location center = displayCenter(session);
        int wallCount = session.remainingWall().size();
        List<Entity> spawned = new ArrayList<>(wallCount);
        for (int i = 0; i < wallCount; i++) {
            SeatWind wind = WallLayout.wallSeat(i);
            int stackIndex = WallLayout.wallColumn(i);
            int topLayer = WallLayout.wallLayer(i);
            double stackWidth = stackIndex * (TILE_WIDTH + TILE_PADDING);
            double startingPos = (17.0D * TILE_WIDTH) / 2.0D - TILE_HEIGHT;
            double yOffset = topLayer * TILE_DEPTH + (topLayer == 1 ? TILE_PADDING : 0.0D);
            Location tileLocation = switch (wind) {
                case EAST -> center.clone().add(WALL_DIRECTION_OFFSET, UPRIGHT_TILE_Y + yOffset, -startingPos + stackWidth);
                case SOUTH -> center.clone().add(startingPos - stackWidth, UPRIGHT_TILE_Y + yOffset, WALL_DIRECTION_OFFSET);
                case WEST -> center.clone().add(-WALL_DIRECTION_OFFSET, UPRIGHT_TILE_Y + yOffset, startingPos - stackWidth);
                case NORTH -> center.clone().add(-startingPos + stackWidth, UPRIGHT_TILE_Y + yOffset, -WALL_DIRECTION_OFFSET);
            };
            spawned.add(DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                seatYaw(wind),
                MahjongTile.UNKNOWN,
                true,
                false,
                null,
                true
            ));
        }
        return spawned;
    }

    public List<Entity> renderDora(MahjongTableSession session) {
        Location center = displayCenter(session);
        List<MahjongTile> dora = session.doraIndicators();
        List<Entity> spawned = new ArrayList<>(dora.size());
        for (int i = 0; i < dora.size(); i++) {
            Location tileLocation = center.clone().add(centeredOffset(Math.max(1, dora.size()), i, DORA_STEP), FLAT_TILE_Y, -DORA_RADIUS);
            spawned.add(DisplayEntities.spawnTileDisplay(session.plugin(), tileLocation, 0.0F, dora.get(i), false, true, null, true));
        }
        return spawned;
    }

    public List<Entity> renderCenterLabel(MahjongTableSession session) {
        Location center = displayCenter(session);
        return List.of(DisplayEntities.spawnLabel(
            session.plugin(),
            center.clone().add(0.0D, 0.3D, 0.0D),
            Component.text(session.publicCenterText()),
            Color.fromARGB(112, 20, 80, 20)
        ));
    }

    public List<Entity> renderViewerOverlay(MahjongTableSession session, Player viewer) {
        Location center = displayCenter(session);
        UUID viewerId = viewer.getUniqueId();
        List<Entity> spawned = new ArrayList<>(session.isSpectator(viewerId) ? 5 : 1);
        if (session.isStarted() || session.isSpectator(viewerId)) {
            spawned.add(DisplayEntities.spawnLabel(
                session.plugin(),
                center.clone().add(0.0D, 0.9D, 0.0D),
                session.viewerOverlay(viewer),
                Color.fromARGB(84, 12, 12, 12),
                List.of(viewerId)
            ));
        }
        if (session.isSpectator(viewerId)) {
            for (SeatWind wind : SeatWind.values()) {
                spawned.add(DisplayEntities.spawnLabel(
                    session.plugin(),
                    add(handDirectionBase(center, wind), offsetAcrossSeat(wind, 0.42D)).add(0.0D, 0.62D, 0.0D),
                    session.spectatorSeatOverlay(viewer, wind),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(viewerId)
                ));
            }
        }
        return spawned;
    }

    public List<Entity> renderHand(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        Location handBase = handDirectionBase(center, wind);
        float yaw = seatYaw(wind);
        List<MahjongTile> hand = session.hand(playerId);
        List<MeldView> melds = session.fuuro(playerId);
        double fuuroOffset = melds.size() < 3 ? 0.0D : (melds.size() - 2.0D) * TILE_WIDTH;
        double startingPos = (hand.size() * TILE_WIDTH + Math.max(0, hand.size() - 1) * TILE_PADDING) / 2.0D + fuuroOffset;
        List<UUID> ownerOnly = List.of(playerId);
        List<UUID> othersOnly = session.viewerIdsExcluding(playerId);
        List<Entity> spawned = new ArrayList<>(hand.size() * 3);

        for (int i = 0; i < hand.size(); i++) {
            double drawGap = i == hand.size() - 1 && hand.size() % 3 == 2 ? TILE_PADDING * 15.0D : 0.0D;
            double stackOffset = i * (TILE_WIDTH + TILE_PADDING) + drawGap;
            Location tileLocation = switch (wind) {
                case EAST -> handBase.clone().add(0.0D, UPRIGHT_TILE_Y, startingPos - stackOffset);
                case SOUTH -> handBase.clone().add(-startingPos + stackOffset, UPRIGHT_TILE_Y, 0.0D);
                case WEST -> handBase.clone().add(0.0D, UPRIGHT_TILE_Y, -startingPos + stackOffset);
                case NORTH -> handBase.clone().add(startingPos - stackOffset, UPRIGHT_TILE_Y, 0.0D);
            };

            DisplayClickAction clickAction = new DisplayClickAction(session.id(), playerId, i);
            ItemDisplay publicDisplay = DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                yaw,
                hand.get(i),
                true,
                false,
                null,
                true,
                othersOnly
            );
            spawned.add(publicDisplay);
            ItemDisplay privateDisplay = DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation,
                yaw,
                hand.get(i),
                false,
                false,
                null,
                true,
                ownerOnly
            );
            spawned.add(privateDisplay);
            spawned.add(DisplayEntities.spawnInteraction(
                session.plugin(),
                tileLocation.clone().add(0.0D, 0.02D, 0.0D),
                0.20F,
                0.24F,
                clickAction,
                ownerOnly
            ));
        }
        return spawned;
    }

    public List<Entity> renderDiscards(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        List<MahjongTile> discards = session.discards(playerId);
        int riichiDiscardIndex = session.riichiDiscardIndex(playerId);
        List<Entity> spawned = new ArrayList<>(discards.size());
        Location start = discardStart(center, wind);

        for (int discardIndex = 0; discardIndex < discards.size(); discardIndex++) {
            int lineCount = discardIndex / DISCARDS_PER_ROW;
            int column = discardIndex % DISCARDS_PER_ROW;
            boolean firstTileInRow = column == 0;
            boolean riichiTile = discardIndex == riichiDiscardIndex;
            boolean previousWasRiichi = discardIndex > 0 && discardIndex - 1 == riichiDiscardIndex;

            Location tileLocation = add(start, multiply(lineOffset(wind), lineCount));
            if (!firstTileInRow) {
                tileLocation = add(tileLocation, multiply(tileOffset(wind), column));
                tileLocation = add(tileLocation, multiply(smallGapOffset(wind), column));
            }

            if (riichiTile) {
                tileLocation = firstTileInRow
                    ? add(tileLocation, add(riichiTileOffset(wind), negate(tileOffset(wind))))
                    : add(tileLocation, riichiTileOffset(wind));
            } else if (!firstTileInRow && previousWasRiichi) {
                tileLocation = add(tileLocation, riichiTileOffset(wind));
            }

            spawned.add(DisplayEntities.spawnTileDisplay(
                session.plugin(),
                tileLocation.add(0.0D, FLAT_TILE_Y, 0.0D),
                DiscardLayout.discardYaw(wind, riichiTile),
                discards.get(discardIndex),
                false,
                true,
                null,
                true
            ));
        }
        return spawned;
    }

    public List<Entity> renderMelds(MahjongTableSession session, SeatWind wind) {
        Location center = displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        float yaw = seatYaw(wind);
        List<MeldView> melds = session.fuuro(playerId);
        if (melds.isEmpty()) {
            return List.of();
        }

        int tileCount = melds.stream().mapToInt(meld -> meld.tiles().size() + (meld.hasAddedKanTile() ? 1 : 0)).sum();
        List<Entity> spawned = new ArrayList<>(tileCount);
        Location cursor = meldStart(center, wind);
        Location lastClaimBase = null;
        boolean lastTileWasHorizontal = false;
        int placedTileCount = 0;

        for (MeldView meld : melds) {
            boolean concealedKan = meld.tiles().size() == 4 && meld.faceDownAt(0) && meld.faceDownAt(meld.tiles().size() - 1);
            if (concealedKan) {
                for (int i = 0; i < meld.tiles().size(); i++) {
                    if (placedTileCount == 0) {
                        cursor = add(cursor, halfVerticalTileOffset(wind));
                    } else if (lastTileWasHorizontal) {
                        cursor = add(cursor, add(halfHorizontalTileOffset(wind), halfVerticalTileOffset(wind)));
                    } else {
                        cursor = add(cursor, verticalTileOffset(wind));
                    }
                    spawned.add(DisplayEntities.spawnTileDisplay(
                        session.plugin(),
                        cursor.clone().add(0.0D, UPRIGHT_TILE_Y, 0.0D),
                        yaw,
                        meld.tiles().get(i),
                        meld.faceDownAt(i),
                        false,
                        null,
                        true
                    ));
                    placedTileCount++;
                }
                lastClaimBase = null;
                lastTileWasHorizontal = false;
                continue;
            }

            for (int i = 0; i < meld.tiles().size(); i++) {
                boolean isClaimTile = meld.hasClaimTile() && i == meld.claimTileIndex();
                if (placedTileCount == 0) {
                    cursor = add(cursor, isClaimTile ? halfHorizontalTileOffset(wind) : halfVerticalTileOffset(wind));
                } else if (isClaimTile || lastTileWasHorizontal) {
                    cursor = isClaimTile && lastTileWasHorizontal
                        ? add(cursor, horizontalTileOffset(wind))
                        : add(cursor, add(halfHorizontalTileOffset(wind), halfVerticalTileOffset(wind)));
                } else {
                    cursor = add(cursor, verticalTileOffset(wind));
                }

                Location baseLocation = isClaimTile ? add(cursor, horizontalTileGravityOffset(wind)) : cursor;
                spawned.add(DisplayEntities.spawnTileDisplay(
                    session.plugin(),
                    baseLocation.clone().add(0.0D, UPRIGHT_TILE_Y, 0.0D),
                    isClaimTile ? yaw + meld.claimYawOffset() : yaw,
                    meld.tiles().get(i),
                    meld.faceDownAt(i),
                    false,
                    null,
                    true
                ));
                lastClaimBase = isClaimTile ? baseLocation.clone() : null;
                lastTileWasHorizontal = isClaimTile;
                placedTileCount++;
            }

            if (meld.hasAddedKanTile() && lastClaimBase != null) {
                spawned.add(DisplayEntities.spawnTileDisplay(
                    session.plugin(),
                    add(lastClaimBase, kakanOffset(wind)).add(0.0D, UPRIGHT_TILE_Y, 0.0D),
                    yaw + meld.claimYawOffset(),
                    meld.addedKanTile(),
                    false,
                    false,
                    null,
                    true
                ));
                lastClaimBase = null;
                lastTileWasHorizontal = false;
            }
        }
        return spawned;
    }

    private static Location displayCenter(MahjongTableSession session) {
        return session.center().add(0.0D, 1.02D, 0.0D);
    }

    private List<Entity> renderTableHitboxes(MahjongTableSession session, Location center) {
        List<Entity> spawned = new ArrayList<>(TABLE_SHULKER_HITBOX_GRID.length * TABLE_SHULKER_HITBOX_GRID.length);
        for (double xOffset : TABLE_SHULKER_HITBOX_GRID) {
            for (double zOffset : TABLE_SHULKER_HITBOX_GRID) {
                spawned.add(DisplayEntities.spawnShulkerHitbox(
                    session.plugin(),
                    center.clone().add(xOffset, TABLE_SHULKER_HITBOX_Y, zOffset)
                ));
            }
        }
        return spawned;
    }

    private static Location centeredCuboid(Location center, double width, double height, double depth) {
        return center.clone().add(-width / 2.0D, 0.0D, -depth / 2.0D);
    }

    private static float seatYaw(SeatWind wind) {
        return DiscardLayout.seatYaw(wind);
    }

    private static Location handDirectionBase(Location center, SeatWind wind) {
        return switch (wind) {
            case EAST -> center.clone().add(HAND_DIRECTION_OFFSET, 0.0D, 0.0D);
            case SOUTH -> center.clone().add(0.0D, 0.0D, HAND_DIRECTION_OFFSET);
            case WEST -> center.clone().add(-HAND_DIRECTION_OFFSET, 0.0D, 0.0D);
            case NORTH -> center.clone().add(0.0D, 0.0D, -HAND_DIRECTION_OFFSET);
        };
    }

    private static Location discardStart(Location center, SeatWind wind) {
        double halfWidthOfSixTiles = TILE_WIDTH * DISCARDS_PER_ROW / 2.0D;
        double paddingFromCenter = halfWidthOfSixTiles + TILE_HEIGHT / 2.0D + TILE_HEIGHT / 4.0D;
        double basicOffset = halfWidthOfSixTiles - TILE_WIDTH / 2.0D;
        return switch (wind) {
            case EAST -> center.clone().add(paddingFromCenter, 0.0D, basicOffset);
            case SOUTH -> center.clone().add(-basicOffset, 0.0D, paddingFromCenter);
            case WEST -> center.clone().add(-paddingFromCenter, 0.0D, -basicOffset);
            case NORTH -> center.clone().add(basicOffset, 0.0D, -paddingFromCenter);
        };
    }

    private static Location meldStart(Location center, SeatWind wind) {
        double halfHeight = TILE_HEIGHT / 2.0D;
        return switch (wind) {
            case EAST -> center.clone().add(HALF_TABLE_LENGTH_NO_BORDER - halfHeight, 0.0D, -HALF_TABLE_LENGTH_NO_BORDER);
            case SOUTH -> center.clone().add(HALF_TABLE_LENGTH_NO_BORDER, 0.0D, HALF_TABLE_LENGTH_NO_BORDER - halfHeight);
            case WEST -> center.clone().add(-HALF_TABLE_LENGTH_NO_BORDER + halfHeight, 0.0D, HALF_TABLE_LENGTH_NO_BORDER);
            case NORTH -> center.clone().add(-HALF_TABLE_LENGTH_NO_BORDER, 0.0D, -HALF_TABLE_LENGTH_NO_BORDER + halfHeight);
        };
    }

    private static Location add(Location location, Offset offset) {
        return location.clone().add(offset.x(), 0.0D, offset.z());
    }

    private static double centeredOffset(int size, int index, double step) {
        return (index - (size - 1) / 2.0D) * step;
    }

    private static Offset tileOffset(SeatWind wind) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, -TILE_WIDTH);
            case SOUTH -> new Offset(TILE_WIDTH, 0.0D);
            case WEST -> new Offset(0.0D, TILE_WIDTH);
            case NORTH -> new Offset(-TILE_WIDTH, 0.0D);
        };
    }

    private static Offset riichiTileOffset(SeatWind wind) {
        double amount = (TILE_HEIGHT + TILE_WIDTH) / 2.0D;
        return switch (wind) {
            case EAST -> new Offset(0.0D, -amount);
            case SOUTH -> new Offset(amount, 0.0D);
            case WEST -> new Offset(0.0D, amount);
            case NORTH -> new Offset(-amount, 0.0D);
        };
    }

    private static Offset lineOffset(SeatWind wind) {
        double amount = TILE_HEIGHT + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(amount, 0.0D);
            case SOUTH -> new Offset(0.0D, amount);
            case WEST -> new Offset(-amount, 0.0D);
            case NORTH -> new Offset(0.0D, -amount);
        };
    }

    private static Offset smallGapOffset(SeatWind wind) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, -TILE_PADDING);
            case SOUTH -> new Offset(TILE_PADDING, 0.0D);
            case WEST -> new Offset(0.0D, TILE_PADDING);
            case NORTH -> new Offset(-TILE_PADDING, 0.0D);
        };
    }

    private static Offset verticalTileOffset(SeatWind wind) {
        double amount = TILE_WIDTH + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset halfVerticalTileOffset(SeatWind wind) {
        return multiply(verticalTileOffset(wind), 0.5D);
    }

    private static Offset horizontalTileOffset(SeatWind wind) {
        double amount = TILE_HEIGHT + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset halfHorizontalTileOffset(SeatWind wind) {
        return multiply(horizontalTileOffset(wind), 0.5D);
    }

    private static Offset kakanOffset(SeatWind wind) {
        double amount = TILE_WIDTH + TILE_PADDING;
        return switch (wind) {
            case EAST -> new Offset(-amount, 0.0D);
            case SOUTH -> new Offset(0.0D, -amount);
            case WEST -> new Offset(amount, 0.0D);
            case NORTH -> new Offset(0.0D, amount);
        };
    }

    private static Offset horizontalTileGravityOffset(SeatWind wind) {
        double amount = (TILE_HEIGHT - TILE_WIDTH) / 2.0D;
        return switch (wind) {
            case EAST -> new Offset(amount, 0.0D);
            case SOUTH -> new Offset(0.0D, amount);
            case WEST -> new Offset(-amount, 0.0D);
            case NORTH -> new Offset(0.0D, -amount);
        };
    }

    private static Offset offsetAcrossSeat(SeatWind wind, double amount) {
        return switch (wind) {
            case EAST -> new Offset(0.0D, amount);
            case SOUTH -> new Offset(-amount, 0.0D);
            case WEST -> new Offset(0.0D, -amount);
            case NORTH -> new Offset(amount, 0.0D);
        };
    }

    private static Offset add(Offset first, Offset second) {
        return new Offset(first.x() + second.x(), first.z() + second.z());
    }

    private static Offset multiply(Offset offset, double factor) {
        return new Offset(offset.x() * factor, offset.z() * factor);
    }

    private static Offset negate(Offset offset) {
        return multiply(offset, -1.0D);
    }

    private static Color seatLabelColor(SeatWind wind, boolean active) {
        if (active) {
            return Color.fromARGB(148, 255, 220, 70);
        }
        return switch (wind) {
            case EAST -> Color.fromARGB(132, 255, 183, 0);
            case SOUTH -> Color.fromARGB(132, 72, 217, 92);
            case WEST -> Color.fromARGB(132, 120, 120, 120);
            case NORTH -> Color.fromARGB(132, 86, 148, 255);
        };
    }

    private record Offset(double x, double z) {
    }
}
