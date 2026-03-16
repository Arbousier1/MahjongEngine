package doublemoon.mahjongcraft.paper.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import doublemoon.mahjongcraft.paper.MahjongPaperPlugin;
import doublemoon.mahjongcraft.paper.riichi.RoundResolution;
import doublemoon.mahjongcraft.paper.riichi.model.ScoreItem;
import doublemoon.mahjongcraft.paper.riichi.model.ScoreSettlement;
import doublemoon.mahjongcraft.paper.riichi.model.YakuSettlement;
import doublemoon.mahjongcraft.paper.table.MahjongTableSession;
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
        return config != null && config.getBoolean("enabled", true);
    }

    public void close() {
        this.dataSource.close();
    }

    public String databaseType() {
        return this.databaseType;
    }

    public void persistRoundResultAsync(MahjongTableSession session, RoundResolution resolution) {
        this.plugin.debug().log("database", "Queueing round persistence for table=" + session.id() + " title=" + resolution.getTitle());
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                this.persistRoundResult(session, resolution);
                this.plugin.debug().log("database", "Persisted round result for table=" + session.id() + " title=" + resolution.getTitle());
            } catch (SQLException ex) {
                this.plugin.getLogger().warning("Failed to persist round result to " + this.databaseType + ": " + ex.getMessage());
            }
        });
    }

    private HikariConfig hikariConfig(ConfigurationSection config) {
        HikariConfig hikari = new HikariConfig();
        ConfigurationSection pool = pool(config);
        hikari.setPoolName("MahjongPaper-" + this.databaseType.toUpperCase(Locale.ROOT));
        hikari.setMaximumPoolSize(pool.getInt("maximumPoolSize", 10));
        hikari.setMinimumIdle(pool.getInt("minimumIdle", 2));
        hikari.setConnectionTimeout(pool.getLong("connectionTimeoutMillis", 10000L));
        hikari.setAutoCommit(true);
        hikari.setConnectionTestQuery("SELECT 1");

        if ("h2".equals(this.databaseType)) {
            ConfigurationSection h2 = this.h2(config);
            String rawPath = Objects.requireNonNull(h2.getString("path"), "database.h2.path");
            Path resolvedPath = DatabasePaths.resolveH2FilePath(this.plugin.getDataFolder().toPath(), rawPath);
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                parent.toFile().mkdirs();
            }
            hikari.setDriverClassName("org.h2.Driver");
            hikari.setJdbcUrl("jdbc:h2:file:" + resolvedPath.toString().replace('\\', '/') + appendH2Parameters(h2.getString("parameters", "")));
            hikari.setUsername(h2.getString("username", "sa"));
            hikari.setPassword(h2.getString("password", ""));
            return hikari;
        }

        String host = Objects.requireNonNull(config.getString("host"), "database.host");
        int port = config.getInt("port", 3306);
        String database = Objects.requireNonNull(config.getString("name"), "database.name");
        String parameters = config.getString("parameters", "");

        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + (parameters.isBlank() ? "" : "?" + parameters));
        hikari.setUsername(Objects.requireNonNull(config.getString("username"), "database.username"));
        hikari.setPassword(config.getString("password", ""));
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
            statement.setInt(6, session.remainingWall().size());
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
        ConfigurationSection pool = config.getConfigurationSection("pool");
        if (pool == null) {
            throw new IllegalStateException("database.pool section is required");
        }
        return pool;
    }

    private ConfigurationSection h2(ConfigurationSection config) {
        return Objects.requireNonNull(config.getConfigurationSection("h2"), "database.h2");
    }

    private static String normalizedType(ConfigurationSection config) {
        String type = config.getString("type", "h2").trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "mariadb", "h2" -> type;
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
            String rawPath = h2.getString("path", "data/mahjongpaper");
            Path resolvedPath = DatabasePaths.resolveH2FilePath(this.plugin.getDataFolder().toPath(), rawPath);
            return "Could not open the H2 database file. Resolved path: " + resolvedPath
                + ". Check database.h2.path and make sure the plugin folder is writable.";
        }

        String host = config.getString("host", "127.0.0.1");
        int port = config.getInt("port", 3306);
        String database = config.getString("name", "mahjongpaper");
        String target = host + ":" + port + "/" + database;
        String message = Objects.toString(rootCause.getMessage(), "");
        String lower = message.toLowerCase(Locale.ROOT);
        if (rootCause instanceof ConnectException || lower.contains("connection refused") || lower.contains("connect")) {
            return "Could not connect to MariaDB at " + target
                + ". Check that the database server is running and verify database.host, database.port, database.name, database.username, and database.password.";
        }
        if (lower.contains("access denied") || lower.contains("authentication")) {
            return "MariaDB authentication failed for " + target
                + ". Check database.username and database.password.";
        }
        return "MariaDB initialization failed for " + target
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
