package maurxp.betterdragon;

import maurxp.betterdragon.application.admin.AdminApplicationService;
import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.leaderboard.LeaderboardApplicationService;
import maurxp.betterdragon.application.permission.BukkitPermissionChecker;
import maurxp.betterdragon.application.permission.PermissionChecker;
import maurxp.betterdragon.application.reward.RewardApplicationService;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonLifecycleListener;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.combat.DragonCombatListener;
import maurxp.betterdragon.combat.DragonExplosionListener;
import maurxp.betterdragon.combat.DragonFlightListener;
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
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.leaderboard.service.DragonLeaderboardListener;
import maurxp.betterdragon.leaderboard.service.LeaderboardService;
import maurxp.betterdragon.leaderboard.storage.LeaderboardStorage;
import maurxp.betterdragon.leaderboard.storage.SQLiteLeaderboardStorage;
import maurxp.betterdragon.persistence.DatabaseManager;
import maurxp.betterdragon.platform.bossbar.BossBarWorldListener;
import maurxp.betterdragon.platform.bossbar.VanillaBossBarController;
import maurxp.betterdragon.platform.bossbar.VanillaBossBarControllerFactory;
import maurxp.betterdragon.presentation.DragonPresentationListener;
import maurxp.betterdragon.reward.allocation.RewardAllocationEngine;
import maurxp.betterdragon.reward.claim.ClaimStorage;
import maurxp.betterdragon.reward.claim.SQLiteClaimStorage;
import maurxp.betterdragon.reward.delivery.BukkitPlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.PlayerInventoryAdapter;
import maurxp.betterdragon.reward.delivery.RewardDeliveryService;
import maurxp.betterdragon.reward.event.RewardEventDispatcher;
import maurxp.betterdragon.reward.service.DragonRewardListener;
import maurxp.betterdragon.reward.service.RewardService;
import maurxp.betterdragon.util.MainThreadDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Clase principal de arranque y ciclo de vida de BetterDragon.
 * <p>
 * Diseñada bajo el principio de responsabilidad única (Bootstrap / Orchestration):
 * esta clase <b>NO es un God Object</b> y no contiene lógica de combate, ni consultas
 * SQL, ni cálculo de recompensas, ni definiciones de habilidades. Su única función es
 * inicializar la infraestructura, inyectar dependencias y orquestar el apagado limpio.
 *
 * @author maurxp
 */
public final class BetterDragonPlugin extends JavaPlugin {

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
        getLogger().info("  Paper 26.1.2-74 | Java 25");
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

            // 7b. Registrar listeners de vuelo y protección de explosiones
            DragonFlightListener flightListener = new DragonFlightListener(sessionManager);
            getServer().getPluginManager().registerEvents(flightListener, this);
            DragonExplosionListener explosionListener = new DragonExplosionListener();
            getServer().getPluginManager().registerEvents(explosionListener, this);

            // 8. Inicializar subsistema de Recompensas y Persistencia Durable
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

            // 9. Inicializar subsistema de Leaderboard Persistente
            this.leaderboardStorage = new SQLiteLeaderboardStorage(databaseManager, getLogger());
            this.leaderboardService = new LeaderboardService(leaderboardStorage, getLogger());
            DragonLeaderboardListener leaderboardListener = new DragonLeaderboardListener(leaderboardService, getLogger());
            getServer().getPluginManager().registerEvents(leaderboardListener, this);

            // 9b. Inicializar presentación y BossBar propia
            DragonPresentationListener presentationListener = new DragonPresentationListener(sessionManager, getLogger());
            getServer().getPluginManager().registerEvents(presentationListener, this);

            // 10. Inicializar Application Layer y Command Framework
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

        // Invariante de apagado:
        // NO matar automáticamente los dragones BetterDragon activos.
        // Se preservan en el mundo con sus firmas PDC para permitir el futuro recovery
        // tras reinicio.
        if (sessionManager != null && sessionManager.getSessionCount() > 0) {
            getLogger().info("[BetterDragon] Preservando " + sessionManager.getSessionCount()
                    + " sesiones y dragones activos para recuperación futura.");
        }

        // Limpieza de presentación y BossBars activas
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

        // Cierre ordenado de persistencia durable
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

}
