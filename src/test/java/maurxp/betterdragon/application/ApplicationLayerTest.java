package maurxp.betterdragon.application;

import maurxp.betterdragon.application.admin.AdminApplicationService;
import maurxp.betterdragon.application.admin.model.ReloadResult;
import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.arena.model.ArenaDetailView;
import maurxp.betterdragon.application.arena.model.ArenaSummaryView;
import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.battle.model.BattleOperationResult;
import maurxp.betterdragon.application.battle.model.BattleStatusView;
import maurxp.betterdragon.application.leaderboard.LeaderboardApplicationService;
import maurxp.betterdragon.application.reward.RewardApplicationService;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.config.ArenaConfigurationSnapshot;
import maurxp.betterdragon.config.BetterDragonConfig;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;
import maurxp.betterdragon.leaderboard.service.LeaderboardService;
import maurxp.betterdragon.leaderboard.storage.LeaderboardStorage;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.claim.InMemoryClaimStorage;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.service.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias de la Application Layer (Fase 3.11)")
class ApplicationLayerTest {

    private Logger logger;
    private ConfigurationService configService;
    private BattleSessionManager sessionManager;
    private BattleManager battleManager;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("ApplicationLayerTest");
        configService = new ConfigurationService(logger, BetterDragonConfig.defaults(), ArenaConfigurationSnapshot.defaults());
        sessionManager = new BattleSessionManager();
        DragonSpawner spawner = new DragonSpawner();
        battleManager = new BattleManager(sessionManager, configService, spawner, logger);
    }

    @Test
    @DisplayName("A1: ArenaQueryService lista correctamente las arenas y sus detalles sin mutar estado")
    void testArenaQueryService() {
        ArenaQueryService arenaService = new ArenaQueryService(configService);

        List<ArenaSummaryView> arenas = arenaService.listArenas();
        assertFalse(arenas.isEmpty(), "Debe existir al menos la arena predeterminada");

        ArenaSummaryView defaultSummary = arenas.getFirst();
        assertEquals("default", defaultSummary.id());
        assertTrue(defaultSummary.isDefault());
        assertEquals("world_the_end", defaultSummary.worldName());
        assertNotNull(defaultSummary.boundsDescription());

        // Detalle de arena default
        Optional<ArenaDetailView> detailOpt = arenaService.getArenaDetail("default");
        assertTrue(detailOpt.isPresent());
        ArenaDetailView detail = detailOpt.get();
        assertEquals("default", detail.id());
        assertEquals("world_the_end", detail.worldName());
        assertNotNull(detail.center());
        assertNotNull(detail.podium());
        assertFalse(detail.activeRules().isEmpty(), "Debe incluir las reglas activas");

        // Arena inexistente retorna Optional.empty()
        Optional<ArenaDetailView> nonExistent = arenaService.getArenaDetail("no_existe");
        assertTrue(nonExistent.isEmpty());
    }

    @Test
    @DisplayName("A2: BattleAdminService valida precondiciones seguras para start y abort")
    void testBattleAdminServiceValidations() {
        BattleAdminService battleService = new BattleAdminService(battleManager, sessionManager, configService, logger);

        // Start con mundo nulo
        BattleOperationResult nullWorldResult = battleService.startBattle(null, "default", "default");
        assertFalse(nullWorldResult.success());
        assertTrue(nullWorldResult.message().contains("no existe o no está cargado"));

        // Abort con mundo nulo
        BattleOperationResult nullWorldAbort = battleService.abortBattle((org.bukkit.World) null, "test");
        assertFalse(nullWorldAbort.success());

        // Abort con BattleId no existente
        BattleId randomId = BattleId.random();
        BattleOperationResult missingAbort = battleService.abortBattle(randomId, "test");
        assertFalse(missingAbort.success());
        assertTrue(missingAbort.message().contains("No se encontró ninguna sesión"));

        // Status de mundo sin batalla activa
        Optional<BattleStatusView> status = battleService.getStatus("world_the_end");
        assertTrue(status.isEmpty());

        // Listado de todos los status cuando no hay batallas
        List<BattleStatusView> allStatuses = battleService.getAllStatuses();
        assertTrue(allStatuses.isEmpty());
    }

    @Test
    @DisplayName("A3: BattleStatusView calcula correctamente el porcentaje de salud y propiedades inmutables")
    void testBattleStatusViewCalculations() {
        BattleId battleId = BattleId.random();
        UUID dragonUuid = UUID.randomUUID();

        BattleStatusView view = new BattleStatusView(
                battleId,
                "world_the_end",
                BattleState.ACTIVE,
                "default",
                "default",
                "phase_1",
                Optional.of(dragonUuid),
                150.0,
                200.0,
                Duration.ofSeconds(90),
                4,
                Optional.of("HeroPlayer"),
                5500.0
        );

        assertEquals(0.75, view.getHealthPercentage(), 0.001);
        assertEquals(150.0, view.currentHealth());
        assertEquals(200.0, view.maxHealth());
        assertEquals("phase_1", view.activePhaseId());
        assertEquals(4, view.participantCount());
        assertTrue(view.topDamagerName().isPresent());
        assertEquals("HeroPlayer", view.topDamagerName().get());
    }

    @Test
    @DisplayName("A4: AdminApplicationService coordina la recarga de configuración y reporta tiempo transcurrido")
    void testAdminApplicationServiceReload(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        Path arenasPath = tempDir.resolve("arenas.yml");

        Files.writeString(configPath, "portal:\n  enabled: false\nlogging:\n  level: INFO\n  debug: false\n");
        Files.writeString(arenasPath, "arenas:\n  default:\n    world: world_the_end\n    center:\n      x: 0.0\n      y: 100.0\n      z: 0.0\n    podium:\n      x: 0.0\n      y: 65.0\n      z: 0.0\n    bounds:\n      min:\n        x: -150.0\n        y: 0.0\n        z: -150.0\n      max:\n        x: 150.0\n        y: 256.0\n        z: 150.0\n    rules:\n      water_allowed: false\n      boundary:\n        enabled: true\n      anti_tunnel:\n        enabled: true\n");

        AdminApplicationService adminService = new AdminApplicationService(configService, tempDir::toFile);
        ReloadResult result = adminService.reloadConfiguration();

        assertTrue(result.success());
        assertTrue(result.message().contains("exitosamente"));
        assertTrue(result.elapsedMillis() >= 0);
    }

    @Test
    @DisplayName("A5: RewardApplicationService maneja argumentos nulos y delega consultas de forma no bloqueante")
    void testRewardApplicationServiceNullSafety() {
        ClaimStorage claimStorage = new InMemoryClaimStorage();
        PlayerInventoryAdapter inventoryAdapter = new PlayerInventoryAdapter() {
            @Override public int deliverItem(UUID playerId, RewardItem item) { return item != null ? item.amount() : 0; }
            @Override public boolean isPlayerOnline(UUID playerId) { return true; }
        };
        RewardDeliveryService deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, Runnable::run, logger);
        RewardService rewardService = new RewardService(
                sessionManager,
                configService,
                new RewardAllocationEngine(),
                deliveryService,
                claimStorage,
                event -> {},
                logger
        );

        RewardApplicationService appReward = new RewardApplicationService(rewardService, claimStorage);

        CompletableFuture<List<RewardClaim>> pendingFuture = appReward.getPendingClaims(null);
        List<RewardClaim> pending = pendingFuture.join();
        assertTrue(pending.isEmpty(), "Null playerId debe retornar lista vacía");

        CompletableFuture<Integer> claimFuture = appReward.claimPendingRewards(null);
        int claimed = claimFuture.join();
        assertEquals(0, claimed, "Null playerId debe retornar 0 ítems entregados");
    }

    @Test
    @DisplayName("A6: LeaderboardApplicationService aplica límites seguros y resuelve consultas")
    void testLeaderboardApplicationServiceLimits() {
        // Storage mock simulado para pruebas de la capa de aplicación
        LeaderboardStorage mockStorage = new LeaderboardStorage() {
            @Override public CompletableFuture<Boolean> recordBattle(maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord b, List<maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord> p) { return CompletableFuture.completedFuture(true); }
            @Override public CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStats(UUID id) { return CompletableFuture.completedFuture(Optional.empty()); }
            @Override public CompletableFuture<List<LeaderboardPlayerStats>> getTopDamage(int limit) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<List<LeaderboardPlayerStats>> getTopSlayers(int limit) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<List<LeaderboardPlayerStats>> getTopParticipations(int limit) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<Optional<maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord>> getBattle(BattleId id) { return CompletableFuture.completedFuture(Optional.empty()); }
            @Override public CompletableFuture<List<maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord>> getBattleParticipants(BattleId id) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<List<maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord>> getPlayerParticipations(UUID id) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<Integer> getBattleCount() { return CompletableFuture.completedFuture(5); }
            @Override public CompletableFuture<Integer> getPlayerCount() { return CompletableFuture.completedFuture(12); }
            @Override public CompletableFuture<Void> clear() { return CompletableFuture.completedFuture(null); }
            @Override public void close() {}
        };

        LeaderboardService service = new LeaderboardService(mockStorage, logger);
        LeaderboardApplicationService appService = new LeaderboardApplicationService(service);

        // Clamping de límites
        assertEquals(0, appService.getTopDamage(-5).join().size());
        assertEquals(0, appService.getTopDamage(200).join().size());
        assertEquals(0, appService.getTopSlayers(10).join().size());
        assertEquals(0, appService.getTopParticipations(10).join().size());

        // Conteo total
        assertEquals(5, appService.getTotalBattles().join());
        assertEquals(12, appService.getTotalPlayers().join());

        // Consultas con UUID nulo o query en blanco
        assertTrue(appService.getPlayerStats(null).join().isEmpty());
        assertTrue(appService.getPlayerStatsByQuery(null).join().isEmpty());
        assertTrue(appService.getPlayerStatsByQuery("   ").join().isEmpty());
    }
}
