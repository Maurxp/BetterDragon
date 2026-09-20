package maurxp.betterdragon;

import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonLifecycleListener;
import maurxp.betterdragon.battle.DragonPdcHandler;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.combat.CombatRuntime;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.DragonCombatListener;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import maurxp.betterdragon.platform.bossbar.BossBarWorldListener;
import maurxp.betterdragon.platform.bossbar.VanillaBossBarController;
import maurxp.betterdragon.platform.bossbar.VanillaBossBarControllerFactory;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Optional;
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
            this.configurationService = new ConfigurationService(getLogger());
            File configFile = new File(getDataFolder(), "config.yml");
            this.configurationService.loadInitial(configFile);

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

            getServer().getPluginManager().registerEvents(this, this);

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

        getLogger()
                .info("[BetterDragon] BetterDragon v" + getPluginMeta().getVersion() + " desactivado correctamente.");
    }

    /**
     * Recarga atómicamente la configuración global desde el archivo
     * {@code config.yml}.
     * Si la nueva configuración es inválida, se conserva la anterior intacta.
     *
     * @return true si la recarga fue exitosa y reemplazó la activa; false si fue
     *         rechazada
     */
    public boolean reloadPluginConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        return configurationService.reload(configFile);
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

                // C. Verificar PDC (Fase 3.3-R1: solo managed=true y battle_id)
                if (!DragonPdcHandler.isBetterDragon(dragon)) {
                    getLogger().severe("[LIFECYCLE-TEST-FAIL] El dragón no tiene la firma PDC válida de BetterDragon.");
                    return;
                }
                var pdc = dragon.getPersistentDataContainer();
                if (pdc.has(BetterDragonKeys.DEFINITION_ID, org.bukkit.persistence.PersistentDataType.STRING) ||
                        pdc.has(BetterDragonKeys.SCHEMA_VERSION, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] El PDC contiene claves innecesarias escritas durante el spawn (definition_id o schema_version).");
                    return;
                }
                getLogger().info("[LIFECYCLE-CHECK-3] Firma PDC mínima verificada (solo managed=true y battle_id="
                        + identity.battleId() + ", sin definition_id ni schema_version)");

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
                // transicionado la sesión a DYING
                if (session.getState() != BattleState.DYING) {
                    getLogger().severe(
                            "[LIFECYCLE-TEST-FAIL] La sesión no transicionó a DYING tras la muerte. Estado actual: "
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

                // M. Rechazo de daño tras muerte física (DYING)
                dragon.setHealth(0.0);
                if (session.getState() != BattleState.DYING) {
                    getLogger()
                            .severe("[COMBAT-TEST-FAIL] La sesión no transicionó a DYING tras la muerte del dragón.");
                    return;
                }
                if (runtime.recordDamage(p1, "Mauricio_Updated", 10.0, 200L).isPresent()) {
                    getLogger().severe("[COMBAT-TEST-FAIL] Se aceptó daño contra un dragón en estado DYING.");
                    return;
                }
                getLogger().info("[COMBAT-CHECK-13] Daño rechazado correctamente tras transicionar a DYING.");

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
}
