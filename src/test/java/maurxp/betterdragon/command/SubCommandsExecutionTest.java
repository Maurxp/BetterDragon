package maurxp.betterdragon.command;

import maurxp.betterdragon.application.admin.AdminApplicationService;
import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.leaderboard.LeaderboardApplicationService;
import maurxp.betterdragon.application.permission.BukkitPermissionChecker;
import maurxp.betterdragon.application.reward.RewardApplicationService;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.command.subcommand.AbortSubCommand;
import maurxp.betterdragon.command.subcommand.ArenaSubCommand;
import maurxp.betterdragon.command.subcommand.ClaimSubCommand;
import maurxp.betterdragon.command.subcommand.HelpSubCommand;
import maurxp.betterdragon.command.subcommand.LeaderboardSubCommand;
import maurxp.betterdragon.command.subcommand.ReloadSubCommand;
import maurxp.betterdragon.command.subcommand.StartSubCommand;
import maurxp.betterdragon.command.subcommand.StatsSubCommand;
import maurxp.betterdragon.command.subcommand.StatusSubCommand;
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
import maurxp.betterdragon.reward.model.RewardItem;
import maurxp.betterdragon.reward.service.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias de Ejecución de Subcomandos (Fase 3.11)")
class SubCommandsExecutionTest {

    private Logger logger;
    private BukkitPermissionChecker permissionChecker;
    private CommandRegistry registry;

    private ConfigurationService configService;
    private BattleSessionManager sessionManager;
    private BattleManager battleManager;

    private LeaderboardApplicationService leaderboardAppService;
    private BattleAdminService battleAdminService;
    private ArenaQueryService arenaQueryService;
    private AdminApplicationService adminAppService;
    private RewardApplicationService rewardAppService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        logger = Logger.getLogger("SubCommandsExecutionTest");
        permissionChecker = new BukkitPermissionChecker();
        registry = new CommandRegistry(permissionChecker, logger);

        Path configPath = tempDir.resolve("config.yml");
        Path arenasPath = tempDir.resolve("arenas.yml");
        Files.writeString(configPath, "portal:\n  enabled: false\nlogging:\n  level: INFO\n  debug: false\n");
        Files.writeString(arenasPath, "arenas:\n  default:\n    world: world_the_end\n    center:\n      x: 0.0\n      y: 100.0\n      z: 0.0\n    podium:\n      x: 0.0\n      y: 65.0\n      z: 0.0\n    bounds:\n      min:\n        x: -150.0\n        y: 0.0\n        z: -150.0\n      max:\n        x: 150.0\n        y: 256.0\n        z: 150.0\n    rules:\n      water_allowed: false\n      boundary:\n        enabled: true\n      anti_tunnel:\n        enabled: true\n");

        configService = new ConfigurationService(logger, BetterDragonConfig.defaults(), ArenaConfigurationSnapshot.defaults());
        sessionManager = new BattleSessionManager();
        DragonSpawner spawner = new DragonSpawner();
        battleManager = new BattleManager(sessionManager, configService, spawner, logger);

        // Leaderboard mock storage con datos para pruebas
        UUID player1Uuid = UUID.randomUUID();
        LeaderboardPlayerStats player1Stats = new LeaderboardPlayerStats(
                player1Uuid,
                "DragonSlayer99",
                5,
                12500.0,
                3500.0,
                3,
                Instant.now().minusSeconds(7200),
                Instant.now()
        );

        LeaderboardStorage mockStorage = new LeaderboardStorage() {
            @Override public CompletableFuture<Boolean> recordBattle(maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord b, List<maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord> p) { return CompletableFuture.completedFuture(true); }
            @Override public CompletableFuture<Optional<LeaderboardPlayerStats>> getPlayerStats(UUID id) {
                if (player1Uuid.equals(id)) return CompletableFuture.completedFuture(Optional.of(player1Stats));
                return CompletableFuture.completedFuture(Optional.empty());
            }
            @Override public CompletableFuture<List<LeaderboardPlayerStats>> getTopDamage(int limit) { return CompletableFuture.completedFuture(List.of(player1Stats)); }
            @Override public CompletableFuture<List<LeaderboardPlayerStats>> getTopSlayers(int limit) { return CompletableFuture.completedFuture(List.of(player1Stats)); }
            @Override public CompletableFuture<List<LeaderboardPlayerStats>> getTopParticipations(int limit) { return CompletableFuture.completedFuture(List.of(player1Stats)); }
            @Override public CompletableFuture<Optional<maurxp.betterdragon.leaderboard.model.LeaderboardBattleRecord>> getBattle(BattleId id) { return CompletableFuture.completedFuture(Optional.empty()); }
            @Override public CompletableFuture<List<maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord>> getBattleParticipants(BattleId id) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<List<maurxp.betterdragon.leaderboard.model.LeaderboardParticipantRecord>> getPlayerParticipations(UUID id) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<Integer> getBattleCount() { return CompletableFuture.completedFuture(1); }
            @Override public CompletableFuture<Integer> getPlayerCount() { return CompletableFuture.completedFuture(1); }
            @Override public CompletableFuture<Void> clear() { return CompletableFuture.completedFuture(null); }
            @Override public void close() {}
        };

        LeaderboardService leaderboardService = new LeaderboardService(mockStorage, logger);
        leaderboardAppService = new LeaderboardApplicationService(leaderboardService);

        battleAdminService = new BattleAdminService(battleManager, sessionManager, configService, logger);
        arenaQueryService = new ArenaQueryService(configService);
        adminAppService = new AdminApplicationService(configService, tempDir::toFile);

        ClaimStorage claimStorage = new InMemoryClaimStorage();
        PlayerInventoryAdapter inventoryAdapter = new PlayerInventoryAdapter() {
            @Override public int deliverItem(UUID playerId, RewardItem item) { return item != null ? item.amount() : 0; }
            @Override public boolean isPlayerOnline(UUID playerId) { return true; }
        };
        RewardDeliveryService deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, Runnable::run, logger);
        RewardService rewardService = new RewardService(sessionManager, configService, new RewardAllocationEngine(), deliveryService, claimStorage, e -> {}, logger);
        rewardAppService = new RewardApplicationService(rewardService, claimStorage);

        // Registrar todos los subcomandos
        registry.register(new HelpSubCommand(registry));
        registry.register(new LeaderboardSubCommand(leaderboardAppService));
        registry.register(new StatsSubCommand(leaderboardAppService, permissionChecker));
        registry.register(new StatusSubCommand(battleAdminService));
        registry.register(new StartSubCommand(battleAdminService, arenaQueryService));
        registry.register(new AbortSubCommand(battleAdminService));
        registry.register(new ReloadSubCommand(adminAppService));
        registry.register(new ArenaSubCommand(arenaQueryService));
        registry.register(new ClaimSubCommand(rewardAppService));
    }

    @Test
    @DisplayName("S1: /bd help muestra listado de comandos y detalles específicos")
    void testHelpSubCommandExecution() {
        TestCommandSender sender = new TestCommandSender("Admin", true);

        // Listado general
        registry.dispatch(sender, "bd", new String[]{"help"});
        assertFalse(sender.getMessages().isEmpty());
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Comandos Disponibles")));
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("/bd leaderboard")));

        // Ayuda de subcomando específico
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"help", "leaderboard"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Ayuda: /bd leaderboard")));
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Permiso:") && m.contains("betterdragon.leaderboard")));

        // Subcomando inexistente
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"help", "falso"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("No se encontró información")));
    }

    @Test
    @DisplayName("S2: /bd leaderboard ejecuta consultas de ranking para daño, slayers y participaciones")
    void testLeaderboardSubCommandExecution() {
        TestCommandSender sender = new TestCommandSender("Player", true);

        // Top Damage
        registry.dispatch(sender, "bd", new String[]{"leaderboard", "damage", "5"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Top Daño")));
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("DragonSlayer99")));

        // Top Slayers
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"top", "slayers", "5"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Top Slayers")));

        // Top Battles
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"lb", "battles", "5"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Top Participaciones")));

        // Categoría desconocida
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"leaderboard", "invalido"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Categoría desconocida")));
    }

    @Test
    @DisplayName("S3: /bd arena list y /bd arena info <id> muestran información estructurada")
    void testArenaSubCommandExecution() {
        TestCommandSender sender = new TestCommandSender("Admin", true);

        // List
        registry.dispatch(sender, "bd", new String[]{"arena", "list"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Arenas Configuradas")));
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("default")));

        // Info
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"arena", "info", "default"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Detalle de Arena: default")));
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Mundo:") && m.contains("world_the_end")));

        // Info no encontrada
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"arena", "info", "inexistente"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("No existe ninguna arena")));
    }

    @Test
    @DisplayName("S4: /bd reload recarga atómicamente y notifica resultado al emisor")
    void testReloadSubCommandExecution() {
        TestCommandSender sender = new TestCommandSender("ConsoleAdmin", true);

        registry.dispatch(sender, "bd", new String[]{"reload"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Recargando configuración")));
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("exitosamente")));
    }

    @Test
    @DisplayName("S5: /bd status reporta estado cuando no hay batallas activas")
    void testStatusSubCommandExecution() {
        TestCommandSender sender = new TestCommandSender("Admin", true);

        registry.dispatch(sender, "bd", new String[]{"status"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("No hay ninguna sesión de batalla activa")));
    }

    @Test
    @DisplayName("S6: /bd start y /bd abort validan emisores desde consola")
    void testStartAndAbortSubCommandsConsoleValidation() {
        TestCommandSender sender = new TestCommandSender("Console", true);

        // Start sin mundo desde consola
        registry.dispatch(sender, "bd", new String[]{"start"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Debes especificar el mundo")));

        // Abort sin mundo desde consola
        sender.clearMessages();
        registry.dispatch(sender, "bd", new String[]{"abort"});
        assertTrue(sender.getMessages().stream().anyMatch(m -> m.contains("Debes especificar el mundo o ID")));
    }
}
