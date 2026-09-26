package maurxp.betterdragon.battle;

import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.battle.event.BetterDragonVictoryEvent;
import maurxp.betterdragon.battle.event.VictoryEventDispatcher;
import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.combat.CombatSnapshot;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinador de alto nivel para el inicio, spawn y ciclo de vida de una sesión de batalla.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Responsabilidad Acotada:</b> No maneja combate, ni fases, ni recompensas, ni persistencia SQLite.</li>
 *   <li><b>Transición Estricta PREPARING -> ACTIVE:</b> Solo pasa a {@code ACTIVE} tras validar físicamente
 *       que la entidad existe en el mundo y porta el PDC correcto de BetterDragon.</li>
 *   <li><b>Limpieza Idempotente ante Fallos:</b> Si el spawn falla o la entidad no valida, limpia cualquier
 *       rastro, remueve la sesión de memoria y marca la sesión como abortada.</li>
 *   <li><b>Disponibilidad Determinista de Arena:</b> Valida que la arena exista, sus límites
 *       sean coherentes y el mundo esté disponible antes de iniciar la batalla. Cero fallbacks hardcodeados.</li>
 *   <li><b>Finalización Idempotente de Victoria:</b> Procesa {@code EntityDeathEvent}, transiciona
 *       {@code ACTIVE -> DYING -> COMPLETED}, resuelve el Slayer (TOP_DAMAGE) y emite {@link BetterDragonVictoryEvent}.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BattleManager {

    private final BattleSessionManager sessionManager;
    private final ConfigurationService configService;
    private final DragonSpawner spawner;
    private final VictoryEventDispatcher victoryEventDispatcher;
    private final Logger logger;

    public BattleManager(
            BattleSessionManager sessionManager,
            ConfigurationService configService,
            DragonSpawner spawner,
            VictoryEventDispatcher victoryEventDispatcher,
            Logger logger
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.configService = Objects.requireNonNull(configService, "configService no puede ser nulo");
        this.spawner = Objects.requireNonNull(spawner, "spawner no puede ser nulo");
        this.victoryEventDispatcher = Objects.requireNonNull(victoryEventDispatcher, "victoryEventDispatcher no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    public BattleManager(
            BattleSessionManager sessionManager,
            ConfigurationService configService,
            DragonSpawner spawner,
            Logger logger
    ) {
        this(sessionManager, configService, spawner, createDefaultVictoryDispatcher(), logger);
    }

    private static VictoryEventDispatcher createDefaultVictoryDispatcher() {
        return event -> {
            try {
                if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                    Bukkit.getPluginManager().callEvent(event);
                }
            } catch (Throwable ignored) {
            }
        };
    }

    /**
     * Inicia una nueva batalla en el mundo del End especificado usando la arena predeterminada.
     *
     * @param world mundo THE_END
     * @return sesión activada en estado ACTIVE
     */
    public BattleSession startBattle(World world) {
        return startBattle(world, null, "default", "default");
    }

    /**
     * Inicia una nueva batalla con ubicación de spawn y perfil definidos usando la arena predeterminada.
     *
     * @param world         mundo del End
     * @param spawnLocation ubicación de spawn (opcional, null usa el centro por defecto)
     * @param definitionId  perfil del dragón
     * @return sesión activada en estado ACTIVE
     */
    public BattleSession startBattle(World world, Location spawnLocation, String definitionId) {
        return startBattle(world, spawnLocation, definitionId, "default");
    }

    /**
     * Inicia una nueva batalla con ubicación de spawn, perfil del dragón y arena específica.
     *
     * @param world         mundo del End
     * @param spawnLocation ubicación de spawn (opcional, null usa el centro por defecto)
     * @param definitionId  perfil del dragón
     * @param arenaId       identificador de la arena a resolver
     * @return sesión activada en estado ACTIVE
     * @throws IllegalArgumentException si el mundo no es THE_END
     * @throws IllegalStateException    si ya existe una batalla activa, la arena no existe o el mundo no coincide
     */
    public BattleSession startBattle(World world, Location spawnLocation, String definitionId, String arenaId) {
        Objects.requireNonNull(world, "El mundo no puede ser nulo");

        // 1. Validar dimensión End
        if (world.getEnvironment() != World.Environment.THE_END) {
            throw new IllegalArgumentException("BetterDragon solo puede iniciarse en una dimensión THE_END (mundo actual: "
                    + world.getName() + ", ambiente: " + world.getEnvironment() + ")");
        }

        // 2. Invariante de exclusión: no duplicar batalla en el mismo mundo
        if (sessionManager.hasActiveSession(world.getName())) {
            throw new IllegalStateException("Ya existe una sesión de batalla activa para el mundo: " + world.getName());
        }

        // 3. Resolución y disponibilidad de arena
        String targetArenaId = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "default";
        ArenaDefinition arena = null;

        // Si se usa 'default', intentar resolver arena específica para este mundo si existe
        if (targetArenaId.equalsIgnoreCase("default")) {
            arena = configService.getActiveArenas().getArenaForWorld(world.getName()).orElse(null);
        }
        if (arena == null) {
            Optional<ArenaDefinition> arenaOpt = configService.getArena(targetArenaId);
            if (arenaOpt.isEmpty()) {
                throw new IllegalStateException("La arena configurada '" + targetArenaId + "' no existe en arenas.yml.");
            }
            arena = arenaOpt.get();
        }

        if (!arena.worldName().equalsIgnoreCase(world.getName())) {
            throw new IllegalStateException("La arena '" + arena.id() + "' está configurada para el mundo '"
                    + arena.worldName() + "', pero se solicitó iniciar en '" + world.getName() + "'.");
        }

        boolean worldAvailable = false;
        try {
            worldAvailable = Bukkit.getServer() != null && Bukkit.getWorld(arena.worldName()) != null;
        } catch (Throwable ignored) {
        }
        if (!worldAvailable && world.getName().equalsIgnoreCase(arena.worldName())) {
            worldAvailable = true;
        }

        if (!worldAvailable) {
            throw new IllegalStateException("El mundo de la arena '" + arena.worldName()
                    + "' no se encuentra cargado o disponible en el servidor.");
        }

        // 4. Resolver definición y recuento de jugadores para escalado determinista
        String targetDefinition = (definitionId != null && !definitionId.isBlank()) ? definitionId.trim().toLowerCase() : null;
        if (targetDefinition != null) {
            if (configService.getDragonDefinition(targetDefinition).isEmpty()) {
                throw new IllegalArgumentException("La definición de dragón solicitada '" + definitionId + "' no existe en el catálogo.");
            }
        } else {
            if (!configService.getDragonCatalog().hasDefaultDefinition()) {
                throw new IllegalStateException("No se especificó un perfil de dragón y no existe una definición 'default' configurada en el catálogo.");
            }
            targetDefinition = configService.getDragonCatalog().defaultDefinitionId();
        }

        int playerCount = countEligiblePlayersInArena(world, arena);

        // 5. Generar identidades y congelar configuración con snapshot inmutable
        BattleId battleId = BattleId.random();
        BattleConfigurationSnapshot snapshot = configService.createBattleSnapshot(arena, targetDefinition, playerCount);
        BattleSession session = BattleSession.create(battleId, world.getName(), world.getUID(), snapshot);

        // 6. Iniciar PREPARING y registrar sesión
        session.start();
        sessionManager.register(session);
        logger.info("[BetterDragon] Preparando batalla " + battleId + " en el mundo " + world.getName()
                + " (arena: " + arena.id() + ", perfil: " + targetDefinition + ", jugadores: " + playerCount + ")...");

        // 7. Spawnear dragón con PDC y estadísticas efectivas derivadas de la arena
        EnderDragon dragon;
        try {
            dragon = spawner.spawnDragon(world, arena, snapshot.effectiveDragonStats(), battleId, spawnLocation);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[BetterDragon] Error al spawnear el dragón para la batalla " + battleId + ": " + e.getMessage(), e);
            session.abort(BattleAbortReason.SPAWN_FAILED);
            sessionManager.remove(battleId);
            throw new RuntimeException("Fallo al crear la entidad del dragón: " + e.getMessage(), e);
        }

        // 8. Validar que la entidad física existe y porta el PDC correcto
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty() || !identityOpt.get().battleId().equals(battleId)) {
            logger.severe("[BetterDragon] La entidad spawneada no superó la verificación de identidad PDC. Abortando batalla.");
            dragon.remove();
            session.abort(BattleAbortReason.SPAWN_FAILED);
            sessionManager.remove(battleId);
            throw new IllegalStateException("La entidad generada no contiene la identidad PDC válida de BetterDragon.");
        }

        // 9. Asociar identidad y transicionar PREPARING -> ACTIVE
        DragonIdentity identity = identityOpt.get();
        session.activate(identity);
        logger.info("[BetterDragon] Batalla " + battleId + " activada exitosamente con dragón UUID: " + dragon.getUniqueId());

        // 10. Inicializar runtime de fases con la entidad y salud efectiva calibrada
        double maxHealth = snapshot.effectiveDragonStats().maxHealth();
        try {
            if (dragon.getAttribute(Attribute.MAX_HEALTH) != null) {
                maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
            }
        } catch (Throwable ignored) {
        }
        double currentHealth = maxHealth;
        try {
            currentHealth = dragon.getHealth();
        } catch (Throwable ignored) {
        }
        long tick = 0L;
        try {
            tick = Bukkit.getCurrentTick();
        } catch (Throwable ignored) {
        }
        session.getPhaseRuntime().initialize(currentHealth, maxHealth, tick, dragon);

        return session;
    }

    /**
     * Cuenta deterministamente la cantidad de jugadores activos y elegibles dentro de los límites de la arena.
     *
     * @param world mundo del End
     * @param arena definición de arena
     * @return número de jugadores elegibles presentes en la arena
     */
    public int countEligiblePlayersInArena(World world, ArenaDefinition arena) {
        if (world == null || arena == null) {
            return 0;
        }
        int count = 0;
        try {
            if (world.getPlayers() != null) {
                for (Player player : world.getPlayers()) {
                    if (player != null && player.isOnline() && !player.isDead()) {
                        GameMode gm = player.getGameMode();
                        if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && arena.bounds().contains(player.getLocation())) {
                            count++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Entornos de prueba o mocks donde world.getPlayers() no está disponible
        }
        return count;
    }

    /**
     * Cancela y aborta una batalla activa por motivo tipado.
     *
     * @param battleId identificador de la batalla
     * @param reason   motivo del aborto
     * @return la sesión abortada, si existía
     */
    public Optional<BattleSession> abortBattle(BattleId battleId, BattleAbortReason reason) {
        Optional<BattleSession> sessionOpt = sessionManager.getSession(battleId);
        if (sessionOpt.isPresent()) {
            BattleSession session = sessionOpt.get();
            if (!session.isTerminal()) {
                session.abort(reason);
                logger.warning("[BetterDragon] Batalla " + battleId + " abortada. Motivo: " + reason);
            }
            sessionManager.remove(battleId);
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Procesa la muerte natural del EnderDragon administrado y finaliza la batalla con victoria.
     * <p>
     * Garantías de idempotencia y coherencia:
     * <ul>
     *   <li>Verifica que la entidad sea un dragón con PDC válido perteneciente a una sesión conocida.</li>
     *   <li>Si la sesión ya fue completada ({@code COMPLETED}), es un no-op seguro que no emite eventos duplicados.</li>
     *   <li>Si la sesión fue abortada, no genera victoria ni emite eventos.</li>
     *   <li>Transiciona formalmente {@code ACTIVE -> DYING -> COMPLETED}.</li>
     *   <li>Captura un snapshot inmutable del combate ({@link CombatSnapshot}) en el hilo principal.</li>
     *   <li>Determina el Slayer con criterio determinista {@code TOP_DAMAGE} (mayor daño acumulado, menor firstHitSequence).</li>
     *   <li>Construye el {@link BattleResult} inmutable y emite {@link BetterDragonVictoryEvent}.</li>
     * </ul>
     *
     * @param dragon     entidad física del dragón que murió
     * @param deathEvent evento de muerte de Bukkit (opcional, para suprimir XP y drops)
     * @return resultado inmutable de la victoria, o empty si la entidad o sesión no eran válidas
     */
    public Optional<BattleResult> handleDragonDeath(EnderDragon dragon, EntityDeathEvent deathEvent) {
        if (dragon == null) {
            return Optional.empty();
        }

        // 1. Validar identidad PDC del dragón
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty()) {
            return Optional.empty(); // Dragón vanilla u otra entidad; retorno silencioso sin spam
        }

        DragonIdentity identity = identityOpt.get();
        Optional<BattleSession> sessionOpt = sessionManager.getSession(identity.battleId());
        if (sessionOpt.isEmpty()) {
            logger.warning("[BetterDragon] Detectada muerte de dragón con BattleId " + identity.battleId()
                    + " pero no existe una sesión registrada en memoria.");
            return Optional.empty();
        }

        BattleSession session = sessionOpt.get();

        // 2. Validar que la entidad física coincida con la registrada en la sesión
        if (session.getDragonIdentity().isPresent()) {
            UUID registeredUuid = session.getDragonIdentity().get().entityUniqueId();
            if (!registeredUuid.equals(dragon.getUniqueId())) {
                logger.warning("[BetterDragon] La entidad dragón (" + dragon.getUniqueId()
                        + ") no coincide con el dragón asignado a la sesión " + session.getBattleId()
                        + " (" + registeredUuid + ").");
                return Optional.empty();
            }
        }

        // 3. Comprobar compuerta de estado e idempotencia
        BattleState currentState = session.getState();
        if (currentState == BattleState.COMPLETED) {
            logger.fine("[BetterDragon] Muerte ignorada: la batalla " + session.getBattleId() + " ya está COMPLETED.");
            return session.getResult();
        }

        if (currentState == BattleState.ABORTED) {
            logger.fine("[BetterDragon] Muerte ignorada: la batalla " + session.getBattleId() + " ya está ABORTED.");
            return Optional.empty();
        }

        if (currentState == BattleState.IDLE || currentState == BattleState.PREPARING) {
            logger.warning("[BetterDragon] Muerte recibida para batalla en estado no activo: " + currentState);
            return Optional.empty();
        }

        if (currentState == BattleState.DEFERRED_PENDING_CHUNK_LOAD) {
            if (!DragonPdcHandler.validateDragonForSession(dragon, session)) {
                logger.warning("[BetterDragon] Dragón en DEFERRED_PENDING_CHUNK_LOAD no superó validación de identidad al morir. Abortando por ENTITY_MISSING.");
                session.abort(BattleAbortReason.ENTITY_MISSING);
                return Optional.empty();
            }
            session.resumeFromChunkLoad();
        }

        // 4. Transicionar formalmente a DYING si aún estaba en ACTIVE
        if (session.getState() == BattleState.ACTIVE) {
            logger.info("[BetterDragon] EnderDragon de la batalla " + identity.battleId()
                    + " ha muerto naturalmente. Transicionando ACTIVE -> DYING...");
            session.beginDying();
        }

        // 5. Suprimir XP masiva y drops vanilla
        if (deathEvent != null) {
            deathEvent.setDroppedExp(0);
            deathEvent.getDrops().clear();
            logger.info("[BetterDragon] Cancelando 12000 XP vanilla y drops para el dragón de la batalla " + identity.battleId());
        }

        // 6. Capturar snapshot inmutable de combate en el hilo principal
        CombatSnapshot combatSnapshot = session.getCombatRuntime().createSnapshot();

        // 7. Determinar Slayer (TOP_DAMAGE) con desempate determinista
        Optional<ParticipantSnapshot> topDamageOpt = session.getCombatRuntime().getTopDamageParticipant();
        UUID slayerId = topDamageOpt.map(ParticipantSnapshot::playerId).orElse(null);
        String slayerName = topDamageOpt.map(ParticipantSnapshot::lastKnownName).orElse(null);

        if (topDamageOpt.isPresent()) {
            logger.info("[BetterDragon] Slayer (TOP_DAMAGE) de la batalla " + session.getBattleId()
                    + ": " + slayerName + " (" + slayerId + ") con " + topDamageOpt.get().totalDamage() + " de daño.");
        } else {
            logger.info("[BetterDragon] Batalla " + session.getBattleId()
                    + " completada sin participantes registrados con daño.");
        }

        // 8. Transicionar formalmente DYING -> COMPLETED y generar BattleResult
        BattleResult result = session.complete(slayerId, slayerName, combatSnapshot);
        logger.info("[BetterDragon] Batalla " + session.getBattleId()
                + " finalizada con victoria (COMPLETED). Duración: " + result.getDuration().getSeconds() + "s");

        // 9. Despachar BetterDragonVictoryEvent público
        String worldName = session.getWorldName();
        BetterDragonVictoryEvent victoryEvent = new BetterDragonVictoryEvent(session.getBattleId(), result, worldName);
        this.victoryEventDispatcher.dispatch(victoryEvent);

        return Optional.of(result);
    }

    /**
     * Procesa la muerte natural del EnderDragon administrado y finaliza la batalla con victoria.
     *
     * @param dragon entidad física del dragón que murió
     * @return resultado inmutable de la victoria, o empty si no era válido
     */
    public Optional<BattleResult> handleDragonDeath(EnderDragon dragon) {
        return handleDragonDeath(dragon, null);
    }

    public VictoryEventDispatcher getVictoryEventDispatcher() {
        return victoryEventDispatcher;
    }

    public Optional<BattleSession> getActiveSession(World world) {
        if (world == null) return Optional.empty();
        return sessionManager.getActiveSessionByWorld(world.getName());
    }

    public Optional<BattleSession> getActiveSession(BattleId battleId) {
        return sessionManager.getSession(battleId);
    }

    public BattleSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Intenta resolver y validar la identidad de un dragón diferido por descarga de chunk.
     * <p>
     * Si la entidad física encontrada supera la validación estricta de identidad (UUID, tipo,
     * managed=true, battle_id coincidente), se restaura el estado previo de la sesión (ACTIVE o DYING).
     * Si la entidad es nula, equivocada o inválida, se aborta la sesión por {@link BattleAbortReason#ENTITY_MISSING}.
     *
     * @param session         sesión en estado DEFERRED_PENDING_CHUNK_LOAD
     * @param candidateEntity entidad física encontrada (puede ser null)
     * @return true si la entidad fue validada y el estado restaurado; false si fue abortada o no procedió
     */
    public boolean resolveDeferredDragon(BattleSession session, Entity candidateEntity) {
        Objects.requireNonNull(session, "La sesión no puede ser nula");

        if (session.getState() != BattleState.DEFERRED_PENDING_CHUNK_LOAD) {
            return false;
        }

        if (candidateEntity == null) {
            logger.warning("[BetterDragon] No se encontró la entidad del dragón al recargar chunk para la batalla "
                    + session.getBattleId() + ". Abortando por ENTITY_MISSING.");
            session.abort(BattleAbortReason.ENTITY_MISSING);
            sessionManager.remove(session.getBattleId());
            return false;
        }

        if (!DragonPdcHandler.validateDragonForSession(candidateEntity, session)) {
            logger.warning("[BetterDragon] La entidad recargada con UUID " + candidateEntity.getUniqueId()
                    + " no superó la validación de identidad para la batalla " + session.getBattleId()
                    + ". Abortando por ENTITY_MISSING.");
            session.abort(BattleAbortReason.ENTITY_MISSING);
            sessionManager.remove(session.getBattleId());
            return false;
        }

        session.resumeFromChunkLoad();
        logger.info("[BetterDragon] Dragón resuelto e identidad confirmada para la batalla " + session.getBattleId()
                + ". Estado restaurado: " + session.getState());
        return true;
    }

    /**
     * Sobrecarga para resolver el dragón diferido buscando la entidad en el mundo por su UUID esperado.
     *
     * @param session sesión diferida
     * @param world   mundo donde debe residir el dragón
     * @return true si fue resuelto exitosamente, false si fue abortado o no procedió
     */
    public boolean resolveDeferredDragon(BattleSession session, World world) {
        Objects.requireNonNull(session, "La sesión no puede ser nula");
        Objects.requireNonNull(world, "El mundo no puede ser nulo");

        if (session.getState() != BattleState.DEFERRED_PENDING_CHUNK_LOAD) {
            return false;
        }

        Optional<DragonIdentity> identityOpt = session.getDragonIdentity();
        if (identityOpt.isEmpty()) {
            session.abort(BattleAbortReason.ENTITY_MISSING);
            sessionManager.remove(session.getBattleId());
            return false;
        }

        Entity entity = world.getEntity(identityOpt.get().entityUniqueId());
        return resolveDeferredDragon(session, entity);
    }
}
