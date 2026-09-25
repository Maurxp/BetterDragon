package maurxp.betterdragon.config;

import java.util.Optional;

/**
 * Representa los atributos base configurables del Ender Dragon en Paper 26.1.2.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Tipado Fuerte e Inmutable:</b> Valores numéricos estrictamente validados (sin NaN ni Infinity).</li>
 *   <li><b>Exclusión de Attribute.SCALE:</b> El motor de Minecraft 26.1.2 no soporta confiablemente
 *       el escalado multipart del Ender Dragon (MC-267372).</li>
 *   <li><b>Compatibilidad Nativa:</b> Solo modela atributos soportados nativamente por Paper API.</li>
 * </ul>
 *
 * @param maxHealth     salud máxima base (obligatoria, > 0.0)
 * @param movementSpeed velocidad de movimiento (opcional, > 0.0)
 * @param followRange   rango de seguimiento de la IA en bloques (opcional, > 0.0)
 * @param attackDamage  daño base de ataque cuerpo a cuerpo (opcional, >= 0.0)
 * @author maurxp
 */
public record DragonAttributes(
        double maxHealth,
        Double rawMovementSpeed,
        Double rawFollowRange,
        Double rawAttackDamage
) {

    public static final double DEFAULT_MAX_HEALTH = 200.0;

    /**
     * Valor indicativo de referencia para velocidad de vuelo vanilla (0.6).
     * Informativo; en {@link #defaults()} los atributos opcionales permanecen sin override (null).
     */
    public static final double DEFAULT_MOVEMENT_SPEED = 0.6;

    /**
     * Valor indicativo de referencia para rango de seguimiento vanilla (128.0 bloques).
     * Informativo; en {@link #defaults()} los atributos opcionales permanecen sin override (null).
     */
    public static final double DEFAULT_FOLLOW_RANGE = 128.0;

    public DragonAttributes {
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0) {
            throw new IllegalArgumentException("maxHealth debe ser un número finito y estrictamente mayor que 0.0. Recibido: " + maxHealth);
        }

        if (rawMovementSpeed != null) {
            if (!Double.isFinite(rawMovementSpeed) || rawMovementSpeed <= 0.0) {
                throw new IllegalArgumentException("movementSpeed debe ser un número finito > 0.0. Recibido: " + rawMovementSpeed);
            }
        }
        if (rawFollowRange != null) {
            if (!Double.isFinite(rawFollowRange) || rawFollowRange <= 0.0) {
                throw new IllegalArgumentException("followRange debe ser un número finito > 0.0. Recibido: " + rawFollowRange);
            }
        }
        if (rawAttackDamage != null) {
            if (!Double.isFinite(rawAttackDamage) || rawAttackDamage < 0.0) {
                throw new IllegalArgumentException("attackDamage debe ser un número finito >= 0.0. Recibido: " + rawAttackDamage);
            }
        }
    }

    public Optional<Double> movementSpeed() {
        return Optional.ofNullable(rawMovementSpeed);
    }

    public Optional<Double> followRange() {
        return Optional.ofNullable(rawFollowRange);
    }

    public Optional<Double> attackDamage() {
        return Optional.ofNullable(rawAttackDamage);
    }

    /**
     * Constructor de conveniencia con solo salud máxima.
     */
    public DragonAttributes(double maxHealth) {
        this(maxHealth, null, null, null);
    }

    /**
     * Genera los atributos base estándar de Minecraft Vanilla.
     *
     * @return atributos por defecto con 200.0 HP y atributos opcionales sin override
     */
    public static DragonAttributes defaults() {
        return new DragonAttributes(DEFAULT_MAX_HEALTH, null, null, null);
    }
}
