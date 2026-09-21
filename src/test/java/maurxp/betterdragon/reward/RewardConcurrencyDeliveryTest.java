package maurxp.betterdragon.reward;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.persistence.DatabaseManager;
import maurxp.betterdragon.reward.claim.SQLiteClaimStorage;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService.DeliveryBatchResult;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardAllocation;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.model.RewardSource;
import maurxp.betterdragon.util.MainThreadDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Fase 3.9-R1: Pruebas de Estrés y Concurrencia Real de Entrega de Recompensas")
class RewardConcurrencyDeliveryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SQLiteClaimStorage claimStorage;
    private CountingInventoryAdapter inventoryAdapter;
    private RewardDeliveryService deliveryService;
    private ExecutorService simulatedMainThread;
    private final Logger logger = Logger.getLogger("RewardConcurrencyDeliveryTest");

    @BeforeEach
    void setUp() throws Exception {
        Path dbPath = tempDir.resolve("concurrency_rewards.db");
        databaseManager = new DatabaseManager(dbPath, logger);
        databaseManager.initialize();
        claimStorage = new SQLiteClaimStorage(databaseManager, logger);

        inventoryAdapter = new CountingInventoryAdapter();

        // El hilo principal de Bukkit es un único hilo secuencial
        simulatedMainThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "Simulated-Bukkit-Main"));
        MainThreadDispatcher dispatcher = runnable -> simulatedMainThread.submit(runnable);

        deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, dispatcher, logger);
    }

    @AfterEach
    void tearDown() {
        if (claimStorage != null) {
            claimStorage.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (simulatedMainThread != null) {
            simulatedMainThread.shutdown();
        }
    }

    @Test
    @DisplayName("C1: Múltiples hilos concurrentes procesando la misma asignación NO duplican la entrega física")
    void testConcurrentDeliveryDoesNotDuplicatePhysicalItems() throws Exception {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setCapacity(playerId, 1000);

        RewardAllocation alloc = new RewardAllocation(
                battleId,
                playerId,
                "ConcurrentHero",
                RewardSource.PARTICIPATION,
                "diamond_bundle",
                new RewardItem("DIAMOND", 64),
                100.0,
                Instant.now()
        );
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<CompletableFuture<DeliveryBatchResult>> futures = new ArrayList<>();

        ExecutorService workers = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<DeliveryBatchResult> f = new CompletableFuture<>();
            futures.add(f);
            workers.submit(() -> {
                try {
                    startLatch.await();
                    deliveryService.deliverPlan(plan).whenComplete((res, ex) -> {
                        if (ex != null) f.completeExceptionally(ex);
                        else f.complete(res);
                        doneLatch.countDown();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    f.completeExceptionally(e);
                    doneLatch.countDown();
                }
            });
        }

        // Disparar todos los hilos simultáneamente
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Todas las operaciones deben completarse dentro del tiempo límite");
        workers.shutdown();

        // 1. Exactamente un claim lógico persistido en SQLite
        assertEquals(1, claimStorage.count().join(), "Debe existir exactamente 1 fila en SQLite");

        // 2. Estado final del claim en SQLite
        RewardClaim claim = claimStorage.findByIdempotencyKey(alloc.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, claim.status(), "El claim debe culminar en CLAIMED");
        assertEquals(64, claim.deliveredAmount(), "Debe tener 64 ítems entregados");
        assertEquals(0, claim.getRemainingAmount(), "Debe tener 0 ítems remanentes");

        // 3. LA PRUEBA CRÍTICA DE R1: ENTREGA FÍSICA NO DUPLICADA
        assertEquals(1, inventoryAdapter.getPhysicalDeliveriesCount(),
                "deliverItem debe ser invocado exactamente 1 sola vez en el inventario");
        assertEquals(64, inventoryAdapter.getTotalItemsDelivered(),
                "El inventario físico debe haber recibido exactamente 64 diamantes, NUNCA 128 ni más");
    }

    @Test
    @DisplayName("C2: CLAIMED + nuevo intento con el mismo RewardAllocation no realiza ninguna nueva entrega física")
    void testClaimedAllocationRepetitionYieldsZeroNewDeliveries() {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setCapacity(playerId, 1000);

        RewardAllocation alloc = new RewardAllocation(
                battleId,
                playerId,
                "RepeatHero",
                RewardSource.PARTICIPATION,
                "emerald_bundle",
                new RewardItem("EMERALD", 32),
                50.0,
                Instant.now()
        );
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        // Primera entrega exitosa
        DeliveryBatchResult result1 = deliveryService.deliverPlan(plan).join();
        assertEquals(1, result1.fullyDeliveredCount());
        assertEquals(32, result1.totalItemsDelivered());
        assertEquals(1, inventoryAdapter.getPhysicalDeliveriesCount());
        assertEquals(32, inventoryAdapter.getTotalItemsDelivered());

        // Segunda llamada posterior al mismo allocation
        DeliveryBatchResult result2 = deliveryService.deliverPlan(plan).join();
        assertEquals(0, result2.totalItemsDelivered(), "El segundo intento debe reportar 0 ítems nuevos");

        // Verificación física: Cero nuevas llamadas a inventario
        assertEquals(1, inventoryAdapter.getPhysicalDeliveriesCount(), "No debe haber llamadas físicas adicionales");
        assertEquals(32, inventoryAdapter.getTotalItemsDelivered(), "El total de ítems no debe alterarse");

        // Verificación de almacenamiento
        RewardClaim claim = claimStorage.findByIdempotencyKey(alloc.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, claim.status());
        assertEquals(32, claim.deliveredAmount());
        assertEquals(0, claim.getRemainingAmount());
    }

    @Test
    @DisplayName("C3: Entrega parcial legítima seguida de reintento concurrente no duplica remanentes")
    void testPartialDeliveryFollowedByConcurrentRetries() throws Exception {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        inventoryAdapter.setOnline(playerId, true);
        inventoryAdapter.setCapacity(playerId, 40); // Capacidad para 40 de 64

        RewardAllocation alloc = new RewardAllocation(
                battleId,
                playerId,
                "PartialHero",
                RewardSource.PARTICIPATION,
                "gold_bundle",
                new RewardItem("GOLD_INGOT", 64),
                80.0,
                Instant.now()
        );
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        // 1. Primera entrega parcial
        DeliveryBatchResult result1 = deliveryService.deliverPlan(plan).join();
        assertEquals(0, result1.fullyDeliveredCount());
        assertEquals(1, result1.pendingCount());
        assertEquals(40, result1.totalItemsDelivered());
        assertEquals(1, inventoryAdapter.getPhysicalDeliveriesCount());
        assertEquals(40, inventoryAdapter.getTotalItemsDelivered());

        // Verificar estado PENDING en SQLite
        RewardClaim claim1 = claimStorage.findByIdempotencyKey(alloc.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.PENDING, claim1.status());
        assertEquals(40, claim1.deliveredAmount());
        assertEquals(24, claim1.getRemainingAmount());

        // 2. Liberar espacio en inventario
        inventoryAdapter.setCapacity(playerId, 100);

        // 3. Múltiples reintentos concurrentes para el mismo jugador
        int retryThreads = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(retryThreads);
        ExecutorService workers = Executors.newFixedThreadPool(retryThreads);

        for (int i = 0; i < retryThreads; i++) {
            workers.submit(() -> {
                try {
                    startLatch.await();
                    deliveryService.retryPendingForPlayer(playerId).whenComplete((count, ex) -> doneLatch.countDown());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        workers.shutdown();

        // 4. Verificación de integridad física
        // Se debe haber entregado exactamente el remanente de 24 ítems una única vez más (total 2 llamadas físicas)
        assertEquals(2, inventoryAdapter.getPhysicalDeliveriesCount(), "Exactamente 2 entregas físicas (40 inicial + 24 remanente)");
        assertEquals(64, inventoryAdapter.getTotalItemsDelivered(), "Total físico acumulado debe ser exactamente 64");

        // 5. Verificación de estado final en SQLite
        RewardClaim finalClaim = claimStorage.findByIdempotencyKey(alloc.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, finalClaim.status());
        assertEquals(64, finalClaim.deliveredAmount());
        assertEquals(0, finalClaim.getRemainingAmount());
    }

    /**
     * Adaptador de inventario para pruebas que contabiliza con precisión atómica las invocaciones.
     */
    private static class CountingInventoryAdapter implements PlayerInventoryAdapter {
        private volatile boolean online = false;
        private final AtomicInteger capacity = new AtomicInteger(Integer.MAX_VALUE);
        private final AtomicInteger totalDeliveriesCount = new AtomicInteger(0);
        private final AtomicInteger totalItemsDelivered = new AtomicInteger(0);

        void setOnline(UUID id, boolean isOnline) {
            this.online = isOnline;
        }

        void setCapacity(UUID id, int cap) {
            this.capacity.set(cap);
        }

        @Override
        public boolean isPlayerOnline(UUID playerId) {
            return online;
        }

        @Override
        public int deliverItem(UUID playerId, RewardItem item) {
            if (!online) return 0;
            totalDeliveriesCount.incrementAndGet();

            int cap = capacity.get();
            int toGive = Math.min(cap, item.amount());
            capacity.addAndGet(-toGive);
            totalItemsDelivered.addAndGet(toGive);
            return toGive;
        }

        int getPhysicalDeliveriesCount() {
            return totalDeliveriesCount.get();
        }

        int getTotalItemsDelivered() {
            return totalItemsDelivered.get();
        }
    }
}
