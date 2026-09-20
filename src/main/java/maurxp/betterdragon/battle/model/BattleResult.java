package maurxp.betterdragon.battle.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Representa el resultado final e inmutable de una batalla de BetterDragon.
 * <p>
 * Este objeto es thread-safe por diseño y puede ser transferido de forma segura
 * a hilos asíncronos para persistencia en SQLite o auditoría histórica.
 *
 * @param battleId            identificador único de la batalla
 * @param finalState          estado terminal alcanzado (COMPLETED o ABORTED)
 * @param startTime           momento de inicio de la sesión
 * @param endTime             momento de conclusión o terminación
 * @param slayerUniqueId      UUID del jugador con mayor daño acumulado (TOP_DAMAGE) si la batalla fue completada
 * @param slayerLastKnownName snapshot del nombre del Slayer al momento de la victoria
 * @param abortReason         motivo de cancelación si la batalla fue abortada
 * @author maurxp
 */
public record BattleResult(
        BattleId battleId,
        BattleState finalState,
        Instant startTime,
        Instant endTime,
        UUID slayerUniqueId,
        String slayerLastKnownName,
        String abortReason
) implements Serializable {

    public BattleResult {
        Objects.requireNonNull(battleId, "El battleId no puede ser nulo");
        Objects.requireNonNull(finalState, "El estado final no puede ser nulo");
        Objects.requireNonNull(startTime, "El startTime no puede ser nulo");
        Objects.requireNonNull(endTime, "El endTime no puede ser nulo");

        if (!finalState.isTerminal()) {
            throw new IllegalArgumentException("El estado de un BattleResult debe ser terminal (COMPLETED o ABORTED), pero fue: " + finalState);
        }

        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime no puede ser anterior a startTime");
        }
    }

    /**
     * Construye un resultado exitoso de batalla completada.
     *
     * @param battleId       identificador de batalla
     * @param startTime      inicio
     * @param endTime        fin
     * @param slayerId       UUID del jugador con TOP_DAMAGE
     * @param slayerName     nombre del Slayer
     * @return resultado inmutable de batalla completada
     */
    public static BattleResult completed(
            BattleId battleId,
            Instant startTime,
            Instant endTime,
            UUID slayerId,
            String slayerName
    ) {
        return new BattleResult(
                battleId,
                BattleState.COMPLETED,
                startTime,
                endTime,
                slayerId,
                slayerName,
                null
        );
    }

    /**
     * Construye un resultado de batalla abortada o cancelada.
     *
     * @param battleId  identificador de batalla
     * @param startTime inicio
     * @param endTime   fin
     * @param reason    motivo de la cancelación
     * @return resultado inmutable de batalla abortada
     */
    public static BattleResult aborted(
            BattleId battleId,
            Instant startTime,
            Instant endTime,
            String reason
    ) {
        return new BattleResult(
                battleId,
                BattleState.ABORTED,
                startTime,
                endTime,
                null,
                null,
                reason != null ? reason : "Cancelación sin motivo especificado"
        );
    }

    /**
     * Calcula la duración total de la batalla.
     *
     * @return duración entre inicio y fin
     */
    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    /**
     * Indica si la batalla concluyó con victoria exitosa.
     *
     * @return true si el estado final es COMPLETED
     */
    public boolean isVictory() {
        return finalState == BattleState.COMPLETED;
    }

    public Optional<UUID> getSlayerUniqueId() {
        return Optional.ofNullable(slayerUniqueId);
    }

    public Optional<String> getSlayerLastKnownName() {
        return Optional.ofNullable(slayerLastKnownName);
    }

    public Optional<String> getAbortReason() {
        return Optional.ofNullable(abortReason);
    }
}
