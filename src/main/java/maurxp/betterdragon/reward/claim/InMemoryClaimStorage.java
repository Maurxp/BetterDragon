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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación en memoria de {@link ClaimStorage} para pruebas unitarias y entornos efímeros.
 * <p>
 * Mantiene los reclamos en memoria de forma determinista durante el ciclo de vida del objeto.
 * <p>
 * <b>Límites y Advertencia Arquitectónica:</b>
 * <ul>
 *   <li><b>Runtime Only:</b> Diseñada exclusivamente para pruebas unitarias y suites rápidas.</li>
 *   <li><b>NO Durable:</b> Los reclamos se pierden al finalizar el proceso.</li>
 *   <li><b>NO Fallback Silencioso:</b> No debe utilizarse como fallback en producción si SQLite falla.</li>
 * </ul>
 *
 * @author maurxp
 */
public class InMemoryClaimStorage implements ClaimStorage {

    private final Map<UUID, RewardClaim> claimsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> idByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public synchronized CompletableFuture<RewardClaim> createIfAbsent(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        UUID existingId = idByIdempotencyKey.get(claim.idempotencyKey());
        if (existingId != null) {
            RewardClaim existing = claimsById.get(existingId);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
        }
        claimsById.put(claim.claimId(), claim);
        idByIdempotencyKey.put(claim.idempotencyKey(), claim.claimId());
        return CompletableFuture.completedFuture(claim);
    }

    @Override
    public synchronized CompletableFuture<Boolean> updateExisting(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        UUID existingId = idByIdempotencyKey.get(claim.idempotencyKey());
        if (existingId == null) {
            return CompletableFuture.completedFuture(false);
        }
        RewardClaim current = claimsById.get(existingId);
        if (current == null) {
            return CompletableFuture.completedFuture(false);
        }
        // Salvaguarda: CLAIMED es un estado terminal definitivo; ninguna actualización posterior puede modificarlo
        if (current.status() == ClaimStatus.CLAIMED) {
            return CompletableFuture.completedFuture(false);
        }
        claimsById.put(existingId, claim);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Void> save(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim no puede ser nulo");
        UUID existingId = idByIdempotencyKey.get(claim.idempotencyKey());
        if (existingId == null) {
            claimsById.put(claim.claimId(), claim);
            idByIdempotencyKey.put(claim.idempotencyKey(), claim.claimId());
        } else {
            RewardClaim current = claimsById.get(existingId);
            if (current != null && current.status() == ClaimStatus.CLAIMED) {
                return CompletableFuture.completedFuture(null);
            }
            claimsById.put(existingId, claim);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Void> saveAll(Collection<RewardClaim> claims) {
        Objects.requireNonNull(claims, "claims no puede ser nulo");
        for (RewardClaim claim : claims) {
            save(claim);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<RewardClaim>> findById(UUID claimId) {
        if (claimId == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(claimsById.get(claimId)));
    }

    @Override
    public CompletableFuture<Optional<RewardClaim>> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        UUID id = idByIdempotencyKey.get(idempotencyKey);
        if (id == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(claimsById.get(id)));
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findByPlayer(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<RewardClaim> list = claimsById.values().stream()
                .filter(claim -> claim.playerId().equals(playerId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findPendingByPlayer(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<RewardClaim> list = claimsById.values().stream()
                .filter(claim -> claim.playerId().equals(playerId))
                .filter(claim -> claim.status() == ClaimStatus.PENDING || claim.status() == ClaimStatus.FAILED_RETRYABLE)
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findByBattleId(BattleId battleId) {
        if (battleId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<RewardClaim> list = claimsById.values().stream()
                .filter(claim -> claim.battleId().equals(battleId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletableFuture<List<RewardClaim>> findByStatus(ClaimStatus status) {
        if (status == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<RewardClaim> list = claimsById.values().stream()
                .filter(claim -> claim.status() == status)
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletableFuture<Integer> count() {
        return CompletableFuture.completedFuture(claimsById.size());
    }

    @Override
    public CompletableFuture<Void> clear() {
        claimsById.clear();
        idByIdempotencyKey.clear();
        return CompletableFuture.completedFuture(null);
    }
}
