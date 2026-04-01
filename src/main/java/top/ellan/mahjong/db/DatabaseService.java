package top.ellan.mahjong.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin;
import top.ellan.mahjong.config.PluginSettings;
import top.ellan.mahjong.error.MahjongErrorCode;
import top.ellan.mahjong.error.MahjongInfrastructureException;
import top.ellan.mahjong.riichi.RoundResolution;
import top.ellan.mahjong.riichi.model.MahjongRule;
import top.ellan.mahjong.riichi.model.ScoreItem;
import top.ellan.mahjong.riichi.model.ScoreSettlement;
import top.ellan.mahjong.riichi.model.YakuSettlement;
import top.ellan.mahjong.table.core.MahjongVariant;
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

public final class DatabaseService {
    private final MahjongPaperPlugin plugin;
    private final PluginSettings.DatabaseSettings databaseSettings;
    private final String databaseType;
    private final SqlDialect sqlDialect;
    private final RankProfileUpsertStrategy rankProfileUpsertStrategy;
    private final HikariDataSource dataSource;

    public DatabaseService(MahjongPaperPlugin plugin, PluginSettings.DatabaseSettings settings) throws InitializationException {
        this.plugin = plugin;
        this.databaseSettings = settings;
        this.databaseType = normalizedType(settings == null ? "h2" : settings.type());
        this.sqlDialect = SqlDialect.fromDatabaseType(this.databaseType);
        this.rankProfileUpsertStrategy = this.createRankProfileUpsertStrategy();

        HikariDataSource initialized = null;
        try {
            initialized = new HikariDataSource(this.hikariConfig());
            this.initializeSchema(initialized);
        } catch (RuntimeException | SQLException ex) {
            if (initialized != null) {
                initialized.close();
            }
            throw this.wrapInitializationException(ex);
        }
        this.dataSource = initialized;
    }

    public static boolean isEnabled(PluginSettings.DatabaseSettings settings) {
        return settings == null || settings.enabled();
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
                MahjongInfrastructureException failure = new MahjongInfrastructureException(
                    MahjongErrorCode.DATABASE_OPERATION_FAILED,
                    MahjongErrorCode.DATABASE_OPERATION_FAILED.publicMessage(),
                    ex
                );
                this.plugin.getLogger().log(
                    failure.logLevel(),
                    failure.code().name() + " operation=persist-round-result databaseType=" + this.databaseType,
                    ex
                );
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
                MahjongInfrastructureException failure = new MahjongInfrastructureException(
                    MahjongErrorCode.DATABASE_OPERATION_FAILED,
                    MahjongErrorCode.DATABASE_OPERATION_FAILED.publicMessage(),
                    ex
                );
                this.plugin.getLogger().log(
                    failure.logLevel(),
                    failure.code().name() + " operation=persist-match-ranks databaseType=" + this.databaseType,
                    ex
                );
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

    public List<PersistentTableRecord> loadPersistentTables() throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT table_id, world_name, center_x, center_y, center_z, variant, bot_match,
                        rule_length, rule_thinking_time, rule_starting_points, rule_min_points_to_win,
                        rule_minimum_han, rule_spectate, rule_red_five, rule_open_tanyao,
                        rule_local_yaku, rule_ron_mode, rule_riichi_profile
                 FROM persistent_table
                 ORDER BY table_id
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            List<PersistentTableRecord> rows = new ArrayList<>();
            while (resultSet.next()) {
                String id = resultSet.getString("table_id");
                MahjongRule rule = this.readPersistentRule(resultSet);
                MahjongVariant variant = this.parseEnum(
                    resultSet.getString("variant"),
                    MahjongVariant.class,
                    MahjongVariant.RIICHI,
                    "persistent_table.variant"
                );
                rows.add(new PersistentTableRecord(
                    id,
                    resultSet.getString("world_name"),
                    resultSet.getDouble("center_x"),
                    resultSet.getDouble("center_y"),
                    resultSet.getDouble("center_z"),
                    variant,
                    rule,
                    resultSet.getBoolean("bot_match")
                ));
            }
            return List.copyOf(rows);
        }
    }

    public void replacePersistentTables(List<PersistentTableRecord> tables) throws SQLException {
        List<PersistentTableRecord> safeTables = tables == null ? List.of() : List.copyOf(tables);
        try (Connection connection = this.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement clear = connection.prepareStatement("DELETE FROM persistent_table");
                 PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO persistent_table (
                         table_id, world_name, center_x, center_y, center_z, variant, bot_match,
                         rule_length, rule_thinking_time, rule_starting_points, rule_min_points_to_win,
                         rule_minimum_han, rule_spectate, rule_red_five, rule_open_tanyao,
                         rule_local_yaku, rule_ron_mode, rule_riichi_profile, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                clear.executeUpdate();
                for (PersistentTableRecord table : safeTables) {
                    this.bindPersistentTable(insert, table);
                    insert.addBatch();
                }
                if (!safeTables.isEmpty()) {
                    insert.executeBatch();
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

    private HikariConfig hikariConfig() {
        HikariConfig hikari = new HikariConfig();
        PluginSettings.DatabasePoolSettings pool = this.databaseSettings == null
            ? new PluginSettings.DatabasePoolSettings(10, 2, 10000L)
            : this.databaseSettings.pool();
        hikari.setPoolName("MahjongPaper-" + this.databaseType.toUpperCase(Locale.ROOT));
        hikari.setMaximumPoolSize(pool == null ? 10 : pool.maxSize());
        hikari.setMinimumIdle(pool == null ? 2 : pool.minIdle());
        hikari.setConnectionTimeout(pool == null ? 10000L : pool.connectionTimeoutMillis());
        hikari.setAutoCommit(true);
        hikari.setConnectionTestQuery("SELECT 1");

        if ("h2".equals(this.databaseType)) {
            PluginSettings.DatabaseH2Settings h2 = this.databaseSettings == null ? null : this.databaseSettings.h2();
            String rawPath = Objects.requireNonNull(h2 == null ? "data/mahjongpaper" : h2.path(), "database.h2.path");
            Path resolvedPath = DatabasePaths.resolveH2FilePath(this.plugin.getDataFolder().toPath(), rawPath);
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                parent.toFile().mkdirs();
            }
            hikari.setDriverClassName("org.h2.Driver");
            hikari.setJdbcUrl("jdbc:h2:file:" + resolvedPath.toString().replace('\\', '/')
                + appendH2Parameters(h2 == null ? "" : Objects.toString(h2.parameters(), "")));
            hikari.setUsername(h2 == null ? "sa" : Objects.toString(h2.username(), "sa"));
            hikari.setPassword(h2 == null ? "" : Objects.toString(h2.password(), ""));
            return hikari;
        }

        PluginSettings.DatabaseConnectionSettings connection = this.databaseSettings == null ? null : this.databaseSettings.connection();
        PluginSettings.DatabaseCredentialsSettings credentials = this.databaseSettings == null ? null : this.databaseSettings.credentials();
        String host = Objects.requireNonNull(connection == null ? "127.0.0.1" : connection.host(), "database.host");
        int port = connection == null ? 3306 : connection.port();
        String database = Objects.requireNonNull(connection == null ? "mahjongpaper" : connection.name(), "database.name");
        String parameters = connection == null ? "" : Objects.toString(connection.parameters(), "");

        if ("mysql".equals(this.databaseType)) {
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + (parameters.isBlank() ? "" : "?" + parameters));
        } else {
            hikari.setDriverClassName("org.mariadb.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + (parameters.isBlank() ? "" : "?" + parameters));
        }
        hikari.setUsername(Objects.requireNonNull(credentials == null ? "root" : credentials.username(), "database.username"));
        hikari.setPassword(credentials == null ? "" : Objects.toString(credentials.password(), ""));
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
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS persistent_table (
                    table_id VARCHAR(32) PRIMARY KEY,
                    world_name VARCHAR(128) NULL,
                    center_x DOUBLE NOT NULL,
                    center_y DOUBLE NOT NULL,
                    center_z DOUBLE NOT NULL,
                    variant VARCHAR(16) NOT NULL,
                    bot_match BOOLEAN NOT NULL DEFAULT FALSE,
                    rule_length VARCHAR(16) NOT NULL,
                    rule_thinking_time VARCHAR(16) NOT NULL,
                    rule_starting_points INT NOT NULL,
                    rule_min_points_to_win INT NOT NULL,
                    rule_minimum_han VARCHAR(16) NOT NULL,
                    rule_spectate BOOLEAN NOT NULL,
                    rule_red_five VARCHAR(16) NOT NULL,
                    rule_open_tanyao BOOLEAN NOT NULL,
                    rule_local_yaku BOOLEAN NOT NULL,
                    rule_ron_mode VARCHAR(16) NOT NULL,
                    rule_riichi_profile VARCHAR(16) NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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

    private static String normalizedType(String rawType) {
        String type = Objects.toString(rawType, "h2").trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "mariadb", "mysql", "h2" -> type;
            default -> throw new IllegalArgumentException("Unsupported database.type: " + type);
        };
    }

    private static String appendH2Parameters(String parameters) {
        return parameters.isBlank() ? "" : ";" + parameters;
    }

    private InitializationException wrapInitializationException(Exception cause) {
        Throwable rootCause = rootCause(cause);
        return new InitializationException(this.databaseType, this.userFacingReason(rootCause), cause, rootCause);
    }

    private String userFacingReason(Throwable rootCause) {
        if ("h2".equals(this.databaseType)) {
            PluginSettings.DatabaseH2Settings h2 = this.databaseSettings == null ? null : this.databaseSettings.h2();
            String rawPath = h2 == null ? "data/mahjongpaper" : Objects.toString(h2.path(), "data/mahjongpaper");
            Path resolvedPath = DatabasePaths.resolveH2FilePath(this.plugin.getDataFolder().toPath(), rawPath);
            return "Could not open the H2 database file. Resolved path: " + resolvedPath
                + ". Check database.h2.path and make sure the plugin folder is writable.";
        }

        PluginSettings.DatabaseConnectionSettings connection = this.databaseSettings == null ? null : this.databaseSettings.connection();
        String host = connection == null ? "127.0.0.1" : Objects.toString(connection.host(), "127.0.0.1");
        int port = connection == null ? 3306 : connection.port();
        String database = connection == null ? "mahjongpaper" : Objects.toString(connection.name(), "mahjongpaper");
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
        this.rankProfileUpsertStrategy.upsert(connection, playerId, profile);
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

    private RankProfileUpsertStrategy createRankProfileUpsertStrategy() {
        return switch (this.sqlDialect) {
            case H2 -> this::upsertRankProfileForH2;
            case MYSQL_FAMILY -> this::upsertRankProfileForMySqlFamily;
        };
    }

    private void upsertRankProfileForH2(Connection connection, java.util.UUID playerId, MahjongSoulRankProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            MERGE INTO player_rank (
                player_uuid, display_name, rank_tier, rank_level, rank_points,
                total_matches, first_places, second_places, third_places, fourth_places, updated_at
            ) KEY (player_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            this.bindRankProfile(statement, playerId, profile, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private void upsertRankProfileForMySqlFamily(Connection connection, java.util.UUID playerId, MahjongSoulRankProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_rank (
                player_uuid, display_name, rank_tier, rank_level, rank_points,
                total_matches, first_places, second_places, third_places, fourth_places, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                rank_tier = VALUES(rank_tier),
                rank_level = VALUES(rank_level),
                rank_points = VALUES(rank_points),
                total_matches = VALUES(total_matches),
                first_places = VALUES(first_places),
                second_places = VALUES(second_places),
                third_places = VALUES(third_places),
                fourth_places = VALUES(fourth_places),
                updated_at = VALUES(updated_at)
            """)) {
            this.bindRankProfile(statement, playerId, profile, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private void bindRankProfile(PreparedStatement statement, java.util.UUID playerId, MahjongSoulRankProfile profile, Timestamp updatedAt) throws SQLException {
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
        statement.setTimestamp(11, updatedAt);
    }

    private MahjongRule readPersistentRule(ResultSet resultSet) throws SQLException {
        MahjongRule rule = new MahjongRule();
        rule.setLength(this.parseEnum(resultSet.getString("rule_length"), MahjongRule.GameLength.class, rule.getLength(), "persistent_table.rule_length"));
        rule.setThinkingTime(this.parseEnum(resultSet.getString("rule_thinking_time"), MahjongRule.ThinkingTime.class, rule.getThinkingTime(), "persistent_table.rule_thinking_time"));
        rule.setStartingPoints(resultSet.getInt("rule_starting_points"));
        rule.setMinPointsToWin(resultSet.getInt("rule_min_points_to_win"));
        rule.setMinimumHan(this.parseEnum(resultSet.getString("rule_minimum_han"), MahjongRule.MinimumHan.class, rule.getMinimumHan(), "persistent_table.rule_minimum_han"));
        rule.setSpectate(resultSet.getBoolean("rule_spectate"));
        rule.setRedFive(this.parseEnum(resultSet.getString("rule_red_five"), MahjongRule.RedFive.class, rule.getRedFive(), "persistent_table.rule_red_five"));
        rule.setOpenTanyao(resultSet.getBoolean("rule_open_tanyao"));
        rule.setLocalYaku(resultSet.getBoolean("rule_local_yaku"));
        rule.setRonMode(this.parseEnum(resultSet.getString("rule_ron_mode"), MahjongRule.RonMode.class, rule.getRonMode(), "persistent_table.rule_ron_mode"));
        rule.setRiichiProfile(this.parseEnum(resultSet.getString("rule_riichi_profile"), MahjongRule.RiichiProfile.class, rule.getRiichiProfile(), "persistent_table.rule_riichi_profile"));
        return rule;
    }

    private void bindPersistentTable(PreparedStatement statement, PersistentTableRecord table) throws SQLException {
        MahjongRule rule = table.rule() == null ? new MahjongRule() : table.rule();
        statement.setString(1, table.id());
        statement.setString(2, table.worldName());
        statement.setDouble(3, table.x());
        statement.setDouble(4, table.y());
        statement.setDouble(5, table.z());
        statement.setString(6, (table.variant() == null ? MahjongVariant.RIICHI : table.variant()).name());
        statement.setBoolean(7, table.botMatch());
        statement.setString(8, rule.getLength().name());
        statement.setString(9, rule.getThinkingTime().name());
        statement.setInt(10, rule.getStartingPoints());
        statement.setInt(11, rule.getMinPointsToWin());
        statement.setString(12, rule.getMinimumHan().name());
        statement.setBoolean(13, rule.getSpectate());
        statement.setString(14, rule.getRedFive().name());
        statement.setBoolean(15, rule.getOpenTanyao());
        statement.setBoolean(16, rule.getLocalYaku());
        statement.setString(17, rule.getRonMode().name());
        statement.setString(18, rule.getRiichiProfile().name());
        statement.setTimestamp(19, Timestamp.from(Instant.now()));
    }

    private <E extends Enum<E>> E parseEnum(String rawValue, Class<E> enumType, E fallback, String columnName) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            this.plugin.getLogger().warning(
                "Invalid database value '" + rawValue + "' for " + columnName + ", using " + fallback.name() + " instead."
            );
            return fallback;
        }
    }

    private interface RankProfileUpsertStrategy {
        void upsert(Connection connection, java.util.UUID playerId, MahjongSoulRankProfile profile) throws SQLException;
    }

    private enum SqlDialect {
        H2,
        MYSQL_FAMILY;

        private static SqlDialect fromDatabaseType(String databaseType) {
            if ("h2".equals(databaseType)) {
                return H2;
            }
            return MYSQL_FAMILY;
        }
    }

    public record PersistentTableRecord(
        String id,
        String worldName,
        double x,
        double y,
        double z,
        MahjongVariant variant,
        MahjongRule rule,
        boolean botMatch
    ) {
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



