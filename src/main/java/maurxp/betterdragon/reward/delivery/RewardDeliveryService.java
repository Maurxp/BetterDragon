package maurxp.betterdragon.reward.delivery;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio encargado de la entrega física de recompensas en los inventarios.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li><b>Separación Estricta:</b> Desacoplado del motor de cálculo (AllocationEngine).</li>
 *   <li><b>Idempotencia:</b> Comprueba en {@link ClaimStorage} antes de entregar para evitar duplicaciones.</li>
 *   <li><b>Protección de Sobrantes:</b> Ante inventarios saturados o jugadores offline,
 *       preserva el remanente en estado PENDING para reclamo posterior sin pérdidas.</li>
 *   <li><b>Resiliencia:</b> Captura errores de entrega y preserva el estado FAILED_RETRYABLE.</li>
 * </ul>
 *
 * @author maurxp
 */
public class RewardDeliveryService {

    private final PlayerInventoryAdapter inventoryAdapter;
    private final ClaimStorage claimStorage;
    private final Logger logger;

    public RewardDeliveryService(
            PlayerInventoryAdapter inventoryAdapter,
            ClaimStorage claimStorage,
            Logger logger
    ) {
        this.inventoryAdapter = Objects.requireNonNull(inventoryAdapter, "inventoryAdapter no puede ser nulo");
        this.claimStorage = Objects.requireNonNull(claimStorage, "claimStorage no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Procesa la entrega de un plan completo de asignación de recompensas.
     *
     * @param plan plan inmutable de asignaciones
     * @return resultado del lote de entrega
     */
    public DeliveryBatchResult deliverPlan(RewardAllocationPlan plan) {
        Objects.requireNonNull(plan, "plan no puede ser nulo");

        int fullyDelivered = 0;
        int pending = 0;
        int totalItemsDelivered = 0;

        for (RewardAllocation allocation : plan.allocations()) {
            String idempotencyKey = allocation.idempotencyKey();
            Optional<RewardClaim> existingOpt = claimStorage.findByIdempotencyKey(idempotencyKey);

            RewardClaim claim;
            if (existingOpt.isPresent()) {
                claim = existingOpt.get();
                if (claim.status() == ClaimStatus.CLAIMED) {
                    logger.fine("[BetterDragon] Recompensa ya entregada previamente (idempotente): " + idempotencyKey);
                    fullyDelivered++;
                    continue;
                }
            } else {
                claim = RewardClaim.createPending(
                        idempotencyKey,
                        allocation.battleId(),
                        allocation.participantId(),
                        allocation.participantName(),
                        allocation.source(),
                        allocation.item()
                );
                claimStorage.save(claim);
            }

            // Intentar entrega física
            DeliveryOutcome outcome = attemptDelivery(claim);
            totalItemsDelivered += outcome.itemsDelivered();

            if (outcome.finalStatus() == ClaimStatus.CLAIMED) {
                fullyDelivered++;
            } else {
                pending++;
            }
        }

        return new DeliveryBatchResult(
                plan.battleId(),
                plan.allocations().size(),
                fullyDelivered,
                pending,
                totalItemsDelivered
        );
    }

    /**
     * Reintenta la entrega de reclamos pendientes para un jugador específico (ej. al conectarse).
     *
     * @param playerId UUID del jugador
     * @return cantidad total de ítems entregados en este reintento
     */
    public int retryPendingForPlayer(UUID playerId) {
        if (playerId == null || !inventoryAdapter.isPlayerOnline(playerId)) {
            return 0;
        }

        var pendingClaims = claimStorage.findPendingByPlayer(playerId);
        int totalDelivered = 0;

        for (RewardClaim claim : pendingClaims) {
            DeliveryOutcome outcome = attemptDelivery(claim);
            totalDelivered += outcome.itemsDelivered();
        }

        return totalDelivered;
    }

    private DeliveryOutcome attemptDelivery(RewardClaim claim) {
        UUID playerId = claim.playerId();

        // 1. Verificar si el jugador está conectado
        if (!inventoryAdapter.isPlayerOnline(playerId)) {
            return new DeliveryOutcome(0, claim.status());
        }

        int remaining = claim.getRemainingAmount();
        if (remaining <= 0) {
            if (claim.status() != ClaimStatus.CLAIMED) {
                RewardClaim updated = claim.withDelivery(0, Instant.now());
                claimStorage.save(updated);
            }
            return new DeliveryOutcome(0, ClaimStatus.CLAIMED);
        }

        try {
            RewardItem toDeliver = claim.item().withAmount(remaining);
            int delivered = inventoryAdapter.deliverItem(playerId, toDeliver);

            if (delivered > 0) {
                RewardClaim updated = claim.withDelivery(delivered, Instant.now());
                claimStorage.save(updated);
                return new DeliveryOutcome(delivered, updated.status());
            } else {
                // Inventario saturado, 0 entregados. Conserva estado PENDING sin pérdidas
                return new DeliveryOutcome(0, claim.status());
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[BetterDragon] Error al entregar recompensa a " + claim.playerName() + ": " + ex.getMessage(), ex);
            RewardClaim failed = claim.withFailure(ex.getMessage());
            claimStorage.save(failed);
            return new DeliveryOutcome(0, ClaimStatus.FAILED_RETRYABLE);
        }
    }

    /**
     * Resultado inmutable de una operación de entrega por lotes.
     */
    public record DeliveryBatchResult(
            BattleId battleId,
            int totalAllocations,
            int fullyDeliveredCount,
            int pendingCount,
            int totalItemsDelivered
    ) {}

    private record DeliveryOutcome(
            int itemsDelivered,
            ClaimStatus finalStatus
    ) {}
}
