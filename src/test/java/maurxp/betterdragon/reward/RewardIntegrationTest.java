package maurxp.betterdragon.reward;

import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.config.DragonDefinition;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.config.RewardItemDefinition;
import maurxp.betterdragon.config.SlayerRewardDefinition;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.claim.InMemoryClaimStorage;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.event.BetterDragonRewardEvent;
import maurxp.betterdragon.reward.event.RewardEventDispatcher;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.model.RewardSource;
import maurxp.betterdragon.reward.service.DragonRewardListener;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Integración de Extremo a Extremo de Recompensas (Fase 3.8)")
class RewardIntegrationTest {

    private BattleSessionManager sessionManager;
    private ConfigurationService configService;
    private ClaimStorage claimStorage;
    private MockInventoryAdapter inventoryAdapter;
    private RewardAllocationEngine allocationEngine;
    private RewardDeliveryService deliveryService;
    private List<BetterDragonRewardEvent> rewardEvents;
    private RewardEventDispatcher rewardEventDispatcher;
    private RewardService rewardService;
    private DragonRewardListener rewardListener;

    @BeforeEach
    void setUp() {
        sessionManager = new BattleSessionManager();
        configService = new ConfigurationService(Logger.getLogger("IntegrationTest"));
        claimStorage = new InMemoryClaimStorage();
        inventoryAdapter = new MockInventoryAdapter();
        allocationEngine = new RewardAllocationEngine();
        deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, Logger.getLogger("IntegrationTest"));
        rewardEvents = new ArrayList<>();
        rewardEventDispatcher = rewardEvents::add;

        rewardService = new RewardService(
                sessionManager,
                configService,
                allocationEngine,
                deliveryService,
                claimStorage,
                rewardEventDispatcher,
                Logger.getLogger("IntegrationTest")
        );

        rewardListener = new DragonRewardListener(rewardService, Logger.getLogger("IntegrationTest"));
    }

    private void registerSession(BattleId battleId) {
        RewardConfigurationSnapshot rewardConfig = new RewardConfigurationSnapshot(
                true,
                10.0,
                List.of(new RewardItemDefinition("integration_pool_diamond", "DIAMOND", 10)),
                new SlayerRewardDefinition(true, true, List.of(new RewardItemDefinition("integration_slayer_sword", "NETHERITE_SWORD", 1)))
        );
        BattleConfigurationSnapshot battleConfig = new BattleConfigurationSnapshot(
                false,
                false,
                DragonDefinition.defaults(),
                ArenaDefinition.defaults(),
                rewardConfig
        );
        BattleSession session = new BattleSession(
                battleId, "world_the_end", UUID.randomUUID(), battleConfig
        );
        sessionManager.register(session);
    }

    @Test
    @DisplayName("BetterDragonVictoryEvent recibido por listener procesa asignaciones y entrega física")
    void testVictoryEventTriggersRewardAllocationAndDelivery() {
        BattleId battleId = BattleId.random();
        registerSession(battleId);
        UUID slayerId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();

        // Conectar ambos jugadores
        inventoryAdapter.setOnline(slayerId, true);
        inventoryAdapter.setOnline(assistantId, true);

        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(slayerId, "Slayer", "Slayer", 800.0, 1L, 10L, 100L),
                new ParticipantSnapshot(assistantId, "Assistant", "Assistant", 200.0, 2L, 11L, 101L)
        );
        CombatSnapshot combatSnapshot = new CombatSnapshot(battleId, participants, 11L, 1000.0);
        BattleResult victoryResult = BattleResult.completed(
                battleId,
                Instant.now().minusSeconds(180),
                Instant.now(),
                slayerId,
                "Slayer",
                combatSnapshot
        );

        BetterDragonVictoryEvent victoryEvent = new BetterDragonVictoryEvent(battleId, victoryResult, "world_the_end");

        // Disparar a través del listener
        rewardListener.onDragonVictory(victoryEvent);

        // 1. Verificar emisión de BetterDragonRewardEvent
        assertEquals(1, rewardEvents.size());
        BetterDragonRewardEvent rewardEvent = rewardEvents.getFirst();
        assertEquals(battleId, rewardEvent.getBattleId());
        assertFalse(rewardEvent.getPlan().isEmpty());

        // 2. Verificar que los reclamos se registraron en ClaimStorage
        List<RewardClaim> slayerClaims = claimStorage.findByPlayer(slayerId);
        assertFalse(slayerClaims.isEmpty());
        for (RewardClaim claim : slayerClaims) {
            assertEquals(ClaimStatus.CLAIMED, claim.status());
        }

        List<RewardClaim> assistantClaims = claimStorage.findByPlayer(assistantId);
        assertFalse(assistantClaims.isEmpty());
        for (RewardClaim claim : assistantClaims) {
            assertEquals(ClaimStatus.CLAIMED, claim.status());
        }

        // 3. Verificar que los ítems físicos fueron depositados en el inventario del adaptador
        assertTrue(inventoryAdapter.getDeliveredCount(slayerId) > 0);
        assertTrue(inventoryAdapter.getDeliveredCount(assistantId) > 0);
    }

    @Test
    @DisplayName("Jugador offline durante la victoria recibe sus ítems al invocar retryPendingClaims")
    void testOfflinePlayerReceivesRewardsUponReconnect() {
        BattleId battleId = BattleId.random();
        registerSession(battleId);
        UUID offlinePlayerId = UUID.randomUUID();

        // Jugador desconectado al momento de la victoria
        inventoryAdapter.setOnline(offlinePlayerId, false);

        List<ParticipantSnapshot> participants = List.of(
                new ParticipantSnapshot(offlinePlayerId, "OfflineGuy", "OfflineGuy", 1000.0, 1L, 5L, 100L)
        );
        CombatSnapshot combatSnapshot = new CombatSnapshot(battleId, participants, 5L, 1000.0);
        BattleResult victoryResult = BattleResult.completed(
                battleId,
                Instant.now().minusSeconds(60),
                Instant.now(),
                offlinePlayerId,
                "OfflineGuy",
                combatSnapshot
        );

        BetterDragonVictoryEvent victoryEvent = new BetterDragonVictoryEvent(battleId, victoryResult, "world_the_end");
        rewardListener.onDragonVictory(victoryEvent);

        // Reclamo en estado PENDING
        List<RewardClaim> pendingClaims = claimStorage.findPendingByPlayer(offlinePlayerId);
        assertFalse(pendingClaims.isEmpty());
        assertEquals(0, inventoryAdapter.getDeliveredCount(offlinePlayerId));

        // El jugador se conecta
        inventoryAdapter.setOnline(offlinePlayerId, true);
        int retriedCount = rewardService.retryPendingClaims(offlinePlayerId);
        assertTrue(retriedCount > 0);

        // Todos los reclamos deben haber pasado a CLAIMED
        List<RewardClaim> remainingPending = claimStorage.findPendingByPlayer(offlinePlayerId);
        assertTrue(remainingPending.isEmpty());
        assertEquals(retriedCount, inventoryAdapter.getDeliveredCount(offlinePlayerId));
    }

    @Test
    @DisplayName("Batalla abortada no procesa recompensas ni despacha BetterDragonRewardEvent")
    void testAbortedBattleDoesNotProcessRewards() {
        BattleId battleId = BattleId.random();
        registerSession(battleId);
        BattleResult aborted = BattleResult.aborted(battleId, Instant.now().minusSeconds(10), Instant.now(), "Admin cancelled");

        BetterDragonVictoryEvent victoryEvent = new BetterDragonVictoryEvent(battleId, aborted, "world_the_end");
        rewardListener.onDragonVictory(victoryEvent);

        assertEquals(0, rewardEvents.size());
        assertEquals(0, claimStorage.count());
    }

    private static class MockInventoryAdapter implements PlayerInventoryAdapter {
        private final Map<UUID, Boolean> online = new HashMap<>();
        private final Map<UUID, Integer> delivered = new HashMap<>();

        void setOnline(UUID id, boolean isOnline) {
            online.put(id, isOnline);
        }

        int getDeliveredCount(UUID id) {
            return delivered.getOrDefault(id, 0);
        }

        @Override
        public boolean isPlayerOnline(UUID playerId) {
            return online.getOrDefault(playerId, false);
        }

        @Override
        public int deliverItem(UUID playerId, RewardItem item) {
            if (!isPlayerOnline(playerId)) return 0;
            delivered.merge(playerId, item.amount(), Integer::sum);
            return item.amount();
        }
    }
}
