package maurxp.betterdragon.reward;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.claim.InMemoryClaimStorage;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService.DeliveryBatchResult;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.model.RewardSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Entrega de Recompensas y Buzón de Reclamos (Fase 3.8)")
class RewardDeliveryAndClaimTest {

    private ClaimStorage claimStorage;
    private MockInventoryAdapter inventoryAdapter;
    private RewardDeliveryService deliveryService;
    private BattleId battleId;

    @BeforeEach
    void setUp() {
        claimStorage = new InMemoryClaimStorage();
        inventoryAdapter = new MockInventoryAdapter();
        deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, Logger.getLogger("RewardTest"));
        battleId = BattleId.random();
    }

    private RewardAllocation createAllocation(UUID playerId, String name, String material, int amount) {
        return createAllocation(playerId, name, "alloc_" + material.toLowerCase(), material, amount);
    }

    private RewardAllocation createAllocation(UUID playerId, String name, String rewardId, String material, int amount) {
        return new RewardAllocation(
                battleId,
                playerId,
                name,
                RewardSource.PARTICIPATION,
                rewardId,
                new RewardItem(material, amount),
                50.0,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Jugador online con inventario libre: entrega total inmediata y estado CLAIMED")
    void testOnlinePlayerFullDelivery() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setAvailableCapacity(playerId, 100);

        RewardAllocation alloc = createAllocation(playerId, "PlayerOnline", "DIAMOND", 64);
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        DeliveryBatchResult result = deliveryService.deliverPlan(plan);

        assertEquals(1, result.fullyDeliveredCount());
        assertEquals(0, result.pendingCount());
        assertEquals(64, result.totalItemsDelivered());

        List<RewardClaim> claims = claimStorage.findByPlayer(playerId);
        assertEquals(1, claims.size());
        RewardClaim claim = claims.getFirst();
        assertEquals(ClaimStatus.CLAIMED, claim.status());
        assertEquals(64, claim.deliveredAmount());
        assertEquals(0, claim.getRemainingAmount());
        assertTrue(claim.getClaimedAt().isPresent());
    }

    @Test
    @DisplayName("Jugador offline: entrega aplazada, reclamo guardado como PENDING sin pérdida de ítems")
    void testOfflinePlayerPreservesPendingClaim() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, false); // Desconectado

        RewardAllocation alloc = createAllocation(playerId, "PlayerOffline", "NETHERITE_INGOT", 5);
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        DeliveryBatchResult result = deliveryService.deliverPlan(plan);

        assertEquals(0, result.fullyDeliveredCount());
        assertEquals(1, result.pendingCount());
        assertEquals(0, result.totalItemsDelivered());

        List<RewardClaim> claims = claimStorage.findByPlayer(playerId);
        assertEquals(1, claims.size());
        RewardClaim claim = claims.getFirst();
        assertEquals(ClaimStatus.PENDING, claim.status());
        assertEquals(0, claim.deliveredAmount());
        assertEquals(5, claim.getRemainingAmount());
        assertTrue(claim.getClaimedAt().isEmpty());
    }

    @Test
    @DisplayName("Inventario completamente lleno: 0 ítems entregados y reclamo conservado en PENDING")
    void testFullInventoryRetainsPending() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setAvailableCapacity(playerId, 0); // Capacidad 0

        RewardAllocation alloc = createAllocation(playerId, "FullInventoryPlayer", "GOLDEN_APPLE", 16);
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        DeliveryBatchResult result = deliveryService.deliverPlan(plan);

        assertEquals(0, result.fullyDeliveredCount());
        assertEquals(1, result.pendingCount());
        assertEquals(0, result.totalItemsDelivered());

        RewardClaim claim = claimStorage.findByPlayer(playerId).getFirst();
        assertEquals(ClaimStatus.PENDING, claim.status());
        assertEquals(0, claim.deliveredAmount());
        assertEquals(16, claim.getRemainingAmount());
    }

    @Test
    @DisplayName("Entrega parcial: se deposita lo que cabe y el remanente se preserva en PENDING")
    void testPartialInventoryDelivery() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setAvailableCapacity(playerId, 10); // Solo caben 10 de 30

        RewardAllocation alloc = createAllocation(playerId, "TightInventoryPlayer", "IRON_INGOT", 30);
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        DeliveryBatchResult result = deliveryService.deliverPlan(plan);

        assertEquals(0, result.fullyDeliveredCount(), "No está completado");
        assertEquals(1, result.pendingCount(), "Tiene remanente pendiente");
        assertEquals(10, result.totalItemsDelivered());

        RewardClaim claim = claimStorage.findByPlayer(playerId).getFirst();
        assertEquals(ClaimStatus.PENDING, claim.status());
        assertEquals(10, claim.deliveredAmount());
        assertEquals(20, claim.getRemainingAmount());
    }

    @Test
    @DisplayName("Reintento tras liberar espacio completa la entrega pendiente y transiciona a CLAIMED")
    void testRetryPendingClaimsCompletesDelivery() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setAvailableCapacity(playerId, 10);

        RewardAllocation alloc = createAllocation(playerId, "Player", "DIAMOND", 25);
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        deliveryService.deliverPlan(plan);

        RewardClaim claimBefore = claimStorage.findByPlayer(playerId).getFirst();
        assertEquals(15, claimBefore.getRemainingAmount());
        assertEquals(ClaimStatus.PENDING, claimBefore.status());

        // El jugador libera espacio en su inventario
        inventoryAdapter.setAvailableCapacity(playerId, 50);

        int deliveredOnRetry = deliveryService.retryPendingForPlayer(playerId);
        assertEquals(15, deliveredOnRetry);

        RewardClaim claimAfter = claimStorage.findByPlayer(playerId).getFirst();
        assertEquals(ClaimStatus.CLAIMED, claimAfter.status());
        assertEquals(25, claimAfter.deliveredAmount());
        assertEquals(0, claimAfter.getRemainingAmount());
    }

    @Test
    @DisplayName("Fallo transitorio captura excepción y conserva el estado FAILED_RETRYABLE")
    void testTransientFailureSetsRetryableState() {
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setSimulateCrash(playerId, true);

        RewardAllocation alloc = createAllocation(playerId, "CrashPlayer", "EMERALD", 10);
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        DeliveryBatchResult result = deliveryService.deliverPlan(plan);

        assertEquals(0, result.fullyDeliveredCount());
        assertEquals(1, result.pendingCount());

        RewardClaim claim = claimStorage.findByPlayer(playerId).getFirst();
        assertEquals(ClaimStatus.FAILED_RETRYABLE, claim.status());
        assertTrue(claim.getFailureReason().isPresent());
        assertTrue(claim.getFailureReason().get().contains("Simulated Inventory Crash"));

        // Recuperar tras solucionar el error transitorio
        inventoryAdapter.setSimulateCrash(playerId, false);
        inventoryAdapter.setAvailableCapacity(playerId, 100);

        int retried = deliveryService.retryPendingForPlayer(playerId);
        assertEquals(10, retried);

        RewardClaim recovered = claimStorage.findByPlayer(playerId).getFirst();
        assertEquals(ClaimStatus.CLAIMED, recovered.status());
    }

    /**
     * Adaptador simulado para pruebas unitarias deterministas.
     */
    private static class MockInventoryAdapter implements PlayerInventoryAdapter {
        private final Set<UUID> onlinePlayers = new HashSet<>();
        private final Map<UUID, Integer> capacity = new HashMap<>();
        private final Set<UUID> crashingPlayers = new HashSet<>();

        void setOnline(UUID id, boolean online) {
            if (online) onlinePlayers.add(id);
            else onlinePlayers.remove(id);
        }

        void setAvailableCapacity(UUID id, int cap) {
            capacity.put(id, cap);
        }

        void setSimulateCrash(UUID id, boolean crash) {
            if (crash) crashingPlayers.add(id);
            else crashingPlayers.remove(id);
        }

        @Override
        public boolean isPlayerOnline(UUID playerId) {
            return onlinePlayers.contains(playerId);
        }

        @Override
        public int deliverItem(UUID playerId, RewardItem item) {
            if (crashingPlayers.contains(playerId)) {
                throw new RuntimeException("Simulated Inventory Crash!");
            }
            if (!isPlayerOnline(playerId)) {
                return 0;
            }
            int available = capacity.getOrDefault(playerId, Integer.MAX_VALUE);
            int toGive = Math.min(available, item.amount());
            capacity.put(playerId, available - toGive);
            return toGive;
        }
    }
}
