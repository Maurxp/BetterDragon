package maurxp.betterdragon.reward.claim;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.persistence.DatabaseManager;
import maurxp.betterdragon.persistence.SchemaInitializer;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.model.RewardSource;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación durable de {@link ClaimStorage} respaldada por SQLite.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Single Writer:</b> Todas las operaciones se canalizan a través de {@link DatabaseManager},
 *       ejecutándose en un hilo dedicado de persistencia fuera del main thread.</li>
 *   <li><b>Idempotencia Atómica:</b> Utiliza {@code INSERT ... ON CONFLICT(idempotency_key) DO UPDATE}
 *       para garantizar que ejecuciones concurrentes o repetidas nunca dupliquen filas.</li>
 *   <li><b>Validación de Integridad:</b> Valida el material persistido con {@link Material#matchMaterial(String)}
 *       sin eliminar silenciosamente el reclamo en caso de corrupción o materiales deprecados.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SQLiteClaimStorage implements ClaimStorage {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public SQLiteClaimStorage(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    @Override
    public CompletableFuture<RewardClaim> createIfAbsent(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        return databaseManager.supplyAsync(conn -> {
            String insertSql = "INSERT INTO " + SchemaInitializer.TABLE_REWARD_CLAIMS + " ("
                    + "claim_id, idempotency_key, battle_id, participant_uuid, player_name, "
                    + "source, material, display_name, lore, original_amount, "
                    + "delivered_amount, remaining_amount, status, created_at, claimed_at, "
                    + "failure_reason, updated_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(idempotency_key) DO NOTHING;";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                bindClaimParameters(pstmt, claim);
                pstmt.executeUpdate();
            }

            // Recuperar el registro almacenado (sea el recién insertado o el existente previo)
            String selectSql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + " WHERE idempotency_key = ?;";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, claim.idempotencyKey());
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        RewardClaim persisted = mapRowToClaim(rs);
                        if (persisted.status() == ClaimStatus.FAILED_RETRYABLE && Material.matchMaterial(persisted.item().material()) == null) {
                            syncInvalidMaterialInDb(conn, persisted.claimId(), persisted.failureReason());
                        }
                        return persisted;
                    }
                }
            }
            throw new SQLException("Fallo al recuperar claim tras createIfAbsent: " + claim.idempotencyKey());
        });
    }

    @Override
    public CompletableFuture<Boolean> updateExisting(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        return databaseManager.supplyAsync(conn -> {
            String sql = "UPDATE " + SchemaInitializer.TABLE_REWARD_CLAIMS + " SET "
                    + "player_name = ?, "
                    + "delivered_amount = ?, "
                    + "remaining_amount = ?, "
                    + "status = ?, "
                    + "claimed_at = ?, "
                    + "failure_reason = ?, "
                    + "updated_at = ? "
                    + "WHERE idempotency_key = ? "
                    + "  AND status != 'CLAIMED';";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, claim.playerName());
                pstmt.setInt(2, claim.deliveredAmount());
                pstmt.setInt(3, claim.getRemainingAmount());
                pstmt.setString(4, claim.status().name());

                if (claim.claimedAt() != null) {
                    pstmt.setLong(5, claim.claimedAt().toEpochMilli());
                } else {
                    pstmt.setNull(5, Types.INTEGER);
                }

                if (claim.failureReason() != null) {
                    pstmt.setString(6, claim.failureReason());
                } else {
                    pstmt.setNull(6, Types.VARCHAR);
                }

                pstmt.setLong(7, System.currentTimeMillis());
                pstmt.setString(8, claim.idempotencyKey());

                int updatedRows = pstmt.executeUpdate();
                return updatedRows > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        return updateExisting(claim).thenCompose(updated -> {
            if (updated) {
                return CompletableFuture.completedFuture(null);
            }
            return createIfAbsent(claim).thenApply(c -> null);
        });
    }

    @Override
    public CompletableFuture<Void> saveAll(Collection<RewardClaim> claims) {
        Objects.requireNonNull(claims, "claims no puede ser nulo");
        if (claims.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RewardClaim claim : claims) {
            futures.add(save(claim));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public CompletableFuture<Optional<RewardClaim>> findById(UUID claimId) {
        if (claimId == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + " WHERE claim_id = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, claimId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        RewardClaim claim = mapRowToClaim(rs);
                        if (claim.status() == ClaimStatus.FAILED_RETRYABLE && Material.matchMaterial(claim.item().material()) == null) {
                            syncInvalidMaterialInDb(conn, claim.claimId(), claim.failureReason());
                        }
                        return Optional.of(claim);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<RewardClaim>> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + " WHERE idempotency_key = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, idempotencyKey);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        RewardClaim claim = mapRowToClaim(rs);
                        if (claim.status() == ClaimStatus.FAILED_RETRYABLE && Material.matchMaterial(claim.item().material()) == null) {
                            syncInvalidMaterialInDb(conn, claim.claimId(), claim.failureReason());
                        }
                        return Optional.of(claim);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findByPlayer(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + " WHERE participant_uuid = ? ORDER BY created_at ASC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<RewardClaim> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRowToClaim(rs));
                    }
                    syncInvalidClaimsInBatch(conn, results);
                    return List.copyOf(results);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findPendingByPlayer(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS
                    + " WHERE participant_uuid = ? AND (status = 'PENDING' OR status = 'FAILED_RETRYABLE') ORDER BY created_at ASC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<RewardClaim> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRowToClaim(rs));
                    }
                    syncInvalidClaimsInBatch(conn, results);
                    return List.copyOf(results);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findByBattleId(BattleId battleId) {
        if (battleId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + " WHERE battle_id = ? ORDER BY created_at ASC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, battleId.asString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<RewardClaim> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRowToClaim(rs));
                    }
                    syncInvalidClaimsInBatch(conn, results);
                    return List.copyOf(results);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findByStatus(ClaimStatus status) {
        if (status == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT * FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + " WHERE status = ? ORDER BY created_at ASC;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, status.name());
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<RewardClaim> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRowToClaim(rs));
                    }
                    syncInvalidClaimsInBatch(conn, results);
                    return List.copyOf(results);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Integer> count() {
        return databaseManager.supplyAsync(conn -> {
            String sql = "SELECT COUNT(*) FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + ";";
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
            String sql = "DELETE FROM " + SchemaInitializer.TABLE_REWARD_CLAIMS + ";";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
            }
        });
    }

    @Override
    public void close() {
        databaseManager.close();
    }

    private void bindClaimParameters(PreparedStatement pstmt, RewardClaim claim) throws SQLException {
        pstmt.setString(1, claim.claimId().toString());
        pstmt.setString(2, claim.idempotencyKey());
        pstmt.setString(3, claim.battleId().asString());
        pstmt.setString(4, claim.playerId().toString());
        pstmt.setString(5, claim.playerName());
        pstmt.setString(6, claim.source().name());
        pstmt.setString(7, claim.item().material());

        if (claim.item().displayName() != null) {
            pstmt.setString(8, claim.item().displayName());
        } else {
            pstmt.setNull(8, Types.VARCHAR);
        }

        if (!claim.item().lore().isEmpty()) {
            pstmt.setString(9, String.join("\n", claim.item().lore()));
        } else {
            pstmt.setNull(9, Types.VARCHAR);
        }

        pstmt.setInt(10, claim.originalAmount());
        pstmt.setInt(11, claim.deliveredAmount());
        pstmt.setInt(12, claim.getRemainingAmount());
        pstmt.setString(13, claim.status().name());
        pstmt.setLong(14, claim.createdAt().toEpochMilli());

        if (claim.claimedAt() != null) {
            pstmt.setLong(15, claim.claimedAt().toEpochMilli());
        } else {
            pstmt.setNull(15, Types.INTEGER);
        }

        if (claim.failureReason() != null) {
            pstmt.setString(16, claim.failureReason());
        } else {
            pstmt.setNull(16, Types.VARCHAR);
        }

        pstmt.setLong(17, System.currentTimeMillis());
    }

    private RewardClaim mapRowToClaim(ResultSet rs) throws SQLException {
        UUID claimId = UUID.fromString(rs.getString("claim_id"));
        String idempotencyKey = rs.getString("idempotency_key");
        BattleId battleId = BattleId.fromString(rs.getString("battle_id"));
        UUID playerId = UUID.fromString(rs.getString("participant_uuid"));
        String playerName = rs.getString("player_name");
        RewardSource source = RewardSource.valueOf(rs.getString("source"));
        String materialName = rs.getString("material");

        String displayName = rs.getString("display_name");
        String loreRaw = rs.getString("lore");
        List<String> lore = (loreRaw == null || loreRaw.isEmpty()) ? List.of() : List.of(loreRaw.split("\n"));

        int originalAmount = rs.getInt("original_amount");
        int deliveredAmount = rs.getInt("delivered_amount");
        ClaimStatus status = ClaimStatus.valueOf(rs.getString("status"));
        Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));

        long claimedAtLong = rs.getLong("claimed_at");
        Instant claimedAt = rs.wasNull() ? null : Instant.ofEpochMilli(claimedAtLong);
        String failureReason = rs.getString("failure_reason");

        // Validación estricta de Material contra la API oficial de Paper
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) {
            logger.warning("[BetterDragon] Reclamo " + claimId + " contiene material desconocido o inválido: '"
                    + materialName + "'. Sincronizando estado a FAILED_RETRYABLE.");
            status = ClaimStatus.FAILED_RETRYABLE;
            if (failureReason == null || failureReason.isEmpty()) {
                failureReason = "Material desconocido o inválido: " + materialName;
            }
        }

        RewardItem item = new RewardItem(materialName, originalAmount, displayName, lore);

        return new RewardClaim(
                claimId,
                idempotencyKey,
                battleId,
                playerId,
                playerName,
                source,
                item,
                originalAmount,
                deliveredAmount,
                status,
                createdAt,
                claimedAt,
                failureReason
        );
    }

    private void syncInvalidMaterialInDb(Connection conn, UUID claimId, String failureReason) {
        if (conn == null) {
            return;
        }
        String updateSql = "UPDATE " + SchemaInitializer.TABLE_REWARD_CLAIMS
                + " SET status = 'FAILED_RETRYABLE', failure_reason = ?, updated_at = ? "
                + " WHERE claim_id = ? AND (status != 'FAILED_RETRYABLE' OR failure_reason IS NULL);";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setString(1, failureReason);
            updateStmt.setLong(2, System.currentTimeMillis());
            updateStmt.setString(3, claimId.toString());
            updateStmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[BetterDragon] Error al sincronizar material inválido en SQLite: " + e.getMessage(), e);
        }
    }

    private void syncInvalidClaimsInBatch(Connection conn, List<RewardClaim> claims) {
        if (conn == null || claims == null || claims.isEmpty()) {
            return;
        }
        for (RewardClaim claim : claims) {
            if (claim.status() == ClaimStatus.FAILED_RETRYABLE && Material.matchMaterial(claim.item().material()) == null) {
                syncInvalidMaterialInDb(conn, claim.claimId(), claim.failureReason());
            }
        }
    }
}
