package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.model.BattleId;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio orquestador del mecanismo de Void Tether para la contención perimetral de participantes
 * y prevención de escapes deliberados o caídas al vacío.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Alcance Exclusivo a Participantes:</b> Solo interviene sobre jugadores registrados con daño válido
 *       en el {@link maurxp.betterdragon.combat.CombatRuntime}; los espectadores o transeúntes no son afectados.</li>
 *   <li><b>Estrategia de Retorno Determinista:</b> Delega en {@link SafeReturnLocationStrategy} para resolver una
 *       ubicación con espacio, soporte sólido y libre de peligros.</li>
 *   <li><b>Prevención de Loops:</b> Aplica un enfriamiento (debounce) para impedir ciclos infinitos de teletransporte
 *       en bordes conflictivos.</li>
 *   <li><b>Resiliencia de Ciclo de Vida:</b> Desconexiones, cambios de mundo y finalización de batalla limpian
 *       el estado en memoria sin retener referencias directas a entidades vivas.</li>
 * </ul>
 *
 * @author maurxp
 */
public class VoidTetherService {

    public static final long LOOP_PREVENTION_COOLDOWN_MS = 1000L;

    private final BattleSessionManager sessionManager;
    private final BoundaryPolicy boundaryPolicy;
    private final SafeReturnLocationStrategy safeReturnStrategy;

    private final Map<UUID, Location> lastKnownValidPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTetherTimeMs = new ConcurrentHashMap<>();

    public VoidTetherService(
            BattleSessionManager sessionManager,
            BoundaryPolicy boundaryPolicy,
            SafeReturnLocationStrategy safeReturnStrategy
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.boundaryPolicy = Objects.requireNonNull(boundaryPolicy, "boundaryPolicy no puede ser nula");
        this.safeReturnStrategy = Objects.requireNonNull(safeReturnStrategy, "safeReturnStrategy no puede ser nula");
    }

    /**
     * Evalúa el desplazamiento de un jugador y aplica el Void Tether si se detecta caída al vacío o salida de arena.
     *
     * @param player jugador evaluado
     * @param from   ubicación previa
     * @param to     ubicación destino
     * @return true si el jugador fue recuperado y teletransportado por Void Tether; false en caso contrario
     */
    public boolean checkAndEnforce(Player player, Location from, Location to) {
        if (player == null || to == null || to.getWorld() == null) {
            return false;
        }

        // 1. Resolver sesión activa para el mundo
        Optional<BattleSession> sessionOpt = resolveActiveSession(to.getWorld());
        if (sessionOpt.isEmpty()) {
            return false;
        }

        BattleSession session = sessionOpt.get();

        // 2. Verificar regla void_tether de la arena
        if (!session.getArena().rules().isVoidTetherEnabled()) {
            return false;
        }

        // 3. Verificar si el jugador es un participante legítimo de combate
        UUID playerId = player.getUniqueId();
        if (!session.getCombatRuntime().isParticipant(playerId)) {
            return false;
        }

        // 4. Evaluación espacial: ¿vacío o fuera de límites de arena?
        boolean isVoidFall = to.getY() <= 0.0 || to.getY() < session.getArena().bounds().minY();
        boolean isOutOfBounds = boundaryPolicy.isBreach(to, session.getArena().bounds());

        if (!isVoidFall && !isOutOfBounds) {
            // El participante está dentro de la arena: si está en zona segura interior, actualizar posición válida
            if (boundaryPolicy.evaluateZone(to, session.getArena().bounds()) == BoundaryZone.INSIDE && to.getY() >= SafeReturnLocationStrategy.MIN_SAFE_Y) {
                lastKnownValidPositions.put(playerId, to.clone());
            }
            return false;
        }

        // 5. Brecha o caída detectada -> Comprobar loop prevention
        long now = System.currentTimeMillis();
        Long lastTether = lastTetherTimeMs.get(playerId);
        if (lastTether != null && (now - lastTether) < LOOP_PREVENTION_COOLDOWN_MS) {
            return false;
        }

        // 6. Determinar ubicación segura de retorno
        Location safeLocation = safeReturnStrategy.resolveSafeLocation(
                player,
                session,
                lastKnownValidPositions.get(playerId)
        );

        if (safeLocation == null) {
            return false;
        }

        // 7. Ejecutar recuperación
        player.teleport(safeLocation);
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0, 0, 0));

        lastTetherTimeMs.put(playerId, now);
        lastKnownValidPositions.put(playerId, safeLocation.clone());

        try {
            player.playSound(safeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        } catch (Throwable ignored) {
        }

        return true;
    }

    /**
     * Interviene ante daño directo por vacío contra un participante activo para rescatarlo.
     *
     * @param player jugador afectado
     * @return true si el jugador fue rescatado
     */
    public boolean rescueFromVoid(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }

        Optional<BattleSession> sessionOpt = resolveActiveSession(player.getWorld());
        if (sessionOpt.isEmpty()) {
            return false;
        }

        BattleSession session = sessionOpt.get();
        if (!session.getArena().rules().isVoidTetherEnabled()) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!session.getCombatRuntime().isParticipant(playerId)) {
            return false;
        }

        Location safeLocation = safeReturnStrategy.resolveSafeLocation(
                player,
                session,
                lastKnownValidPositions.get(playerId)
        );

        if (safeLocation == null) {
            return false;
        }

        player.teleport(safeLocation);
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0, 0, 0));

        lastTetherTimeMs.put(playerId, System.currentTimeMillis());
        lastKnownValidPositions.put(playerId, safeLocation.clone());

        try {
            player.playSound(safeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        } catch (Throwable ignored) {
        }

        return true;
    }

    /**
     * Limpia el registro de rastreo temporal cuando un participante se desconecta.
     */
    public void onPlayerQuit(UUID playerId) {
        if (playerId != null) {
            lastKnownValidPositions.remove(playerId);
            lastTetherTimeMs.remove(playerId);
        }
    }

    /**
     * Limpia el registro de rastreo temporal cuando un participante cambia de mundo.
     */
    public void onPlayerChangeWorld(UUID playerId) {
        if (playerId != null) {
            lastKnownValidPositions.remove(playerId);
            lastTetherTimeMs.remove(playerId);
        }
    }

    /**
     * Limpia todas las referencias de rastreo al finalizar una batalla.
     */
    public void onBattleEnd(BattleId battleId) {
        lastKnownValidPositions.clear();
        lastTetherTimeMs.clear();
    }

    public Optional<Location> getLastKnownValidPosition(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return Optional.ofNullable(lastKnownValidPositions.get(playerId));
    }

    public Optional<Long> getLastTetherTime(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return Optional.ofNullable(lastTetherTimeMs.get(playerId));
    }

    private Optional<BattleSession> resolveActiveSession(org.bukkit.World world) {
        Optional<BattleSession> sessionOpt = sessionManager.getActiveSessionByWorld(world.getUID());
        if (sessionOpt.isPresent() && sessionOpt.get().isActive()) {
            return sessionOpt;
        }
        sessionOpt = sessionManager.getActiveSessionByWorld(world.getName());
        return (sessionOpt.isPresent() && sessionOpt.get().isActive()) ? sessionOpt : Optional.empty();
    }
}
