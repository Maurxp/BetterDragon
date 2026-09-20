package maurxp.betterdragon.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * Estado mutable en tiempo de ejecución de un participante de combate.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Main-Thread Confined:</b> Vive y se muta exclusivamente en el hilo principal
 *       del servidor; no requiere sincronización, locks ni Atomic*.</li>
 *   <li><b>Preservación de historicalName:</b> El nombre asignado en el primer impacto
 *       es inmutable. Los impactos posteriores solo actualizan {@code lastKnownName}.</li>
 *   <li><b>Sin Colecciones Infinitas:</b> Mantiene acumulados y secuencias de hit sin
 *       almacenar listas de hits individuales ($O(1)$ por participante).</li>
 * </ul>
 *
 * @author maurxp
 */
public class ParticipantCombatState {

    private final UUID playerId;
    private final String historicalName;
    private String lastKnownName;
    private double totalDamage;
    private final long firstHitSequence;
    private long lastHitSequence;
    private long lastActivityTick;

    public ParticipantCombatState(UUID playerId, String initialName, double initialDamage, long sequence, long currentTick) {
        this.playerId = Objects.requireNonNull(playerId, "playerId no puede ser nulo");
        this.historicalName = Objects.requireNonNull(initialName, "initialName no puede ser nulo");
        this.lastKnownName = initialName;

        if (Double.isNaN(initialDamage) || Double.isInfinite(initialDamage) || initialDamage <= 0) {
            throw new IllegalArgumentException("El daño inicial debe ser finito y mayor que 0. Valor: " + initialDamage);
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("La secuencia inicial no puede ser negativa: " + sequence);
        }

        this.totalDamage = initialDamage;
        this.firstHitSequence = sequence;
        this.lastHitSequence = sequence;
        this.lastActivityTick = currentTick;
    }

    /**
     * Registra un nuevo impacto válido del participante, actualizando el daño total,
     * el nombre conocido más reciente, la secuencia del último hit y el tick de actividad.
     * <p>
     * {@code historicalName} <b>nunca</b> es modificado por esta operación.
     *
     * @param currentName nombre del jugador en el momento del impacto
     * @param damage      daño infligido (debe ser > 0 y finito)
     * @param sequence    secuencia monotónica asignada al impacto
     * @param currentTick tick del servidor
     */
    public void recordHit(String currentName, double damage, long sequence, long currentTick) {
        if (Double.isNaN(damage) || Double.isInfinite(damage) || damage <= 0) {
            throw new IllegalArgumentException("El daño debe ser finito y mayor que 0. Valor: " + damage);
        }
        if (sequence < this.lastHitSequence) {
            throw new IllegalArgumentException("La secuencia (" + sequence + ") no puede ser menor a la última registrada (" + this.lastHitSequence + ")");
        }

        this.totalDamage += damage;
        if (currentName != null && !currentName.isBlank()) {
            this.lastKnownName = currentName;
        }
        this.lastHitSequence = sequence;
        this.lastActivityTick = currentTick;
    }

    /**
     * Exporta el estado actual a una instantánea inmutable segura para consumo externo.
     *
     * @return snapshot inmutable del participante
     */
    public ParticipantSnapshot toSnapshot() {
        return new ParticipantSnapshot(
                this.playerId,
                this.historicalName,
                this.lastKnownName,
                this.totalDamage,
                this.firstHitSequence,
                this.lastHitSequence,
                this.lastActivityTick
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getHistoricalName() {
        return historicalName;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public long getFirstHitSequence() {
        return firstHitSequence;
    }

    public long getLastHitSequence() {
        return lastHitSequence;
    }

    public long getLastActivityTick() {
        return lastActivityTick;
    }
}
