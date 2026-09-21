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

        DeliveryBatchResult result = deliveryService.deliverPlan(plan).join();

        assertEquals(1, result.fullyDeliveredCount());
        assertEquals(0, result.pendingCount());
        assertEquals(64, result.totalItemsDelivered());

        List<RewardClaim> claims = claimStorage.findByPlayer(playerId).join();
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

        DeliveryBatchResult result = deliveryService.deliverPlan(plan).join();

        assertEquals(0, result.fullyDeliveredCount());
        assertEquals(1, result.pendingCount());
        assertEquals(0, result.totalItemsDelivered());

        List<RewardClaim> claims = claimStorage.findByPlayer(playerId).join();
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

        DeliveryBatchResult result = deliveryService.deliverPlan(plan).join();

        assertEquals(0, result.fullyDeliveredCount());
        assertEquals(1, result.pendingCount());
        assertEquals(0, result.totalItemsDelivered());

        RewardClaim claim = claimStorage.findByPlayer(playerId).join().getFirst();
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

        DeliveryBatchResult result = deliveryService.deliverPlan(plan).join();

        assertEquals(0, result.fullyDeliveredCount(), "No está completado");
        assertEquals(1, result.pendingCount(), "Tiene remanente pendiente");
        assertEquals(10, result.totalItemsDelivered());

        RewardClaim claim = claimStorage.findByPlayer(playerId).join().getFirst();
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

        deliveryService.deliverPlan(plan).join();

        RewardClaim claimBefore = claimStorage.findByPlayer(playerId).join().getFirst();
        assertEquals(15, claimBefore.getRemainingAmount());
        assertEquals(ClaimStatus.PENDING, claimBefore.status());

        // El jugador libera espacio en su inventario
        inventoryAdapter.setAvailableCapacity(playerId, 50);

        int deliveredOnRetry = deliveryService.retryPendingForPlayer(playerId).join();
        assertEquals(15, deliveredOnRetry);

        RewardClaim claimAfter = claimStorage.findByPlayer(playerId).join().getFirst();
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

        DeliveryBatchResult result = deliveryService.deliverPlan(plan).join();

        assertEquals(0, result.fullyDeliveredCount());
        assertEquals(1, result.pendingCount());

        RewardClaim claim = claimStorage.findByPlayer(playerId).join().getFirst();
        assertEquals(ClaimStatus.FAILED_RETRYABLE, claim.status());
        assertTrue(claim.getFailureReason().isPresent());
        assertTrue(claim.getFailureReason().get().contains("Simulated Inventory Crash"));

        // Recuperar tras solucionar el error transitorio
        inventoryAdapter.setSimulateCrash(playerId, false);
        inventoryAdapter.setAvailableCapacity(playerId, 100);

        int retried = deliveryService.retryPendingForPlayer(playerId).join();
        assertEquals(10, retried);

        RewardClaim recovered = claimStorage.findByPlayer(playerId).join().getFirst();
        assertEquals(ClaimStatus.CLAIMED, recovered.status());
    }

    @Test
    @DisplayName("R2: Stale CLAIMED update es rechazado y no modifica el claim en InMemoryClaimStorage")
    void testStaleClaimedUpdateRejected_InMemory() {
        UUID playerId = UUID.randomUUID();
        String idempotencyKey = "battle:" + battleId.asString() + ":" + playerId + ":stale_inmemory";

        // 1. Crear un claim: status = CLAIMED, deliveredAmount = 64, remainingAmount = 0
        RewardClaim claimedInStorage = new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                "MemPlayer",
                RewardSource.SLAYER,
                new RewardItem("DIAMOND", 64),
                64,
                64,
                ClaimStatus.CLAIMED,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(10),
                null
        );
        claimStorage.createIfAbsent(claimedInStorage).join();

        RewardClaim initialInStorage = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, initialInStorage.status());
        assertEquals(64, initialInStorage.deliveredAmount());
        assertEquals(0, initialInStorage.getRemainingAmount());
        assertEquals("MemPlayer", initialInStorage.playerName());
        assertNull(initialInStorage.failureReason());

        // 2. Crear un objeto stale: mismo idempotencyKey, status = CLAIMED, deliveredAmount = 32, remainingAmount = 32
        RewardClaim staleClaim = new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                "OverwrittenMemPlayer",
                RewardSource.SLAYER,
                new RewardItem("DIAMOND", 64),
                64,
                32,
                ClaimStatus.CLAIMED,
                Instant.now().minusSeconds(120),
                null,
                "Stale failure in memory"
        );

        // 3. Ejecutar: updateExisting(staleClaim)
        boolean updateResult = claimStorage.updateExisting(staleClaim).join();

        // 4. Verificar: la actualización es rechazada/no aplicada
        assertFalse(updateResult, "updateExisting de claim stale sobre CLAIMED en memoria debe retornar false");

        // Comprobar el almacenamiento en memoria
        RewardClaim verifiedInStorage = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, verifiedInStorage.status(), "status debe seguir CLAIMED");
        assertEquals(64, verifiedInStorage.deliveredAmount(), "deliveredAmount debe seguir 64");
        assertEquals(0, verifiedInStorage.getRemainingAmount(), "remainingAmount debe seguir 0");
        assertEquals("MemPlayer", verifiedInStorage.playerName(), "playerName no debe ser sobrescrito");
        assertEquals(claimedInStorage.claimId(), verifiedInStorage.claimId(), "claimId no debe cambiar");
        assertNull(verifiedInStorage.failureReason(), "failureReason no debe ser sobrescrito");
    }

    @Test
    @DisplayName("R2: Transiciones válidas e inválidas en InMemoryClaimStorage")
    void testValidAndInvalidTransitions_InMemory() {
        UUID playerId = UUID.randomUUID();
        String idempotencyKey = "battle:" + battleId.asString() + ":" + playerId + ":transitions_inmemory";

        RewardClaim initial = new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                "MemPlayer",
                RewardSource.PARTICIPATION,
                new RewardItem("EMERALD", 64),
                64,
                0,
                ClaimStatus.PENDING,
                Instant.now(),
                null,
                null
        );
        claimStorage.createIfAbsent(initial).join();

        // PENDING -> PENDING (entrega parcial)
        RewardClaim partial = initial.withDelivery(20, Instant.now());
        assertTrue(claimStorage.updateExisting(partial).join(), "PENDING -> PENDING debe ser aceptada");
        RewardClaim memPending = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.PENDING, memPending.status());
        assertEquals(20, memPending.deliveredAmount());
        assertEquals(44, memPending.getRemainingAmount());

        // PENDING -> FAILED_RETRYABLE
        RewardClaim failed = memPending.withFailure("Simulated Failure");
        assertTrue(claimStorage.updateExisting(failed).join(), "PENDING -> FAILED_RETRYABLE debe ser aceptada");
        RewardClaim memFailed = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.FAILED_RETRYABLE, memFailed.status());
        assertEquals("Simulated Failure", memFailed.failureReason());

        // FAILED_RETRYABLE -> PENDING (reintento)
        RewardClaim retrying = new RewardClaim(
                memFailed.claimId(),
                memFailed.idempotencyKey(),
                memFailed.battleId(),
                memFailed.playerId(),
                memFailed.playerName(),
                memFailed.source(),
                memFailed.item(),
                memFailed.originalAmount(),
                memFailed.deliveredAmount(),
                ClaimStatus.PENDING,
                memFailed.createdAt(),
                null,
                null
        );
        assertTrue(claimStorage.updateExisting(retrying).join(), "FAILED_RETRYABLE -> PENDING debe ser aceptada");
        RewardClaim memRetrying = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.PENDING, memRetrying.status());

        // PENDING -> CLAIMED (entrega total)
        RewardClaim completed = memRetrying.withDelivery(44, Instant.now());
        assertTrue(claimStorage.updateExisting(completed).join(), "PENDING -> CLAIMED debe ser aceptada");
        RewardClaim memClaimed = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, memClaimed.status());
        assertEquals(64, memClaimed.deliveredAmount());
        assertEquals(0, memClaimed.getRemainingAmount());

        // FAILED_RETRYABLE -> CLAIMED (también probado directamente en otro claim)
        String failedToClaimedKey = "battle:" + battleId.asString() + ":" + playerId + ":mem_failed_to_claimed";
        RewardClaim f2cInitial = new RewardClaim(
                UUID.randomUUID(), failedToClaimedKey, battleId, playerId, "MemPlayer",
                RewardSource.PARTICIPATION, new RewardItem("EMERALD", 10), 10, 0,
                ClaimStatus.FAILED_RETRYABLE, Instant.now(), null, "Initial failure"
        );
        claimStorage.createIfAbsent(f2cInitial).join();
        RewardClaim f2cClaimed = new RewardClaim(
                f2cInitial.claimId(), failedToClaimedKey, battleId, playerId, "MemPlayer",
                RewardSource.PARTICIPATION, new RewardItem("EMERALD", 10), 10, 10,
                ClaimStatus.CLAIMED, f2cInitial.createdAt(), Instant.now(), null
        );
        assertTrue(claimStorage.updateExisting(f2cClaimed).join(), "FAILED_RETRYABLE -> CLAIMED debe ser aceptada");

        // Intentos inválidos desde CLAIMED (deben ser todos rechazados):
        // CLAIMED -> PENDING
        RewardClaim invalidPending = new RewardClaim(
                memClaimed.claimId(), idempotencyKey, battleId, playerId, "MemPlayer",
                RewardSource.PARTICIPATION, memClaimed.item(), 64, 0,
                ClaimStatus.PENDING, memClaimed.createdAt(), null, null
        );
        assertFalse(claimStorage.updateExisting(invalidPending).join(), "CLAIMED -> PENDING debe ser rechazada");

        // CLAIMED -> FAILED_RETRYABLE
        RewardClaim invalidFailed = new RewardClaim(
                memClaimed.claimId(), idempotencyKey, battleId, playerId, "MemPlayer",
                RewardSource.PARTICIPATION, memClaimed.item(), 64, 20,
                ClaimStatus.FAILED_RETRYABLE, memClaimed.createdAt(), null, "Late failure"
        );
        assertFalse(claimStorage.updateExisting(invalidFailed).join(), "CLAIMED -> FAILED_RETRYABLE debe ser rechazada");

        // CLAIMED -> CLAIMED (stale update)
        RewardClaim invalidClaimed = new RewardClaim(
                memClaimed.claimId(), idempotencyKey, battleId, playerId, "TamperedPlayer",
                RewardSource.PARTICIPATION, memClaimed.item(), 64, 10,
                ClaimStatus.CLAIMED, memClaimed.createdAt(), Instant.now(), null
        );
        assertFalse(claimStorage.updateExisting(invalidClaimed).join(), "CLAIMED -> CLAIMED debe ser rechazada");

        // Verificar que el almacenamiento en memoria sigue intacto en CLAIMED 64/0
        RewardClaim finalMem = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, finalMem.status());
        assertEquals(64, finalMem.deliveredAmount());
        assertEquals(0, finalMem.getRemainingAmount());
        assertEquals("MemPlayer", finalMem.playerName());
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
