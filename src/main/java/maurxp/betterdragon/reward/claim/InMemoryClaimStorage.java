package maurxp.betterdragon.reward.claim;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardClaim;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria de {@link ClaimStorage} para la Fase 3.8.
 * <p>
 * Mantiene los reclamos en memoria de forma segura y determinista durante el ciclo
 * de vida del proceso en ejecución.
 * <p>
 * <b>Límites y Alcance de Fase 3.8:</b>
 * <ul>
 *   <li><b>Protección de Proceso:</b> Garantiza idempotencia y entrega confiable ante desconexiones
 *       e inventarios llenos <i>mientras el servidor permanezca en ejecución</i>.</li>
 *   <li><b>NO Durable:</b> Los reclamos almacenados se pierden ante apagado, reinicio o caída del proceso.</li>
 *   <li><b>Persistencia Durable (Fase 3.9):</b> La persistencia transaccional en disco (SQLite),
 *       la recuperación tras reinicio y las migraciones se implementarán en la Fase 3.9 sin
 *       modificar los contratos de Rewards.</li>
 * </ul>
 *
 * @author maurxp
 */
public class InMemoryClaimStorage implements ClaimStorage {

    private final Map<UUID, RewardClaim> claimsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> idByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public void save(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        claimsById.put(claim.claimId(), claim);
        idByIdempotencyKey.put(claim.idempotencyKey(), claim.claimId());
    }

    @Override
    public void saveAll(Collection<RewardClaim> claims) {
        Objects.requireNonNull(claims, "claims no puede ser nulo");
        for (RewardClaim claim : claims) {
            save(claim);
        }
    }

    @Override
    public Optional<RewardClaim> findById(UUID claimId) {
        if (claimId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(claimsById.get(claimId));
    }

    @Override
    public Optional<RewardClaim> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        UUID id = idByIdempotencyKey.get(idempotencyKey);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(claimsById.get(id));
    }

    @Override
    public List<RewardClaim> findByPlayer(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return claimsById.values().stream()
                .filter(claim -> claim.playerId().equals(playerId))
                .toList();
    }

    @Override
    public List<RewardClaim> findPendingByPlayer(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return claimsById.values().stream()
                .filter(claim -> claim.playerId().equals(playerId))
                .filter(claim -> claim.status() == ClaimStatus.PENDING || claim.status() == ClaimStatus.FAILED_RETRYABLE)
                .toList();
    }

    @Override
    public List<RewardClaim> findByBattleId(BattleId battleId) {
        if (battleId == null) {
            return List.of();
        }
        return claimsById.values().stream()
                .filter(claim -> claim.battleId().equals(battleId))
                .toList();
    }

    @Override
    public List<RewardClaim> findByStatus(ClaimStatus status) {
        if (status == null) {
            return List.of();
        }
        return claimsById.values().stream()
                .filter(claim -> claim.status() == status)
                .toList();
    }

    @Override
    public int count() {
        return claimsById.size();
    }

    @Override
    public void clear() {
        claimsById.clear();
        idByIdempotencyKey.clear();
    }
}
