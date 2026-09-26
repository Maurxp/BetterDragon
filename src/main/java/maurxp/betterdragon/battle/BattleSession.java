package maurxp.betterdragon.battle;

import maurxp.betterdragon.ability.AbilityCooldownTracker;
import maurxp.betterdragon.ability.AbilityEngine;
import maurxp.betterdragon.ability.ArenaBattleSpatialContext;
import maurxp.betterdragon.ability.BattleSpatialContext;
import maurxp.betterdragon.ability.LocationResolver;
import maurxp.betterdragon.ability.TargetSelector;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.combat.CombatRuntime;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.phase.PhaseRuntime;
import maurxp.betterdragon.presentation.DragonBossBar;
import maurxp.betterdragon.util.BetterDragonKeys;
import maurxp.betterdragon.util.CancellableTask;
import maurxp.betterdragon.util.DelayedTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Representa el estado mutable en tiempo de ejecución de una batalla de
 * BetterDragon.
 * <p>
 * Principios de diseño:
 * <ul>
 * <li><b>Main-Thread Confinement:</b> Vive exclusivamente en el hilo principal
 * del servidor,
 * sin necesidad de sincronización concurrente interna (sin locks ni
 * Atomic*).</li>
 * <li><b>Protección de Invariantes:</b> Las transiciones de estado solo se
 * realizan a través
 * de métodos de dominio explícitos que validan la máquina de estados.</li>
 * <li><b>Snapshot Inmutable:</b> Almacena un
 * {@link BattleConfigurationSnapshot} inmutable
 * que congela los parámetros de la batalla y la arena; recargas posteriores del plugin no
 * afectan la sesión.</li>
 * <li><b>Aislamiento de Persistencia:</b> No almacena referencias a entidades
 * Bukkit vivas
 * ({@code EnderDragon} o {@code Player}) ni a objetos {@code World},
 * permitiendo
 * recuperación y serialización limpia.</li>
 * <li><b>Combat Runtime Aislado:</b> Cada sesión posee su propia instancia de
 * {@link CombatRuntime},
 * garantizando aislamiento total entre batallas sin singletons globales.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BattleSession {

    private final BattleId battleId;
    private final String worldName;
    private final UUID worldUniqueId;
    private final BattleConfigurationSnapshot configSnapshot;
    private final BattleSpatialContext spatialContext;
    private final Instant createdAt;
    private final CombatRuntime combatRuntime;
    private final PhaseRuntime phaseRuntime;
    private final AbilityEngine abilityEngine;
    private final DragonBossBar bossBar;
    private final DelayedTaskScheduler delayedTaskScheduler;

    private final Set<UUID> minionUuids = ConcurrentHashMap.newKeySet();
    private final Set<Entity> trackedMinions = ConcurrentHashMap.newKeySet();
    private final List<CancellableTask> pendingTasks = new CopyOnWriteArrayList<>();

    private BattleState state;
    private DragonIdentity dragonIdentity;
    private Instant activatedAt;
    private Instant completedAt;
    private BattleState stateBeforeChunkDeferral;
    private BattleResult result;
    private boolean enrageActive = false;

    public BattleSession(BattleId battleId, String worldName, UUID worldUniqueId,
            BattleConfigurationSnapshot configSnapshot, DelayedTaskScheduler delayedTaskScheduler) {
        this.battleId = Objects.requireNonNull(battleId, "El battleId no puede ser nulo");
        this.worldName = Objects.requireNonNull(worldName, "El worldName no puede ser nulo");
        this.worldUniqueId = Objects.requireNonNull(worldUniqueId, "El worldUniqueId no puede ser nulo");
        this.configSnapshot = Objects.requireNonNull(configSnapshot, "El configSnapshot no puede ser nulo");
        this.createdAt = Instant.now();
        this.state = BattleState.IDLE;
        this.combatRuntime = new CombatRuntime(this);
        this.spatialContext = new ArenaBattleSpatialContext(configSnapshot.arenaDefinition());
        this.delayedTaskScheduler = delayedTaskScheduler != null ? delayedTaskScheduler : (runnable, delay) -> {
            if (delay <= 0) {
                runnable.run();
                return () -> {};
            }
            try {
                org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskLater(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(BattleSession.class),
                        runnable,
                        delay
                );
                return bt::cancel;
            } catch (Exception | LinkageError e) {
                runnable.run();
                return () -> {};
            }
        };

        AbilityCooldownTracker cooldownTracker = new AbilityCooldownTracker();
        TargetSelector targetSelector = new TargetSelector(this.spatialContext, new Random());
        LocationResolver locationResolver = new LocationResolver(this.spatialContext);
        this.abilityEngine = new AbilityEngine(
                configSnapshot.dragonDefinition().abilities(),
                cooldownTracker,
                targetSelector,
                locationResolver,
                null,
                this.delayedTaskScheduler,
                null
        );
        this.phaseRuntime = new PhaseRuntime(this, configSnapshot.dragonDefinition().phases(), this.abilityEngine, null);
        this.bossBar = new DragonBossBar(
                this.battleId,
                configSnapshot.dragonDefinition().bossbar(),
                configSnapshot.dragonDefinition().displayName()
        );
    }

    public BattleSession(BattleId battleId, String worldName, UUID worldUniqueId,
            BattleConfigurationSnapshot configSnapshot) {
        this(battleId, worldName, worldUniqueId, configSnapshot, null);
    }

    public BattleSession(BattleId battleId, String worldName, UUID worldUniqueId) {
        this(battleId, worldName, worldUniqueId, BattleConfigurationSnapshot.defaults(), null);
    }


    /**
     * Fábrica para crear una nueva sesión de batalla en estado IDLE con snapshot
     * congelado.
     *
     * @param battleId       identificador único
     * @param worldName      nombre del mundo
     * @param worldUniqueId  UUID del mundo
     * @param configSnapshot instantánea inmutable de configuración
     * @return nueva sesión en estado IDLE
     */
    public static BattleSession create(BattleId battleId, String worldName, UUID worldUniqueId,
            BattleConfigurationSnapshot configSnapshot) {
        return new BattleSession(battleId, worldName, worldUniqueId, configSnapshot);
    }

    public static BattleSession create(BattleId battleId, String worldName, UUID worldUniqueId,
            BattleConfigurationSnapshot configSnapshot, DelayedTaskScheduler delayedTaskScheduler) {
        return new BattleSession(battleId, worldName, worldUniqueId, configSnapshot, delayedTaskScheduler);
    }

    /**
     * Fábrica para crear una nueva sesión de batalla en estado IDLE con
     * configuración por defecto.
     *
     * @param battleId      identificador único
     * @param worldName     nombre del mundo
     * @param worldUniqueId UUID del mundo
     * @return nueva sesión en estado IDLE
     */
    public static BattleSession create(BattleId battleId, String worldName, UUID worldUniqueId) {
        return new BattleSession(battleId, worldName, worldUniqueId, BattleConfigurationSnapshot.defaults());
    }

    /**
     * Inicia la fase de preparación de la batalla.
     * Transición: IDLE -> PREPARING.
     */
    public void start() {
        transitionTo(BattleState.PREPARING);
    }

    /**
     * Activa el combate una vez que la entidad del dragón ha sido generada o
     * vinculada.
     * Transición: PREPARING -> ACTIVE.
     *
     * @param dragonIdentity identidad inmutable del dragón asignado
     * @throws IllegalArgumentException si la identidad no pertenece a esta batalla
     */
    public void activate(DragonIdentity dragonIdentity) {
        Objects.requireNonNull(dragonIdentity, "La identidad del dragón no puede ser nula al activar la batalla");
        if (!dragonIdentity.battleId().equals(this.battleId)) {
            throw new IllegalArgumentException("El DragonIdentity pertenece a una batalla distinta: "
                    + dragonIdentity.battleId() + " (esperado: " + this.battleId + ")");
        }

        transitionTo(BattleState.ACTIVE);
        this.dragonIdentity = dragonIdentity;
        this.activatedAt = Instant.now();
        this.bossBar.setVisible(true);
        syncBossBarViewers();
    }

    /**
     * Marca el inicio del proceso de muerte del dragón al llegar a 0 de salud.
     * Transición: ACTIVE -> DYING.
     */
    public void beginDying() {
        this.bossBar.cleanup();
        cancelPendingTasks();
        cleanSessionMinions();
        transitionTo(BattleState.DYING);
    }

    /**
     * Concluye la batalla con victoria y genera el resultado inmutable final con instantánea de combate.
     * Transición: DYING -> COMPLETED.
     *
     * @param slayerUniqueId      UUID del Slayer (TOP_DAMAGE)
     * @param slayerLastKnownName snapshot del nombre del Slayer
     * @param combatSnapshot      instantánea inmutable del estado de combate con participantes
     * @return resultado inmutable de la batalla
     */
    public BattleResult complete(UUID slayerUniqueId, String slayerLastKnownName, CombatSnapshot combatSnapshot) {
        if (this.state == BattleState.COMPLETED && this.result != null) {
            return this.result;
        }
        this.bossBar.cleanup();
        cancelPendingTasks();
        cleanSessionMinions();
        transitionTo(BattleState.COMPLETED);
        this.completedAt = Instant.now();

        this.result = BattleResult.completed(
                this.battleId,
                this.activatedAt != null ? this.activatedAt : this.createdAt,
                this.completedAt,
                slayerUniqueId,
                slayerLastKnownName,
                combatSnapshot);
        return this.result;
    }

    /**
     * Concluye la batalla con victoria y genera el resultado inmutable final.
     * Transición: DYING -> COMPLETED.
     *
     * @param slayerUniqueId      UUID del Slayer (TOP_DAMAGE)
     * @param slayerLastKnownName snapshot del nombre del Slayer
     * @return resultado inmutable de la batalla
     */
    public BattleResult complete(UUID slayerUniqueId, String slayerLastKnownName) {
        return complete(slayerUniqueId, slayerLastKnownName, this.combatRuntime != null ? this.combatRuntime.createSnapshot() : null);
    }

    /**
     * Cancela o aborta la batalla por un motivo tipado de dominio.
     * Transición: cualquier estado operativo -> ABORTED.
     *
     * @param reason razón tipada de la cancelación
     * @return resultado inmutable de la batalla abortada
     */
    public BattleResult abort(BattleAbortReason reason) {
        Objects.requireNonNull(reason, "La razón de aborto no puede ser nula");
        return abort(reason.name());
    }

    /**
     * Aborta la sesión usando la razón por defecto de comando administrativo.
     *
     * @return resultado inmutable de la batalla abortada
     */
    public BattleResult abort() {
        return abort(BattleAbortReason.MANUAL_ABORT);
    }

    /**
     * Cancela o aborta la batalla por una condición no recuperable o intervención
     * administrativa.
     * Transición: cualquier estado operativo -> ABORTED.
     *
     * @param reason motivo de la cancelación
     * @return resultado inmutable de la batalla abortada
     */
    public BattleResult abort(String reason) {
        if (this.state == BattleState.ABORTED && this.result != null) {
            return this.result;
        }
        this.bossBar.cleanup();
        cancelPendingTasks();
        cleanSessionMinions();
        transitionTo(BattleState.ABORTED);
        this.completedAt = Instant.now();

        this.result = BattleResult.aborted(
                this.battleId,
                this.activatedAt != null ? this.activatedAt : this.createdAt,
                this.completedAt,
                reason);
        return this.result;
    }

    /**
     * Pausa la batalla debido a la descarga del chunk del dragón.
     * Transición: ACTIVE o DYING -> DEFERRED_PENDING_CHUNK_LOAD.
     */
    public void deferPendingChunkLoad() {
        if (this.state != BattleState.ACTIVE && this.state != BattleState.DYING) {
            throw new IllegalStateException(
                    "Solo batallas en ACTIVE o DYING pueden pausarse por descarga de chunk. Estado actual: "
                            + this.state);
        }
        this.stateBeforeChunkDeferral = this.state;
        this.bossBar.setVisible(false);
        cancelPendingTasks();
        transitionTo(BattleState.DEFERRED_PENDING_CHUNK_LOAD);
    }

    /**
     * Registra un esbirro invocado durante la batalla para su posterior limpieza determinista.
     */
    public void registerMinion(Entity minion) {
        if (minion != null) {
            this.minionUuids.add(minion.getUniqueId());
            this.trackedMinions.add(minion);
        }
    }

    /**
     * Retorna una vista inmutable de los UUIDs de esbirros activos registrados.
     */
    public Set<UUID> getMinionUuids() {
        return Collections.unmodifiableSet(minionUuids);
    }

    /**
     * Registra una tarea diferida cancelable (ej. telegrafiado sensorial en curso).
     */
    public void registerPendingTask(CancellableTask task) {
        if (task != null) {
            this.pendingTasks.add(task);
        }
    }

    /**
     * Cancela todas las tareas diferidas pendientes de ejecución vinculadas a esta sesión.
     */
    public void cancelPendingTasks() {
        for (CancellableTask task : pendingTasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
        pendingTasks.clear();
    }

    /**
     * Ejecuta una rutina de barrido selectivo (EXP-007) eliminando el 100% de los esbirros
     * creados por esta batalla identificados mediante PDC, sin alterar mobs pacíficos ni entidades externas.
     */
    public void cleanSessionMinions() {
        try {
            World world = Bukkit.getWorld(this.worldName);
            cleanSessionMinions(world);
        } catch (Exception ignored) {
            cleanSessionMinions((World) null);
        }
    }

    /**
     * Ejecuta la rutina de limpieza selectiva de esbirros sobre un mundo explícito.
     *
     * @param world mundo en el que realizar el barrido
     */
    public void cleanSessionMinions(World world) {
        try {
            // 1. Limpieza de referencias directas en memoria
            for (Entity entity : trackedMinions) {
                try {
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                } catch (Exception ignored) {
                }
            }
            trackedMinions.clear();

            // 2. Limpieza de minions registrados por UUID
            for (UUID uuid : minionUuids) {
                try {
                    if (Bukkit.getServer() != null) {
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity != null && entity.isValid()) {
                            entity.remove();
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. Barrido complementario sobre entidades vivas con PDC de esta batalla
            if (world != null && world.getEntities() != null) {
                String battleIdStr = this.battleId.asString();
                for (Entity entity : world.getEntities()) {
                    if (entity != null && entity.isValid()) {
                        PersistentDataContainer pdc = entity.getPersistentDataContainer();
                        boolean isMinion = pdc.has(BetterDragonKeys.MINION, PersistentDataType.BOOLEAN)
                                || pdc.has(BetterDragonKeys.MINION, PersistentDataType.BYTE);
                        if (isMinion && battleIdStr.equals(pdc.get(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING))) {
                            entity.remove();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        minionUuids.clear();
    }

    public DelayedTaskScheduler getDelayedTaskScheduler() {
        return delayedTaskScheduler;
    }

    /**
     * Reanuda la batalla una vez que el chunk ha vuelto a ser cargado.
     * Transición: DEFERRED_PENDING_CHUNK_LOAD -> ACTIVE o DYING.
     */
    public void resumeFromChunkLoad() {
        if (this.state != BattleState.DEFERRED_PENDING_CHUNK_LOAD) {
            throw new IllegalStateException("La sesión no está en estado DEFERRED_PENDING_CHUNK_LOAD: " + this.state);
        }
        BattleState targetState = this.stateBeforeChunkDeferral != null ? this.stateBeforeChunkDeferral
                : BattleState.ACTIVE;
        this.stateBeforeChunkDeferral = null;
        transitionTo(targetState);
        this.bossBar.setVisible(true);
        syncBossBarViewers();
    }

    private void transitionTo(BattleState next) {
        this.state.validateTransition(next);
        this.state = next;
    }

    // --- Consultas de Estado ---

    public BattleId getBattleId() {
        return battleId;
    }

    public String getWorldName() {
        return worldName;
    }

    public UUID getWorldUniqueId() {
        return worldUniqueId;
    }

    public BattleState getState() {
        return state;
    }

    public Optional<DragonIdentity> getDragonIdentity() {
        return Optional.ofNullable(dragonIdentity);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Optional<Instant> getActivatedAt() {
        return Optional.ofNullable(activatedAt);
    }

    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    /**
     * Retorna el resultado final inmutable de la batalla si ha concluido.
     *
     * @return Optional con el BattleResult si la sesión es terminal, o empty si continúa en curso
     */
    public Optional<BattleResult> getResult() {
        return Optional.ofNullable(result);
    }

    public boolean isActive() {
        return state == BattleState.ACTIVE;
    }

    public boolean isTerminal() {
        return state.isTerminal();
    }

    /**
     * Retorna el estado operativo que tenía la sesión antes de entrar en
     * DEFERRED_PENDING_CHUNK_LOAD.
     *
     * @return Optional con el estado previo (ACTIVE o DYING), o empty si no está
     *         pausada por chunk
     */
    public Optional<BattleState> getStateBeforeChunkDeferral() {
        return Optional.ofNullable(stateBeforeChunkDeferral);
    }

    /**
     * Retorna la instantánea de configuración congelada para esta sesión de
     * batalla.
     *
     * @return snapshot inmutable de configuración
     */
    public BattleConfigurationSnapshot getConfigSnapshot() {
        return configSnapshot;
    }

    /**
     * Alias de conveniencia para {@link #getConfigSnapshot()}.
     *
     * @return snapshot inmutable de configuración
     */
    public BattleConfigurationSnapshot getSnapshot() {
        return configSnapshot;
    }

    /**
     * Retorna el runtime de combate asociado exclusivamente a esta sesión de
     * batalla.
     *
     * @return runtime de combate activo
     */
    public CombatRuntime getCombatRuntime() {
        return combatRuntime;
    }

    /**
     * Retorna el runtime de fases asociado exclusivamente a esta sesión de batalla.
     *
     * @return runtime de fases activo
     */
    public PhaseRuntime getPhaseRuntime() {
        return phaseRuntime;
    }

    /**
     * Retorna el motor de habilidades asociado exclusivamente a esta sesión de batalla.
     *
     * @return motor de habilidades activo
     */
    public AbilityEngine getAbilityEngine() {
        return abilityEngine;
    }

    /**
     * Retorna la definición de arena congelada para esta sesión de batalla.
     *
     * @return ArenaDefinition inmutable
     */
    public ArenaDefinition getArena() {
        return configSnapshot.arenaDefinition();
    }

    /**
     * Retorna el identificador de la arena asociada a esta batalla.
     *
     * @return identificador de la arena
     */
    public String getArenaId() {
        return configSnapshot.arenaDefinition().id();
    }

    /**
     * Retorna el contexto espacial activo de la batalla respaldado por la arena.
     *
     * @return BattleSpatialContext de la sesión
     */
    public BattleSpatialContext getSpatialContext() {
        return spatialContext;
    }

    /**
     * Retorna el controlador de presentación de la BossBar para esta sesión.
     *
     * @return instancia de DragonBossBar
     */
    public DragonBossBar getBossBar() {
        return bossBar;
    }

    /**
     * Retorna si el estado Soft Enrage está activo en esta sesión de combate.
     *
     * @return true si el dragón está en Soft Enrage
     */
    public boolean isEnrageActive() {
        return enrageActive;
    }

    /**
     * Evalúa si la salud actual cruza el umbral de activación de Soft Enrage.
     * <p>
     * Garantía de Monotonicidad:
     * Una vez activado (false -> true), jamás regresa a false durante la misma batalla,
     * incluso si la salud se recupera posteriormente por cristales del End.
     *
     * @param currentHealth salud actual
     * @param maxHealth     salud máxima
     * @return true si Enrage está activo (recién activado o previamente activo)
     */
    public boolean checkEnrage(double currentHealth, double maxHealth) {
        if (this.enrageActive) {
            return true;
        }
        var enrage = configSnapshot.dragonDefinition().enrage();
        if (enrage == null || !enrage.enabled()) {
            return false;
        }
        if (!Double.isFinite(currentHealth) || !Double.isFinite(maxHealth) || maxHealth <= 0.0) {
            return false;
        }
        double ratio = currentHealth / maxHealth;
        if (!Double.isFinite(ratio) || Double.isNaN(ratio)) {
            return false;
        }
        if (ratio <= enrage.threshold()) {
            this.enrageActive = true;
            this.bossBar.setEnraged(true);
            this.bossBar.playEnrageFeedback();
            return true;
        }
        return false;
    }

    /**
     * Actualiza la salud en la BossBar y evalúa la activación de Soft Enrage.
     *
     * @param currentHealth salud actual
     * @param maxHealth     salud máxima
     */
    public void updateDragonHealth(double currentHealth, double maxHealth) {
        this.bossBar.updateHealth(currentHealth, maxHealth);
        checkEnrage(currentHealth, maxHealth);
    }

    /**
     * Sincroniza los espectadores de la BossBar con los jugadores válidos presentes en la arena.
     */
    public void syncBossBarViewers() {
        if (!isActive()) {
            return;
        }
        try {
            if (org.bukkit.Bukkit.getServer() == null) {
                return;
            }
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(this.worldUniqueId);
            if (world == null) {
                world = org.bukkit.Bukkit.getWorld(this.worldName);
            }
            if (world == null) {
                return;
            }
            java.util.List<org.bukkit.entity.Player> eligible = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player p : world.getPlayers()) {
                if (p != null && p.isOnline() && !p.isDead()) {
                    if (this.spatialContext == null || this.spatialContext.getBounds() == null
                            || this.spatialContext.isInArena(p.getLocation())) {
                        eligible.add(p);
                    }
                }
            }
            this.bossBar.updateViewers(eligible);
        } catch (Exception ignored) {
            // Protección ante entornos de pruebas o excepciones de Bukkit
        }
    }

    /**
     * Ejecuta el ciclo periódico de actualización de combate, fases y habilidades.
     *
     * @param currentTick tick lógico del servidor
     * @param dragon      entidad física activa del dragón
     */
    public void tick(long currentTick, EnderDragon dragon) {
        if (!isActive() || dragon == null || !dragon.isValid()) {
            return;
        }
        double maxHealth = 200.0;
        try {
            if (dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                maxHealth = dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            } else {
                maxHealth = dragon.getMaxHealth();
            }
        } catch (Exception ignored) {
        }
        updateDragonHealth(dragon.getHealth(), maxHealth);
        syncBossBarViewers();
        this.phaseRuntime.tick(currentTick, dragon);
    }
}
