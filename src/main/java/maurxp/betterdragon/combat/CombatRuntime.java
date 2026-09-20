package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.bukkit.Bukkit;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Runtime de combate de BetterDragon confinado al hilo principal del servidor.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Registrar participantes legítimos exclusivamente mediante daño válido.</li>
 *   <li>Acumular daño por jugador con precisión {@code double}.</li>
 *   <li>Gestionar el contador monotónico {@code hitSequence} ($0, 1, 2, \dots$).</li>
 *   <li>Preservar {@code historicalName} y actualizar {@code lastKnownName}.</li>
 *   <li>Calcular y consultar {@code TOP_DAMAGE} con desempate determinista.</li>
 *   <li>Emitir {@link BetterDragonDamageEvent} con semántica de cancelación limpia.</li>
 *   <li>Proveer instantáneas inmutables ({@link CombatSnapshot}) sin exponer mapas mutables.</li>
 * </ul>
 * <p>
 * Este componente <b>NO</b> gestiona el ciclo de vida de la batalla, ni fases, ni recompensas,
 * ni persistencia SQLite, ni leaderboard, ni portales.
 *
 * @author maurxp
 */
public class CombatRuntime {

    private final BattleSession session;
    private final DamageEventDispatcher eventDispatcher;
    private final Map<UUID, ParticipantCombatState> participants;
    private long hitSequence;

    /**
     * Comparador determinista para determinar TOP_DAMAGE:
     * 1. Mayor totalDamage.
     * 2. Menor firstHitSequence (quien golpeó primero / aportó antes).
     */
    private static final Comparator<ParticipantSnapshot> TOP_DAMAGE_COMPARATOR = Comparator
            .comparingDouble(ParticipantSnapshot::totalDamage)
            .thenComparing(Comparator.comparingLong(ParticipantSnapshot::firstHitSequence).reversed());

    public CombatRuntime(BattleSession session, DamageEventDispatcher eventDispatcher) {
        this.session = Objects.requireNonNull(session, "session no puede ser nula");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher no puede ser nulo");
        this.participants = new HashMap<>();
        this.hitSequence = 0L;
    }

    public CombatRuntime(BattleSession session) {
        this(session, createDefaultDispatcher());
    }

    private static DamageEventDispatcher createDefaultDispatcher() {
        return event -> {
            try {
                if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                    Bukkit.getPluginManager().callEvent(event);
                    return !event.isCancelled();
                }
            } catch (Throwable ignored) {
                // Fallback seguro si se ejecuta fuera de un entorno Bukkit inicializado
            }
            return true;
        };
    }

    /**
     * Registra un impacto de daño realizado por un jugador contra el BetterDragon de esta sesión.
     * <p>
     * Flujo de ejecución:
     * <ol>
     *   <li>Verifica que la sesión se encuentre en estado {@link BattleState#ACTIVE}.</li>
     *   <li>Valida que {@code playerId} no sea nulo y que {@code damage} sea finito y &gt; 0.</li>
     *   <li>Calcula la secuencia tentativa {@code prospectiveSequence = hitSequence + 1}.</li>
     *   <li>Emite el evento {@link BetterDragonDamageEvent}.</li>
     *   <li>Si el evento es cancelado, no consume secuencia ni muta el estado.</li>
     *   <li>Si es aceptado, actualiza {@code hitSequence}, acumula daño y actualiza el participante.</li>
     * </ol>
     *
     * @param playerId    UUID del jugador atacante
     * @param currentName nombre del jugador al momento del impacto
     * @param damage      daño numérico procesado
     * @param currentTick tick del servidor al momento del impacto
     * @return snapshot del participante actualizado, o {@code Optional.empty()} si fue rechazado o cancelado
     */
    public Optional<ParticipantSnapshot> recordDamage(UUID playerId, String currentName, double damage, long currentTick) {
        // 1. Validar estado operativo de la sesión (solo ACTIVE acepta daño normal)
        if (session.getState() != BattleState.ACTIVE) {
            return Optional.empty();
        }

        // 2. Validaciones de dominio
        if (playerId == null) {
            return Optional.empty();
        }
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage <= 0.0) {
            return Optional.empty();
        }

        String safeName = (currentName != null && !currentName.isBlank()) ? currentName : "Unknown";

        // 3. Obtener UUID del dragón asociado
        UUID dragonId = session.getDragonIdentity()
                .map(DragonIdentity::entityUniqueId)
                .orElse(playerId); // Fallback seguro en casos límite

        // 4. Secuencia tentativa (no consumida aún)
        long prospectiveSequence = this.hitSequence + 1L;

        // 5. Emitir evento cancelable
        BetterDragonDamageEvent event = new BetterDragonDamageEvent(
                this.session,
                dragonId,
                playerId,
                safeName,
                damage,
                prospectiveSequence
        );

        boolean accepted = this.eventDispatcher.dispatch(event);
        if (!accepted) {
            // Cancelado por listener externo: no consumir secuencia ni modificar estado
            return Optional.empty();
        }

        // 6. Confirmar secuencia
        this.hitSequence = prospectiveSequence;

        // 7. Actualizar o crear estado de participante
        ParticipantCombatState participant = this.participants.get(playerId);
        if (participant == null) {
            participant = new ParticipantCombatState(
                    playerId,
                    safeName,
                    damage,
                    this.hitSequence,
                    currentTick
            );
            this.participants.put(playerId, participant);
        } else {
            participant.recordHit(safeName, damage, this.hitSequence, currentTick);
        }

        return Optional.of(participant.toSnapshot());
    }

    /**
     * Consulta la instantánea de un participante por su UUID.
     *
     * @param playerId UUID del jugador
     * @return instantánea inmutable del participante, o empty si no ha participado
     */
    public Optional<ParticipantSnapshot> getParticipant(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        ParticipantCombatState state = this.participants.get(playerId);
        return state != null ? Optional.of(state.toSnapshot()) : Optional.empty();
    }

    /**
     * Consulta todos los participantes actuales como una lista inmutable de instantáneas.
     *
     * @return lista inmutable de {@link ParticipantSnapshot}
     */
    public List<ParticipantSnapshot> getParticipants() {
        return this.participants.values().stream()
                .map(ParticipantCombatState::toSnapshot)
                .toList();
    }

    /**
     * Determina si un jugador es participante registrado con al menos un impacto válido.
     *
     * @param playerId UUID del jugador
     * @return true si es participante
     */
    public boolean isParticipant(UUID playerId) {
        return playerId != null && this.participants.containsKey(playerId);
    }

    /**
     * Retorna el daño total acumulado por un jugador específico.
     *
     * @param playerId UUID del jugador
     * @return daño total acumulado, o 0.0 si no ha participado
     */
    public double getTotalDamage(UUID playerId) {
        if (playerId == null) {
            return 0.0;
        }
        ParticipantCombatState state = this.participants.get(playerId);
        return state != null ? state.getTotalDamage() : 0.0;
    }

    /**
     * Retorna la suma del daño acumulado por todos los participantes.
     *
     * @return daño global total
     */
    public double getTotalDamageAll() {
        double sum = 0.0;
        for (ParticipantCombatState state : this.participants.values()) {
            sum += state.getTotalDamage();
        }
        return sum;
    }

    /**
     * Consulta el participante con mayor daño total acumulado (TOP_DAMAGE).
     * <p>
     * Criterio de Desempate Determinista:
     * Si dos o más jugadores tienen exactamente el mismo daño acumulado, el desempate
     * se resuelve a favor del jugador con menor {@code firstHitSequence} (quien aportó primero).
     *
     * @return instantánea del Slayer (TOP_DAMAGE), o empty si no hay participantes
     */
    public Optional<ParticipantSnapshot> getTopDamageParticipant() {
        if (this.participants.isEmpty()) {
            return Optional.empty();
        }

        return this.participants.values().stream()
                .map(ParticipantCombatState::toSnapshot)
                .max(TOP_DAMAGE_COMPARATOR);
    }

    /**
     * Retorna el valor actual del contador monotónico de secuencias de impactos.
     *
     * @return secuencia monotónica
     */
    public long getHitSequence() {
        return this.hitSequence;
    }

    /**
     * Retorna la cantidad total de participantes registrados.
     *
     * @return número de participantes
     */
    public int getParticipantCount() {
        return this.participants.size();
    }

    /**
     * Genera una instantánea inmutable completa del estado de combate para exportación
     * a sistemas externos (resultados de batalla, recompensas, leaderboard, persistencia).
     *
     * @return instantánea inmutable {@link CombatSnapshot}
     */
    public CombatSnapshot createSnapshot() {
        return new CombatSnapshot(
                this.session.getBattleId(),
                getParticipants(),
                this.hitSequence,
                getTotalDamageAll()
        );
    }

    public BattleSession getSession() {
        return session;
    }
}
