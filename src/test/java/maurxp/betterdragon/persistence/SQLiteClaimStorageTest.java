package maurxp.betterdragon.persistence;

import maurxp.betterdragon.battle.model.BattleId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias e Integración de Persistencia Durable SQLite (Fase 3.9)")
class SQLiteClaimStorageTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private DatabaseManager databaseManager;
    private SQLiteClaimStorage claimStorage;
    private final Logger logger = Logger.getLogger("SQLiteClaimStorageTest");

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("test_betterdragon.db");
        databaseManager = new DatabaseManager(dbPath, logger);
        databaseManager.initialize();
        claimStorage = new SQLiteClaimStorage(databaseManager, logger);
    }

    @AfterEach
    void tearDown() {
        if (claimStorage != null) {
            claimStorage.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private RewardClaim createSampleClaim(BattleId battleId, UUID playerId, String rewardId,
                                         String material, int amount, ClaimStatus status) {
        String key = battleId.asString() + ":" + playerId + ":" + rewardId;
        RewardItem item = new RewardItem(material, amount);
        Instant claimedAt = status == ClaimStatus.CLAIMED ? Instant.now() : null;
        int delivered = status == ClaimStatus.CLAIMED ? amount : 0;
        return new RewardClaim(
                UUID.randomUUID(),
                key,
                battleId,
                playerId,
                "TestPlayer",
                RewardSource.PARTICIPATION,
                item,
                amount,
                delivered,
                status,
                Instant.now(),
                claimedAt,
                status == ClaimStatus.FAILED_RETRYABLE ? "Test failure reason" : null
        );
    }

    // =========================================================================
    // A. SCHEMA TESTS
    // =========================================================================

    @Test
    @DisplayName("A1-A4: DB nueva crea schema version 1, tablas e índices requeridos")
    void testSchemaCreationAndVersion() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            // Verificar metadata
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT value FROM bd_schema_metadata WHERE key = 'schema_version'")) {
                assertTrue(rs.next(), "Debe existir registro en bd_schema_metadata");
                assertEquals(String.valueOf(SchemaInitializer.CURRENT_SCHEMA_VERSION), rs.getString("value"), "La versión de schema debe ser la actual");
            }

            // Verificar existencia de tabla bd_reward_claims
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='bd_reward_claims'")) {
                assertTrue(rs.next(), "Debe existir la tabla bd_reward_claims");
            }

            // Verificar existencia de índices
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_reward_claims_%'")) {
                List<String> indices = new ArrayList<>();
                while (rs.next()) {
                    indices.add(rs.getString("name"));
                }
                assertTrue(indices.contains("idx_reward_claims_status"), "Debe existir índice por status");
                assertTrue(indices.contains("idx_reward_claims_participant"), "Debe existir índice por participant_uuid");
                assertTrue(indices.contains("idx_reward_claims_battle"), "Debe existir índice por battle_id");
            }
        }
    }

    // =========================================================================
    // B. INSERT & RETRIEVE
    // =========================================================================

    @Test
    @DisplayName("B5-B7: Insertar claim y recuperarlo conservando todos sus campos exactos")
    void testInsertAndRetrieveClaim() {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        RewardClaim claim = createSampleClaim(battleId, playerId, "diamond_reward", "DIAMOND", 64, ClaimStatus.PENDING);

        claimStorage.save(claim).join();

        Optional<RewardClaim> loadedOpt = claimStorage.findByIdempotencyKey(claim.idempotencyKey()).join();
        assertTrue(loadedOpt.isPresent(), "El claim debe encontrarse por idempotencyKey");

        RewardClaim loaded = loadedOpt.get();
        assertEquals(claim.claimId(), loaded.claimId());
        assertEquals(claim.idempotencyKey(), loaded.idempotencyKey());
        assertEquals(claim.battleId(), loaded.battleId());
        assertEquals(claim.playerId(), loaded.playerId());
        assertEquals(claim.playerName(), loaded.playerName());
        assertEquals(claim.source(), loaded.source());
        assertEquals(claim.item().material(), loaded.item().material());
        assertEquals(64, loaded.getRemainingAmount());
        assertEquals(0, loaded.deliveredAmount());
        assertEquals(ClaimStatus.PENDING, loaded.status());
        assertNotNull(loaded.createdAt());
    }

    // =========================================================================
    // C. IDEMPOTENCIA
    // =========================================================================

    @Test
    @DisplayName("C8-C10: Insertar mismo idempotencyKey dos veces no genera duplicados")
    void testIdempotentInsert() {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        RewardClaim claim = createSampleClaim(battleId, playerId, "r1", "EMERALD", 10, ClaimStatus.PENDING);

        claimStorage.save(claim).join();
        assertEquals(1, claimStorage.count().join());

        // Segunda inserción con la misma clave
        claimStorage.save(claim).join();
        assertEquals(1, claimStorage.count().join(), "No debe crear un segundo claim");

        Optional<RewardClaim> loaded = claimStorage.findByIdempotencyKey(claim.idempotencyKey()).join();
        assertTrue(loaded.isPresent());
        assertEquals(10, loaded.get().getRemainingAmount());
    }

    // =========================================================================
    // D. ESTADOS
    // =========================================================================

    @Test
    @DisplayName("D11-D13: Estados PENDING, CLAIMED y FAILED_RETRYABLE persisten correctamente")
    void testStatusesPersist() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();

        RewardClaim pending = createSampleClaim(b, p, "r_pend", "GOLD_INGOT", 5, ClaimStatus.PENDING);
        RewardClaim claimed = createSampleClaim(b, p, "r_claimed", "IRON_INGOT", 10, ClaimStatus.CLAIMED);
        RewardClaim failed = createSampleClaim(b, p, "r_failed", "COPPER_INGOT", 20, ClaimStatus.FAILED_RETRYABLE);

        claimStorage.save(pending).join();
        claimStorage.save(claimed).join();
        claimStorage.save(failed).join();

        assertEquals(ClaimStatus.PENDING, claimStorage.findByIdempotencyKey(pending.idempotencyKey()).join().orElseThrow().status());
        assertEquals(ClaimStatus.CLAIMED, claimStorage.findByIdempotencyKey(claimed.idempotencyKey()).join().orElseThrow().status());
        assertEquals(ClaimStatus.FAILED_RETRYABLE, claimStorage.findByIdempotencyKey(failed.idempotencyKey()).join().orElseThrow().status());
    }

    // =========================================================================
    // E. REMAINING AMOUNT & PARTIAL DELIVERY
    // =========================================================================

    @Test
    @DisplayName("E14-E15: Actualización de remainingAmount tras entrega parcial persiste")
    void testPartialDeliveryRemainingAmount() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();

        RewardClaim claim = createSampleClaim(b, p, "r_partial", "NETHERITE_SCRAP", 20, ClaimStatus.PENDING);
        claimStorage.save(claim).join();

        // Simular entrega de 8 ítems, quedando 12 pendientes
        RewardClaim partiallyDelivered = claim.withDelivery(8, Instant.now());
        claimStorage.save(partiallyDelivered).join();

        RewardClaim reloaded = claimStorage.findByIdempotencyKey(claim.idempotencyKey()).join().orElseThrow();
        assertEquals(8, reloaded.deliveredAmount());
        assertEquals(12, reloaded.getRemainingAmount());
        assertEquals(ClaimStatus.PENDING, reloaded.status());
    }

    // =========================================================================
    // F & G. RECOVERY TRAS CIERRE Y REAPERTURA (REBOOT)
    // =========================================================================

    @Test
    @DisplayName("F16-F20 & G21-G23: Recovery real: PENDING y FAILED_RETRYABLE sobreviven reinicio, CLAIMED no reaparece como pendiente")
    void testRealRecoveryAcrossReopen() throws Exception {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();

        RewardClaim pending = createSampleClaim(b, p, "rec_pending", "DIAMOND", 32, ClaimStatus.PENDING);
        RewardClaim failed = createSampleClaim(b, p, "rec_failed", "EMERALD", 16, ClaimStatus.FAILED_RETRYABLE);
        RewardClaim claimed = createSampleClaim(b, p, "rec_claimed", "GOLDEN_APPLE", 8, ClaimStatus.CLAIMED);

        claimStorage.save(pending).join();
        claimStorage.save(failed).join();
        claimStorage.save(claimed).join();

        // Cerrar completamente la base de datos simulando shutdown del servidor
        claimStorage.close();
        databaseManager.close();

        // Reabrir nueva instancia sobre el mismo archivo .db simulando inicio del servidor
        DatabaseManager reopenedDb = new DatabaseManager(dbPath, logger);
        reopenedDb.initialize();
        SQLiteClaimStorage reopenedStorage = new SQLiteClaimStorage(reopenedDb, logger);

        try {
            // Consultar pendientes y reintentables para el jugador
            List<RewardClaim> pendingClaims = reopenedStorage.findPendingByPlayer(p).join();
            assertEquals(2, pendingClaims.size(), "PENDING y FAILED_RETRYABLE deben ser recuperados");

            boolean hasPending = pendingClaims.stream().anyMatch(c -> c.idempotencyKey().endsWith("rec_pending") && c.status() == ClaimStatus.PENDING && c.getRemainingAmount() == 32);
            boolean hasFailed = pendingClaims.stream().anyMatch(c -> c.idempotencyKey().endsWith("rec_failed") && c.status() == ClaimStatus.FAILED_RETRYABLE && c.getRemainingAmount() == 16);
            boolean hasClaimed = pendingClaims.stream().anyMatch(c -> c.idempotencyKey().endsWith("rec_claimed"));

            assertTrue(hasPending, "El claim PENDING debe persistir intacto");
            assertTrue(hasFailed, "El claim FAILED_RETRYABLE debe persistir intacto");
            assertFalse(hasClaimed, "El claim CLAIMED no debe aparecer como pendiente");

            // Sin embargo, el claim CLAIMED sigue existiendo en el historial para auditoría
            Optional<RewardClaim> claimedInDb = reopenedStorage.findByIdempotencyKey(claimed.idempotencyKey()).join();
            assertTrue(claimedInDb.isPresent(), "CLAIMED debe conservarse en el historial");
            assertEquals(ClaimStatus.CLAIMED, claimedInDb.get().status());
        } finally {
            reopenedStorage.close();
            reopenedDb.close();
        }
    }

    // =========================================================================
    // H, I, J. AISLAMIENTO POR UUID, BATTLE ID, REWARD ID
    // =========================================================================

    @Test
    @DisplayName("H24-J27: Aislamiento por UUID, BattleId y RewardId")
    void testIsolationAndDeduplication() {
        BattleId b1 = BattleId.random();
        BattleId b2 = BattleId.random();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // Claims para p1 en b1
        RewardClaim c1 = createSampleClaim(b1, p1, "r1", "DIAMOND", 5, ClaimStatus.PENDING);
        RewardClaim c2 = createSampleClaim(b1, p1, "r2", "DIAMOND", 10, ClaimStatus.PENDING); // Mismo material, distinto rewardId

        // Claim para p2 en b1
        RewardClaim c3 = createSampleClaim(b1, p2, "r1", "DIAMOND", 5, ClaimStatus.PENDING);

        // Claim para p1 en b2
        RewardClaim c4 = createSampleClaim(b2, p1, "r1", "DIAMOND", 5, ClaimStatus.PENDING);

        claimStorage.save(c1).join();
        claimStorage.save(c2).join();
        claimStorage.save(c3).join();
        claimStorage.save(c4).join();

        assertEquals(4, claimStorage.count().join());

        // Claims de p1 no deben incluir los de p2
        List<RewardClaim> p1Claims = claimStorage.findByPlayer(p1).join();
        assertEquals(3, p1Claims.size());
        assertFalse(p1Claims.stream().anyMatch(c -> c.playerId().equals(p2)));

        // Mismo material pero distinto rewardId son claims distintos
        assertNotEquals(c1.idempotencyKey(), c2.idempotencyKey());
    }

    // =========================================================================
    // K & L. MATERIAL Y SOURCE
    // =========================================================================

    @Test
    @DisplayName("K28-L30: Material y Source válidos se conservan de forma estable")
    void testMaterialAndSourceStability() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();

        RewardClaim claim = new RewardClaim(
                UUID.randomUUID(),
                b.asString() + ":" + p + ":slayer_sword",
                b,
                p,
                "SlayerPlayer",
                RewardSource.SLAYER,
                new RewardItem("NETHERITE_SWORD", 1),
                1,
                0,
                ClaimStatus.PENDING,
                Instant.now(),
                null,
                null
        );

        claimStorage.save(claim).join();

        RewardClaim loaded = claimStorage.findByIdempotencyKey(claim.idempotencyKey()).join().orElseThrow();
        assertEquals("NETHERITE_SWORD", loaded.item().material());
        assertEquals(RewardSource.SLAYER, loaded.source());
    }

    // =========================================================================
    // M. TIMESTAMPS
    // =========================================================================

    @Test
    @DisplayName("M31-M32: Timestamps created_at y updated_at persisten y se actualizan")
    void testTimestamps() throws Exception {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();

        RewardClaim claim = createSampleClaim(b, p, "time_reward", "ARROW", 64, ClaimStatus.PENDING);
        claimStorage.save(claim).join();

        long createdTime = claim.createdAt().toEpochMilli();

        Thread.sleep(15); // Garantizar avance de reloj

        RewardClaim updated = claim.withDelivery(32, Instant.now());
        claimStorage.save(updated).join();

        RewardClaim reloaded = claimStorage.findByIdempotencyKey(claim.idempotencyKey()).join().orElseThrow();
        assertEquals(createdTime, reloaded.createdAt().toEpochMilli());

        // Verificar updated_at en SQLite directamente
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             PreparedStatement pstmt = conn.prepareStatement("SELECT updated_at FROM bd_reward_claims WHERE idempotency_key = ?")) {
            pstmt.setString(1, claim.idempotencyKey());
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                long updatedAt = rs.getLong("updated_at");
                assertTrue(updatedAt >= createdTime, "updated_at debe ser mayor o igual a created_at");
            }
        }
    }

    // =========================================================================
    // N. CONCURRENCIA / IDEMPOTENCIA
    // =========================================================================

    @Test
    @DisplayName("N33: Múltiples escrituras concurrentes con la misma clave no generan duplicados")
    void testConcurrentIdempotentInserts() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();
        RewardClaim claim = createSampleClaim(b, p, "concurrent_rw", "DIAMOND", 10, ClaimStatus.PENDING);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(claimStorage.save(claim));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertEquals(1, claimStorage.count().join(), "A pesar de 20 escrituras concurrentes solo debe haber 1 registro");
    }

    // =========================================================================
    // DELIVERY INTEGRATION CON SQLITE
    // =========================================================================

    @Test
    @DisplayName("Delivery + SQLite: Entrega parcial y luego reintento completo transiciona y persiste CLAIMED")
    void testDeliveryServiceWithSQLiteStorage() {
        TestInventoryAdapter adapter = new TestInventoryAdapter();
        RewardDeliveryService deliveryService = new RewardDeliveryService(
                adapter, claimStorage, Runnable::run, logger
        );

        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        adapter.setOnline(playerId, true);
        adapter.setCapacity(playerId, 10); // Solo caben 10 de 25

        RewardAllocation alloc = new RewardAllocation(
                battleId,
                playerId,
                "DeliveryHero",
                RewardSource.PARTICIPATION,
                "partial_alloc",
                new RewardItem("IRON_INGOT", 25),
                50.0,
                Instant.now()
        );
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        // 1. Primera entrega parcial
        DeliveryBatchResult result1 = deliveryService.deliverPlan(plan).join();
        assertEquals(0, result1.fullyDeliveredCount());
        assertEquals(1, result1.pendingCount());
        assertEquals(10, result1.totalItemsDelivered());

        // Verificar en SQLite
        RewardClaim claim1 = claimStorage.findByPlayer(playerId).join().getFirst();
        assertEquals(ClaimStatus.PENDING, claim1.status());
        assertEquals(15, claim1.getRemainingAmount());
        assertEquals(10, claim1.deliveredAmount());

        // 2. Liberar espacio e invocar reintento
        adapter.setCapacity(playerId, 50);
        int retried = deliveryService.retryPendingForPlayer(playerId).join();
        assertEquals(15, retried);

        // Verificar en SQLite que transicionó a CLAIMED
        RewardClaim claim2 = claimStorage.findByPlayer(playerId).join().getFirst();
        assertEquals(ClaimStatus.CLAIMED, claim2.status());
        assertEquals(0, claim2.getRemainingAmount());
        assertEquals(25, claim2.deliveredAmount());

        // Verificar que findPendingByPlayer devuelve vacío
        List<RewardClaim> pending = claimStorage.findPendingByPlayer(playerId).join();
        assertTrue(pending.isEmpty());
    }

    // =========================================================================
    // FAILURE & ERROR HANDLING TESTS
    // =========================================================================

    @Test
    @DisplayName("Falla controlada: Versión futura de schema es rechazada con IllegalStateException")
    void testFutureSchemaVersionRejected() throws Exception {
        Path futureDbPath = tempDir.resolve("future_betterdragon.db");

        // Crear una base con schema version 99
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + futureDbPath.toAbsolutePath())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE bd_schema_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
                stmt.execute("INSERT INTO bd_schema_metadata (key, value) VALUES ('schema_version', '99');");
            }
        }

        DatabaseManager futureManager = new DatabaseManager(futureDbPath, logger);
        IllegalStateException ex = assertThrows(IllegalStateException.class, futureManager::initialize);
        assertTrue(ex.getMessage().contains("Versión de esquema incompatible detectada"),
                "Debe rechazar versiones futuras de schema");
        futureManager.close();
    }

    @Test
    @DisplayName("Falla controlada: Operaciones sobre almacenamiento cerrado lanzan IllegalStateException")
    void testClosedStorageThrowsException() {
        claimStorage.close();
        databaseManager.close();

        RewardClaim claim = createSampleClaim(BattleId.random(), UUID.randomUUID(), "c1", "DIAMOND", 1, ClaimStatus.PENDING);

        CompletableFuture<Void> future = claimStorage.save(claim);
        assertThrows(Exception.class, future::join);
    }

    @Test
    @DisplayName("K29: Material inválido o desconocido en DB se conserva y previene CLAIMED silencioso sincronizando SQLite")
    void testInvalidMaterialHandling() throws Exception {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();
        String key = b.asString() + ":" + p + ":unknown_item";

        // Insertar un registro con material inexistente directamente por JDBC
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO bd_reward_claims ("
                             + "claim_id, idempotency_key, battle_id, participant_uuid, player_name, "
                             + "source, material, original_amount, delivered_amount, remaining_amount, "
                             + "status, created_at, updated_at"
                             + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, UUID.randomUUID().toString());
            pstmt.setString(2, key);
            pstmt.setString(3, b.asString());
            pstmt.setString(4, p.toString());
            pstmt.setString(5, "LegacyPlayer");
            pstmt.setString(6, RewardSource.PARTICIPATION.name());
            pstmt.setString(7, "LEGACY_NONEXISTENT_MATERIAL_XYZ");
            pstmt.setInt(8, 10);
            pstmt.setInt(9, 10);
            pstmt.setInt(10, 0);
            pstmt.setString(11, ClaimStatus.CLAIMED.name());
            pstmt.setLong(12, System.currentTimeMillis());
            pstmt.setLong(13, System.currentTimeMillis());
            pstmt.executeUpdate();
        }

        // Al leer a través de SQLiteClaimStorage
        Optional<RewardClaim> loaded = claimStorage.findByIdempotencyKey(key).join();
        assertTrue(loaded.isPresent());
        RewardClaim claim = loaded.get();
        assertEquals("LEGACY_NONEXISTENT_MATERIAL_XYZ", claim.item().material(), "El nombre del material debe conservarse");
        assertEquals(ClaimStatus.FAILED_RETRYABLE, claim.status(), "Material inválido previene CLAIMED silencioso");
        assertTrue(claim.failureReason() != null && claim.failureReason().contains("Material desconocido"));

        // Comprobación R1: Verificar que SQLite fue sincronizado persistentemente a FAILED_RETRYABLE
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             PreparedStatement pstmt = conn.prepareStatement("SELECT status, failure_reason FROM bd_reward_claims WHERE idempotency_key = ?")) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("FAILED_RETRYABLE", rs.getString("status"), "SQLite debe estar sincronizado a FAILED_RETRYABLE");
                assertNotNull(rs.getString("failure_reason"));
            }
        }

        // Comprobación R1: Verificar que findPendingByPlayer lo recupera correctamente para reintento/auditoría
        List<RewardClaim> pendingList = claimStorage.findPendingByPlayer(p).join();
        assertEquals(1, pendingList.size(), "findPendingByPlayer debe recuperar el claim sincronizado como FAILED_RETRYABLE");
        assertEquals(key, pendingList.getFirst().idempotencyKey());
    }

    @Test
    @DisplayName("R1: createIfAbsent es atómico bajo concurrencia y todos los hilos reciben la misma instancia")
    void testAtomicCreateIfAbsentConcurrent() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();
        RewardClaim claim = createSampleClaim(b, p, "atomic_absent", "DIAMOND", 32, ClaimStatus.PENDING);

        List<CompletableFuture<RewardClaim>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(claimStorage.createIfAbsent(claim));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertEquals(1, claimStorage.count().join(), "Exactamente 1 fila debe existir en DB");

        UUID expectedId = futures.getFirst().join().claimId();
        for (CompletableFuture<RewardClaim> f : futures) {
            RewardClaim result = f.join();
            assertEquals(expectedId, result.claimId(), "Todos los hilos deben obtener la misma instancia lógica persistida");
            assertEquals(ClaimStatus.PENDING, result.status());
            assertEquals(32, result.getRemainingAmount());
        }
    }

    @Test
    @DisplayName("R1: createIfAbsent no sobrescribe un claim que ya está en estado CLAIMED")
    void testCreateIfAbsentDoesNotOverwriteClaimed() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();
        RewardClaim claimed = createSampleClaim(b, p, "already_claimed", "EMERALD", 64, ClaimStatus.CLAIMED);

        claimStorage.save(claimed).join();
        assertEquals(ClaimStatus.CLAIMED, claimStorage.findByIdempotencyKey(claimed.idempotencyKey()).join().orElseThrow().status());

        // Intentar createIfAbsent con una nueva instancia en estado PENDING con el mismo idempotencyKey
        RewardClaim pendingAttempt = createSampleClaim(b, p, "already_claimed", "EMERALD", 64, ClaimStatus.PENDING);
        RewardClaim returnedClaim = claimStorage.createIfAbsent(pendingAttempt).join();

        // Debe retornar el existente intacto
        assertEquals(claimed.claimId(), returnedClaim.claimId());
        assertEquals(ClaimStatus.CLAIMED, returnedClaim.status());
        assertEquals(64, returnedClaim.deliveredAmount());
        assertEquals(0, returnedClaim.getRemainingAmount());

        // Verificar en SQLite
        RewardClaim persisted = claimStorage.findByIdempotencyKey(claimed.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, persisted.status());
        assertEquals(0, persisted.getRemainingAmount());
    }

    @Test
    @DisplayName("R1: updateExisting protege transiciones y no permite degradar CLAIMED a PENDING")
    void testUpdateExistingGuardsClaimed() {
        BattleId b = BattleId.random();
        UUID p = UUID.randomUUID();
        RewardClaim initial = createSampleClaim(b, p, "guard_update", "GOLD_INGOT", 20, ClaimStatus.PENDING);
        claimStorage.createIfAbsent(initial).join();

        // 1. Actualización parcial válida de PENDING a PENDING con delivery
        RewardClaim partial = initial.withDelivery(10, Instant.now());
        boolean updated1 = claimStorage.updateExisting(partial).join();
        assertTrue(updated1, "Actualización legítima debe retornar true");

        RewardClaim inDb1 = claimStorage.findByIdempotencyKey(initial.idempotencyKey()).join().orElseThrow();
        assertEquals(10, inDb1.deliveredAmount());
        assertEquals(10, inDb1.getRemainingAmount());
        assertEquals(ClaimStatus.PENDING, inDb1.status());

        // 2. Transición a CLAIMED
        RewardClaim completed = partial.withDelivery(10, Instant.now());
        boolean updated2 = claimStorage.updateExisting(completed).join();
        assertTrue(updated2);

        RewardClaim inDb2 = claimStorage.findByIdempotencyKey(initial.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, inDb2.status());
        assertEquals(0, inDb2.getRemainingAmount());

        // 3. Intento ilegítimo de degradar CLAIMED a PENDING
        RewardClaim illegalDowngrade = createSampleClaim(b, p, "guard_update", "GOLD_INGOT", 20, ClaimStatus.PENDING);
        boolean updated3 = claimStorage.updateExisting(illegalDowngrade).join();
        assertFalse(updated3, "Intento de degradar CLAIMED debe ser rechazado y retornar false");

        RewardClaim inDb3 = claimStorage.findByIdempotencyKey(initial.idempotencyKey()).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, inDb3.status(), "El estado en SQLite debe permanecer CLAIMED");
        assertEquals(0, inDb3.getRemainingAmount());
    }

    @Test
    @DisplayName("R2: Stale CLAIMED update es rechazado y no modifica columnas en SQLite")
    void testStaleClaimedUpdateRejected_SQLite() {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        String idempotencyKey = "battle:" + battleId.asString() + ":" + playerId + ":stale_test";

        // 1. Crear un claim: status = CLAIMED, deliveredAmount = 64, remainingAmount = 0
        RewardClaim claimedInDb = new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                "TestPlayer",
                RewardSource.SLAYER,
                new RewardItem("DIAMOND", 64),
                64,
                64,
                ClaimStatus.CLAIMED,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(10),
                null
        );
        claimStorage.createIfAbsent(claimedInDb).join();

        RewardClaim initialInDb = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, initialInDb.status());
        assertEquals(64, initialInDb.deliveredAmount());
        assertEquals(0, initialInDb.getRemainingAmount());
        assertEquals("TestPlayer", initialInDb.playerName());
        assertNull(initialInDb.failureReason());

        // 2. Crear un objeto stale: mismo idempotencyKey, status = CLAIMED, deliveredAmount = 32, remainingAmount = 32
        RewardClaim staleClaim = new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                "OverwrittenName",
                RewardSource.SLAYER,
                new RewardItem("DIAMOND", 64),
                64,
                32,
                ClaimStatus.CLAIMED,
                Instant.now().minusSeconds(120),
                null,
                "Stale failure"
        );

        // 3. Ejecutar: updateExisting(staleClaim)
        boolean updateResult = claimStorage.updateExisting(staleClaim).join();

        // 4. Verificar: la actualización es rechazada/no aplicada
        assertFalse(updateResult, "updateExisting de claim stale sobre CLAIMED debe retornar false");

        // Comprobar la DB real directamente
        RewardClaim verifiedInDb = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, verifiedInDb.status(), "status debe seguir CLAIMED");
        assertEquals(64, verifiedInDb.deliveredAmount(), "deliveredAmount debe seguir 64");
        assertEquals(0, verifiedInDb.getRemainingAmount(), "remainingAmount debe seguir 0");
        assertEquals("TestPlayer", verifiedInDb.playerName(), "playerName no debe ser sobrescrito");
        assertEquals(claimedInDb.claimId(), verifiedInDb.claimId(), "claimId no debe cambiar");
        assertNull(verifiedInDb.failureReason(), "failureReason no debe ser sobrescrito");
    }

    @Test
    @DisplayName("R2: Transiciones válidas e inválidas en SQLite")
    void testValidAndInvalidTransitions_SQLite() {
        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        String idempotencyKey = "battle:" + battleId.asString() + ":" + playerId + ":transitions_test";

        RewardClaim initial = new RewardClaim(
                UUID.randomUUID(),
                idempotencyKey,
                battleId,
                playerId,
                "TestPlayer",
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
        RewardClaim dbPending = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.PENDING, dbPending.status());
        assertEquals(20, dbPending.deliveredAmount());
        assertEquals(44, dbPending.getRemainingAmount());

        // PENDING -> FAILED_RETRYABLE
        RewardClaim failed = dbPending.withFailure("Simulated Failure");
        assertTrue(claimStorage.updateExisting(failed).join(), "PENDING -> FAILED_RETRYABLE debe ser aceptada");
        RewardClaim dbFailed = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.FAILED_RETRYABLE, dbFailed.status());
        assertEquals("Simulated Failure", dbFailed.failureReason());

        // FAILED_RETRYABLE -> PENDING (reintento)
        RewardClaim retrying = new RewardClaim(
                dbFailed.claimId(),
                dbFailed.idempotencyKey(),
                dbFailed.battleId(),
                dbFailed.playerId(),
                dbFailed.playerName(),
                dbFailed.source(),
                dbFailed.item(),
                dbFailed.originalAmount(),
                dbFailed.deliveredAmount(),
                ClaimStatus.PENDING,
                dbFailed.createdAt(),
                null,
                null
        );
        assertTrue(claimStorage.updateExisting(retrying).join(), "FAILED_RETRYABLE -> PENDING debe ser aceptada");
        RewardClaim dbRetrying = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.PENDING, dbRetrying.status());

        // PENDING -> CLAIMED (entrega total)
        RewardClaim completed = dbRetrying.withDelivery(44, Instant.now());
        assertTrue(claimStorage.updateExisting(completed).join(), "PENDING -> CLAIMED debe ser aceptada");
        RewardClaim dbClaimed = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, dbClaimed.status());
        assertEquals(64, dbClaimed.deliveredAmount());
        assertEquals(0, dbClaimed.getRemainingAmount());

        // FAILED_RETRYABLE -> CLAIMED (también probado directamente en otro claim)
        String failedToClaimedKey = "battle:" + battleId.asString() + ":" + playerId + ":failed_to_claimed";
        RewardClaim f2cInitial = new RewardClaim(
                UUID.randomUUID(), failedToClaimedKey, battleId, playerId, "TestPlayer",
                RewardSource.PARTICIPATION, new RewardItem("EMERALD", 10), 10, 0,
                ClaimStatus.FAILED_RETRYABLE, Instant.now(), null, "Initial failure"
        );
        claimStorage.createIfAbsent(f2cInitial).join();
        RewardClaim f2cClaimed = new RewardClaim(
                f2cInitial.claimId(), failedToClaimedKey, battleId, playerId, "TestPlayer",
                RewardSource.PARTICIPATION, new RewardItem("EMERALD", 10), 10, 10,
                ClaimStatus.CLAIMED, f2cInitial.createdAt(), Instant.now(), null
        );
        assertTrue(claimStorage.updateExisting(f2cClaimed).join(), "FAILED_RETRYABLE -> CLAIMED debe ser aceptada");

        // Intentos inválidos desde CLAIMED (deben ser todos rechazados):
        // CLAIMED -> PENDING
        RewardClaim invalidPending = new RewardClaim(
                dbClaimed.claimId(), idempotencyKey, battleId, playerId, "TestPlayer",
                RewardSource.PARTICIPATION, dbClaimed.item(), 64, 0,
                ClaimStatus.PENDING, dbClaimed.createdAt(), null, null
        );
        assertFalse(claimStorage.updateExisting(invalidPending).join(), "CLAIMED -> PENDING debe ser rechazada");

        // CLAIMED -> FAILED_RETRYABLE
        RewardClaim invalidFailed = new RewardClaim(
                dbClaimed.claimId(), idempotencyKey, battleId, playerId, "TestPlayer",
                RewardSource.PARTICIPATION, dbClaimed.item(), 64, 20,
                ClaimStatus.FAILED_RETRYABLE, dbClaimed.createdAt(), null, "Late failure"
        );
        assertFalse(claimStorage.updateExisting(invalidFailed).join(), "CLAIMED -> FAILED_RETRYABLE debe ser rechazada");

        // CLAIMED -> CLAIMED (stale update)
        RewardClaim invalidClaimed = new RewardClaim(
                dbClaimed.claimId(), idempotencyKey, battleId, playerId, "TamperedPlayer",
                RewardSource.PARTICIPATION, dbClaimed.item(), 64, 10,
                ClaimStatus.CLAIMED, dbClaimed.createdAt(), Instant.now(), null
        );
        assertFalse(claimStorage.updateExisting(invalidClaimed).join(), "CLAIMED -> CLAIMED debe ser rechazada");

        // Verificar que la DB sigue intacta en CLAIMED 64/0
        RewardClaim finalDb = claimStorage.findByIdempotencyKey(idempotencyKey).join().orElseThrow();
        assertEquals(ClaimStatus.CLAIMED, finalDb.status());
        assertEquals(64, finalDb.deliveredAmount());
        assertEquals(0, finalDb.getRemainingAmount());
        assertEquals("TestPlayer", finalDb.playerName());
    }

    @Test
    @DisplayName("R1: DatabaseManager.initializeAsync() inicializa en segundo plano sin bloquear y queda listo")
    void testAsyncDatabaseInitialization() throws Exception {
        Path asyncDbPath = tempDir.resolve("async_betterdragon.db");
        try (DatabaseManager asyncManager = new DatabaseManager(asyncDbPath, logger)) {
            assertFalse(asyncManager.isReady(), "Inicialmente no debe estar listo");
            CompletableFuture<Void> initFuture = asyncManager.initializeAsync();
            initFuture.join();
            assertTrue(asyncManager.isReady(), "Debe marcarse listo tras completar initializeAsync");
            assertNull(asyncManager.getInitializationError());

            // Probar una consulta sobre el manager inicializado asíncronamente
            Integer count = asyncManager.supplyAsync(conn -> {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master;")) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }).join();
            assertTrue(count >= 0);
        }
    }

    @Test
    @DisplayName("Delivery completo + Reopen: Full delivery persiste CLAIMED y tras reopen no reaparece como pending")
    void testFullDeliveryPersistsClaimedAndNoLongerPendingReopen() throws Exception {
        TestInventoryAdapter adapter = new TestInventoryAdapter();
        RewardDeliveryService deliveryService = new RewardDeliveryService(
                adapter, claimStorage, Runnable::run, logger
        );

        BattleId battleId = BattleId.random();
        UUID playerId = UUID.randomUUID();
        adapter.setOnline(playerId, true);
        adapter.setCapacity(playerId, 100); // Espacio suficiente

        RewardAllocation alloc = new RewardAllocation(
                battleId,
                playerId,
                "HeroFull",
                RewardSource.PARTICIPATION,
                "full_alloc",
                new RewardItem("GOLD_INGOT", 16),
                50.0,
                Instant.now()
        );
        RewardAllocationPlan plan = new RewardAllocationPlan(battleId, List.of(alloc), Instant.now());

        DeliveryBatchResult result = deliveryService.deliverPlan(plan).join();
        assertEquals(1, result.fullyDeliveredCount());
        assertEquals(0, result.pendingCount());
        assertEquals(16, result.totalItemsDelivered());

        // Cerrar y reabrir
        claimStorage.close();
        databaseManager.close();

        DatabaseManager reopenedDb = new DatabaseManager(dbPath, logger);
        reopenedDb.initialize();
        SQLiteClaimStorage reopenedStorage = new SQLiteClaimStorage(reopenedDb, logger);

        try {
            List<RewardClaim> pending = reopenedStorage.findPendingByPlayer(playerId).join();
            assertTrue(pending.isEmpty(), "No debe tener reclamos pendientes tras reapertura");

            Optional<RewardClaim> claimed = reopenedStorage.findByIdempotencyKey(alloc.idempotencyKey()).join();
            assertTrue(claimed.isPresent());
            assertEquals(ClaimStatus.CLAIMED, claimed.get().status());
            assertEquals(16, claimed.get().deliveredAmount());
            assertEquals(0, claimed.get().getRemainingAmount());
        } finally {
            reopenedStorage.close();
            reopenedDb.close();
        }
    }

    // =========================================================================
    // HELPER TEST ADAPTER
    // =========================================================================

    private static class TestInventoryAdapter implements PlayerInventoryAdapter {
        private final Map<UUID, Boolean> online = new HashMap<>();
        private final Map<UUID, Integer> capacity = new HashMap<>();
        private final Map<UUID, Integer> totalDelivered = new HashMap<>();

        void setOnline(UUID id, boolean isOnline) {
            online.put(id, isOnline);
        }

        void setCapacity(UUID id, int cap) {
            capacity.put(id, cap);
        }

        @Override
        public boolean isPlayerOnline(UUID playerId) {
            return online.getOrDefault(playerId, false);
        }

        @Override
        public int deliverItem(UUID playerId, RewardItem item) {
            if (!isPlayerOnline(playerId)) return 0;
            int avail = capacity.getOrDefault(playerId, Integer.MAX_VALUE);
            int toGive = Math.min(avail, item.amount());
            capacity.put(playerId, avail - toGive);
            totalDelivered.merge(playerId, toGive, Integer::sum);
            return toGive;
        }
    }
}
