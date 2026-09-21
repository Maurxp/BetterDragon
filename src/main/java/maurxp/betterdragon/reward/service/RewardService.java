package maurxp.betterdragon.reward.service;

import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.event.BetterDragonRewardEvent;
import maurxp.betterdragon.reward.event.RewardEventDispatcher;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio central orquestador del subsistema de recompensas de BetterDragon.
 * <p>
 * Coordina el ciclo completo tras la victoria:
 * <ol>
 *   <li>Resolución de snapshot inmutable de configuración de recompensas.</li>
 *   <li>Cálculo puro y determinista de elegibilidad y reparto proporcional (AllocationEngine).</li>
 *   <li>Entrega segura, idempotente y con tolerancia a desconexión/inventario lleno (RewardDeliveryService).</li>
 *   <li>Emisión desacoplada de {@link BetterDragonRewardEvent}.</li>
 * </ol>
 *
 * @author maurxp
 */
public class RewardService {

    private final BattleSessionManager sessionManager;
    private final ConfigurationService configService;
    private final RewardAllocationEngine allocationEngine;
    private final RewardDeliveryService deliveryService;
    private final ClaimStorage claimStorage;
    private final RewardEventDispatcher rewardEventDispatcher;
    private final Logger logger;

    public RewardService(
            BattleSessionManager sessionManager,
            ConfigurationService configService,
            RewardAllocationEngine allocationEngine,
            RewardDeliveryService deliveryService,
            ClaimStorage claimStorage,
            RewardEventDispatcher rewardEventDispatcher,
            Logger logger
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.configService = Objects.requireNonNull(configService, "configService no puede ser nulo");
        this.allocationEngine = Objects.requireNonNull(allocationEngine, "allocationEngine no puede ser nulo");
        this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService no puede ser nulo");
        this.claimStorage = Objects.requireNonNull(claimStorage, "claimStorage no puede ser nulo");
        this.rewardEventDispatcher = Objects.requireNonNull(rewardEventDispatcher, "rewardEventDispatcher no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Procesa la adjudicación y entrega de recompensas a partir de un {@link BattleResult} victorioso.
     *
     * @param result resultado inmutable de la batalla
     * @return plan de asignación calculado
     */
    public RewardAllocationPlan processVictory(BattleResult result) {
        Objects.requireNonNull(result, "BattleResult no puede ser nulo");

        BattleId battleId = result.battleId();

        if (!result.isVictory()) {
            logger.fine("[BetterDragon] Omitiendo recompensas: la batalla " + battleId + " no culminó en victoria.");
            return RewardAllocationPlan.empty(battleId);
        }

        // 1. Obtener snapshot inmutable de configuración congelado en la sesión,
        // o recurrir al snapshot activo si la sesión ya fue retirada de memoria.
        RewardConfigurationSnapshot rewardConfig = sessionManager.getSession(battleId)
                .map(session -> session.getConfigSnapshot().rewardConfig())
                .orElseGet(() -> configService.getActiveConfig().rewardConfig());

        if (!rewardConfig.enabled()) {
            logger.info("[BetterDragon] Recompensas deshabilitadas para la batalla " + battleId + ".");
            return RewardAllocationPlan.empty(battleId);
        }

        // 2. Cálculo puro y determinista de elegibilidad, reparto y Slayer
        RewardAllocationPlan plan = allocationEngine.calculateAllocation(result, rewardConfig);
        if (plan.isEmpty()) {
            logger.info("[BetterDragon] No se generaron asignaciones de recompensa para la batalla " + battleId + ".");
            return plan;
        }

        // 3. Entrega física e idempotente (protegiendo remanentes en ClaimStorage) de forma no bloqueante
        deliveryService.deliverPlan(plan).whenComplete((deliveryResult, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "[BetterDragon] Error durante la entrega de recompensas para batalla "
                        + battleId + ": " + error.getMessage(), error);
            } else {
                logger.info("[BetterDragon] Recompensas procesadas para batalla " + battleId + ": "
                        + deliveryResult.fullyDeliveredCount() + " completadas, "
                        + deliveryResult.pendingCount() + " pendientes de reclamo, "
                        + deliveryResult.totalItemsDelivered() + " ítems entregados.");

                // 4. Despacho del evento informativo de dominio
                BetterDragonRewardEvent event = new BetterDragonRewardEvent(battleId, plan, deliveryResult);
                rewardEventDispatcher.dispatch(event);
            }
        });

        return plan;
    }

    /**
     * Reintenta de forma asíncrona la entrega de cualquier reclamo pendiente para el jugador especificado.
     *
     * @param playerId UUID del jugador
     * @return CompletableFuture con el total de ítems efectivamente entregados
     */
    public CompletableFuture<Integer> retryPendingClaims(UUID playerId) {
        return deliveryService.retryPendingForPlayer(playerId);
    }

    public ClaimStorage getClaimStorage() {
        return claimStorage;
    }

    public RewardAllocationEngine getAllocationEngine() {
        return allocationEngine;
    }

    public RewardDeliveryService getDeliveryService() {
        return deliveryService;
    }
}
