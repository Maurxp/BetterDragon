package maurxp.betterdragon.reward;

import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.config.RewardItemDefinition;
import maurxp.betterdragon.config.SlayerRewardDefinition;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.claim.InMemoryClaimStorage;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService.DeliveryBatchResult;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.event.BetterDragonRewardEvent;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.model.RewardSource;
import maurxp.betterdragon.reward.service.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Idempotencia y Deduplicación de Recompensas (Fase 3.8)")
class RewardIdempotencyTest {

    private ClaimStorage claimStorage;
    private TrackingInventoryAdapter inventoryAdapter;
    private RewardDeliveryService deliveryService;
    private BattleId battleId;

    @BeforeEach
    void setUp() {
        claimStorage = new InMemoryClaimStorage();
        inventoryAdapter = new TrackingInventoryAdapter();
        deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, Logger.getLogger("IdempotencyTest"));
        battleId = BattleId.random();
    }

    @Test
    @DisplayName("Idempotency key canónica: estructura estable basada en battleId:participantId:rewardId")
    void testIdempotencyKeyStability() {
        UUID playerId = UUID.randomUUID();
        String rewardId = "participation_diamond_5";
        RewardAllocation alloc1 = new RewardAllocation(
                battleId,
                playerId,
                "Hero",
                RewardSource.PARTICIPATION,
                rewardId,
                new RewardItem("DIAMOND", 10),
                100.0,
                Instant.now()
        );

        String expectedKey = battleId.asString() + ":" + playerId + ":" + rewardId;
        assertEquals(expectedKey, alloc1.idempotencyKey());

        // Segunda llamada genera exactamente la misma clave determinista
        assertEquals(alloc1.idempotencyKey(), alloc1.idempotencyKey());
    }

    @Test
    @DisplayName("Colisión evitada: mismo battleId, jugador, material y source con IDs distintos generan 2 claims independientes")
    void testSameMaterialDifferentRewardIdsDoNotCollide() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);

        // Reward A: DIAMOND x5 con id = "reward_a"
        RewardAllocation allocA = new RewardAllocation(
                battleId,
                playerId,
                "Hero",
                RewardSource.PARTICIPATION,
                "reward_a",
                new RewardItem("DIAMOND", 5),
                50.0,
                Instant.now()
        );

        // Reward B: DIAMOND x10 con id = "reward_b"
        RewardAllocation allocB = new RewardAllocation(
                battleId,
                playerId,
                "Hero",
                RewardSource.PARTICIPATION,
                "reward_b",
                new RewardItem("DIAMOND", 10),
                50.0,
                Instant.now()
        );

        assertNotEquals(allocA.idempotencyKey(), allocB.idempotencyKey(), "Idempotency keys deben ser distintas para IDs distintos");

        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(allocA, allocB), Instant.now());
        DeliveryBatchResult result = deliveryService.deliverPlan(plan);

        // 2 asignaciones completamente entregadas, 15 diamantes en total
        assertEquals(2, result.fullyDeliveredCount());
        assertEquals(15, result.totalItemsDelivered());
        assertEquals(15, inventoryAdapter.getTotalReceived(playerId, "DIAMOND"));

        // Dos claims independientes y distintos registrados en almacenamiento
        List<RewardClaim> storedClaims = claimStorage.findByPlayer(playerId);
        assertEquals(2, storedClaims.size(), "Deben coexistir exactamente 2 claims distintos sin colisión");

        RewardClaim claimA = claimStorage.findByIdempotencyKey(allocA.idempotencyKey()).orElseThrow();
        assertEquals(5, claimA.deliveredAmount());
        assertEquals(ClaimStatus.CLAIMED, claimA.status());

        RewardClaim claimB = claimStorage.findByIdempotencyKey(allocB.idempotencyKey()).orElseThrow();
        assertEquals(10, claimB.deliveredAmount());
        assertEquals(ClaimStatus.CLAIMED, claimB.status());
    }

    @Test
    @DisplayName("Recompensa ya entregada (CLAIMED): deliverPlan no duplica la entrega física")
    void testAlreadyClaimedDoesNotDeliverAgain() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);

        RewardAllocation alloc = new RewardAllocation(
                battleId,
                playerId,
                "Hero",
                RewardSource.PARTICIPATION,
                "diamond_pool_reward",
                new RewardItem("DIAMOND", 64),
                100.0,
                Instant.now()
        );
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        // Primera entrega
        var firstResult = deliveryService.deliverPlan(plan);
        assertEquals(1, firstResult.fullyDeliveredCount());
        assertEquals(64, firstResult.totalItemsDelivered());
        assertEquals(64, inventoryAdapter.getTotalReceived(playerId, "DIAMOND"));

        // Segunda entrega idéntica (ej. reintento tras re-procesamiento)
        var secondResult = deliveryService.deliverPlan(plan);
        assertEquals(1, secondResult.fullyDeliveredCount());
        assertEquals(0, secondResult.totalItemsDelivered(), "No debe entregar ítems adicionales");
        assertEquals(64, inventoryAdapter.getTotalReceived(playerId, "DIAMOND"), "El jugador debe seguir teniendo exactamente 64");

        // En almacenamiento solo existe un reclamo
        assertEquals(1, claimStorage.count());
        assertEquals(ClaimStatus.CLAIMED, claimStorage.findByIdempotencyKey(alloc.idempotencyKey()).orElseThrow().status());
    }

    @Test
    @DisplayName("Doble llamada a processVictory(): emite eventos pero nunca duplica recompensas físicas")
    void testDoubleProcessVictoryDoesNotDuplicate() {
        UUID p1 = UUID.randomUUID();
        inventoryAdapter.setOnline(p1, true);

        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(p1, "Hero", "Hero", 1000.0, 1L, 10L, 100L)
        );
        CombatSnapshot combatSnapshot = new CombatSnapshot(battleId, participants, 10L, 1000.0);
        BattleResult victoryResult = BattleResult.completed(
                battleId,
                Instant.now().minusSeconds(60),
                Instant.now(),
                p1,
                "Hero",
                combatSnapshot
        );

        BattleSessionManager sessionManager = new BattleSessionManager();
        ConfigurationService configService = new ConfigurationService(Logger.getLogger("Test"));

        // Registrar sesión en memoria con configuración de prueba explícita
        RewardConfigurationSnapshot sessionRewardConfig = new RewardConfigurationSnapshot(
                true,
                10.0,
                List.of(new RewardItemDefinition("test_pool_diamond", "DIAMOND", 10)),
                SlayerRewardDefinition.defaults()
        );
        maurxp.betterdragon.config.BattleConfigurationSnapshot sessionConfig = new maurxp.betterdragon.config.BattleConfigurationSnapshot(
                false,
                false,
                maurxp.betterdragon.config.DragonDefinition.defaults(),
                maurxp.betterdragon.arena.ArenaDefinition.defaults(),
                sessionRewardConfig
        );
        maurxp.betterdragon.battle.BattleSession session = new maurxp.betterdragon.battle.BattleSession(
                battleId, "world_the_end", UUID.randomUUID(), sessionConfig
        );
        sessionManager.register(session);

        RewardAllocationEngine allocationEngine = new RewardAllocationEngine();
        List<BetterDragonRewardEvent> emittedEvents = new ArrayList<>();

        RewardService rewardService = new RewardService(
                sessionManager,
                configService,
                allocationEngine,
                deliveryService,
                claimStorage,
                emittedEvents::add,
                Logger.getLogger("Test")
        );

        // 1. Primer procesamiento
        RewardAllocationPlan plan1 = rewardService.processVictory(victoryResult);
        assertFalse(plan1.isEmpty());
        int diamondsAfterFirst = inventoryAdapter.getTotalReceived(p1, "DIAMOND");
        assertEquals(10, diamondsAfterFirst, "Debe haber recibido los 10 diamantes configurados en la sesión");
        assertEquals(1, emittedEvents.size());

        int storedClaimsFirst = claimStorage.count();

        // 2. Segundo procesamiento idéntico (simula doble listener o doble invocación)
        RewardAllocationPlan plan2 = rewardService.processVictory(victoryResult);
        assertFalse(plan2.isEmpty());
        int diamondsAfterSecond = inventoryAdapter.getTotalReceived(p1, "DIAMOND");

        assertEquals(diamondsAfterFirst, diamondsAfterSecond, "La cantidad física de ítems entregados no debe cambiar");
        assertEquals(storedClaimsFirst, claimStorage.count(), "No deben crearse reclamos duplicados en ClaimStorage");
        assertEquals(2, emittedEvents.size(), "El evento informativo se emite, pero la entrega física es 100% idempotente");
    }

    /**
     * Adaptador que lleva cuenta acumulativa exacta de ítems recibidos por jugador y material.
     */
    private static class TrackingInventoryAdapter implements PlayerInventoryAdapter {
        private final Map<UUID, Boolean> onlineStatus = new HashMap<>();
        private final Map<UUID, Map<String, Integer>> receivedItems = new HashMap<>();

        void setOnline(UUID id, boolean online) {
            onlineStatus.put(id, online);
        }

        int getTotalReceived(UUID id, String material) {
            return receivedItems.getOrDefault(id, Map.of()).getOrDefault(material, 0);
        }

        @Override
        public boolean isPlayerOnline(UUID playerId) {
            return onlineStatus.getOrDefault(playerId, false);
        }

        @Override
        public int deliverItem(UUID playerId, RewardItem item) {
            if (!isPlayerOnline(playerId)) return 0;
            receivedItems.computeIfAbsent(playerId, k -> new HashMap<>())
                    .merge(item.material(), item.amount(), Integer::sum);
            return item.amount();
        }
    }
}
