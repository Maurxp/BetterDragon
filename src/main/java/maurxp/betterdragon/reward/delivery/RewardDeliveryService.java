package maurxp.betterdragon.reward.delivery;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.util.MainThreadDispatcher;

import org.bukkit.Material;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio encargado de la entrega física de recompensas en los inventarios y la coordinación
 * con el almacenamiento durable.
 * <p>
 * Responsabilidades y Garantías:
 * <ul>
 *   <li><b>Separación de Hilos:</b> La persistencia SQLite se realiza asíncronamente; la manipulación
 *       de inventario y eventos de Bukkit se confinan estrictamente al hilo principal.</li>
 *   <li><b>Idempotencia y Exclusión de Concurrencia:</b> Deduplica por {@code idempotencyKey} y
 *       unifica entregas en vuelo evitando cualquier posibilidad de doble entrega física concurrente.</li>
 *   <li><b>Protección de Sobrantes:</b> Ante inventarios saturados o jugadores offline,
 *       preserva el remanente en estado PENDING en almacenamiento durable.</li>
 *   <li><b>Resiliencia:</b> Captura fallos transitorios y preserva el estado FAILED_RETRYABLE.</li>
 * </ul>
 *
 * @author maurxp
 */
public class RewardDeliveryService {

    private final PlayerInventoryAdapter inventoryAdapter;
    private final ClaimStorage claimStorage;
    private final MainThreadDispatcher mainThreadDispatcher;
    private final Logger logger;
    private final ConcurrentHashMap<String, CompletableFuture<SingleAllocationOutcome>> inFlightDeliveries = new ConcurrentHashMap<>();

    public RewardDeliveryService(
            PlayerInventoryAdapter inventoryAdapter,
            ClaimStorage claimStorage,
            MainThreadDispatcher mainThreadDispatcher,
            Logger logger
    ) {
        this.inventoryAdapter = Objects.requireNonNull(inventoryAdapter, "inventoryAdapter no puede ser nulo");
        this.claimStorage = Objects.requireNonNull(claimStorage, "claimStorage no puede ser nulo");
        this.mainThreadDispatcher = Objects.requireNonNull(mainThreadDispatcher, "mainThreadDispatcher no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Constructor de conveniencia que utiliza ejecución síncrona inmediata para pruebas unitarias.
     */
    public RewardDeliveryService(
            PlayerInventoryAdapter inventoryAdapter,
            ClaimStorage claimStorage,
            Logger logger
    ) {
        this(inventoryAdapter, claimStorage, Runnable::run, logger);
    }

    /**
     * Procesa la entrega de un plan completo de asignación de recompensas de forma asíncrona.
     *
     * @param plan plan inmutable de asignaciones
     * @return CompletableFuture con el resultado del lote de entrega
     */
    public CompletableFuture<DeliveryBatchResult> deliverPlan(RewardAllocationPlan plan) {
        Objects.requireNonNull(plan, "plan no puede ser nulo");
        if (plan.isEmpty()) {
            return CompletableFuture.completedFuture(new DeliveryBatchResult(plan.battleId(), 0, 0, 0, 0));
        }

        CompletableFuture<DeliveryBatchResult> batchFuture = new CompletableFuture<>();
        List<CompletableFuture<SingleAllocationOutcome>> allocationFutures = new ArrayList<>();

        for (RewardAllocation allocation : plan.allocations()) {
            allocationFutures.add(processSingleAllocation(allocation));
        }

        CompletableFuture.allOf(allocationFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> {
                    if (err != null) {
                        batchFuture.completeExceptionally(err);
                    } else {
                        int fullyDelivered = 0;
                        int pending = 0;
                        int totalItems = 0;
                        for (CompletableFuture<SingleAllocationOutcome> f : allocationFutures) {
                            SingleAllocationOutcome outcome = f.join();
                            if (outcome.status() == ClaimStatus.CLAIMED) {
                                fullyDelivered++;
                            } else {
                                pending++;
                            }
                            totalItems += outcome.itemsDelivered();
                        }
                        batchFuture.complete(new DeliveryBatchResult(
                                plan.battleId(),
                                plan.allocations().size(),
                                fullyDelivered,
                                pending,
                                totalItems
                        ));
                    }
                });

        return batchFuture;
    }

    private CompletableFuture<SingleAllocationOutcome> processSingleAllocation(RewardAllocation allocation) {
        String idempotencyKey = allocation.idempotencyKey();

        CompletableFuture<SingleAllocationOutcome> flow = inFlightDeliveries.compute(idempotencyKey, (key, currentInFlight) -> {
            if (currentInFlight != null) {
                logger.fine("[BetterDragon] Entrega en vuelo detectada para clave idempotente: " + idempotencyKey);
                return currentInFlight;
            }

            RewardClaim initialPending = RewardClaim.createPending(
                    idempotencyKey,
                    allocation.battleId(),
                    allocation.participantId(),
                    allocation.participantName(),
                    allocation.source(),
                    allocation.item()
            );

            return claimStorage.createIfAbsent(initialPending)
                    .thenCompose(persistedClaim -> {
                        if (persistedClaim.status() == ClaimStatus.CLAIMED || persistedClaim.getRemainingAmount() <= 0) {
                            logger.fine("[BetterDragon] Recompensa ya entregada previamente (idempotente): " + idempotencyKey);
                            return CompletableFuture.completedFuture(new SingleAllocationOutcome(0, ClaimStatus.CLAIMED));
                        }
                        return executeDeliveryFlow(persistedClaim);
                    });
        });

        flow.whenComplete((outcome, err) -> inFlightDeliveries.remove(idempotencyKey, flow));
        return flow;
    }

    private CompletableFuture<SingleAllocationOutcome> executeDeliveryFlow(RewardClaim claim) {
        CompletableFuture<SingleAllocationOutcome> future = new CompletableFuture<>();
        mainThreadDispatcher.runOnMainThread(() -> {
            try {
                DeliveryOutcome outcome = attemptPhysicalDelivery(claim);
                if (outcome.updatedClaim() != null) {
                    claimStorage.updateExisting(outcome.updatedClaim())
                            .whenComplete((updated, err) -> {
                                if (err != null) {
                                    logger.log(Level.SEVERE, "[BetterDragon] Error al actualizar reclamo en persistencia: " + err.getMessage(), err);
                                    future.complete(new SingleAllocationOutcome(outcome.itemsDelivered(), ClaimStatus.FAILED_RETRYABLE));
                                } else {
                                    future.complete(new SingleAllocationOutcome(outcome.itemsDelivered(), outcome.finalStatus()));
                                }
                            });
                } else {
                    future.complete(new SingleAllocationOutcome(outcome.itemsDelivered(), outcome.finalStatus()));
                }
            } catch (Throwable ex) {
                logger.log(Level.SEVERE, "[BetterDragon] Excepción durante entrega en main thread: " + ex.getMessage(), ex);
                future.complete(new SingleAllocationOutcome(0, ClaimStatus.FAILED_RETRYABLE));
            }
        });
        return future;
    }

    /**
     * Reintenta de forma asíncrona la entrega de reclamos pendientes para un jugador específico (ej. al conectarse).
     *
     * @param playerId UUID del jugador
     * @return CompletableFuture con la cantidad total de ítems entregados en este reintento
     */
    public CompletableFuture<Integer> retryPendingForPlayer(UUID playerId) {
        if (playerId == null || !inventoryAdapter.isPlayerOnline(playerId)) {
            return CompletableFuture.completedFuture(0);
        }

        return claimStorage.findPendingByPlayer(playerId).thenCompose(pendingClaims -> {
            if (pendingClaims.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }

            List<CompletableFuture<SingleAllocationOutcome>> futures = new ArrayList<>();
            for (RewardClaim claim : pendingClaims) {
                String key = claim.idempotencyKey();
                CompletableFuture<SingleAllocationOutcome> flow = inFlightDeliveries.compute(key, (k, current) -> {
                    if (current != null) {
                        return current;
                    }
                    return claimStorage.findByIdempotencyKey(key)
                            .thenCompose(latestOpt -> {
                                RewardClaim toDeliver = latestOpt.orElse(claim);
                                if (toDeliver.status() == ClaimStatus.CLAIMED || toDeliver.getRemainingAmount() <= 0) {
                                    return CompletableFuture.completedFuture(new SingleAllocationOutcome(0, ClaimStatus.CLAIMED));
                                }
                                return executeDeliveryFlow(toDeliver);
                            });
                });
                flow.whenComplete((res, err) -> inFlightDeliveries.remove(key, flow));
                futures.add(flow);
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        int total = 0;
                        for (CompletableFuture<SingleAllocationOutcome> f : futures) {
                            total += f.join().itemsDelivered();
                        }
                        return total;
                    });
        });
    }

    /**
     * Intenta la entrega física del ítem al inventario del jugador.
     * <p>
     * <b>Debe invocarse exclusivamente en el hilo principal de Bukkit.</b>
     *
     * @param claim reclamo a entregar
     * @return resultado de la entrega física
     */
    public DeliveryOutcome attemptPhysicalDelivery(RewardClaim claim) {
        UUID playerId = claim.playerId();

        // 1. Verificar si el jugador está conectado
        if (!inventoryAdapter.isPlayerOnline(playerId)) {
            return new DeliveryOutcome(0, claim.status(), null);
        }

        // 2. Validación estricta de Material contra la API oficial de Paper
        Material material = Material.matchMaterial(claim.item().material());
        if (material == null || material == Material.AIR || material.name().endsWith("AIR")) {
            logger.warning("[BetterDragon] Material no entregable para reclamo " + claim.claimId() + ": '"
                    + claim.item().material() + "'. Sincronizando como FAILED_RETRYABLE.");
            RewardClaim failed = claim.withFailure("Material desconocido o inválido: " + claim.item().material());
            return new DeliveryOutcome(0, ClaimStatus.FAILED_RETRYABLE, failed);
        }

        int remaining = claim.getRemainingAmount();
        if (remaining <= 0) {
            if (claim.status() != ClaimStatus.CLAIMED) {
                RewardClaim updated = claim.withDelivery(0, Instant.now());
                return new DeliveryOutcome(0, ClaimStatus.CLAIMED, updated);
            }
            return new DeliveryOutcome(0, ClaimStatus.CLAIMED, null);
        }

        try {
            RewardItem toDeliver = claim.item().withAmount(remaining);
            int delivered = inventoryAdapter.deliverItem(playerId, toDeliver);

            if (delivered > 0) {
                RewardClaim updated = claim.withDelivery(delivered, Instant.now());
                return new DeliveryOutcome(delivered, updated.status(), updated);
            } else {
                // Inventario saturado, 0 entregados. Conserva estado PENDING sin pérdidas
                return new DeliveryOutcome(0, claim.status(), null);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[BetterDragon] Error al entregar recompensa a " + claim.playerName() + ": " + ex.getMessage(), ex);
            RewardClaim failed = claim.withFailure(ex.getMessage());
            return new DeliveryOutcome(0, ClaimStatus.FAILED_RETRYABLE, failed);
        }
    }

    public PlayerInventoryAdapter getInventoryAdapter() {
        return inventoryAdapter;
    }

    public ClaimStorage getClaimStorage() {
        return claimStorage;
    }

    public MainThreadDispatcher getMainThreadDispatcher() {
        return mainThreadDispatcher;
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

    public record DeliveryOutcome(
            int itemsDelivered,
            ClaimStatus finalStatus,
            RewardClaim updatedClaim
    ) {}

    private record SingleAllocationOutcome(
            int itemsDelivered,
            ClaimStatus status
    ) {}
}
