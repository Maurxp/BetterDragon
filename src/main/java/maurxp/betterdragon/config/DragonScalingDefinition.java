package maurxp.betterdragon.config;

import java.util.Objects;

/**
 * Configuración inmutable del modelo de escalado de un dragón según jugadores en arena.
 *
 * @param enabled             si el escalado dinámico/inicial está activado
 * @param mode                modo algorítmico de escalado (NONE, LINEAR)
 * @param healthPerPlayer     incremento porcentual de salud por jugador adicional (alpha >= 0.0)
 * @param maxHealthMultiplier multiplicador máximo permitido como límite superior (cap >= 1.0)
 * @author maurxp
 */
public record DragonScalingDefinition(
        boolean enabled,
        ScalingMode mode,
        double healthPerPlayer,
        double maxHealthMultiplier
) {

    /**
     * Incremento provisional de salud por jugador adicional (+25%).
     * <p>
     * <b>Estatus:</b> {@code TUNING_CANDIDATE}. Valor inicial de referencia de ingeniería,
     * no derivado de Vanilla ni validado empíricamente; sujeto a balance en pruebas de juego.
     */
    public static final double DEFAULT_HEALTH_PER_PLAYER = 0.25;

    /**
     * Multiplicador máximo provisional de escalado de salud (3.0x = 300% de la vida base).
     * <p>
     * <b>Estatus:</b> {@code TUNING_CANDIDATE}. Límite superior provisional para evitar combates
     * desproporcionados en incursiones masivas; sujeto a calibración posterior.
     */
    public static final double DEFAULT_MAX_HEALTH_MULTIPLIER = 3.0;

    public DragonScalingDefinition {
        if (mode == null) {
            mode = enabled ? ScalingMode.LINEAR : ScalingMode.NONE;
        } else if (enabled && mode == ScalingMode.NONE) {
            throw new IllegalArgumentException("El modo de escalado no puede ser NONE si enabled es true");
        }
        if (!Double.isFinite(healthPerPlayer) || healthPerPlayer < 0.0) {
            throw new IllegalArgumentException("healthPerPlayer debe ser un número finito >= 0.0. Recibido: " + healthPerPlayer);
        }
        if (!Double.isFinite(maxHealthMultiplier) || maxHealthMultiplier < 1.0) {
            throw new IllegalArgumentException("maxHealthMultiplier debe ser un número finito >= 1.0. Recibido: " + maxHealthMultiplier);
        }
    }

    /**
     * Genera la configuración estándar de escalado recomendada por BetterDragon (desactivada por defecto).
     */
    public static DragonScalingDefinition defaults() {
        return new DragonScalingDefinition(false, ScalingMode.LINEAR, DEFAULT_HEALTH_PER_PLAYER, DEFAULT_MAX_HEALTH_MULTIPLIER);
    }

    /**
     * Genera una configuración de escalado formalmente deshabilitada.
     */
    public static DragonScalingDefinition disabled() {
        return new DragonScalingDefinition(false, ScalingMode.NONE, 0.0, 1.0);
    }

    /**
     * Genera una configuración de escalado lineal activo.
     */
    public static DragonScalingDefinition linear(double healthPerPlayer, double maxHealthMultiplier) {
        return new DragonScalingDefinition(true, ScalingMode.LINEAR, healthPerPlayer, maxHealthMultiplier);
    }
}
