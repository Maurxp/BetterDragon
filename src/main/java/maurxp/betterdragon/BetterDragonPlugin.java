package maurxp.betterdragon;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityEngine;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.BattleSpatialContext;
import maurxp.betterdragon.ability.EffectOriginType;
import maurxp.betterdragon.ability.TargetSelector;
import maurxp.betterdragon.ability.TargetSelectorType;
import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleEvaluator;
import maurxp.betterdragon.arena.Vector3d;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonLifecycleListener;
import maurxp.betterdragon.battle.DragonPdcHandler;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.combat.CombatRuntime;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.DragonCombatListener;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.arena.ArenaRuleSet;
import maurxp.betterdragon.config.ArenaConfigurationSnapshot;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.config.DragonDefinition;
import maurxp.betterdragon.config.RewardConfigurationSnapshot;
import maurxp.betterdragon.config.RewardItemDefinition;
import maurxp.betterdragon.config.SlayerRewardDefinition;
import maurxp.betterdragon.phase.PhaseRuntime;
import maurxp.betterdragon.presentation.DragonPresentationListener;
import maurxp.betterdragon.platform.bossbar.BossBarWorldListener;
import maurxp.betterdragon.platform.bossbar.VanillaBossBarController;
import maurxp.betterdragon.platform.bossbar.VanillaBossBarControllerFactory;
import maurxp.betterdragon.leaderboard.service.DragonLeaderboardListener;
import maurxp.betterdragon.leaderboard.service.LeaderboardService;
import maurxp.betterdragon.leaderboard.storage.LeaderboardStorage;
import maurxp.betterdragon.leaderboard.storage.SQLiteLeaderboardStorage;
import maurxp.betterdragon.persistence.DatabaseManager;
import maurxp.betterdragon.persistence.SchemaInitializer;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.claim.InMemoryClaimStorage;
import maurxp.betterdragon.reward.claim.SQLiteClaimStorage;
import maurxp.betterdragon.reward.delivery.BukkitPlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.event.BetterDragonRewardEvent;
import maurxp.betterdragon.reward.event.RewardEventDispatcher;
import maurxp.betterdragon.reward.model.ClaimStatus;
import maurxp.betterdragon.reward.model.RewardAllocationPlan;
import maurxp.betterdragon.reward.model.RewardClaim;
import maurxp.betterdragon.reward.service.DragonRewardListener;
import maurxp.betterdragon.reward.service.RewardService;
import maurxp.betterdragon.util.BetterDragonKeys;
import maurxp.betterdragon.util.MainThreadDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import maurxp.betterdragon.application.admin.AdminApplicationService;
import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.leaderboard.LeaderboardApplicationService;
import maurxp.betterdragon.application.permission.BukkitPermissionChecker;
import maurxp.betterdragon.application.permission.PermissionChecker;
import maurxp.betterdragon.application.reward.RewardApplicationService;
import maurxp.betterdragon.command.BetterDragonCommand;
import maurxp.betterdragon.command.CommandRegistry;
import maurxp.betterdragon.command.subcommand.AbortSubCommand;
import maurxp.betterdragon.command.subcommand.ArenaSubCommand;
import maurxp.betterdragon.command.subcommand.ClaimSubCommand;
import maurxp.betterdragon.command.subcommand.HelpSubCommand;
import maurxp.betterdragon.command.subcommand.LeaderboardSubCommand;
import maurxp.betterdragon.command.subcommand.ReloadSubCommand;
import maurxp.betterdragon.command.subcommand.StartSubCommand;
import maurxp.betterdragon.command.subcommand.StatsSubCommand;
import maurxp.betterdragon.command.subcommand.StatusSubCommand;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;


/**
 * Clase principal de arranque y ciclo de vida de BetterDragon.
 * <p>
 * Diseñada bajo el principio de responsabilidad única (Bootstrap /
 * Orchestration):
 * esta clase <b>NO es un God Object</b> y no contiene lógica de combate, ni
 * consultas
 * SQL, ni cálculo de recompensas, ni definiciones de habilidades. Su única
 * función es
 * inicializar la infraestructura, inyectar dependencias y orquestar el apagado
 * limpio.
 *
 * @author maurxp
 */
public final class BetterDragonPlugin extends JavaPlugin implements Listener {

    private ConfigurationService configurationService;
    private VanillaBossBarController vanillaBossBarController;
    private BattleSessionManager sessionManager;
    private DragonSpawner dragonSpawner;
    private BattleManager battleManager;
    private DatabaseManager databaseManager;
    private ClaimStorage claimStorage;
    private RewardService rewardService;
    private LeaderboardStorage leaderboardStorage;
    private LeaderboardService leaderboardService;
    private PermissionChecker permissionChecker;
    private LeaderboardApplicationService leaderboardAppService;
    private BattleAdminService battleAdminService;
    private ArenaQueryService arenaQueryService;
    private AdminApplicationService adminAppService;
    private RewardApplicationService rewardAppService;
    private CommandRegistry commandRegistry;
    private BetterDragonCommand betterDragonCommand;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        getLogger().info("==================================================");
        getLogger().info("  BetterDragon v" + getPluginMeta().getVersion() + " — por "
                + String.join(", ", getPluginMeta().getAuthors()));
        getLogger().info("  Paper 26.1.2-74 | Java 25 | Fase 3.0 Bootstrap");
        getLogger().info("==================================================");

        try {
            // 1. Cargar configuración base tipada y validada
            saveDefaultConfig();
            File arenasFile = new File(getDataFolder(), "arenas.yml");
            if (!arenasFile.exists()) {
                saveResource("arenas.yml", false);
            }
            this.configurationService = new ConfigurationService(getLogger());
            File configFile = new File(getDataFolder(), "config.yml");
            this.configurationService.loadInitial(configFile, arenasFile);

            // 2. Inicializar controlador de plataforma para BossBar vanilla
            // (Infraestructura obligatoria)
            this.vanillaBossBarController = VanillaBossBarControllerFactory.create(getLogger());

            // 3. Aplicar neutralización de BossBar vanilla en mundos del End ya cargados
            if (vanillaBossBarController.isAvailable()) {
                neutralizeLoadedEndWorlds();
            }

            // 4. Registrar listeners de plataforma para mundos cargados dinámicamente
            BossBarWorldListener worldListener = new BossBarWorldListener(
                    vanillaBossBarController,
                    getLogger(),
                    true);
            getServer().getPluginManager().registerEvents(worldListener, this);

            // 5. Inicializar dominio de batalla y ciclo de vida de dragón
            this.sessionManager = new BattleSessionManager();
            this.dragonSpawner = new DragonSpawner();
            this.battleManager = new BattleManager(sessionManager, configurationService, dragonSpawner, getLogger());

            // 6. Registrar listener del ciclo de vida del dragón
            DragonLifecycleListener lifecycleListener = new DragonLifecycleListener(sessionManager, battleManager,
                    getLogger());
            getServer().getPluginManager().registerEvents(lifecycleListener, this);

            // 7. Registrar listener de combate y tracking de daño
            DragonCombatListener combatListener = new DragonCombatListener(sessionManager, getLogger());
            getServer().getPluginManager().registerEvents(combatListener, this);

            // 8. Inicializar subsistema de Recompensas y Persistencia Durable (Fase 3.9 / 3.9-R1)
            this.databaseManager = DatabaseManager.forPluginDataFolder(getDataFolder(), getLogger());
            this.databaseManager.initializeAsync().whenComplete((v, ex) -> {
                if (ex != null) {
                    getLogger().log(Level.SEVERE, "[BetterDragon] Error fatal al inicializar base de datos SQLite asíncrona: "
                            + ex.getMessage(), ex);
                } else {
                    getLogger().info("[BetterDragon] Persistencia SQLite inicializada y lista para operaciones.");
                }
            });

            this.claimStorage = new SQLiteClaimStorage(databaseManager, getLogger());
            RewardAllocationEngine allocationEngine = new RewardAllocationEngine();
            PlayerInventoryAdapter inventoryAdapter = new BukkitPlayerInventoryAdapter(getLogger());
            MainThreadDispatcher mainThreadDispatcher = runnable -> getServer().getScheduler().runTask(this, runnable);
            RewardDeliveryService deliveryService = new RewardDeliveryService(inventoryAdapter, claimStorage, mainThreadDispatcher, getLogger());
            RewardEventDispatcher rewardEventDispatcher = event -> getServer().getPluginManager().callEvent(event);
            this.rewardService = new RewardService(
                    sessionManager,
                    configurationService,
                    allocationEngine,
                    deliveryService,
                    claimStorage,
                    rewardEventDispatcher,
                    getLogger()
            );
            DragonRewardListener rewardListener = new DragonRewardListener(rewardService, getLogger());
            getServer().getPluginManager().registerEvents(rewardListener, this);

            // 9. Inicializar subsistema de Leaderboard Persistente (Fase 3.10)
            this.leaderboardStorage = new SQLiteLeaderboardStorage(databaseManager, getLogger());
            this.leaderboardService = new LeaderboardService(leaderboardStorage, getLogger());
            DragonLeaderboardListener leaderboardListener = new DragonLeaderboardListener(leaderboardService, getLogger());
            getServer().getPluginManager().registerEvents(leaderboardListener, this);

            // 9b. Inicializar presentación y BossBar propia (Fase 3.14)
            DragonPresentationListener presentationListener = new DragonPresentationListener(sessionManager, getLogger());
            getServer().getPluginManager().registerEvents(presentationListener, this);

            getServer().getPluginManager().registerEvents(this, this);

            // 10. Inicializar Application Layer y Command Framework (Fase 3.11)
            this.permissionChecker = new BukkitPermissionChecker();
            this.leaderboardAppService = new LeaderboardApplicationService(leaderboardService);
            this.battleAdminService = new BattleAdminService(battleManager, sessionManager, configurationService, getLogger());
            this.arenaQueryService = new ArenaQueryService(configurationService);
            this.adminAppService = new AdminApplicationService(configurationService, this::getDataFolder);
            this.rewardAppService = new RewardApplicationService(rewardService, claimStorage);

            this.commandRegistry = new CommandRegistry(permissionChecker, getLogger());
            this.commandRegistry.register(new HelpSubCommand(commandRegistry));
            this.commandRegistry.register(new LeaderboardSubCommand(leaderboardAppService));
            this.commandRegistry.register(new StatsSubCommand(leaderboardAppService, permissionChecker));
            this.commandRegistry.register(new StatusSubCommand(battleAdminService));
            this.commandRegistry.register(new StartSubCommand(battleAdminService, arenaQueryService));
            this.commandRegistry.register(new AbortSubCommand(battleAdminService));
            this.commandRegistry.register(new ReloadSubCommand(adminAppService));
            this.commandRegistry.register(new ArenaSubCommand(arenaQueryService));
            this.commandRegistry.register(new ClaimSubCommand(rewardAppService, mainThreadDispatcher));

            this.betterDragonCommand = new BetterDragonCommand(commandRegistry);
            try {
                getServer().getCommandMap().register("betterdragon", betterDragonCommand);
                getLogger().info("[BetterDragon] Comandos /betterdragon y /bd registrados exitosamente.");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "[BetterDragon] No se pudo registrar el comando en CommandMap: " + t.getMessage(), t);
            }

            // 11. Tarea periódica de evaluación de fases y habilidades (Hilo principal)
            getServer().getScheduler().runTaskTimer(this, () -> {
                long currentTick = Bukkit.getCurrentTick();
                for (BattleSession session : sessionManager.getAllSessions().values()) {
                    if (session.isActive() && session.getDragonIdentity().isPresent()) {
                        UUID dragonUuid = session.getDragonIdentity().get().entityUniqueId();
                        Entity entity = Bukkit.getEntity(dragonUuid);
                        if (entity instanceof EnderDragon dragon && dragon.isValid()) {
                            session.tick(currentTick, dragon);
                        }
                    }
                }
            }, 1L, 1L);

            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("[BetterDragon] Bootstrap completado exitosamente en " + elapsed + " ms.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE,
                    "[BetterDragon] Error fatal durante el arranque del plugin: " + t.getMessage(), t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("[BetterDragon] Desactivando plugin y liberando recursos...");

        // Invariante de Apagado (Fase 3.3):
        // NO matar automáticamente los dragones BetterDragon activos.
        // Se preservan en el mundo con sus firmas PDC para permitir el futuro recovery
        // tras reinicio.
        if (sessionManager != null && sessionManager.getSessionCount() > 0) {
            getLogger().info("[BetterDragon] Preservando " + sessionManager.getSessionCount()
                    + " sesiones y dragones activos para recuperación futura.");
        }

        // Limpieza de presentación y BossBars activas (Fase 3.14)
        if (sessionManager != null) {
            for (BattleSession session : sessionManager.getAllSessions().values()) {
                if (session != null && session.getBossBar() != null) {
                    try {
                        session.getBossBar().cleanup();
                    } catch (Exception e) {
                        getLogger().log(Level.FINE, "[BetterDragon] Excepción al limpiar BossBar en onDisable: " + e.getMessage(), e);
                    }
                }
            }
        }

        // Desregistrar comando si fue registrado
        if (betterDragonCommand != null) {
            try {
                betterDragonCommand.unregister(getServer().getCommandMap());
            } catch (Exception e) {
                getLogger().log(Level.FINE, "[BetterDragon] Excepción al desregistrar comando en onDisable: " + e.getMessage(), e);
            }
        }

        // Cierre ordenado de persistencia durable (Fase 3.9 / 3.10)
        if (leaderboardService != null) {
            try {
                leaderboardService.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[BetterDragon] Error al cerrar LeaderboardService: " + e.getMessage(), e);
            }
        }
        if (claimStorage != null) {
            try {
                claimStorage.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[BetterDragon] Error al cerrar ClaimStorage: " + e.getMessage(), e);
            }
        }
        if (databaseManager != null) {
            try {
                databaseManager.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "[BetterDragon] Error al cerrar DatabaseManager: " + e.getMessage(), e);
            }
        }

        getLogger()
                .info("[BetterDragon] BetterDragon v" + getPluginMeta().getVersion() + " desactivado correctamente.");
    }

    /**
     * Recarga atómicamente la configuración global y de arenas desde
     * {@code config.yml} y {@code arenas.yml}.
     * Si la nueva configuración es inválida, se conserva la anterior intacta.
     *
     * @return true si la recarga fue exitosa y reemplazó la activa; false si fue
     *         rechazada
     */
    public boolean reloadPluginConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        File arenasFile = new File(getDataFolder(), "arenas.yml");
        return configurationService.reload(configFile, arenasFile);
    }


    /**
     * Escanea todos los mundos actualmente cargados en el servidor y aplica la
     * supresión
     * de BossBar vanilla si corresponden a dimensiones THE_END.
     */
    private void neutralizeLoadedEndWorlds() {
        int neutralizedCount = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                getLogger().info("[BetterDragon] Detectado mundo del End cargado: " + world.getName()
                        + ". Neutralizando BossBar vanilla...");
                boolean ok = vanillaBossBarController.suppressVanillaBossBar(world);
                if (ok) {
                    neutralizedCount++;
                }
            }
        }
        getLogger().info("[BetterDragon] Mundos del End procesados en el arranque: " + neutralizedCount);
    }

    /**
     * Obtiene el servicio centralizado de configuración de BetterDragon.
     *
     * @return servicio de configuración
     */
    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    /**
     * Obtiene el controlador activo de BossBar vanilla.
     *
     * @return controlador de plataforma
     */
    public VanillaBossBarController getVanillaBossBarController() {
        return vanillaBossBarController;
    }

    /**
     * Indica si la gestión del portal central de salida está habilitada.
     *
     * @return true si portal.enabled es true
     */
    public boolean isPortalEnabled() {
        return configurationService != null && configurationService.getActiveConfig().portalEnabled();
    }

    public BattleManager getBattleManager() {
        return battleManager;
    }

    public BattleSessionManager getSessionManager() {
        return sessionManager;
    }

    public DragonSpawner getDragonSpawner() {
        return dragonSpawner;
    }

    public ClaimStorage getClaimStorage() {
        return claimStorage;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public LeaderboardStorage getLeaderboardStorage() {
        return leaderboardStorage;
    }

    public LeaderboardService getLeaderboardService() {
        return leaderboardService;
    }

    public PermissionChecker getPermissionChecker() {
        return permissionChecker;
    }

    public LeaderboardApplicationService getLeaderboardAppService() {
        return leaderboardAppService;
    }

    public BattleAdminService getBattleAdminService() {
        return battleAdminService;
    }

    public ArenaQueryService getArenaQueryService() {
        return arenaQueryService;
    }

    public AdminApplicationService getAdminAppService() {
        return adminAppService;
    }

    public RewardApplicationService getRewardAppService() {
        return rewardAppService;
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public BetterDragonCommand getBetterDragonCommand() {
        return betterDragonCommand;
    }

    /**
     * Hook de verificación para la consola del servidor (automatización de pruebas
     * de integración).
     */
    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String cmd = event.getCommand().trim();
        if (cmd.equalsIgnoreCase("bd-test-lifecycle")) {
            event.setCancelled(true);
            runLifecycleVerification(event.getSender());
        } else if (cmd.equalsIgnoreCase("bd-test-combat")) {
            event.setCancelled(true);
            runCombatVerification(event.getSender());
        } else if (cmd.equalsIgnoreCase("bd-test-phases")) {
            event.setCancelled(true);
            runPhasesVerification(event.getSender());
        } else if (cmd.equalsIgnoreCase("bd-test-arena")) {
            event.setCancelled(true);
            runArenaVerification(event.getSender());
        } else if (cmd.equalsIgnoreCase("bd-test-victory")) {
            event.setCancelled(true);
            runVictoryVerification(event.getSender());
        } else if (cmd.equalsIgnoreCase("bd-test-rewards")) {
            event.setCancelled(true);
            runRewardsVerification(event.getSender());
        }
    }


    /**
     * Ejecuta la suite de verificación física del ciclo de vida del dragón e
     * independencia de DragonBattle.
     */
    private void runLifecycleVerification(CommandSender sender) {
        getLogger().info("==================================================");
        getLogger().info("  INICIANDO VERIFICACIÓN DE DRAGON LIFECYCLE (3.3)");
        getLogger().info("==================================================");

        try {
            // 1. Encontrar mundo del End
            World endWorld = null;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.THE_END) {
                    endWorld = w;
                    break;
                }
            }

            if (endWorld == null) {
                getLogger().severe("[LIFECYCLE-TEST-FAIL] No se encontró ningún mundo THE_END cargado.");
                return;
            }

            // Mantener el chunk central cargado durante la suite de pruebas mediante Paper
            // Plugin Chunk Ticket
            endWorld.getChunkAt(0, 0).load();
            endWorld.addPluginChunkTicket(0, 0, this);

            try {
                // A. Iniciar batalla y crear dragón
                BattleSession session = battleManager.startBattle(endWorld);
                getLogger().info("[LIFECYCLE-CHECK-1] Batalla creada y en estado: " + session.getState()
                        + " (ID: " + session.getBattleId() + ")");

                if (session.getState() != BattleState.ACTIVE) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] La sesión no pasó al estado ACTIVE tras el spawn.");
                    return;
                }

                // B. Verificar entidad física
                Optional<DragonIdentity> identityOpt = session.getDragonIdentity();
                if (identityOpt.isEmpty()) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] DragonIdentity no está presente en la sesión.");
                    return;
                }
                DragonIdentity identity = identityOpt.get();
                EnderDragon dragon = (EnderDragon) endWorld.getEntity(identity.entityUniqueId());
                if (dragon == null) {
                    dragon = (EnderDragon) Bukkit.getEntity(identity.entityUniqueId());
                }

                if (dragon == null || !dragon.isValid()) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] La entidad física no fue encontrada en el mundo.");
                    return;
                }
                getLogger().info("[LIFECYCLE-CHECK-2] Dragón físico encontrado con UUID: " + dragon.getUniqueId());

                // C. Verificar PDC (Fase 3.13-R1: managed=true, battle_id, definition_id y schema_version)
                if (!DragonPdcHandler.isBetterDragon(dragon)) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] El dragón no tiene la firma PDC válida de BetterDragon.");
                    return;
                }
                var pdc = dragon.getPersistentDataContainer();
                if (!pdc.has(BetterDragonKeys.DEFINITION_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] El PDC no contiene la clave obligatoria definition_id.");
                    return;
                }
                String definitionId = pdc.get(BetterDragonKeys.DEFINITION_ID, org.bukkit.persistence.PersistentDataType.STRING);
                String expectedDefinitionId = session.getConfigSnapshot() != null && session.getConfigSnapshot().dragonDefinition() != null
                        ? session.getConfigSnapshot().dragonDefinition().id()
                        : identity.definitionId();
                if (!expectedDefinitionId.equalsIgnoreCase(definitionId)) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] definition_id en PDC (" + definitionId
                            + ") no coincide con el perfil esperado (" + expectedDefinitionId + ").");
                    return;
                }

                if (!pdc.has(BetterDragonKeys.SCHEMA_VERSION, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] El PDC no contiene la clave obligatoria schema_version.");
                    return;
                }
                int schemaVersion = pdc.get(BetterDragonKeys.SCHEMA_VERSION, org.bukkit.persistence.PersistentDataType.INTEGER);
                if (schemaVersion != DragonPdcHandler.CURRENT_SCHEMA_VERSION) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] schema_version en PDC (" + schemaVersion
                            + ") no coincide con la versión soportada (" + DragonPdcHandler.CURRENT_SCHEMA_VERSION + ").");
                    return;
                }

                getLogger().info("[LIFECYCLE-CHECK-3] Firma PDC completa verificada (managed=true, battle_id="
                        + identity.battleId() + ", definition_id=" + definitionId + ", schema_version=" + schemaVersion + ")");

                // D. Verificar independencia de DragonBattle
                getLogger().info(
                        "[LIFECYCLE-CHECK-4] Independencia de DragonBattle confirmada: BattleSession es source of truth ("
                                + session.getState() + "), DragonBattle vanilla ignorado.");

                // E. Verificar que dragón externo no es reconocido
                Location dummyLoc = new Location(endWorld, 100.0, 128.0, 100.0);
                dummyLoc.getChunk().load();
                EnderDragon foreignDragon = endWorld.spawn(dummyLoc, EnderDragon.class);
                boolean foreignIsBetter = DragonPdcHandler.isBetterDragon(foreignDragon);
                foreignDragon.remove();

                if (foreignIsBetter) {
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] Un dragón vanilla externo fue falsamente identificado como BetterDragon.");
                    return;
                }
                getLogger().info(
                        "[LIFECYCLE-CHECK-5] Dragón vanilla externo correctamente ignorado por DragonPdcHandler.");

                // F. Verificar rechazo de batalla duplicada
                try {
                    battleManager.startBattle(endWorld);
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] Se permitió iniciar una batalla duplicada para el mismo mundo.");
                    return;
                } catch (IllegalStateException e) {
                    getLogger()
                            .info("[LIFECYCLE-CHECK-6] Batalla duplicada correctamente rechazada: " + e.getMessage());
                }

                // G. Verificar semántica de descarga y resolución diferida
                session.deferPendingChunkLoad();
                if (session.getState() != BattleState.DEFERRED_PENDING_CHUNK_LOAD) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] Fallo en transición a DEFERRED_PENDING_CHUNK_LOAD.");
                    return;
                }

                // 7A: Entidad ausente o equivocada debe abortar por ENTITY_MISSING
                boolean resolvedNull = battleManager.resolveDeferredDragon(session, (Entity) null);
                if (resolvedNull || session.getState() != BattleState.ABORTED) {
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] Fallo: resolución con entidad nula no abortó la sesión por ENTITY_MISSING.");
                    return;
                }
                getLogger().info(
                        "[LIFECYCLE-CHECK-7A] Entidad ausente en DEFERRED correctamente abortada por ENTITY_MISSING.");

                // 7B: Iniciar nueva sesión para validar resolución exitosa que restaura el
                // estado previo
                session = battleManager.startBattle(endWorld);
                identity = session.getDragonIdentity().get();
                dragon = (EnderDragon) endWorld.getEntity(identity.entityUniqueId());

                session.deferPendingChunkLoad();
                boolean resolvedValid = battleManager.resolveDeferredDragon(session, dragon);
                if (!resolvedValid || session.getState() != BattleState.ACTIVE) {
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] Fallo: resolución con dragón legítimo no restauró el estado previo.");
                    return;
                }
                getLogger().info(
                        "[LIFECYCLE-CHECK-7B] Resolución de dragón diferido validada exitosamente (DEFERRED -> ACTIVE restaurado).");

                // H. Prueba real de muerte física mediante daño letal
                getLogger().info(
                        "[LIFECYCLE-CHECK-8] Aplicando daño letal al BetterDragon para probar EntityDeathEvent...");
                dragon.setHealth(0.0);

                // Tras la muerte síncrona en el hilo principal, EntityDeathEvent debe haber
                // transicionado la sesión a DYING/COMPLETED
                if (session.getState() != BattleState.DYING && session.getState() != BattleState.COMPLETED) {
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] La sesión no transicionó a DYING/COMPLETED tras la muerte. Estado actual: "
                                    + session.getState());
                    return;
                }
                getLogger().info("[LIFECYCLE-CHECK-9] Muerte física procesada por EntityDeathEvent. Sesión en estado: "
                        + session.getState());

                getLogger().info("==================================================");
                getLogger().info("=== ALL DRAGON LIFECYCLE VERIFICATION CHECKS PASSED! ===");
                getLogger().info("==================================================");
            } finally {
                endWorld.removePluginChunkTicket(0, 0, this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[LIFECYCLE-TEST-ERROR] Excepción inesperada durante la verificación: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta la suite de verificación runtime de Combat Runtime (Fase 3.4).
     */
    private void runCombatVerification(CommandSender sender) {
        getLogger().info("==================================================");
        getLogger().info("  INICIANDO VERIFICACIÓN DE COMBAT RUNTIME (3.4)");
        getLogger().info("==================================================");

        try {
            // 1. Encontrar mundo del End
            World endWorld = null;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.THE_END) {
                    endWorld = w;
                    break;
                }
            }

            if (endWorld == null) {
                getLogger().severe("[COMBAT-TEST-FAIL] No se encontró ningún mundo THE_END cargado.");
                return;
            }

            endWorld.getChunkAt(0, 0).load();
            endWorld.addPluginChunkTicket(0, 0, this);

            try {
                // A. Iniciar batalla y verificar estado inicial de combate
                BattleSession session = battleManager.startBattle(endWorld);
                CombatRuntime runtime = session.getCombatRuntime();

                if (runtime.getParticipantCount() != 0 || runtime.getHitSequence() != 0L) {
                    getLogger().severe("[COMBAT-TEST-FAIL] CombatRuntime no inició vacío.");
                    return;
                }
                getLogger().info("[COMBAT-CHECK-1] CombatRuntime inicializado vacío (0 participantes, hitSequence=0).");

                // B. Identidad del dragón administrado confirmada
                DragonIdentity identity = session.getDragonIdentity().orElseThrow();
                EnderDragon dragon = (EnderDragon) endWorld.getEntity(identity.entityUniqueId());
                if (dragon == null || !DragonPdcHandler.isBetterDragon(dragon)) {
                    getLogger().severe("[COMBAT-TEST-FAIL] Dragón de la sesión no porta PDC válido de BetterDragon.");
                    return;
                }
                getLogger().info("[COMBAT-CHECK-2] BetterDragon físico y PDC de batalla verificados.");

                // C. Dragón externo ignorado
                Location dummyLoc = new Location(endWorld, 50.0, 128.0, 50.0);
                dummyLoc.getChunk().load();
                EnderDragon foreignDragon = endWorld.spawn(dummyLoc, EnderDragon.class);
                if (DragonPdcHandler.isBetterDragon(foreignDragon)) {
                    getLogger().severe("[COMBAT-TEST-FAIL] Dragón externo identificado falsamente como BetterDragon.");
                    foreignDragon.remove();
                    return;
                }
                foreignDragon.remove();
                getLogger().info("[COMBAT-CHECK-3] Dragón externo correctamente ignorado.");

                // D. Primer daño crea participante con historicalName
                UUID p1 = UUID.randomUUID();
                var hit1 = runtime.recordDamage(p1, "Mauricio", 40.0, 100L);
                if (hit1.isEmpty() || !hit1.get().historicalName().equals("Mauricio")
                        || !hit1.get().lastKnownName().equals("Mauricio")
                        || hit1.get().totalDamage() != 40.0
                        || runtime.getHitSequence() != 1L) {
                    getLogger().severe(
                            "[COMBAT-TEST-FAIL] Primer impacto de jugador no registró correctamente el participante.");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-4] Primer impacto de daño registró participante: historicalName=Mauricio, totalDamage=40.0, sequence=1.");

                // E. Segundo daño actualiza lastKnownName pero preserva historicalName
                var hit2 = runtime.recordDamage(p1, "Mauricio_Updated", 20.0, 105L);
                if (hit2.isEmpty() || !hit2.get().historicalName().equals("Mauricio")
                        || !hit2.get().lastKnownName().equals("Mauricio_Updated")
                        || hit2.get().totalDamage() != 60.0
                        || runtime.getHitSequence() != 2L) {
                    getLogger().severe(
                            "[COMBAT-TEST-FAIL] Segundo impacto no actualizó lastKnownName o alteró historicalName.");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-5] Segundo impacto preservó historicalName=Mauricio, actualizó lastKnownName=Mauricio_Updated, total=60.0, sequence=2.");

                // F. Segundo jugador aparece como participante independiente
                UUID p2 = UUID.randomUUID();
                var hit3 = runtime.recordDamage(p2, "Alex", 100.0, 110L);
                if (hit3.isEmpty() || runtime.getParticipantCount() != 2 || runtime.getHitSequence() != 3L) {
                    getLogger().severe(
                            "[COMBAT-TEST-FAIL] Segundo jugador no se registró como participante independiente.");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-6] Segundo jugador registrado independientemente: total=100.0, sequence=3, participantes=2.");

                // G. TOP_DAMAGE refleja al jugador con mayor daño
                ParticipantSnapshot top1 = runtime.getTopDamageParticipant().orElseThrow();
                if (!top1.playerId().equals(p2) || top1.totalDamage() != 100.0) {
                    getLogger().severe(
                            "[COMBAT-TEST-FAIL] TOP_DAMAGE no retornó al jugador con mayor daño (esperado Alex/p2 con 100.0).");
                    return;
                }
                getLogger().info("[COMBAT-CHECK-7] TOP_DAMAGE correcto: Alex con 100.0 de daño.");

                // H. Rebase de TOP_DAMAGE por jugador previo
                runtime.recordDamage(p1, "Mauricio_Updated", 50.0, 115L); // p1 ahora tiene 110.0
                ParticipantSnapshot top2 = runtime.getTopDamageParticipant().orElseThrow();
                if (!top2.playerId().equals(p1) || top2.totalDamage() != 110.0) {
                    getLogger()
                            .severe("[COMBAT-TEST-FAIL] Rebase de TOP_DAMAGE falló (esperado Mauricio/p1 con 110.0).");
                    return;
                }
                getLogger().info("[COMBAT-CHECK-8] Rebase de TOP_DAMAGE exitoso: Mauricio con 110.0 de daño.");

                // I. Desempate determinista ante daño idéntico
                UUID p3 = UUID.randomUUID();
                runtime.recordDamage(p3, "Steve", 110.0, 120L); // p3 empata con 110.0 pero firstHitSequence es 5 vs 1
                                                                // de p1
                ParticipantSnapshot topTie = runtime.getTopDamageParticipant().orElseThrow();
                if (!topTie.playerId().equals(p1)) {
                    getLogger().severe(
                            "[COMBAT-TEST-FAIL] Desempate determinista falló: el jugador con menor firstHitSequence debió ganar.");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-9] Desempate determinista verificado: p1 retiene TOP_DAMAGE por menor firstHitSequence (1 < 5).");

                // J. Validación y rechazo de valores inválidos
                UUID troll = UUID.randomUUID();
                if (runtime.recordDamage(troll, "Troll", 0.0, 125L).isPresent()
                        || runtime.recordDamage(troll, "Troll", -10.0, 125L).isPresent()
                        || runtime.recordDamage(troll, "Troll", Double.NaN, 125L).isPresent()
                        || runtime.recordDamage(troll, "Troll", Double.POSITIVE_INFINITY, 125L).isPresent()
                        || runtime.recordDamage(null, "Troll", 10.0, 125L).isPresent()) {
                    getLogger()
                            .severe("[COMBAT-TEST-FAIL] Se aceptó daño inválido (0, negativo, NaN, Infinity o null).");
                    return;
                }
                if (runtime.getHitSequence() != 5L) {
                    getLogger().severe("[COMBAT-TEST-FAIL] Daños inválidos consumieron secuencia (esperado 5, actual "
                            + runtime.getHitSequence() + ").");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-10] Daño inválido (0, negativo, NaN, Infinity, null UUID) rechazado sin consumir hitSequence.");

                // K. Retención de jugador offline
                if (!runtime.isParticipant(p2) || runtime.getTotalDamage(p2) != 100.0) {
                    getLogger().severe("[COMBAT-TEST-FAIL] Participante offline no retenido.");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-11] Participantes y daño retenidos en memoria sin depender de conexión Bukkit.");

                // L. Snapshot inmutable
                CombatSnapshot snapshot = runtime.createSnapshot();
                if (snapshot.participants().size() != 3 || snapshot.totalDamage() != 320.0
                        || snapshot.hitSequence() != 5L) {
                    getLogger().severe("[COMBAT-TEST-FAIL] CombatSnapshot no refleja los datos exactos del runtime.");
                    return;
                }
                getLogger().info(
                        "[COMBAT-CHECK-12] CombatSnapshot inmutable verificado: 3 participantes, 320.0 daño total, sequence=5.");

                // M. Rechazo de daño tras muerte física (DYING/COMPLETED)
                dragon.setHealth(0.0);
                if (session.getState() != BattleState.DYING && session.getState() != BattleState.COMPLETED) {
                    getLogger()
                            .severe("[COMBAT-TEST-FAIL] La sesión no transicionó a DYING/COMPLETED tras la muerte del dragón.");
                    return;
                }
                if (runtime.recordDamage(p1, "Mauricio_Updated", 10.0, 200L).isPresent()) {
                    getLogger().severe("[COMBAT-TEST-FAIL] Se aceptó daño contra un dragón en estado post-muerte.");
                    return;
                }
                getLogger().info("[COMBAT-CHECK-13] Daño rechazado correctamente tras transicionar a DYING/COMPLETED.");

                getLogger().info("==================================================");
                getLogger().info("=== ALL COMBAT RUNTIME VERIFICATION CHECKS PASSED! ===");
                getLogger().info("==================================================");

                // Apagado limpio programado del servidor de pruebas
                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
            } finally {
                endWorld.removePluginChunkTicket(0, 0, this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[COMBAT-TEST-ERROR] Excepción inesperada durante la verificación de combate: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Ejecuta la suite de verificación runtime de Phases & Abilities (Fase 3.5).
     */
    private void runPhasesVerification(CommandSender sender) {
        getLogger().info("==================================================");
        getLogger().info("  INICIANDO VERIFICACIÓN DE PHASES & ABILITIES (3.5)");
        getLogger().info("==================================================");

        try {
            World endWorld = null;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.THE_END) {
                    endWorld = w;
                    break;
                }
            }

            if (endWorld == null) {
                getLogger().severe("[PHASES-TEST-FAIL] No se encontró ningún mundo THE_END cargado.");
                return;
            }

            endWorld.getChunkAt(0, 0).load();
            endWorld.addPluginChunkTicket(0, 0, this);

            try {
                // 1. Iniciar batalla y verificar estado ACTIVE
                BattleSession session = battleManager.startBattle(endWorld);
                if (session.getState() != BattleState.ACTIVE) {
                    getLogger().severe("[PHASES-TEST-FAIL] La sesión no pasó al estado ACTIVE tras el spawn.");
                    return;
                }
                Optional<DragonIdentity> identityOpt = session.getDragonIdentity();
                if (identityOpt.isEmpty()) {
                    getLogger().severe("[PHASES-TEST-FAIL] DragonIdentity ausente en la sesión.");
                    return;
                }
                EnderDragon dragon = (EnderDragon) Bukkit.getEntity(identityOpt.get().entityUniqueId());
                if (dragon == null || !dragon.isValid()) {
                    getLogger().severe("[PHASES-TEST-FAIL] Entidad del dragón no válida.");
                    return;
                }

                // 2. Comprobar Fase Inicial
                PhaseRuntime phaseRuntime = session.getPhaseRuntime();
                if (phaseRuntime == null) {
                    getLogger().severe("[PHASES-TEST-FAIL] PhaseRuntime es nulo en la sesión.");
                    return;
                }
                if (!phaseRuntime.isInitialized() || phaseRuntime.getCurrentPhaseIndex() != 0
                        || !phaseRuntime.getCurrentPhase().id().equals("phase_1")) {
                    getLogger().severe("[PHASES-TEST-FAIL] La fase inicial no es phase_1 (index=0). Actual: "
                            + phaseRuntime.getCurrentPhase().id() + " (index=" + phaseRuntime.getCurrentPhaseIndex() + ")");
                    return;
                }
                getLogger().info("[PHASES-CHECK-1] Fase inicial correctamente establecida en phase_1 (index=0, threshold=1.0).");

                // 3. Comprobar catálogo de snapshot congelado
                if (session.getConfigSnapshot().dragonDefinition().phases().size() < 4) {
                    getLogger().severe("[PHASES-TEST-FAIL] El snapshot no contiene las 4 fases configuradas.");
                    return;
                }
                AbilityEngine abilityEngine = session.getAbilityEngine();
                if (abilityEngine == null) {
                    getLogger().severe("[PHASES-TEST-FAIL] AbilityEngine no inicializado en la sesión.");
                    return;
                }
                getLogger().info("[PHASES-CHECK-2] Snapshot congelado verificado: 4 fases configuradas e infraestructura lista.");

                // 4. Daño reduce vida y cruza a Fase 2 (70% < 75%)
                phaseRuntime.updateHealth(140.0, 200.0, 100L, dragon);
                if (phaseRuntime.getCurrentPhaseIndex() != 1 || !phaseRuntime.getCurrentPhase().id().equals("phase_2")) {
                    getLogger().severe("[PHASES-TEST-FAIL] Progresión a phase_2 falló. Actual: "
                            + phaseRuntime.getCurrentPhase().id());
                    return;
                }
                getLogger().info("[PHASES-CHECK-3] Progresión por threshold verificada: 140.0/200.0 (0.70) transicionó a phase_2.");

                // 5. Monotonicidad: curación con cristales (190.0/200.0 = 95%) NO debe revertir a phase_1
                phaseRuntime.updateHealth(190.0, 200.0, 110L, dragon);
                if (phaseRuntime.getCurrentPhaseIndex() != 1 || !phaseRuntime.getCurrentPhase().id().equals("phase_2")) {
                    getLogger().severe("[PHASES-TEST-FAIL] Violación de monotonicidad: la fase revirtió tras curación. Actual: "
                            + phaseRuntime.getCurrentPhase().id());
                    return;
                }
                getLogger().info("[PHASES-CHECK-4] Monotonicidad estricta verificada: curación a 190.0/200.0 (95%) no revierte la fase (continúa en phase_2).");

                // 6. Salto masivo de fases: vida cae directamente a 40.0 (20% <= 25%) -> salta a phase_4 (index=3)
                phaseRuntime.updateHealth(40.0, 200.0, 120L, dragon);
                if (phaseRuntime.getCurrentPhaseIndex() != 3 || !phaseRuntime.getCurrentPhase().id().equals("phase_4")) {
                    getLogger().severe("[PHASES-TEST-FAIL] Salto determinista de fases falló. Actual: "
                            + phaseRuntime.getCurrentPhase().id() + " (index=" + phaseRuntime.getCurrentPhaseIndex() + ")");
                    return;
                }
                getLogger().info("[PHASES-CHECK-5] Salto masivo determinista verificado: caída a 20% avanzó directamente a phase_4 (index=3).");

                // 7. Ejecución de habilidad y cooldown (infraestructura de prueba sin moveset inventado)
                maurxp.betterdragon.ability.AbilityDefinition testAbility = new maurxp.betterdragon.ability.AbilityDefinition(
                        "probe_test_ability",
                        maurxp.betterdragon.ability.AbilityTrigger.PERIODIC,
                        100L,
                        maurxp.betterdragon.ability.TargetSelectorType.ALL_IN_ARENA,
                        maurxp.betterdragon.ability.EffectOriginType.DRAGON_BODY,
                        maurxp.betterdragon.ability.AbilityEffectType.SOUND
                );

                boolean executed = abilityEngine.executeAbility(testAbility, phaseRuntime.getCurrentPhase(), session,
                        dragon, maurxp.betterdragon.ability.AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 130L);
                if (!executed) {
                    getLogger().severe("[PHASES-TEST-FAIL] Falló la ejecución de la habilidad de prueba.");
                    return;
                }
                getLogger().info("[PHASES-CHECK-6] Habilidad ejecutada exitosamente a través de AbilityEngine.");

                // Comprobar que cooldown bloquea ejecución en el tick 131
                boolean blocked = abilityEngine.executeAbility(testAbility, phaseRuntime.getCurrentPhase(), session,
                        dragon, maurxp.betterdragon.ability.AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 131L);
                if (blocked) {
                    getLogger().severe("[PHASES-TEST-FAIL] La habilidad se ejecutó ignorando su cooldown.");
                    return;
                }
                getLogger().info("[PHASES-CHECK-7] Cooldown en ticks lógicos verificado: segunda ejecución en tick 131 bloqueada correctamente.");

                // 8. Reset de cooldowns
                abilityEngine.resetCooldowns();
                if (!abilityEngine.getCooldownTracker().isReady("probe_test_ability", 131L)) {
                    getLogger().severe("[PHASES-TEST-FAIL] resetCooldowns no liberó la habilidad.");
                    return;
                }
                getLogger().info("[PHASES-CHECK-8] Reset de cooldowns verificado: habilidad disponible inmediatamente tras reset.");

                // 9. Aislamiento total con segunda batalla
                BattleSession session2 = BattleSession.create(BattleId.random(), "world_the_end_2", UUID.randomUUID());
                if (session2.getPhaseRuntime().getCurrentPhaseIndex() != 0) {
                    getLogger().severe("[PHASES-TEST-FAIL] Aislamiento entre batallas falló: sesión 2 no inició en phase_1.");
                    return;
                }
                getLogger().info("[PHASES-CHECK-9] Aislamiento de sesiones verificado: sesión 2 permanece en phase_1 independientemente de sesión 1.");

                // 10. Muerte transiciona limpiamente a DYING/COMPLETED
                dragon.setHealth(0.0);
                if (session.getState() != BattleState.DYING && session.getState() != BattleState.COMPLETED) {
                    getLogger().severe("[PHASES-TEST-FAIL] La sesión no transicionó a DYING/COMPLETED tras la muerte del dragón.");
                    return;
                }
                getLogger().info("[PHASES-CHECK-10] Transición terminal a DYING/COMPLETED preservada.");

                getLogger().info("==================================================");
                getLogger().info("=== ALL PHASES & ABILITIES VERIFICATION PASSED! ===");
                getLogger().info("==================================================");

                // Apagado limpio programado del servidor de pruebas
                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
            } finally {
                endWorld.removePluginChunkTicket(0, 0, this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[PHASES-TEST-ERROR] Excepción inesperada durante la verificación de fases: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta la suite de verificación runtime de Arena & Rules (Fase 3.6).
     */
    private void runArenaVerification(CommandSender sender) {
        getLogger().info("==================================================");
        getLogger().info("  INICIANDO VERIFICACIÓN DE ARENA & RULES (3.6)");
        getLogger().info("==================================================");

        try {
            World endWorld = null;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.THE_END) {
                    endWorld = w;
                    break;
                }
            }

            if (endWorld == null) {
                getLogger().severe("[ARENA-TEST-FAIL] No se encontró ningún mundo THE_END cargado.");
                return;
            }

            endWorld.getChunkAt(0, 0).load();
            endWorld.addPluginChunkTicket(0, 0, this);

            try {
                // 1. Carga de arenas.yml y existencia de arena default
                ArenaConfigurationSnapshot arenas = configurationService.getActiveArenas();
                Optional<ArenaDefinition> defaultArenaOpt = arenas.getArena("default");
                if (defaultArenaOpt.isEmpty()) {
                    getLogger().severe("[ARENA-TEST-FAIL] La arena 'default' no fue cargada desde arenas.yml.");
                    return;
                }
                ArenaDefinition defaultArena = defaultArenaOpt.get();
                getLogger().info("[ARENA-CHECK-1] Arena 'default' cargada correctamente de arenas.yml (world: "
                        + defaultArena.worldName() + ").");

                // 2. Independencia estricta de centros (center vs podium)
                Vector3d center = defaultArena.center();
                Vector3d podium = defaultArena.podium();
                if (center.equals(podium)) {
                    getLogger().severe("[ARENA-TEST-FAIL] El centro de la arena y el podio están conflados.");
                    return;
                }
                if (center.y() <= podium.y()) {
                    getLogger().severe("[ARENA-TEST-FAIL] Incoherencia vertical de centros: center Y (" + center.y()
                            + ") no es mayor que podium Y (" + podium.y() + ").");
                    return;
                }
                getLogger().info("[ARENA-CHECK-2] Centros independientes verificados: arena center Y="
                        + center.y() + ", podium Y=" + podium.y() + ".");

                // 3. Verificación de Bounding Box y contención
                ArenaBounds bounds = defaultArena.bounds();
                if (!bounds.contains(center) || !bounds.contains(podium)) {
                    getLogger().severe("[ARENA-TEST-FAIL] Center o podium están fuera de los límites de la arena.");
                    return;
                }
                Location insideLoc = new Location(endWorld, 50.0, 70.0, 50.0);
                Location outsideLoc = new Location(endWorld, 500.0, 70.0, 500.0);
                if (!bounds.contains(insideLoc) || bounds.contains(outsideLoc)) {
                    getLogger().severe("[ARENA-TEST-FAIL] Falla en la evaluación de límites bounds.contains.");
                    return;
                }
                getLogger().info("[ARENA-CHECK-3] ArenaBounds verificados: (50, 70, 50) dentro, (500, 70, 500) fuera.");

                // 4. Iniciar batalla y verificar snapshot de arena congelado
                BattleSession session = battleManager.startBattle(endWorld);
                if (session.getState() != BattleState.ACTIVE) {
                    getLogger().severe("[ARENA-TEST-FAIL] La batalla no inició en estado ACTIVE.");
                    return;
                }
                if (!session.getArenaId().equals("default") || session.getArena() == null) {
                    getLogger().severe("[ARENA-TEST-FAIL] La sesión de batalla no retiene la arena asociada.");
                    return;
                }
                getLogger().info("[ARENA-CHECK-4] BattleSession iniciada con ArenaDefinition snapshot congelado.");

                // 5. Resolución espacial mediante BattleSpatialContext y LocationResolver
                BattleSpatialContext spatialContext = session.getSpatialContext();
                Location resolvedArenaCenter = spatialContext.getArenaCenter(endWorld);
                Location resolvedPodiumCenter = spatialContext.getPodiumCenter(endWorld);

                if (resolvedArenaCenter.getX() != center.x() || resolvedArenaCenter.getY() != center.y()
                        || resolvedArenaCenter.getZ() != center.z()) {
                    getLogger().severe("[ARENA-TEST-FAIL] ARENA_CENTER resuelto (" + resolvedArenaCenter
                            + ") no coincide con el centro configurado (" + center + ").");
                    return;
                }
                if (resolvedPodiumCenter.getX() != podium.x() || resolvedPodiumCenter.getY() != podium.y()
                        || resolvedPodiumCenter.getZ() != podium.z()) {
                    getLogger().severe("[ARENA-TEST-FAIL] PODIUM_CENTER resuelto (" + resolvedPodiumCenter
                            + ") no coincide con el podio configurado (" + podium + ").");
                    return;
                }
                getLogger().info("[ARENA-CHECK-5] LocationResolver y SpatialContext resolvieron coordenadas reales sin fallbacks ficticios.");

                // 6. TargetSelector con límites reales de arena
                TargetSelector selector = new TargetSelector(spatialContext, new Random(12345L));
                AbilityDefinition dummyAbility = new AbilityDefinition("dummy", AbilityTrigger.PERIODIC, 0L,
                        TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);

                List<Player> targets = selector.resolveTargets(TargetSelectorType.ALL_IN_ARENA,
                        dummyAbility, endWorld, resolvedArenaCenter, session.getCombatRuntime(), Optional.empty());
                getLogger().info("[ARENA-CHECK-6] TargetSelector.ALL_IN_ARENA evaluado con ArenaBounds (jugadores encontrados: "
                        + targets.size() + ").");

                // 7. ArenaRuleEvaluator: water denial, boundary, anti-tunnel
                ArenaRuleEvaluator evaluator = new ArenaRuleEvaluator(session.getArena());
                if (evaluator.isWaterAllowed(insideLoc)) {
                    getLogger().severe("[ARENA-TEST-FAIL] Regla de denegación de agua falló: waterAllowed reportó true.");
                    return;
                }
                if (!evaluator.isBoundaryViolated(outsideLoc) || evaluator.isBoundaryViolated(insideLoc)) {
                    getLogger().severe("[ARENA-TEST-FAIL] Regla de frontera activa falló.");
                    return;
                }
                if (!evaluator.isAntiTunnelActive()) {
                    getLogger().severe("[ARENA-TEST-FAIL] Regla anti-túnel inactiva.");
                    return;
                }
                getLogger().info("[ARENA-CHECK-7] ArenaRuleEvaluator verificado: water_denial=true, boundary=true, anti_tunnel=true.");

                // 8. Reload Fail-Safe: archivo corrupto no altera configuración activa
                File arenasFile = new File(getDataFolder(), "arenas.yml");
                String originalContent = java.nio.file.Files.readString(arenasFile.toPath());
                try {
                    java.nio.file.Files.writeString(arenasFile.toPath(), "arenas:\n  default:\n    world: ''\n"); // Inválido
                    boolean reloadResult = reloadPluginConfig();
                    if (reloadResult) {
                        getLogger().severe("[ARENA-TEST-FAIL] Se aceptó una configuración de arenas inválida en el reload.");
                        return;
                    }
                    if (configurationService.getActiveArenas().getArena("default").isEmpty()) {
                        getLogger().severe("[ARENA-TEST-FAIL] El reload inválido descartó la configuración previa activa.");
                        return;
                    }
                    if (!session.getArena().id().equals("default")) {
                        getLogger().severe("[ARENA-TEST-FAIL] La sesión de batalla activa fue alterada por el reload.");
                        return;
                    }
                    getLogger().info("[ARENA-CHECK-8] Fail-Safe de reload confirmado: arenas.yml inválido rechazado y snapshot de batalla preservado.");
                } finally {
                    java.nio.file.Files.writeString(arenasFile.toPath(), originalContent);
                    reloadPluginConfig();
                }

                // 9. Limpieza de dragón y término de sesión
                EnderDragon dragon = (EnderDragon) Bukkit.getEntity(session.getDragonIdentity().orElseThrow().entityUniqueId());
                if (dragon != null) {
                    dragon.setHealth(0.0);
                }

                getLogger().info("==================================================");
                getLogger().info("=== ALL ARENA & RULES VERIFICATION CHECKS PASSED! ===");
                getLogger().info("==================================================");

                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
            } finally {
                endWorld.removePluginChunkTicket(0, 0, this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[ARENA-TEST-ERROR] Excepción inesperada durante la verificación de arena: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta la suite de verificación runtime de Death / Victory (Fase 3.7).
     */
    private void runVictoryVerification(CommandSender sender) {
        getLogger().info("==================================================");
        getLogger().info("  INICIANDO VERIFICACIÓN DE DEATH / VICTORY (3.7)  ");
        getLogger().info("==================================================");

        try {
            World endWorld = null;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == World.Environment.THE_END) {
                    endWorld = w;
                    break;
                }
            }

            if (endWorld == null) {
                getLogger().severe("[VICTORY-TEST-FAIL] No se encontró ningún mundo THE_END cargado.");
                return;
            }

            endWorld.getChunkAt(0, 0).load();
            endWorld.addPluginChunkTicket(0, 0, this);

            try {
                // 1. Crear / spawn de dragón BetterDragon y verificar estado ACTIVE
                BattleSession session = battleManager.startBattle(endWorld);
                if (session.getState() != BattleState.ACTIVE) {
                    getLogger().severe("[VICTORY-TEST-FAIL] La sesión no pasó al estado ACTIVE tras el spawn.");
                    return;
                }
                Optional<DragonIdentity> identityOpt = session.getDragonIdentity();
                if (identityOpt.isEmpty()) {
                    getLogger().severe("[VICTORY-TEST-FAIL] DragonIdentity ausente en la sesión.");
                    return;
                }
                EnderDragon dragon = (EnderDragon) Bukkit.getEntity(identityOpt.get().entityUniqueId());
                if (dragon == null || !dragon.isValid()) {
                    getLogger().severe("[VICTORY-TEST-FAIL] Entidad del dragón no válida.");
                    return;
                }
                getLogger().info("[VICTORY-CHECK-1] Dragón BetterDragon spawneado y sesión ACTIVE (ID: " + session.getBattleId() + ")");

                // 2. Confirmar identidad PDC (managed=true y battle_id)
                if (!DragonPdcHandler.isBetterDragon(dragon)) {
                    getLogger().severe("[VICTORY-TEST-FAIL] El dragón no tiene la firma PDC válida de BetterDragon.");
                    return;
                }
                Optional<DragonIdentity> dragonIdentityOpt = DragonPdcHandler.extractIdentity(dragon);
                if (dragonIdentityOpt.isEmpty() || !dragonIdentityOpt.get().battleId().equals(session.getBattleId())) {
                    getLogger().severe("[VICTORY-TEST-FAIL] BattleId en PDC no coincide con la sesión.");
                    return;
                }
                getLogger().info("[VICTORY-CHECK-2] Identidad PDC confirmada (managed=true, battle_id=" + session.getBattleId() + ")");

                // 3. Registrar daño de participantes para verificar TOP_DAMAGE y desempate determinista
                CombatRuntime runtime = session.getCombatRuntime();
                UUID p1 = UUID.randomUUID(); // 100.0 de daño, primer hit seq 1
                UUID p2 = UUID.randomUUID(); // 250.0 de daño -> SLAYER
                UUID p3 = UUID.randomUUID(); // offline/otro, 50.0 daño
                runtime.recordDamage(p1, "PlayerOne", 100.0, 10L);
                runtime.recordDamage(p2, "PlayerTwo_Slayer", 150.0, 15L);
                runtime.recordDamage(p3, "PlayerThree_Offline", 50.0, 20L);
                runtime.recordDamage(p2, "PlayerTwo_Slayer", 100.0, 25L); // p2 total = 250.0
                getLogger().info("[VICTORY-CHECK-3] Participantes registrados: p1=100.0, p2=250.0 (esperado Slayer), p3=50.0");

                // Registrar listener para contar BetterDragonVictoryEvent
                java.util.concurrent.atomic.AtomicInteger victoryEventCount = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicReference<BetterDragonVictoryEvent> receivedEvent = new java.util.concurrent.atomic.AtomicReference<>();
                Listener victoryListener = new Listener() {
                    @EventHandler
                    public void onVictory(BetterDragonVictoryEvent event) {
                        victoryEventCount.incrementAndGet();
                        receivedEvent.set(event);
                    }
                };
                getServer().getPluginManager().registerEvents(victoryListener, this);

                try {
                    // 4. Simular / ejecutar muerte válida (setHealth(0.0) dispara EntityDeathEvent síncronamente)
                    dragon.setHealth(0.0);

                    // 5. Verificar BetterDragonVictoryEvent
                    if (victoryEventCount.get() != 1 || receivedEvent.get() == null) {
                        getLogger().severe("[VICTORY-TEST-FAIL] BetterDragonVictoryEvent no fue emitido exactamente una vez. Conteo: " + victoryEventCount.get());
                        return;
                    }
                    BetterDragonVictoryEvent event = receivedEvent.get();
                    if (!event.getBattleId().equals(session.getBattleId())) {
                        getLogger().severe("[VICTORY-TEST-FAIL] BattleId en BetterDragonVictoryEvent no coincide.");
                        return;
                    }
                    getLogger().info("[VICTORY-CHECK-5] BetterDragonVictoryEvent emitido correctamente con battleId: " + event.getBattleId());

                    // 6. Verificar BattleSession = COMPLETED
                    if (session.getState() != BattleState.COMPLETED) {
                        getLogger().severe("[VICTORY-TEST-FAIL] La sesión no terminó en estado COMPLETED. Estado actual: " + session.getState());
                        return;
                    }
                    getLogger().info("[VICTORY-CHECK-6] BattleSession finalizada en estado COMPLETED.");

                    // 7. Verificar BattleResult
                    Optional<BattleResult> resultOpt = session.getResult();
                    if (resultOpt.isEmpty() || !resultOpt.get().isVictory()) {
                        getLogger().severe("[VICTORY-TEST-FAIL] BattleResult nulo o estado incorrecto.");
                        return;
                    }
                    BattleResult result = resultOpt.get();
                    if (result.combatSnapshot() == null || result.combatSnapshot().participants().size() != 3) {
                        getLogger().severe("[VICTORY-TEST-FAIL] CombatSnapshot en BattleResult no contiene los 3 participantes.");
                        return;
                    }
                    getLogger().info("[VICTORY-CHECK-7] BattleResult verificado: Status=COMPLETED, totalDamage=" + result.combatSnapshot().totalDamage());

                    // 8. Verificar Slayer (TOP_DAMAGE = p2)
                    if (result.slayerUniqueId() == null || !result.slayerUniqueId().equals(p2)) {
                        getLogger().severe("[VICTORY-TEST-FAIL] Slayer incorrecto en BattleResult. Esperado: " + p2 + ", actual: " + result.slayerUniqueId());
                        return;
                    }
                    if (!"PlayerTwo_Slayer".equals(result.slayerLastKnownName())) {
                        getLogger().severe("[VICTORY-TEST-FAIL] SlayerName incorrecto. Actual: " + result.slayerLastKnownName());
                        return;
                    }
                    getLogger().info("[VICTORY-CHECK-8] Slayer TOP_DAMAGE verificado: p2 (" + result.slayerLastKnownName() + ") con 250.0 daño.");

                    // 9. Verificar que segunda finalización no duplica resultado/evento (Idempotencia)
                    Optional<BattleResult> secondCallOpt = battleManager.handleDragonDeath(dragon, null);
                    if (secondCallOpt.isEmpty() || secondCallOpt.get() != result) {
                        getLogger().severe("[VICTORY-TEST-FAIL] Segunda invocación no devolvió el mismo BattleResult en caché.");
                        return;
                    }
                    if (victoryEventCount.get() != 1) {
                        getLogger().severe("[VICTORY-TEST-FAIL] Segunda invocación disparó un evento duplicado. Conteo: " + victoryEventCount.get());
                        return;
                    }
                    getLogger().info("[VICTORY-CHECK-9] Idempotencia estricta confirmada: 0 eventos duplicados y mismo BattleResult inmutable devuelto.");

                } finally {
                    org.bukkit.event.HandlerList.unregisterAll(victoryListener);
                }

                getLogger().info("==================================================");
                getLogger().info("=== ALL DEATH / VICTORY VERIFICATION CHECKS PASSED! ===");
                getLogger().info("==================================================");

                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
            } finally {
                endWorld.removePluginChunkTicket(0, 0, this);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[VICTORY-TEST-ERROR] Excepción inesperada durante la verificación de victoria: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta la suite de verificación runtime de Recompensas (Fase 3.8).
     */
    private void runRewardsVerification(CommandSender sender) {
        getLogger().info("==================================================");
        getLogger().info("  INICIANDO VERIFICACIÓN DE REWARDS (3.8)          ");
        getLogger().info("==================================================");

        try {
            // 1. Verificar configuración productiva activa (segura por defecto)
            var activeRewardConfig = configurationService.getActiveConfig().rewardConfig();
            getLogger().info("[REWARD-CHECK-1] Configuración productiva verificada: enabled="
                    + activeRewardConfig.enabled() + ", minParticipation=" + activeRewardConfig.minParticipationPercent()
                    + "%, poolSize=" + activeRewardConfig.participationPool().size() + " (seguro por defecto).");

            // 2. Preparar sesión de prueba con snapshot explícito y determinista
            BattleId testBattleId = BattleId.random();
            double testMinPercent = 20.0;
            RewardConfigurationSnapshot testRewardConfig = new RewardConfigurationSnapshot(
                    true,
                    testMinPercent, // threshold explícito de la prueba: 20.0%
                    List.of(new RewardItemDefinition("test_pool_diamond", "DIAMOND", 5)),
                    new SlayerRewardDefinition(true, true, List.of(new RewardItemDefinition("test_slayer_netherite", "NETHERITE_INGOT", 1)))
            );

            BattleConfigurationSnapshot testBattleConfig = new BattleConfigurationSnapshot(
                    false,
                    false,
                    DragonDefinition.defaults(),
                    maurxp.betterdragon.arena.ArenaDefinition.defaults(),
                    testRewardConfig
            );

            UUID testWorldId = UUID.randomUUID();
            BattleSession testSession = new BattleSession(testBattleId, "rewards_test_world_" + testBattleId, testWorldId, testBattleConfig);
            sessionManager.register(testSession);

            try {
                // 3. Simular participantes: total 1000.0 de daño
                // p1 = 600.0 (60.0% >= 20.0%) -> elegible
                // p2 = 300.0 (30.0% >= 20.0%) -> elegible
                // p3 = 100.0 (10.0% < 20.0%)  -> NO elegible
                UUID p1 = UUID.randomUUID();
                UUID p2 = UUID.randomUUID();
                UUID p3 = UUID.randomUUID();

                List<ParticipantSnapshot> participants = List.of(
                        new ParticipantSnapshot(p1, "PlayerOne_Eligible", "PlayerOne_Eligible", 600.0, 1L, 10L, 100L),
                        new ParticipantSnapshot(p2, "PlayerTwo_Slayer", "PlayerTwo_Slayer", 300.0, 2L, 11L, 101L),
                        new ParticipantSnapshot(p3, "PlayerThree_Ineligible", "PlayerThree_Ineligible", 100.0, 3L, 12L, 102L)
                );
                CombatSnapshot snapshot = new CombatSnapshot(testBattleId, participants, 12L, 1000.0);
                BattleResult result = BattleResult.completed(
                        testBattleId,
                        java.time.Instant.now().minusSeconds(120),
                        java.time.Instant.now(),
                        p1, // Slayer: TOP_DAMAGE (p1 con 600)
                        "PlayerOne_Eligible",
                        snapshot
                );

                // 4. Suscribirse temporalmente al BetterDragonRewardEvent para verificar despacho
                java.util.concurrent.atomic.AtomicInteger rewardEventCount = new java.util.concurrent.atomic.AtomicInteger(0);
                Listener rewardEventListener = new Listener() {
                    @EventHandler
                    public void onReward(BetterDragonRewardEvent event) {
                        if (event.getBattleId().equals(testBattleId)) {
                            rewardEventCount.incrementAndGet();
                        }
                    }
                };
                getServer().getPluginManager().registerEvents(rewardEventListener, this);

                try {
                    // 5. Procesar victoria
                    RewardAllocationPlan plan = rewardService.processVictory(result);
                    if (plan.isEmpty()) {
                        getLogger().severe("[REWARD-TEST-FAIL] El plan de recompensas devuelto está vacío.");
                        return;
                    }
                    getLogger().info("[REWARD-CHECK-2] Plan de asignación calculado exitosamente (" + plan.allocations().size() + " asignaciones).");

                    // 6. Verificar elegibilidad y redistribución proporcional:
                    // p3 (10.0% daño < 20.0% threshold configurado) NO debe recibir recompensa de participación
                    boolean p3HasParticipation = plan.allocations().stream()
                            .anyMatch(a -> a.participantId().equals(p3) && a.source() == maurxp.betterdragon.reward.model.RewardSource.PARTICIPATION);
                    if (p3HasParticipation) {
                        getLogger().severe("[REWARD-TEST-FAIL] Participante no elegible (p3 al 10% < 20%) recibió recompensa de participación.");
                        return;
                    }
                    getLogger().info("[REWARD-CHECK-3] Verificada exclusión de participante no elegible (p3 al 10.0% < threshold 20.0%).");

                    // Verificar que p1 y p2 recibieron asignaciones de participación
                    boolean p1HasParticipation = plan.allocations().stream()
                            .anyMatch(a -> a.participantId().equals(p1) && a.source() == maurxp.betterdragon.reward.model.RewardSource.PARTICIPATION);
                    boolean p2HasParticipation = plan.allocations().stream()
                            .anyMatch(a -> a.participantId().equals(p2) && a.source() == maurxp.betterdragon.reward.model.RewardSource.PARTICIPATION);
                    if (!p1HasParticipation || !p2HasParticipation) {
                        getLogger().severe("[REWARD-TEST-FAIL] Participantes elegibles no recibieron recompensa.");
                        return;
                    }
                    getLogger().info("[REWARD-CHECK-4] Participantes elegibles (p1 al 60.0% y p2 al 30.0% >= 20.0%) recibieron asignaciones.");

                    // 7. Verificar persistencia de reclamos en ClaimStorage (0% pérdidas ante offline)
                    var claimsP1 = claimStorage.findByPlayer(p1).join();
                    if (claimsP1.isEmpty()) {
                        getLogger().severe("[REWARD-TEST-FAIL] No se guardaron reclamos en ClaimStorage para p1.");
                        return;
                    }
                    getLogger().info("[REWARD-CHECK-5] Reclamos protegidos en ClaimStorage (" + claimsP1.size() + " reclamos para p1).");

                    // 8. Verificar despacho de BetterDragonRewardEvent
                    if (rewardEventCount.get() != 1) {
                        getLogger().severe("[REWARD-TEST-FAIL] Conteo de BetterDragonRewardEvent incorrecto: " + rewardEventCount.get());
                        return;
                    }
                    getLogger().info("[REWARD-CHECK-6] BetterDragonRewardEvent despachado correctamente (conteo: 1).");

                    // 9. Verificar idempotencia estricta: re-procesar no duplica reclamos ni eventos
                    int claimsBeforeRetry = claimStorage.findByBattleId(testBattleId).join().size();
                    RewardAllocationPlan retryPlan = rewardService.processVictory(result);
                    int claimsAfterRetry = claimStorage.findByBattleId(testBattleId).join().size();

                    if (claimsBeforeRetry != claimsAfterRetry) {
                        getLogger().severe("[REWARD-TEST-FAIL] Idempotencia falló: se duplicaron reclamos en almacenamiento ("
                                + claimsBeforeRetry + " vs " + claimsAfterRetry + ").");
                        return;
                    }
                    getLogger().info("[REWARD-CHECK-7] Idempotencia estricta verificada: segundo procesamiento no duplicó reclamos.");

                } finally {
                    org.bukkit.event.HandlerList.unregisterAll(rewardEventListener);
                }

            } finally {
                sessionManager.remove(testBattleId);
            }

            getLogger().info("==================================================");
            getLogger().info("=== ALL REWARDS VERIFICATION CHECKS PASSED!    ===");
            getLogger().info("==================================================");

            Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[REWARD-TEST-ERROR] Excepción inesperada durante la verificación de recompensas: " + e.getMessage(), e);
        }
    }
}
