package maurxp.betterdragon.leaderboard;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;
import maurxp.betterdragon.leaderboard.service.LeaderboardService;
import maurxp.betterdragon.leaderboard.storage.SQLiteLeaderboardStorage;
import maurxp.betterdragon.persistence.DatabaseManager;
import maurxp.betterdragon.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias e Integración del Leaderboard Persistente (Fase 3.10)")
class SQLiteLeaderboardStorageTest {

    @TempDir
    Path tempDir;

    private Path dbPath;
    private DatabaseManager databaseManager;
    private SQLiteLeaderboardStorage leaderboardStorage;
    private LeaderboardService leaderboardService;
    private final Logger logger = Logger.getLogger("SQLiteLeaderboardStorageTest");

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("test_leaderboard.db");
        databaseManager = new DatabaseManager(dbPath, logger);
        databaseManager.initializeSync();
        leaderboardStorage = new SQLiteLeaderboardStorage(databaseManager, logger);
        leaderboardService = new LeaderboardService(leaderboardStorage, logger);
    }

    @AfterEach
    void tearDown() {
        if (leaderboardService != null) {
            leaderboardService.close();
        }
        if (databaseManager != null && !databaseManager.isClosed()) {
            databaseManager.close();
        }
    }

    // =========================================================================
    // A. SCHEMA & MIGRATION TESTS (Requisitos 24, 25, 42-A, 43)
    // =========================================================================

    @Test
    @DisplayName("A1: Base de datos nueva se inicializa directamente en schema v2 con tablas e índices")
    void testFreshDatabaseInitializesToV2() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            // Verificar schema_version = 2
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT value FROM bd_schema_metadata WHERE key = 'schema_version'")) {
                assertTrue(rs.next(), "Debe existir registro schema_version");
                assertEquals("2", rs.getString("value"), "Schema version debe ser 2");
            }

            // Verificar existencia de tablas del leaderboard
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                List<String> tables = new ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
                assertTrue(tables.contains(SchemaInitializer.TABLE_REWARD_CLAIMS), "Debe contener tabla bd_reward_claims");
                assertTrue(tables.contains(SchemaInitializer.TABLE_LEADERBOARD_BATTLES), "Debe contener tabla bd_leaderboard_battles");
                assertTrue(tables.contains(SchemaInitializer.TABLE_LEADERBOARD_PARTICIPATION), "Debe contener tabla bd_leaderboard_participation");
                assertTrue(tables.contains(SchemaInitializer.TABLE_LEADERBOARD_PLAYERS), "Debe contener tabla bd_leaderboard_players");
            }
        }
    }

    @Test
    @DisplayName("A2 & Req 43: Migración explícita v1 -> v2 conserva datos de bd_reward_claims intactos")
    void testMigrationFromV1ToV2PreservesClaims() throws Exception {
        Path migrationDbPath = tempDir.resolve("migration_v1_v2.db");

        // 1. Simular base de datos preexistente en versión 1 con datos en bd_reward_claims
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + migrationDbPath.toAbsolutePath())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE bd_schema_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
                stmt.execute("INSERT INTO bd_schema_metadata (key, value) VALUES ('schema_version', '1');");

                stmt.execute("CREATE TABLE bd_reward_claims ("
                        + "claim_id TEXT PRIMARY KEY, idempotency_key TEXT UNIQUE NOT NULL, battle_id TEXT NOT NULL, "
                        + "participant_uuid TEXT NOT NULL, player_name TEXT NOT NULL, source TEXT NOT NULL, "
                        + "material TEXT NOT NULL, display_name TEXT, lore TEXT, original_amount INTEGER NOT NULL, "
                        + "delivered_amount INTEGER NOT NULL, remaining_amount INTEGER NOT NULL, status TEXT NOT NULL, "
                        + "created_at INTEGER NOT NULL, claimed_at INTEGER, failure_reason TEXT, updated_at INTEGER NOT NULL);");

                stmt.execute("INSERT INTO bd_reward_claims (claim_id, idempotency_key, battle_id, participant_uuid, player_name, "
                        + "source, material, original_amount, delivered_amount, remaining_amount, status, created_at, updated_at) "
                        + "VALUES ('claim-123', 'idemp-123', 'battle-1', 'player-uuid-1', 'Mauricio', 'SLAYER', 'NETHERITE_SWORD', "
                        + "1, 1, 0, 'CLAIMED', 1000, 1000);");
            }
        }

        // 2. Inicializar DatabaseManager sobre la DB en v1
        DatabaseManager migratorManager = new DatabaseManager(migrationDbPath, logger);
        migratorManager.initializeSync();

        // 3. Verificar que la versión ascendió a 2 y el claim continúa intacto
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + migrationDbPath.toAbsolutePath())) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT value FROM bd_schema_metadata WHERE key = 'schema_version'")) {
                assertTrue(rs.next());
                assertEquals("2", rs.getString("value"), "La base debe haber migrado a schema versión 2");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT claim_id, status, player_name, material FROM bd_reward_claims WHERE claim_id = 'claim-123'")) {
                assertTrue(rs.next(), "El claim preexistente debe seguir existiendo");
                assertEquals("CLAIMED", rs.getString("status"));
                assertEquals("Mauricio", rs.getString("player_name"));
                assertEquals("NETHERITE_SWORD", rs.getString("material"));
            }

            // Verificar que las nuevas tablas del leaderboard fueron creadas
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'bd_leaderboard_%'")) {
                List<String> lbTables = new ArrayList<>();
                while (rs.next()) {
                    lbTables.add(rs.getString("name"));
                }
                assertEquals(3, lbTables.size(), "Deben haberse creado las 3 tablas de leaderboard");
            }
        } finally {
            migratorManager.close();
        }
    }

    @Test
    @DisplayName("A3: Reinicializar DB ya en v2 es idempotente y no falla")
    void testReinitializeV2IsIdempotent() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            SchemaInitializer initializer = new SchemaInitializer(logger);
            assertDoesNotThrow(() -> initializer.initializeSchema(conn));
        }
    }

    @Test
    @DisplayName("A4: Rechazo fail-safe ante base de datos con versión futura (v3+)")
    void testFutureSchemaVersionThrowsException() throws Exception {
        Path futureDb = tempDir.resolve("future_schema.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + futureDb.toAbsolutePath())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE bd_schema_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
                stmt.execute("INSERT INTO bd_schema_metadata (key, value) VALUES ('schema_version', '99');");
            }
        }

        DatabaseManager futureManager = new DatabaseManager(futureDb, logger);
        assertThrows(Exception.class, futureManager::initializeSync, "Debe rechazar versiones futuras de esquema");
        futureManager.close();
    }

    // =========================================================================
    // B. BATTLE RECORDING & RETRIEVAL (Requisitos 8, 9, 42-B)
    // =========================================================================

    @Test
    @DisplayName("B1: Registrar batalla exitosa con world, slayer y timestamps")
    void testRecordBattleAndRetrieve() {
        BattleId battleId = BattleId.random();
        Instant completedAt = Instant.ofEpochMilli(1700000000000L);
        UUID slayerUuid = UUID.randomUUID();
        String worldName = "world_the_end";

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, completedAt, worldName, slayerUuid);
        boolean recorded = leaderboardStorage.recordBattle(battle, List.of()).join();
        assertTrue(recorded, "La batalla debe registrarse exitosamente");

        Optional<LeaderboardBattleRecord> retrieved = leaderboardStorage.getBattle(battleId).join();
        assertTrue(retrieved.isPresent(), "La batalla debe recuperarse por ID");
        assertEquals(battleId, retrieved.get().battleId());
        assertEquals(completedAt, retrieved.get().completedAt());
        assertEquals(worldName, retrieved.get().worldName());
        assertEquals(Optional.of(slayerUuid), retrieved.get().getSlayerUuid());
    }

    // =========================================================================
    // C. PARTICIPATION HISTORY (Requisitos 10, 15, 42-C)
    // =========================================================================

    @Test
    @DisplayName("C1: Registrar participaciones preservando historicalName, daño y secuencia")
    void testRecordParticipationHistory() {
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, now, "world_the_end", player1);

        LeaderboardParticipantRecord part1 = new LeaderboardParticipantRecord(
                battleId, player1, "Mauricio_Old", "Mauricio_New", 5500.0, 1L, true, now);
        LeaderboardParticipantRecord part2 = new LeaderboardParticipantRecord(
                battleId, player2, "Alex", "Alex", 2000.0, 2L, false, now);

        leaderboardStorage.recordBattle(battle, List.of(part1, part2)).join();

        List<LeaderboardParticipantRecord> participants = leaderboardStorage.getBattleParticipants(battleId).join();
        assertEquals(2, participants.size());

        // Verificar ordenación por daño DESC en participaciones
        assertEquals(player1, participants.get(0).playerUuid());
        assertEquals("Mauricio_Old", participants.get(0).historicalName());
        assertEquals(5500.0, participants.get(0).damage());
        assertTrue(participants.get(0).wasSlayer());

        assertEquals(player2, participants.get(1).playerUuid());
        assertEquals("Alex", participants.get(1).historicalName());
        assertEquals(2000.0, participants.get(1).damage());
        assertFalse(participants.get(1).wasSlayer());
    }

    // =========================================================================
    // D. PLAYER AGGREGATES & AVERAGE DAMAGE (Requisitos 5, 11, 17, 18, 36, 42-D)
    // =========================================================================

    @Test
    @DisplayName("D1: Estadísticas acumuladas iniciales y averageDamage derivado en memoria")
    void testInitialPlayerAggregatesAndAverageDamage() {
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();
        UUID player = UUID.randomUUID();

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, now, "world_the_end", player);
        LeaderboardParticipantRecord part = new LeaderboardParticipantRecord(
                battleId, player, "Steve", "Steve", 4500.0, 1L, true, now);

        leaderboardStorage.recordBattle(battle, List.of(part)).join();

        Optional<LeaderboardPlayerStats> statsOpt = leaderboardStorage.getPlayerStats(player).join();
        assertTrue(statsOpt.isPresent());

        LeaderboardPlayerStats stats = statsOpt.get();
        assertEquals(player, stats.playerUuid());
        assertEquals("Steve", stats.lastKnownName());
        assertEquals(1, stats.battlesParticipated());
        assertEquals(4500.0, stats.totalDamage());
        assertEquals(4500.0, stats.highestDamage());
        assertEquals(1, stats.slayerCount());
        assertEquals(now.toEpochMilli(), stats.firstParticipationAt().toEpochMilli());
        assertEquals(now.toEpochMilli(), stats.lastParticipationAt().toEpochMilli());

        // averageDamage se calcula dinámicamente y no se guarda en tabla
        assertEquals(4500.0, stats.getAverageDamage(), 0.001);
    }

    // =========================================================================
    // E. MULTIPLE BATTLES ACCUMULATION (Requisitos 17, 42-E)
    // =========================================================================

    @Test
    @DisplayName("E1 & Req 42-E: Batallas múltiples acumulan daño, calculan highest y número de participaciones")
    void testMultipleBattlesAccumulation() {
        // Ejemplo mandatorio de especificación:
        // Battle 1 = 5000
        // Battle 2 = 7200
        // Battle 3 = 6100
        // Esperado: total = 18300, highest = 7200, participations = 3, average = 6100
        UUID player = UUID.randomUUID();

        BattleId b1 = BattleId.random();
        Instant t1 = Instant.ofEpochMilli(1000000L);
        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b1, t1, "world", player),
                List.of(new LeaderboardParticipantRecord(b1, player, "Player", "Player", 5000.0, 1L, true, t1))
        ).join();

        BattleId b2 = BattleId.random();
        Instant t2 = Instant.ofEpochMilli(2000000L);
        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b2, t2, "world", player),
                List.of(new LeaderboardParticipantRecord(b2, player, "Player", "Player", 7200.0, 1L, true, t2))
        ).join();

        BattleId b3 = BattleId.random();
        Instant t3 = Instant.ofEpochMilli(3000000L);
        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b3, t3, "world", null),
                List.of(new LeaderboardParticipantRecord(b3, player, "Player", "Player", 6100.0, 1L, false, t3))
        ).join();

        LeaderboardPlayerStats stats = leaderboardStorage.getPlayerStats(player).join().orElseThrow();

        assertEquals(3, stats.battlesParticipated(), "Debe haber participado en 3 batallas");
        assertEquals(18300.0, stats.totalDamage(), 0.001, "El daño total debe ser 18300");
        assertEquals(7200.0, stats.highestDamage(), 0.001, "El daño máximo debe ser 7200");
        assertEquals(2, stats.slayerCount(), "Debe tener 2 victorias como Slayer");
        assertEquals(t1.toEpochMilli(), stats.firstParticipationAt().toEpochMilli(), "first_participation debe ser t1");
        assertEquals(t3.toEpochMilli(), stats.lastParticipationAt().toEpochMilli(), "last_participation debe ser t3");
        assertEquals(6100.0, stats.getAverageDamage(), 0.001, "El daño promedio debe ser 6100");
    }

    // =========================================================================
    // F. SLAYER LOGIC (Requisitos 7, 18, 42-F)
    // =========================================================================

    @Test
    @DisplayName("F1: Slayer incrementa solo para el jugador con wasSlayer=true")
    void testSlayerCountIncrement() {
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();
        UUID slayer = UUID.randomUUID();
        UUID helper = UUID.randomUUID();

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, now, "world", slayer);
        LeaderboardParticipantRecord p1 = new LeaderboardParticipantRecord(battleId, slayer, "Slayer", "Slayer", 1000.0, 1L, true, now);
        LeaderboardParticipantRecord p2 = new LeaderboardParticipantRecord(battleId, helper, "Helper", "Helper", 500.0, 2L, false, now);

        leaderboardStorage.recordBattle(battle, List.of(p1, p2)).join();

        LeaderboardPlayerStats slayerStats = leaderboardStorage.getPlayerStats(slayer).join().orElseThrow();
        assertEquals(1, slayerStats.slayerCount());

        LeaderboardPlayerStats helperStats = leaderboardStorage.getPlayerStats(helper).join().orElseThrow();
        assertEquals(0, helperStats.slayerCount());
    }

    // =========================================================================
    // G. IDEMPOTENCY TESTS (Requisitos 22, 42-G, 44)
    // =========================================================================

    @Test
    @DisplayName("G1 & Req 44: Procesar la misma batalla 3 veces no duplica batallas ni estadísticas")
    void testStrictIdempotencyMultipleExecutions() {
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();
        UUID player = UUID.randomUUID();

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, now, "world_the_end", player);
        LeaderboardParticipantRecord part = new LeaderboardParticipantRecord(
                battleId, player, "Mauricio", "Mauricio", 5000.0, 1L, true, now);

        // Primera ejecución
        boolean firstRun = leaderboardStorage.recordBattle(battle, List.of(part)).join();
        assertTrue(firstRun, "La primera ejecución debe devolver true");

        // Segunda ejecución (idéntica)
        boolean secondRun = leaderboardStorage.recordBattle(battle, List.of(part)).join();
        assertFalse(secondRun, "La segunda ejecución debe devolver false (idempotente)");

        // Tercera ejecución (idéntica)
        boolean thirdRun = leaderboardStorage.recordBattle(battle, List.of(part)).join();
        assertFalse(thirdRun, "La tercera ejecución debe devolver false (idempotente)");

        // Verificar invariantes de idempotencia
        assertEquals(1, leaderboardStorage.getBattleCount().join(), "Solo debe existir 1 batalla");
        assertEquals(1, leaderboardStorage.getPlayerCount().join(), "Solo debe existir 1 jugador");

        List<LeaderboardParticipantRecord> participants = leaderboardStorage.getBattleParticipants(battleId).join();
        assertEquals(1, participants.size(), "Solo debe existir 1 fila de participación");

        LeaderboardPlayerStats stats = leaderboardStorage.getPlayerStats(player).join().orElseThrow();
        assertEquals(1, stats.battlesParticipated(), "battles_participated debe ser exactamente 1");
        assertEquals(5000.0, stats.totalDamage(), 0.001, "total_damage debe ser exactamente 5000.0");
        assertEquals(5000.0, stats.highestDamage(), 0.001, "highest_damage debe ser exactamente 5000.0");
        assertEquals(1, stats.slayerCount(), "slayer_count debe ser exactamente 1");
    }

    // =========================================================================
    // H & I. ISOLATION TESTS (Requisitos 42-H, 42-I)
    // =========================================================================

    @Test
    @DisplayName("H1: Aislamiento estricto entre jugadores distintos")
    void testPlayerIsolation() {
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, now, "world", playerA);
        LeaderboardParticipantRecord pA = new LeaderboardParticipantRecord(battleId, playerA, "A", "A", 3000.0, 1L, true, now);
        LeaderboardParticipantRecord pB = new LeaderboardParticipantRecord(battleId, playerB, "B", "B", 1500.0, 2L, false, now);

        leaderboardStorage.recordBattle(battle, List.of(pA, pB)).join();

        LeaderboardPlayerStats statsA = leaderboardStorage.getPlayerStats(playerA).join().orElseThrow();
        LeaderboardPlayerStats statsB = leaderboardStorage.getPlayerStats(playerB).join().orElseThrow();

        assertEquals(3000.0, statsA.totalDamage());
        assertEquals(1500.0, statsB.totalDamage());
        assertEquals(1, statsA.slayerCount());
        assertEquals(0, statsB.slayerCount());
    }

    @Test
    @DisplayName("I1: Aislamiento estricto entre batallas distintas")
    void testBattleIsolation() {
        BattleId b1 = BattleId.random();
        BattleId b2 = BattleId.random();
        Instant now = Instant.now();
        UUID player = UUID.randomUUID();

        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b1, now, "world1", player),
                List.of(new LeaderboardParticipantRecord(b1, player, "P", "P", 100.0, 1L, true, now))
        ).join();

        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b2, now, "world2", null),
                List.of(new LeaderboardParticipantRecord(b2, player, "P", "P", 200.0, 1L, false, now))
        ).join();

        assertEquals(1, leaderboardStorage.getBattleParticipants(b1).join().size());
        assertEquals(1, leaderboardStorage.getBattleParticipants(b2).join().size());
        assertEquals("world1", leaderboardStorage.getBattle(b1).join().orElseThrow().worldName());
        assertEquals("world2", leaderboardStorage.getBattle(b2).join().orElseThrow().worldName());
    }

    // =========================================================================
    // J. PLAYER RENAME (Requisitos 6, 15, 42-J)
    // =========================================================================

    @Test
    @DisplayName("J1 & Req 15: Cambio de nombre actualiza lastKnownName pero preserva historicalName en batallas previas")
    void testPlayerRenamePreservesHistoricalName() {
        UUID player = UUID.randomUUID();
        BattleId b1 = BattleId.random();
        Instant t1 = Instant.ofEpochMilli(1000L);

        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b1, t1, "world", player),
                List.of(new LeaderboardParticipantRecord(b1, player, "Mauricio", "Mauricio", 1000.0, 1L, true, t1))
        ).join();

        BattleId b2 = BattleId.random();
        Instant t2 = Instant.ofEpochMilli(2000L);

        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b2, t2, "world", player),
                List.of(new LeaderboardParticipantRecord(b2, player, "Mauricio123", "Mauricio123", 2000.0, 1L, true, t2))
        ).join();

        // 1. En historial de participaciones:
        List<LeaderboardParticipantRecord> parts = leaderboardStorage.getPlayerParticipations(player).join();
        assertEquals(2, parts.size());
        assertEquals("Mauricio", parts.get(0).historicalName(), "Batalla 1 debe preservar historicalName Mauricio");
        assertEquals("Mauricio123", parts.get(1).historicalName(), "Batalla 2 debe preservar historicalName Mauricio123");

        // 2. En estadísticas acumuladas globales:
        LeaderboardPlayerStats stats = leaderboardStorage.getPlayerStats(player).join().orElseThrow();
        assertEquals("Mauricio123", stats.lastKnownName(), "lastKnownName debe haberse actualizado al más reciente");
    }

    // =========================================================================
    // K. RESTART RECOVERY (Requisitos 37, 42-K)
    // =========================================================================

    @Test
    @DisplayName("K1: Persistencia sobrevive al cierre y reapertura de la conexión SQLite")
    void testRestartRecoverySurvivesProcessRestart() throws Exception {
        UUID player = UUID.randomUUID();
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();

        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(battleId, now, "world_the_end", player),
                List.of(new LeaderboardParticipantRecord(battleId, player, "Survivor", "Survivor", 8800.0, 1L, true, now))
        ).join();

        // Cerrar completamente
        leaderboardService.close();
        databaseManager.close();

        // Reabrir nuevo DatabaseManager y Storage sobre el mismo archivo físico
        DatabaseManager reopenedDb = new DatabaseManager(dbPath, logger);
        reopenedDb.initializeSync();
        SQLiteLeaderboardStorage reopenedStorage = new SQLiteLeaderboardStorage(reopenedDb, logger);

        try {
            LeaderboardPlayerStats stats = reopenedStorage.getPlayerStats(player).join().orElseThrow();
            assertEquals("Survivor", stats.lastKnownName());
            assertEquals(8800.0, stats.totalDamage());
            assertEquals(1, stats.slayerCount());
            assertEquals(1, reopenedStorage.getBattleCount().join());
        } finally {
            reopenedStorage.close();
        }
    }

    // =========================================================================
    // L. RANKINGS & DETERMINISTIC QUERIES (Requisitos 31-35, 42-L)
    // =========================================================================

    @Test
    @DisplayName("L1: Rankings con desempate determinista por UUID ASC y límites")
    void testRankingsAndDeterministicTieBreak() {
        // Crear 3 jugadores con empates forzados para validar orden determinista
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        BattleId b = BattleId.random();
        Instant now = Instant.now();

        // u1 y u2 tienen el mismo daño total (5000.0); u3 tiene más daño (8000.0)
        leaderboardStorage.recordBattle(
                new LeaderboardBattleRecord(b, now, "world", u3),
                List.of(
                        new LeaderboardParticipantRecord(b, u1, "Player1", "Player1", 5000.0, 2L, false, now),
                        new LeaderboardParticipantRecord(b, u2, "Player2", "Player2", 5000.0, 3L, false, now),
                        new LeaderboardParticipantRecord(b, u3, "Player3", "Player3", 8000.0, 1L, true, now)
                )
        ).join();

        // 1. Top Damage: debe ser u3 (8000), seguido por u1 (5000), luego u2 (5000) por u1 < u2
        List<LeaderboardPlayerStats> topDamage = leaderboardStorage.getTopDamage(2).join();
        assertEquals(2, topDamage.size(), "Límite debe aplicarse");
        assertEquals(u3, topDamage.get(0).playerUuid());
        assertEquals(u1, topDamage.get(1).playerUuid(), "Ante empate de daño, desempata player_uuid ASC");

        // 2. Top Slayers: u3 tiene 1, u1 y u2 tienen 0
        List<LeaderboardPlayerStats> topSlayers = leaderboardStorage.getTopSlayers(10).join();
        assertEquals(3, topSlayers.size());
        assertEquals(u3, topSlayers.get(0).playerUuid());
        assertEquals(u1, topSlayers.get(1).playerUuid(), "Ante empate en 0 slayers, u1 va antes que u2 por UUID ASC");
        assertEquals(u2, topSlayers.get(2).playerUuid());

        // 3. Top Participations: todos tienen 1, deben ordenarse u1, u2, u3 por UUID ASC
        List<LeaderboardPlayerStats> topPart = leaderboardStorage.getTopParticipations(10).join();
        assertEquals(u1, topPart.get(0).playerUuid());
        assertEquals(u2, topPart.get(1).playerUuid());
        assertEquals(u3, topPart.get(2).playerUuid());

        // 4. Validar rechazo de límite inválido
        assertThrows(Exception.class, () -> leaderboardStorage.getTopDamage(0).join());
        assertThrows(Exception.class, () -> leaderboardStorage.getTopDamage(-5).join());
    }

    @Test
    @DisplayName("L2: Consultas sobre base vacía devuelven empty o listas vacías")
    void testEmptyLeaderboardQueries() {
        assertTrue(leaderboardStorage.getPlayerStats(UUID.randomUUID()).join().isEmpty());
        assertTrue(leaderboardStorage.getTopDamage(10).join().isEmpty());
        assertTrue(leaderboardStorage.getTopSlayers(10).join().isEmpty());
        assertTrue(leaderboardStorage.getTopParticipations(10).join().isEmpty());
        assertEquals(0, leaderboardStorage.getBattleCount().join());
        assertEquals(0, leaderboardStorage.getPlayerCount().join());
    }

    // =========================================================================
    // M. CONCURRENCY TESTS (Requisitos 26, 42-M)
    // =========================================================================

    @Test
    @DisplayName("M1: Intentos concurrentes de registrar la misma batalla no causan colisiones ni duplican datos")
    void testConcurrentBattleRecording() throws Exception {
        BattleId battleId = BattleId.random();
        Instant now = Instant.now();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        LeaderboardBattleRecord battle = new LeaderboardBattleRecord(battleId, now, "world", player1);
        List<LeaderboardParticipantRecord> participants = List.of(
                new LeaderboardParticipantRecord(battleId, player1, "P1", "P1", 3000.0, 1L, true, now),
                new LeaderboardParticipantRecord(battleId, player2, "P2", "P2", 2000.0, 2L, false, now)
        );

        int threads = 8;
        ExecutorService threadPool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    return leaderboardStorage.recordBattle(battle, participants).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, threadPool);
            futures.add(f);
        }

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        threadPool.shutdown();
        assertTrue(threadPool.awaitTermination(5, TimeUnit.SECONDS));

        // Exactamente una tarea debió retornar true, las demás false
        long successCount = futures.stream().filter(f -> Boolean.TRUE.equals(f.join())).count();
        assertEquals(1, successCount, "Exactamente una tarea debió insertar la batalla");

        // Verificar datos en base de datos
        assertEquals(1, leaderboardStorage.getBattleCount().join());
        LeaderboardPlayerStats stats1 = leaderboardStorage.getPlayerStats(player1).join().orElseThrow();
        assertEquals(1, stats1.battlesParticipated());
        assertEquals(3000.0, stats1.totalDamage());
    }

    // =========================================================================
    // N. INTEGRATION WITH BATTLE RESULT (Requisitos 13, 14)
    // =========================================================================

    @Test
    @DisplayName("N1: LeaderboardService consume BattleResult y registra victoria correctamente")
    void testLeaderboardServiceConsumesBattleResult() {
        BattleId battleId = BattleId.random();
        Instant start = Instant.now().minusSeconds(120);
        Instant end = Instant.now();
        UUID slayerUuid = UUID.randomUUID();

        ParticipantSnapshot p1 = new ParticipantSnapshot(slayerUuid, "Hero", "Hero_V2", 9000.0, 1L, 10L, 500L);
        CombatSnapshot combatSnapshot = new CombatSnapshot(battleId, List.of(p1), 10L, 9000.0);

        BattleResult result = BattleResult.completed(battleId, start, end, slayerUuid, "Hero_V2", combatSnapshot);

        boolean recorded = leaderboardService.recordVictory(result, "world_the_end").join();
        assertTrue(recorded);

        LeaderboardPlayerStats stats = leaderboardService.getPlayerStats(slayerUuid).join().orElseThrow();
        assertEquals("Hero_V2", stats.lastKnownName());
        assertEquals(9000.0, stats.totalDamage());
        assertEquals(1, stats.slayerCount());
        assertEquals(1, stats.battlesParticipated());

        // Comprobar que una batalla ABORTED es rechazada por el servicio
        BattleResult aborted = BattleResult.aborted(BattleId.random(), start, end, "Dragon missing");
        boolean abortedRecorded = leaderboardService.recordVictory(aborted, "world_the_end").join();
        assertFalse(abortedRecorded, "Batallas abortadas no deben registrarse en leaderboard");
    }
}
