package maurxp.betterdragon.leaderboard.storage;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;
import maurxp.betterdragon.persistence.DatabaseManager;
import maurxp.betterdragon.persistence.SchemaInitializer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación SQLite de {@link LeaderboardStorage} persistente e idempotente.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Transacciones Atómicas:</b> Cada registro de batalla se ejecuta en una única transacción
 *       SQLite (batalla + participaciones + agregados de jugadores). Si falla, se realiza rollback completo.</li>
 *   <li><b>Idempotencia Estricta:</b> La inserción de la batalla utiliza {@code ON CONFLICT(battle_id) DO NOTHING}.
 *       Si la fila no es insertada, la transacción se revierte de inmediato sin alterar acumulados.</li>
 *   <li><b>Agregación Eficiente:</b> El acumulado en {@code bd_leaderboard_players} utiliza
 *       {@code INSERT ... ON CONFLICT(player_uuid) DO UPDATE} con funciones {@code MAX} y {@code MIN} nativas de SQLite.</li>
 *   <li><b>Single Writer Seguro:</b> Todas las operaciones se delegan al {@link DatabaseManager} en su hilo
 *       dedicado sin bloquear en ningún momento el hilo del servidor.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SQLiteLeaderboardStorage implements LeaderboardStorage {

    public static final int DEFAULT_RANKING_LIMIT = 10;
    public static final int MAX_RANKING_LIMIT = 1000;

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public SQLiteLeaderboardStorage(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    @Override
    public CompletableFuture<Boolean> recordBattle(LeaderboardBattleRecord battle, List<LeaderboardParticipantRecord> participants) {
        Objects.requireNonNull(battle, "battle no puede ser nulo");
        Objects.requireNonNull(participants, "participants no puede ser nulo");

        return databaseManager.supplyAsync(conn -> {
            boolean initialAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // 1. Insertar batalla con ON CONFLICT DO NOTHING para garantizar idempotencia atómica
                String insertBattleSql = "INSERT INTO " + SchemaInitializer.TABLE_LEADERBOARD_BATTLES
                        + " (battle_id, completed_at, world_name, slayer_uuid) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(battle_id) DO NOTHING;";
                int battleInserted;
                try (PreparedStatement pstmt = conn.prepareStatement(insertBattleSql)) {
                    pstmt.setString(1, battle.battleId().asString());
                    pstmt.setLong(2, battle.completedAt().toEpochMilli());
                    pstmt.setString(3, battle.worldName());
                    if (battle.slayerUuid() != null) {
                        pstmt.setString(4, battle.slayerUuid().toString());
                    } else {
                        pstmt.setNull(4, Types.VARCHAR);
                    }
                    battleInserted = pstmt.executeUpdate();
                }

                // Si la batalla ya existía (battleInserted == 0), abortar transacción sin mutar participaciones ni acumulados
                if (battleInserted == 0) {
                    conn.rollback();
                    return false;
                }

                // 2. Insertar historial de participaciones si hay participantes
                if (!participants.isEmpty()) {
                    String insertPartSql = "INSERT INTO " + SchemaInitializer.TABLE_LEADERBOARD_PARTICIPATION
                            + " (battle_id, player_uuid, historical_name, damage, first_hit_sequence, was_slayer, participated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?);";
                    try (PreparedStatement pstmt = conn.prepareStatement(insertPartSql)) {
                        for (LeaderboardParticipantRecord p : participants) {
                            pstmt.setString(1, p.battleId().asString());
                            pstmt.setString(2, p.playerUuid().toString());
                            pstmt.setString(3, p.historicalName());
                            pstmt.setDouble(4, p.damage());
                            pstmt.setLong(5, p.firstHitSequence());
                            pstmt.setInt(6, p.wasSlayer() ? 1 : 0);
                            pstmt.setLong(7, p.participatedAt().toEpochMilli());
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }

                    // 3. Upsert de estadísticas acumuladas por jugador
                    String upsertPlayerSql = "INSERT INTO " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS
                            + " (player_uuid, last_known_name, battles_participated, total_damage, highest_damage, slayer_count, first_participation_at, last_participation_at) "
                            + "VALUES (?, ?, 1, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(player_uuid) DO UPDATE SET "
                            + "  last_known_name = excluded.last_known_name, "
                            + "  battles_participated = " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ".battles_participated + 1, "
                            + "  total_damage = " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ".total_damage + excluded.total_damage, "
                            + "  highest_damage = MAX(" + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ".highest_damage, excluded.highest_damage), "
                            + "  slayer_count = " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ".slayer_count + excluded.slayer_count, "
                            + "  first_participation_at = MIN(" + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ".first_participation_at, excluded.first_participation_at), "
                            + "  last_participation_at = MAX(" + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ".last_participation_at, excluded.last_participation_at);";
                    try (PreparedStatement pstmt = conn.prepareStatement(upsertPlayerSql)) {
                        for (LeaderboardParticipantRecord p : participants) {
                            pstmt.setString(1, p.playerUuid().toString());
                            pstmt.setString(2, p.lastKnownName());
                            pstmt.setDouble(3, p.damage());
                            pstmt.setDouble(4, p.damage());
                            pstmt.setInt(5, p.wasSlayer() ? 1 : 0);
                            pstmt.setLong(6, p.participatedAt().toEpochMilli());
                            pstmt.setLong(7, p.participatedAt().toEpochMilli());
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.WARNING, "[BetterDragon] Fallo al revertir transacción de leaderboard: " + rollbackEx.getMessage(), rollbackEx);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(initialAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStats(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT player_uuid, last_known_name, battles_participated, total_damage, highest_damage, slayer_count, first_participation_at, last_participation_at "
                    + "FROM " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS
                    + " WHERE player_uuid = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToPlayerStats(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopDamage(int limit) {
        if (limit <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit debe ser mayor que cero: " + limit));
        }
        int safeLimit = Math.min(limit, MAX_RANKING_LIMIT);
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT player_uuid, last_known_name, battles_participated, total_damage, highest_damage, slayer_count, first_participation_at, last_participation_at "
                    + "FROM " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS
                    + " ORDER BY total_damage DESC, player_uuid ASC LIMIT ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, safeLimit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<LeaderboardPlayerStats> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRowToPlayerStats(rs));
                    }
                    return List.copyOf(list);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopSlayers(int limit) {
        if (limit <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit debe ser mayor que cero: " + limit));
        }
        int safeLimit = Math.min(limit, MAX_RANKING_LIMIT);
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT player_uuid, last_known_name, battles_participated, total_damage, highest_damage, slayer_count, first_participation_at, last_participation_at "
                    + "FROM " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS
                    + " ORDER BY slayer_count DESC, player_uuid ASC LIMIT ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, safeLimit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<LeaderboardPlayerStats> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRowToPlayerStats(rs));
                    }
                    return List.copyOf(list);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardPlayerStats>> getTopParticipations(int limit) {
        if (limit <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit debe ser mayor que cero: " + limit));
        }
        int safeLimit = Math.min(limit, MAX_RANKING_LIMIT);
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT player_uuid, last_known_name, battles_participated, total_damage, highest_damage, slayer_count, first_participation_at, last_participation_at "
                    + "FROM " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS
                    + " ORDER BY battles_participated DESC, player_uuid ASC LIMIT ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, safeLimit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<LeaderboardPlayerStats> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRowToPlayerStats(rs));
                    }
                    return List.copyOf(list);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<LeaderboardBattleRecord>> getBattle(BattleId battleId) {
        if (battleId == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT battle_id, completed_at, world_name, slayer_uuid FROM "
                    + SchemaInitializer.TABLE_LEADERBOARD_BATTLES + " WHERE battle_id = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, battleId.asString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToBattleRecord(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardParticipantRecord>> getBattleParticipants(BattleId battleId) {
        if (battleId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT battle_id, player_uuid, historical_name, damage, first_hit_sequence, was_slayer, participated_at "
                    + "FROM " + SchemaInitializer.TABLE_LEADERBOARD_PARTICIPATION
                    + " WHERE battle_id = ? ORDER BY damage DESC, first_hit_sequence ASC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, battleId.asString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<LeaderboardParticipantRecord> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRowToParticipantRecord(rs));
                    }
                    return List.copyOf(list);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<LeaderboardParticipantRecord>> getPlayerParticipations(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT battle_id, player_uuid, historical_name, damage, first_hit_sequence, was_slayer, participated_at "
                    + "FROM " + SchemaInitializer.TABLE_LEADERBOARD_PARTICIPATION
                    + " WHERE player_uuid = ? ORDER BY participated_at ASC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<LeaderboardParticipantRecord> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRowToParticipantRecord(rs));
                    }
                    return List.copyOf(list);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getBattleCount() {
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT COUNT(*) FROM " + SchemaInitializer.TABLE_LEADERBOARD_BATTLES + ";";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<Integer> getPlayerCount() {
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT COUNT(*) FROM " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ";";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<Void> clear() {
        return databaseManager.runAsync(conn -> {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM " + SchemaInitializer.TABLE_LEADERBOARD_PARTICIPATION + ";");
                stmt.execute("DELETE FROM " + SchemaInitializer.TABLE_LEADERBOARD_BATTLES + ";");
                stmt.execute("DELETE FROM " + SchemaInitializer.TABLE_LEADERBOARD_PLAYERS + ";");
            }
        });
    }

    @Override
    public void close() {
        databaseManager.close();
    }

    private LeaderboardPlayerStats mapRowToPlayerStats(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String lastKnownName = rs.getString("last_known_name");
        int battles = rs.getInt("battles_participated");
        double totalDamage = rs.getDouble("total_damage");
        double highestDamage = rs.getDouble("highest_damage");
        int slayerCount = rs.getInt("slayer_count");
        Instant firstAt = Instant.ofEpochMilli(rs.getLong("first_participation_at"));
        Instant lastAt = Instant.ofEpochMilli(rs.getLong("last_participation_at"));

        return new LeaderboardPlayerStats(
                playerUuid,
                lastKnownName,
                battles,
                totalDamage,
                highestDamage,
                slayerCount,
                firstAt,
                lastAt
        );
    }

    private LeaderboardBattleRecord mapRowToBattleRecord(ResultSet rs) throws SQLException {
        BattleId battleId = BattleId.fromString(rs.getString("battle_id"));
        Instant completedAt = Instant.ofEpochMilli(rs.getLong("completed_at"));
        String worldName = rs.getString("world_name");
        String slayerStr = rs.getString("slayer_uuid");
        UUID slayerUuid = (slayerStr == null || slayerStr.isBlank()) ? null : UUID.fromString(slayerStr);

        return new LeaderboardBattleRecord(
                battleId,
                completedAt,
                worldName,
                slayerUuid
        );
    }

    private LeaderboardParticipantRecord mapRowToParticipantRecord(ResultSet rs) throws SQLException {
        BattleId battleId = BattleId.fromString(rs.getString("battle_id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String historicalName = rs.getString("historical_name");
        double damage = rs.getDouble("damage");
        long firstHitSequence = rs.getLong("first_hit_sequence");
        boolean wasSlayer = rs.getInt("was_slayer") == 1;
        Instant participatedAt = Instant.ofEpochMilli(rs.getLong("participated_at"));

        return LeaderboardParticipantRecord.ofHistorical(
                battleId,
                playerUuid,
                historicalName,
                damage,
                firstHitSequence,
                wasSlayer,
                participatedAt
        );
    }
}
