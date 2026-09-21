package maurxp.betterdragon.config;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Instantánea inmutable que congela los parámetros y catálogo de recompensas
 * aplicables a una sesión de batalla.
 * <p>
 * Invariante: Una vez creada la batalla, este snapshot permanece congelado, garantizando
 * que recargas de configuración posteriores no alteren la distribución de la batalla activa.
 *
 * @param enabled                  si el sistema de recompensas está activo para la batalla
 * @param minParticipationPercent  porcentaje mínimo de daño requerido para elegibilidad (0.0 a 100.0)
 * @param participationPool        lista de ítems del pool que se redistribuyen proporcionalmente
 * @param slayerReward             definición de la recompensa de Slayer
 * @author maurxp
 */
public record RewardConfigurationSnapshot(
        boolean enabled,
        double minParticipationPercent,
        List<RewardItemDefinition> participationPool,
        SlayerRewardDefinition slayerReward
) implements Serializable {

    public static final boolean DEFAULT_ENABLED = false;
    public static final double DEFAULT_MIN_PARTICIPATION_PERCENT = 0.0;

    public RewardConfigurationSnapshot {
        if (Double.isNaN(minParticipationPercent) || Double.isInfinite(minParticipationPercent)
                || minParticipationPercent < 0.0 || minParticipationPercent > 100.0) {
            throw new IllegalArgumentException("minParticipationPercent debe ser un número finito entre 0.0 y 100.0. Valor: "
                    + minParticipationPercent);
        }
        Objects.requireNonNull(participationPool, "participationPool no puede ser nulo");
        Objects.requireNonNull(slayerReward, "slayerReward no puede ser nulo");
        participationPool = List.copyOf(participationPool);
    }

    /**
     * Alias de conveniencia para {@link #participationPool()}.
     *
     * @return lista de ítems a distribuir
     */
    public List<RewardItemDefinition> participantRewards() {
        return participationPool;
    }

    /**
     * Snapshot predeterminado con valores técnicos neutrales y seguros (deshabilitado por defecto sin ítems inventados).
     */
    public static RewardConfigurationSnapshot defaults() {
        return new RewardConfigurationSnapshot(
                DEFAULT_ENABLED,
                DEFAULT_MIN_PARTICIPATION_PERCENT,
                List.of(),
                SlayerRewardDefinition.defaults()
        );
    }

    /**
     * Snapshot deshabilitado.
     */
    public static RewardConfigurationSnapshot disabled() {
        return new RewardConfigurationSnapshot(
                false,
                DEFAULT_MIN_PARTICIPATION_PERCENT,
                List.of(),
                SlayerRewardDefinition.defaults()
        );
    }
}
