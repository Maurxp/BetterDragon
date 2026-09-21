package maurxp.betterdragon.application.battle;

import maurxp.betterdragon.application.battle.model.BattleOperationResult;
import maurxp.betterdragon.application.battle.model.BattleStatusView;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.combat.ParticipantSnapshot;
import maurxp.betterdragon.config.ConfigurationService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import maurxp.betterdragon.phase.PhaseDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de aplicación para la gestión y administración del ciclo de combate.
 * <p>
 * Centraliza las operaciones de consulta de estado, inicio forzado y cancelación
 * de batallas sin exponer el estado mutable interno de {@link BattleSession}.
 *
 * @author maurxp
 */
public class BattleAdminService {

    private final BattleManager battleManager;
    private final BattleSessionManager sessionManager;
    private final ConfigurationService configService;
    private final Logger logger;

    public BattleAdminService(
            BattleManager battleManager,
            BattleSessionManager sessionManager,
            ConfigurationService configService,
            Logger logger
    ) {
        this.battleManager = Objects.requireNonNull(battleManager, "battleManager no puede ser nulo");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.configService = Objects.requireNonNull(configService, "configService no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Retorna la lista de vistas de estado de todas las batallas actualmente activas o en memoria.
     */
    public List<BattleStatusView> getAllStatuses() {
        List<BattleStatusView> statuses = new ArrayList<>();
        for (BattleSession session : sessionManager.getAllSessions().values()) {
            if (!session.isTerminal()) {
                statuses.add(buildStatusView(session));
            }
        }
        return List.copyOf(statuses);
    }

    /**
     * Obtiene la vista de estado de la batalla activa en el mundo indicado.
     *
     * @param world mundo THE_END
     * @return Optional con el estado si existe una batalla activa
     */
    public Optional<BattleStatusView> getStatus(World world) {
        if (world == null) {
            return Optional.empty();
        }
        return getStatus(world.getName());
    }

    /**
     * Obtiene la vista de estado de la batalla activa por nombre de mundo.
     *
     * @param worldName nombre del mundo
     * @return Optional con el estado si existe una batalla activa
     */
    public Optional<BattleStatusView> getStatus(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }
        return sessionManager.getActiveSessionByWorld(worldName)
                .map(this::buildStatusView);
    }

    /**
     * Inicia una nueva batalla en el mundo y arena especificados.
     *
     * @param world        mundo THE_END donde iniciar
     * @param definitionId identificador de la definición del dragón (opcional, null usa "default")
     * @param arenaId      identificador de la arena (opcional, null usa "default")
     * @return resultado tipado de la operación
     */
    public BattleOperationResult startBattle(World world, String definitionId, String arenaId) {
        if (world == null) {
            return BattleOperationResult.failure("El mundo especificado no existe o no está cargado.");
        }

        if (world.getEnvironment() != World.Environment.THE_END) {
            return BattleOperationResult.failure("BetterDragon solo opera en dimensiones THE_END (mundo actual: "
                    + world.getName() + ", ambiente: " + world.getEnvironment() + ").");
        }

        if (sessionManager.hasActiveSession(world.getName())) {
            return BattleOperationResult.failure("Ya existe una batalla activa en el mundo " + world.getName() + ".");
        }

        String targetDefinition = (definitionId != null && !definitionId.isBlank()) ? definitionId.trim() : "default";
        String targetArena = (arenaId != null && !arenaId.isBlank()) ? arenaId.trim() : "default";

        try {
            BattleSession session = battleManager.startBattle(world, null, targetDefinition, targetArena);
            return BattleOperationResult.success(
                    "Batalla iniciada exitosamente en el mundo " + world.getName() + " (Arena: " + targetArena + ").",
                    session.getBattleId()
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return BattleOperationResult.failure(e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[BetterDragon] Error inesperado al iniciar batalla vía comando: " + e.getMessage(), e);
            return BattleOperationResult.failure("Error interno al iniciar batalla: " + e.getMessage());
        }
    }

    /**
     * Cancela y aborta la batalla activa en el mundo indicado, removiendo al dragón si existe.
     *
     * @param world  mundo THE_END
     * @param reason descripción del motivo de cancelación
     * @return resultado tipado de la operación
     */
    public BattleOperationResult abortBattle(World world, String reason) {
        if (world == null) {
            return BattleOperationResult.failure("El mundo especificado no existe o no está cargado.");
        }

        Optional<BattleSession> sessionOpt = sessionManager.getActiveSessionByWorld(world.getName());
        if (sessionOpt.isEmpty()) {
            return BattleOperationResult.failure("No hay ninguna batalla activa en el mundo " + world.getName() + ".");
        }

        BattleSession session = sessionOpt.get();
        return abortSession(session, reason);
    }

    /**
     * Cancela y aborta la batalla con el ID especificado.
     *
     * @param battleId ID de la batalla
     * @param reason   motivo de cancelación
     * @return resultado tipado de la operación
     */
    public BattleOperationResult abortBattle(BattleId battleId, String reason) {
        if (battleId == null) {
            return BattleOperationResult.failure("El ID de batalla no puede ser nulo.");
        }

        Optional<BattleSession> sessionOpt = sessionManager.getSession(battleId);
        if (sessionOpt.isEmpty()) {
            return BattleOperationResult.failure("No se encontró ninguna sesión con el ID " + battleId + ".");
        }

        BattleSession session = sessionOpt.get();
        return abortSession(session, reason);
    }

    private BattleOperationResult abortSession(BattleSession session, String reason) {
        BattleId battleId = session.getBattleId();
        String worldName = session.getWorldName();

        // 1. Remover físicamente la entidad del dragón si está en el mundo para evitar dragones huérfanos
        session.getDragonIdentity().ifPresent(identity -> {
            try {
                Entity entity = Bukkit.getEntity(identity.entityUniqueId());
                if (entity != null && entity.isValid()) {
                    entity.remove();
                    logger.info("[BetterDragon] Entidad dragón " + identity.entityUniqueId()
                            + " removida físicamente tras aborto manual.");
                }
            } catch (Throwable t) {
                logger.warning("[BetterDragon] No se pudo remover físicamente al dragón en aborto: " + t.getMessage());
            }
        });

        // 2. Abortar en BattleManager
        battleManager.abortBattle(battleId, BattleAbortReason.MANUAL_ABORT);

        String desc = (reason != null && !reason.isBlank()) ? " (Motivo: " + reason.trim() + ")" : "";
        return BattleOperationResult.success("Batalla " + battleId + " en " + worldName + " abortada correctamente" + desc + ".", battleId);
    }

    private BattleStatusView buildStatusView(BattleSession session) {
        Instant start = session.getActivatedAt().orElseGet(session::getCreatedAt);
        Duration elapsed = Duration.between(start, Instant.now());

        Optional<UUID> dragonUuid = session.getDragonIdentity().map(id -> id.entityUniqueId());

        double currentHealth = 0.0;
        double maxHealth = 200.0;

        if (dragonUuid.isPresent()) {
            try {
                Entity entity = Bukkit.getEntity(dragonUuid.get());
                if (entity instanceof EnderDragon dragon && dragon.isValid()) {
                    currentHealth = dragon.getHealth();
                    if (dragon.getAttribute(Attribute.MAX_HEALTH) != null) {
                        maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        PhaseDefinition currentPhase = session.getPhaseRuntime().getCurrentPhase();
        String activePhase = (currentPhase != null) ? currentPhase.id() : "ninguna";

        Optional<ParticipantSnapshot> topDamager = session.getCombatRuntime().getTopDamageParticipant();
        Optional<String> topDamagerName = topDamager.map(ParticipantSnapshot::lastKnownName);
        double topDamage = topDamager.map(ParticipantSnapshot::totalDamage).orElse(0.0);

        return new BattleStatusView(
                session.getBattleId(),
                session.getWorldName(),
                session.getState(),
                session.getConfigSnapshot().arenaDefinition().id(),
                session.getConfigSnapshot().dragonDefinition().id(),
                activePhase,
                dragonUuid,
                currentHealth,
                maxHealth,
                elapsed,
                session.getCombatRuntime().getParticipantCount(),
                topDamagerName,
                topDamage
        );
    }
}
