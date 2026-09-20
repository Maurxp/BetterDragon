package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestor en memoria de las sesiones de batalla activas en el servidor.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Main-Thread Confinement:</b> Emplea estructuras estándar de Java ({@link HashMap})
 *       sin sobrecarga de sincronización concurrente, ya que se ejecuta en el hilo principal.</li>
 *   <li><b>Invariante de Contexto:</b> Garantiza que no coexistan dos sesiones activas
 *       simultáneamente en el mismo mundo del juego.</li>
 *   <li><b>Alcance Acotado:</b> No gestiona combate, persistencia, recompensas ni comandos.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BattleSessionManager {

    private final Map<BattleId, BattleSession> sessionsById = new HashMap<>();
    private final Map<String, BattleId> activeSessionByWorld = new HashMap<>();
    private final Map<UUID, BattleId> activeSessionByWorldId = new HashMap<>();

    /**
     * Registra una nueva sesión de batalla en el administrador.
     *
     * @param session sesión a registrar
     * @throws IllegalStateException si ya existe una batalla activa para el mismo mundo o ID
     */
    public void register(BattleSession session) {
        Objects.requireNonNull(session, "La sesión no puede ser nula");

        BattleId id = session.getBattleId();
        String world = session.getWorldName();
        UUID worldId = session.getWorldUniqueId();

        if (sessionsById.containsKey(id)) {
            throw new IllegalStateException("Ya existe una sesión registrada con el ID: " + id);
        }

        if (hasActiveSession(world)) {
            throw new IllegalStateException("Ya existe una sesión de batalla activa para el mundo: " + world);
        }

        sessionsById.put(id, session);
        activeSessionByWorld.put(world, id);
        activeSessionByWorldId.put(worldId, id);
    }

    /**
     * Obtiene una sesión por su BattleId.
     *
     * @param battleId identificador de la batalla
     * @return Optional con la sesión si existe
     */
    public Optional<BattleSession> getSession(BattleId battleId) {
        if (battleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsById.get(battleId));
    }

    /**
     * Obtiene la sesión activa para el mundo indicado por nombre.
     *
     * @param worldName nombre del mundo
     * @return Optional con la sesión activa si existe
     */
    public Optional<BattleSession> getActiveSessionByWorld(String worldName) {
        if (worldName == null) {
            return Optional.empty();
        }
        BattleId id = activeSessionByWorld.get(worldName);
        if (id == null) {
            return Optional.empty();
        }
        BattleSession session = sessionsById.get(id);
        return (session != null && !session.isTerminal()) ? Optional.of(session) : Optional.empty();
    }

    /**
     * Obtiene la sesión activa para el mundo indicado por UUID.
     *
     * @param worldUniqueId UUID del mundo
     * @return Optional con la sesión activa si existe
     */
    public Optional<BattleSession> getActiveSessionByWorld(UUID worldUniqueId) {
        if (worldUniqueId == null) {
            return Optional.empty();
        }
        BattleId id = activeSessionByWorldId.get(worldUniqueId);
        if (id == null) {
            return Optional.empty();
        }
        BattleSession session = sessionsById.get(id);
        return (session != null && !session.isTerminal()) ? Optional.of(session) : Optional.empty();
    }

    /**
     * Comprueba si existe una sesión activa registrada para el mundo indicado.
     *
     * @param worldName nombre del mundo
     * @return true si hay una sesión activa no terminal
     */
    public boolean hasActiveSession(String worldName) {
        if (worldName == null) {
            return false;
        }
        BattleId id = activeSessionByWorld.get(worldName);
        if (id == null) {
            return false;
        }
        BattleSession session = sessionsById.get(id);
        return session != null && !session.isTerminal();
    }

    /**
     * Remueve una sesión finalizada o cancelada del gestor de memoria.
     *
     * @param battleId identificador de la sesión a remover
     * @return la sesión removida, si existía
     */
    public Optional<BattleSession> remove(BattleId battleId) {
        if (battleId == null) {
            return Optional.empty();
        }

        BattleSession removed = sessionsById.remove(battleId);
        if (removed != null) {
            activeSessionByWorld.remove(removed.getWorldName());
            activeSessionByWorldId.remove(removed.getWorldUniqueId());
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Devuelve una vista de solo lectura de todas las sesiones registradas.
     *
     * @return mapa inmutable de sesiones por ID
     */
    public Map<BattleId, BattleSession> getAllSessions() {
        return Collections.unmodifiableMap(sessionsById);
    }

    /**
     * Devuelve el total de sesiones registradas.
     *
     * @return cantidad de sesiones en memoria
     */
    public int getSessionCount() {
        return sessionsById.size();
    }
}
