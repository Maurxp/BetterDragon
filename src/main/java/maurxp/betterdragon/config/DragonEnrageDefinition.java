package maurxp.betterdragon.config;

/**
 * Configuración inmutable para el modificador transversal de Soft Enrage.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Modificador Transversal:</b> No es una fase de combate (Combat Phase); opera de forma transversal
 *       sobre cualquier fase en curso cuando la salud desciende por debajo del umbral.</li>
 *   <li><b>Monotonicidad:</b> Una vez activado durante la batalla, permanece activo de forma unidireccional (false -> true).</li>
 *   <li><b>Aceleración de Habilidades:</b> Reduce el tiempo de recarga efectivo multiplicando el cooldown base
 *       por {@code cooldownMultiplier} sin alterar la definición original de la habilidad.</li>
 *   <li><b>Tuning Candidates:</b> Los valores predeterminados (0.20 y 0.75) son candidatos iniciales de balance
 *       sujetos a validación empírica en playtesting.</li>
 * </ul>
 *
 * @param enabled            si el Soft Enrage está habilitado en este perfil
 * @param threshold          umbral relativo de vida en el rango (0.0, 1.0] que dispara el Enrage
 * @param cooldownMultiplier factor multiplicador (> 0.0) aplicado al cooldown base de las habilidades
 * @author maurxp
 */
public record DragonEnrageDefinition(
        boolean enabled,
        double threshold,
        double cooldownMultiplier
) {
    /** Candidato de tuning provisional no validado experimentalmente (TUNING_CANDIDATE). */
    public static final double DEFAULT_THRESHOLD = 0.20;
    /** Candidato de tuning provisional no validado experimentalmente (TUNING_CANDIDATE). */
    public static final double DEFAULT_COOLDOWN_MULTIPLIER = 0.75;

    public DragonEnrageDefinition {
        if (!Double.isFinite(threshold) || threshold <= 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("enrage.threshold debe ser un número finito en (0.0, 1.0]. Se encontró: " + threshold);
        }
        if (!Double.isFinite(cooldownMultiplier) || cooldownMultiplier <= 0.0) {
            throw new IllegalArgumentException("enrage.cooldown_multiplier debe ser un número finito > 0.0. Se encontró: " + cooldownMultiplier);
        }
    }

    /**
     * Construye la configuración estándar por defecto para Soft Enrage.
     */
    public static DragonEnrageDefinition defaults() {
        return new DragonEnrageDefinition(true, DEFAULT_THRESHOLD, DEFAULT_COOLDOWN_MULTIPLIER);
    }

    /**
     * Construye una configuración con Soft Enrage deshabilitado.
     */
    public static DragonEnrageDefinition disabled() {
        return new DragonEnrageDefinition(false, DEFAULT_THRESHOLD, DEFAULT_COOLDOWN_MULTIPLIER);
    }
}
