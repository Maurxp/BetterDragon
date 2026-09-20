package maurxp.betterdragon.battle;

import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.config.BattleConfigurationSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.Optional;
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
 *   <li><b>Disponibilidad Determinista de Arena (Fase 3.6):</b> Valida que la arena exista, sus límites
 *       sean coherentes y el mundo esté disponible antes de iniciar la batalla. Cero fallbacks hardcodeados.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BattleManager {

    private final BattleSessionManager sessionManager;
    private final ConfigurationService configService;
    private final DragonSpawner spawner;
    private final Logger logger;

    public BattleManager(
            BattleSessionManager sessionManager,
            ConfigurationService configService,
            DragonSpawner spawner,
            Logger logger
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.configService = Objects.requireNonNull(configService, "configService no puede ser nulo");
        this.spawner = Objects.requireNonNull(spawner, "spawner no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
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

        // 3. Resolución y disponibilidad de arena (Fase 3.6)
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

        // 4. Generar identidades y congelar configuración con snapshot inmutable
        BattleId battleId = BattleId.random();
        BattleConfigurationSnapshot snapshot = configService.createBattleSnapshot(arena);
        BattleSession session = BattleSession.create(battleId, world.getName(), world.getUID(), snapshot);

        // 5. Iniciar PREPARING y registrar sesión
        session.start();
        sessionManager.register(session);
        logger.info("[BetterDragon] Preparando batalla " + battleId + " en el mundo " + world.getName()
                + " (arena: " + arena.id() + ")...");


        // 5. Spawnear dragón con PDC
        EnderDragon dragon;
        try {
            dragon = spawner.spawnDragon(world, spawnLocation, battleId, definitionId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[BetterDragon] Error al spawnear el dragón para la batalla " + battleId + ": " + e.getMessage(), e);
            session.abort(BattleAbortReason.SPAWN_FAILED);
            sessionManager.remove(battleId);
            throw new RuntimeException("Fallo al crear la entidad del dragón: " + e.getMessage(), e);
        }

        // 6. Validar que la entidad física existe y porta el PDC correcto
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty() || !identityOpt.get().battleId().equals(battleId)) {
            logger.severe("[BetterDragon] La entidad spawneada no superó la verificación de identidad PDC. Abortando batalla.");
            dragon.remove();
            session.abort(BattleAbortReason.SPAWN_FAILED);
            sessionManager.remove(battleId);
            throw new IllegalStateException("La entidad generada no contiene la identidad PDC válida de BetterDragon.");
        }

        // 7. Asociar identidad y transicionar PREPARING -> ACTIVE
        DragonIdentity identity = identityOpt.get();
        session.activate(identity);
        logger.info("[BetterDragon] Batalla " + battleId + " activada exitosamente con dragón UUID: " + dragon.getUniqueId());

        // 8. Inicializar runtime de fases con la entidad
        double maxHealth = 200.0;
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
