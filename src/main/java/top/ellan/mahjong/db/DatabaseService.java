package top.ellan.mahjong.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import top.ellan.mahjong.config.ConfigAccess;
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.riichi.RoundResolution;
import top.ellan.mahjong.riichi.model.MahjongRule;
import top.ellan.mahjong.riichi.model.ScoreItem;
import top.ellan.mahjong.riichi.model.ScoreSettlement;
import top.ellan.mahjong.riichi.model.YakuSettlement;
import top.ellan.mahjong.table.core.MahjongTableSession;
import top.ellan.mahjong.table.core.TableFinalStanding;
import java.net.ConnectException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.bukkit.configuration.ConfigurationSection;

public final class DatabaseService {
    private final MahjongPaperPlugin plugin;
    private final String databaseType;
    private final HikariDataSource dataSource;

    public DatabaseService(MahjongPaperPlugin plugin, ConfigurationSection config) throws InitializationException {
        this.plugin = plugin;
        this.databaseType = normalizedType(config);

        HikariDataSource initialized = null;
        try {
            initialized = new HikariDataSource(this.hikariConfig(config));
            this.initializeSchema(initialized);
        } catch (RuntimeException | SQLException ex) {
            if (initialized != null) {
                initialized.close();
            }
            throw this.wrapInitializationException(config, ex);
        }
        this.dataSource = initialized;
    }

    public static boolean isEnabled(ConfigurationSection config) {
        return ConfigAccess.bool(config, true, "enabled");
    }

    public void close() {
        this.dataSource.close();
    }

    public String databaseType() {
        return this.databaseType;
    }

    public boolean rankingEnabled() {
        return this.plugin.settings().rankingEnabled();
    }

    public void persistRoundResultAsync(MahjongTableSession session, RoundResolution resolution) {
        this.plugin.debug().log("database", "Queueing round persistence for table=" + session.id() + " title=" + resolution.getTitle());
        this.plugin.async().execute("persist-round-result", () -> {
            try {
                this.persistRoundResult(session, resolution);
                this.plugin.debug().log("database", "Persisted round result for table=" + session.id() + " title=" + resolution.getTitle());
            } catch (SQLException ex) {
                this.plugin.getLogger().warning("Failed to persist round result to " + this.databaseType + ": " + ex.getMessage());
            }
        });
    }

    public void persistMatchRanksAsync(String tableId, MahjongRule.GameLength length, List<TableFinalStanding> standings) {
        if (!this.rankingEnabled() || standings.isEmpty()) {
            return;
        }
        List<TableFinalStanding> snapshot = List.copyOf(standings);
        this.plugin.debug().log("database", "Queueing rank persistence for table=" + tableId + " standings=" + snapshot.size());
        this.plugin.async().execute("persist-match-ranks", () -> {
            try {
                this.persistMatchRanks(tableId, length, snapshot);
                this.plugin.debug().log("database", "Persisted match rank results for table=" + tableId);
            } catch (SQLException ex) {
                this.plugin.getLogger().warning("Failed to persist match rank results to " + this.databaseType + ": " + ex.getMessage());
            }
        });
    }

    public MahjongSoulRankProfile loadRankProfile(java.util.UUID playerId, String displayName) throws SQLException {
        if (!this.rankingEnabled()) {
            return MahjongSoulRankProfile.defaultProfile(playerId, displayName);
        }
        try (Connection connection = this.dataSource.getConnection()) {
            MahjongSoulRankProfile profile = this.selectRankProfile(connection, playerId);
            return profile == null ? MahjongSoulRankProfile.defaultProfile(playerId, displayName) : profile;
        }
    }

    void persistRoundResultSync(MahjongTableSession session, RoundResolution resolution) throws SQLException {
        this.persistRoundResult(session, resolution);
    }

    void persistMatchRanksSync(String tableId, MahjongRule.GameLength length, List<TableFinalStanding> standings) throws SQLException {
        this.persistMatchRanks(tableId, length, standings);
    }

    private HikariConfig hikariConfig(ConfigurationSection config) {
        HikariConfig hikari = new HikariConfig();
        ConfigurationSection pool = pool(config);
        hikari.setPoolName("MahjongPaper-" + this.databaseType.toUpperCase(Locale.ROOT));
        hikari.setMaximumPoolSize(ConfigAccess.integer(pool, 10, "maxSize", "maximumPoolSize"));
        hikari.setMinimumIdle(ConfigAccess.integer(pool, 2, "minIdle", "minimumIdle"));
        hikari.setConnectionTimeout(ConfigAccess.longValue(pool, 10000L, "connectionTimeoutMillis"));
        hikari.setAutoCommit(true);
        hikari.setConnectionTestQuery("SELECT 1");

        if ("h2".equals(this.databaseType)) {
            ConfigurationSection h2 = this.h2(config);
            String rawPath = Objects.requireNonNull(ConfigAccess.string(h2, null, "path"), "database.h2.path");
            Path resolvedPath = DatabasePaths.resolveH2FilePath(this.plugin.getDataFolder().toPath(), rawPath);
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                parent.toFile().mkdirs();
            }
            hikari.setDriverClassName("org.h2.Driver");
            hikari.setJdbcUrl("jdbc:h2:file:" + resolvedPath.toString().replace('\\', '/') + appendH2Parameters(ConfigAccess.string(h2, "", "parameters")));
            hikari.setUsername(ConfigAccess.string(h2, "sa", "username"));
            hikari.setPassword(ConfigAccess.string(h2, "", "password"));
            return hikari;
        }

        String host = Objects.requireNonNull(ConfigAccess.string(config, null, "connection.host", "host"), "database.host");
        int port = ConfigAccess.integer(config, 3306, "connection.port", "port");
        String database = Objects.requireNonNull(ConfigAccess.string(config, null, "connection.name", "name"), "database.name");
        String parameters = ConfigAccess.string(config, "", "connection.parameters", "parameters");

        if ("mysql".equals(this.databaseType)) {
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + (parameters.isBlank() ? "" : "?" + parameters));
        } else {
            hikari.setDriverClassName("org.mariadb.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + (parameters.isBlank() ? "" : "?" + parameters));
        }
        hikari.setUsername(Objects.requireNonNull(ConfigAccess.string(config, null, "credentials.username", "username"), "database.username"));
        hikari.setPassword(ConfigAccess.string(config, "", "credentials.password", "password"));
        return hikari;
    }

    private void initializeSchema(HikariDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS round_history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    table_id VARCHAR(32) NOT NULL,
                    resolution_title VARCHAR(128) NOT NULL,
                    round_display VARCHAR(64) NOT NULL,
                    dealer_name VARCHAR(64) NOT NULL,
                    draw_type VARCHAR(32) NULL,
                    wall_count INT NOT NULL,
                    dice_points INT NOT NULL,
                    dora_indicators TEXT NOT NULL,
                    ura_dora_indicators TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS round_player_result (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    round_history_id BIGINT NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    display_name VARCHAR(64) NOT NULL,
                    score_origin INT NULL,
                    score_change INT NULL,
                    score_total INT NULL,
                    winning BOOLEAN NOT NULL DEFAULT FALSE,
                    riichi BOOLEAN NOT NULL DEFAULT FALSE,
                    fu INT NULL,
                    han INT NULL,
                    score INT NULL,
                    winning_tile VARCHAR(32) NULL,
                    yaku_summary TEXT NULL,
                    hand_summary TEXT NULL,
                    meld_summary TEXT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_round_player_result_history
                        FOREIGN KEY (round_history_id) REFERENCES round_history(id)
                        ON DELETE CASCADE
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_rank (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    display_name VARCHAR(64) NOT NULL,
                    rank_tier VARCHAR(16) NOT NULL,
                    rank_level INT NOT NULL,
                    rank_points INT NOT NULL,
                    total_matches INT NOT NULL DEFAULT 0,
                    first_places INT NOT NULL DEFAULT 0,
                    second_places INT NOT NULL DEFAULT 0,
                    third_places INT NOT NULL DEFAULT 0,
                    fourth_places INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rank_history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    table_id VARCHAR(32) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    display_name VARCHAR(64) NOT NULL,
                    room_code VARCHAR(16) NOT NULL,
                    match_length VARCHAR(16) NOT NULL,
                    place INT NOT NULL,
                    raw_score INT NOT NULL,
                    rank_point_change INT NOT NULL,
                    previous_tier VARCHAR(16) NOT NULL,
                    previous_level INT NOT NULL,
                    previous_points INT NOT NULL,
                    updated_tier VARCHAR(16) NOT NULL,
                    updated_level INT NOT NULL,
                    updated_points INT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    private void persistRoundResult(MahjongTableSession session, RoundResolution resolution) throws SQLException {
        Map<String, ScoreItem> scoreItemsByUuid = this.scoreItemsByUuid(resolution.getScoreSettlement());
        Map<String, YakuSettlement> yakuByUuid = this.yakuSettlementsByUuid(resolution.getYakuSettlements());

        try (Connection connection = this.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long roundHistoryId = this.insertRoundHistory(connection, session, resolution);
                for (String uuid : unionKeys(scoreItemsByUuid, yakuByUuid)) {
                    this.insertPlayerResult(connection, roundHistoryId, uuid, session, scoreItemsByUuid.get(uuid), yakuByUuid.get(uuid));
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private long insertRoundHistory(Connection connection, MahjongTableSession session, RoundResolution resolution) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO round_history (
                table_id, resolution_title, round_display, dealer_name, draw_type,
                wall_count, dice_points, dora_indicators, ura_dora_indicators, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, session.id());
            statement.setString(2, resolution.getTitle());
            statement.setString(3, session.roundDisplay());
            statement.setString(4, session.dealerName());
            statement.setString(5, resolution.getDraw() == null ? null : resolution.getDraw().name());
            statement.setInt(6, session.remainingWallCount());
            statement.setInt(7, session.dicePoints());
            statement.setString(8, this.joinTokens(session.doraIndicators()));
            statement.setString(9, this.joinTokens(session.uraDoraIndicators()));
            statement.setTimestamp(10, Timestamp.from(Instant.now()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a generated round_history id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertPlayerResult(
        Connection connection,
        long roundHistoryId,
        String uuid,
        MahjongTableSession session,
        ScoreItem scoreItem,
        YakuSettlement yakuSettlement
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO round_player_result (
                round_history_id, player_uuid, display_name, score_origin, score_change, score_total,
                winning, riichi, fu, han, score, winning_tile, yaku_summary, hand_summary, meld_summary, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            String displayName = scoreItem != null ? scoreItem.getDisplayName() : session.displayName(java.util.UUID.fromString(uuid));
            Integer scoreOrigin = scoreItem == null ? null : scoreItem.getScoreOrigin();
            Integer scoreChange = scoreItem == null ? null : scoreItem.getScoreChange();
            Integer scoreTotal = scoreItem == null ? null : scoreItem.getScoreOrigin() + scoreItem.getScoreChange();

            statement.setLong(1, roundHistoryId);
            statement.setString(2, uuid);
            statement.setString(3, displayName);
            setNullableInt(statement, 4, scoreOrigin);
            setNullableInt(statement, 5, scoreChange);
            setNullableInt(statement, 6, scoreTotal);
            statement.setBoolean(7, yakuSettlement != null);
            statement.setBoolean(8, yakuSettlement != null && yakuSettlement.getRiichi());
            setNullableInt(statement, 9, yakuSettlement == null ? null : yakuSettlement.getFu());
            setNullableInt(statement, 10, yakuSettlement == null ? null : yakuSettlement.getHan());
            setNullableInt(statement, 11, yakuSettlement == null ? null : yakuSettlement.getScore());
            statement.setString(12, yakuSettlement == null ? null : yakuSettlement.getWinningTile().name());
            statement.setString(13, yakuSettlement == null ? null : this.yakuSummary(yakuSettlement));
            statement.setString(14, yakuSettlement == null ? null : this.joinTokens(yakuSettlement.getHands()));
            statement.setString(15, yakuSettlement == null ? null : this.meldSummary(yakuSettlement));
            statement.setTimestamp(16, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private Map<String, ScoreItem> scoreItemsByUuid(ScoreSettlement settlement) {
        Map<String, ScoreItem> results = new HashMap<>();
        if (settlement == null) {
            return results;
        }
        for (ScoreItem scoreItem : settlement.getScoreList()) {
            results.put(scoreItem.getStringUUID(), scoreItem);
        }
        return results;
    }

    private Map<String, YakuSettlement> yakuSettlementsByUuid(List<YakuSettlement> settlements) {
        Map<String, YakuSettlement> results = new HashMap<>();
        for (YakuSettlement settlement : settlements) {
            results.put(settlement.getUuid(), settlement);
        }
        return results;
    }

    private void persistMatchRanks(String tableId, MahjongRule.GameLength gameLength, List<TableFinalStanding> standings) throws SQLException {
        List<TableFinalStanding> humanStandings = standings.stream()
            .filter(standing -> !standing.bot())
            .toList();
        if (humanStandings.isEmpty()) {
            return;
        }

        MahjongSoulRankRules.MatchLength matchLength = MahjongSoulRankRules.matchLength(gameLength);
        MahjongSoulRankRules.Room room = MahjongSoulRankRules.roomFor(
            matchLength,
            this.plugin.settings().rankingEastRoom(),
            this.plugin.settings().rankingSouthRoom()
        );

        try (Connection connection = this.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<java.util.UUID, MahjongSoulRankProfile> currentProfiles = new HashMap<>();
                boolean allPlayersCelestial = true;
                for (TableFinalStanding standing : humanStandings) {
                    MahjongSoulRankProfile current = this.selectRankProfile(connection, standing.playerId());
                    if (current == null) {
                        current = MahjongSoulRankProfile.defaultProfile(standing.playerId(), standing.displayName());
                    }
                    currentProfiles.put(standing.playerId(), current);
                    if (!current.isCelestial()) {
                        allPlayersCelestial = false;
                    }
                }

                for (TableFinalStanding standing : humanStandings) {
                    MahjongSoulRankProfile current = currentProfiles.get(standing.playerId());
                    MahjongSoulRankRules.RankedMatchResult result = MahjongSoulRankRules.applyMatch(
                        current,
                        room,
                        matchLength,
                        standing.place(),
                        standing.points(),
                        allPlayersCelestial
                    );
                    this.upsertRankProfile(connection, result.updated().playerId(), result.updated());
                    this.insertRankHistory(connection, tableId, standing.displayName(), result);
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static List<String> unionKeys(Map<String, ScoreItem> scoreItems, Map<String, YakuSettlement> yakuSettlements) {
        List<String> keys = new ArrayList<>(scoreItems.keySet());
        for (String uuid : yakuSettlements.keySet()) {
            if (!scoreItems.containsKey(uuid)) {
                keys.add(uuid);
            }
        }
        return keys;
    }

    private String yakuSummary(YakuSettlement settlement) {
        StringJoiner joiner = new StringJoiner(", ");
        settlement.getYakuList().forEach(joiner::add);
        settlement.getYakumanList().forEach(joiner::add);
        settlement.getDoubleYakumanList().forEach(yaku -> joiner.add(yaku.name()));
        if (settlement.getNagashiMangan()) {
            joiner.add("NAGASHI_MANGAN");
        }
        return joiner.toString();
    }

    private String meldSummary(YakuSettlement settlement) {
        StringJoiner joiner = new StringJoiner(" | ");
        settlement.getFuuroList().forEach(pair -> joiner.add((pair.getFirst() ? "open" : "closed") + ":" + this.joinTokens(pair.getSecond())));
        return joiner.toString();
    }

    private String joinTokens(List<?> values) {
        StringJoiner joiner = new StringJoiner(" ");
        values.forEach(value -> joiner.add(String.valueOf(value).toLowerCase(Locale.ROOT)));
        return joiner.toString();
    }

    private static ConfigurationSection pool(ConfigurationSection config) {
        ConfigurationSection pool = ConfigAccess.firstSection(config, "pool");
        if (pool == null) {
            throw new IllegalStateException("database.pool section is required");
        }
        return pool;
    }

    private ConfigurationSection h2(ConfigurationSection config) {
        return Objects.requireNonNull(ConfigAccess.firstSection(config, "h2"), "database.h2");
    }

    private static String normalizedType(ConfigurationSection config) {
        String type = ConfigAccess.string(config, "h2", "connection.type", "type").trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "mariadb", "mysql", "h2" -> type;
            default -> throw new IllegalArgumentException("Unsupported database.type: " + type);
        };
    }

    private static String appendH2Parameters(String parameters) {
        return parameters.isBlank() ? "" : ";" + parameters;
    }

    private InitializationException wrapInitializationException(ConfigurationSection config, Exception cause) {
        Throwable rootCause = rootCause(cause);
        return new InitializationException(this.databaseType, this.userFacingReason(config, rootCause), cause, rootCause);
    }

    private String userFacingReason(ConfigurationSection config, Throwable rootCause) {
        if ("h2".equals(this.databaseType)) {
            ConfigurationSection h2 = this.h2(config);
            String rawPath = ConfigAccess.string(h2, "data/mahjongpaper", "path");
            Path resolvedPath = DatabasePaths.resolveH2FilePath(this.plugin.getDataFolder().toPath(), rawPath);
            return "Could not open the H2 database file. Resolved path: " + resolvedPath
                + ". Check database.h2.path and make sure the plugin folder is writable.";
        }

        String host = ConfigAccess.string(config, "127.0.0.1", "connection.host", "host");
        int port = ConfigAccess.integer(config, 3306, "connection.port", "port");
        String database = ConfigAccess.string(config, "mahjongpaper", "connection.name", "name");
        String target = host + ":" + port + "/" + database;
        String databaseLabel = "mysql".equals(this.databaseType) ? "MySQL" : "MariaDB";
        String message = Objects.toString(rootCause.getMessage(), "");
        String lower = message.toLowerCase(Locale.ROOT);
        if (rootCause instanceof ConnectException || lower.contains("connection refused") || lower.contains("connect")) {
            return "Could not connect to " + databaseLabel + " at " + target
                + ". Check that the database server is running and verify database.connection.host, database.connection.port, database.connection.name, and database.credentials.";
        }
        if (lower.contains("access denied") || lower.contains("authentication")) {
            return databaseLabel + " authentication failed for " + target
                + ". Check database.credentials.username and database.credentials.password.";
        }
        return databaseLabel + " initialization failed for " + target
            + ". Check the database connection settings and network availability.";
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static void setNullableInt(PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, java.sql.Types.INTEGER);
        } else {
            statement.setInt(parameterIndex, value);
        }
    }

    private MahjongSoulRankProfile selectRankProfile(Connection connection, java.util.UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT player_uuid, display_name, rank_tier, rank_level, rank_points,
                   total_matches, first_places, second_places, third_places, fourth_places
            FROM player_rank
            WHERE player_uuid = ?
            """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new MahjongSoulRankProfile(
                    java.util.UUID.fromString(resultSet.getString("player_uuid")),
                    resultSet.getString("display_name"),
                    MahjongSoulRankRules.Tier.valueOf(resultSet.getString("rank_tier")),
                    resultSet.getInt("rank_level"),
                    resultSet.getInt("rank_points"),
                    resultSet.getInt("total_matches"),
                    resultSet.getInt("first_places"),
                    resultSet.getInt("second_places"),
                    resultSet.getInt("third_places"),
                    resultSet.getInt("fourth_places")
                );
            }
        }
    }

    private void upsertRankProfile(Connection connection, java.util.UUID playerId, MahjongSoulRankProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            MERGE INTO player_rank (
                player_uuid, display_name, rank_tier, rank_level, rank_points,
                total_matches, first_places, second_places, third_places, fourth_places, updated_at
            ) KEY (player_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, profile.displayName());
            statement.setString(3, profile.tier().name());
            statement.setInt(4, profile.level());
            statement.setInt(5, profile.rankPoints());
            statement.setInt(6, profile.totalMatches());
            statement.setInt(7, profile.firstPlaces());
            statement.setInt(8, profile.secondPlaces());
            statement.setInt(9, profile.thirdPlaces());
            statement.setInt(10, profile.fourthPlaces());
            statement.setTimestamp(11, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException mergeFailure) {
            this.upsertRankProfilePortable(connection, playerId, profile, mergeFailure);
        }
    }

    private void upsertRankProfilePortable(Connection connection, java.util.UUID playerId, MahjongSoulRankProfile profile, SQLException mergeFailure) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE player_rank SET
                display_name = ?, rank_tier = ?, rank_level = ?, rank_points = ?,
                total_matches = ?, first_places = ?, second_places = ?, third_places = ?, fourth_places = ?, updated_at = ?
            WHERE player_uuid = ?
            """)) {
            update.setString(1, profile.displayName());
            update.setString(2, profile.tier().name());
            update.setInt(3, profile.level());
            update.setInt(4, profile.rankPoints());
            update.setInt(5, profile.totalMatches());
            update.setInt(6, profile.firstPlaces());
            update.setInt(7, profile.secondPlaces());
            update.setInt(8, profile.thirdPlaces());
            update.setInt(9, profile.fourthPlaces());
            update.setTimestamp(10, Timestamp.from(Instant.now()));
            update.setString(11, playerId.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO player_rank (
                player_uuid, display_name, rank_tier, rank_level, rank_points,
                total_matches, first_places, second_places, third_places, fourth_places, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            insert.setString(1, playerId.toString());
            insert.setString(2, profile.displayName());
            insert.setString(3, profile.tier().name());
            insert.setInt(4, profile.level());
            insert.setInt(5, profile.rankPoints());
            insert.setInt(6, profile.totalMatches());
            insert.setInt(7, profile.firstPlaces());
            insert.setInt(8, profile.secondPlaces());
            insert.setInt(9, profile.thirdPlaces());
            insert.setInt(10, profile.fourthPlaces());
            insert.setTimestamp(11, Timestamp.from(Instant.now()));
            insert.executeUpdate();
        } catch (SQLException insertFailure) {
            insertFailure.addSuppressed(mergeFailure);
            throw insertFailure;
        }
    }

    private void insertRankHistory(
        Connection connection,
        String tableId,
        String displayName,
        MahjongSoulRankRules.RankedMatchResult result
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO rank_history (
                table_id, player_uuid, display_name, room_code, match_length, place, raw_score, rank_point_change,
                previous_tier, previous_level, previous_points, updated_tier, updated_level, updated_points, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, tableId);
            statement.setString(2, result.updated().playerId().toString());
            statement.setString(3, displayName);
            statement.setString(4, result.room().name());
            statement.setString(5, result.length().name());
            statement.setInt(6, result.place());
            statement.setInt(7, result.rawScore());
            statement.setInt(8, result.rankPointChange());
            statement.setString(9, result.previous().tier().name());
            statement.setInt(10, result.previous().level());
            statement.setInt(11, result.previous().rankPoints());
            statement.setString(12, result.updated().tier().name());
            statement.setInt(13, result.updated().level());
            statement.setInt(14, result.updated().rankPoints());
            statement.setTimestamp(15, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public static final class InitializationException extends Exception {
        private final String databaseType;
        private final String userFacingReason;
        private final Throwable rootCause;

        public InitializationException(String databaseType, String userFacingReason, Throwable cause, Throwable rootCause) {
            super(userFacingReason, cause);
            this.databaseType = databaseType;
            this.userFacingReason = userFacingReason;
            this.rootCause = rootCause;
        }

        public String databaseType() {
            return this.databaseType;
        }

        public String userFacingReason() {
            return this.userFacingReason;
        }

        public Throwable rootCause() {
            return this.rootCause;
        }
    }
}



