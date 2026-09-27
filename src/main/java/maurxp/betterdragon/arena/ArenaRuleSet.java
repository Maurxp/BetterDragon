package maurxp.betterdragon.arena;

import maurxp.betterdragon.anticheese.ExplosionPolicyType;

import java.util.Objects;

/**
 * Conjunto inmutable de reglas geométricas, ambientales y anti-cheese aplicables a una arena.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Aislamiento Total:</b> Concentra exclusivamente reglas de arena (límites, agua, anti-túnel,
 *       política de explosiones y void tether), sin mezclarse con el motor de habilidades ni el cálculo de recompensas.</li>
 *   <li><b>Separación de Detección y Consecuencia:</b> Modela si una regla está activa para su consulta,
 *       sin inventar mecánicas destructivas arbitrarias ni teletransportaciones prematuras.</li>
 *   <li><b>Valores Predeterminados Seguros:</b> Provee defaults de producción recomendados.</li>
 * </ul>
 *
 * @param waterAllowed       si se permite la colocación y flujo de agua dentro de la arena (false = water denial activo)
 * @param boundaryEnabled    si los límites perimetrales de la arena están habilitados para detección
 * @param antiTunnelEnabled  si la regla de anti-túnel está habilitada para evaluación
 * @param explosionPolicy    política de neutralización de explosiones (ALLOW, BLOCK, PROTECT_ARENA)
 * @param voidTetherEnabled  si el mecanismo de Void Tether está habilitado para recuperar participantes
 * @author maurxp
 */
public record ArenaRuleSet(
        boolean waterAllowed,
        boolean boundaryEnabled,
        boolean antiTunnelEnabled,
        ExplosionPolicyType explosionPolicy,
        boolean voidTetherEnabled
) {

    public static final boolean DEFAULT_WATER_ALLOWED = false;
    public static final boolean DEFAULT_BOUNDARY_ENABLED = true;
    public static final boolean DEFAULT_ANTI_TUNNEL_ENABLED = true;
    public static final ExplosionPolicyType DEFAULT_EXPLOSION_POLICY = ExplosionPolicyType.PROTECT_ARENA;
    public static final boolean DEFAULT_VOID_TETHER_ENABLED = true;

    public ArenaRuleSet {
        explosionPolicy = explosionPolicy != null ? explosionPolicy : DEFAULT_EXPLOSION_POLICY;
    }

    /**
     * Constructor de conveniencia retrocompatible con la firma original de tres parámetros.
     *
     * @param waterAllowed      si se permite agua
     * @param boundaryEnabled   si se habilitan límites
     * @param antiTunnelEnabled si se habilita anti-túnel
     */
    public ArenaRuleSet(boolean waterAllowed, boolean boundaryEnabled, boolean antiTunnelEnabled) {
        this(waterAllowed, boundaryEnabled, antiTunnelEnabled, DEFAULT_EXPLOSION_POLICY, DEFAULT_VOID_TETHER_ENABLED);
    }

    /**
     * Retorna el conjunto de reglas estándar predeterminado.
     *
     * @return reglas por defecto
     */
    public static ArenaRuleSet defaults() {
        return new ArenaRuleSet(
                DEFAULT_WATER_ALLOWED,
                DEFAULT_BOUNDARY_ENABLED,
                DEFAULT_ANTI_TUNNEL_ENABLED,
                DEFAULT_EXPLOSION_POLICY,
                DEFAULT_VOID_TETHER_ENABLED
        );
    }

    /**
     * Indica si el agua está permitida dentro de la arena.
     *
     * @return true si el agua está permitida
     */
    public boolean isWaterAllowed() {
        return waterAllowed;
    }

    /**
     * Indica si la regla de denegación de agua (water denial) está activa.
     *
     * @return true si el agua está denegada (!waterAllowed)
     */
    public boolean isWaterDenialEnabled() {
        return !waterAllowed;
    }

    /**
     * Indica si la detección de límites de la arena está activa.
     *
     * @return true si los límites están activos
     */
    public boolean isBoundaryEnabled() {
        return boundaryEnabled;
    }

    /**
     * Indica si la regla de anti-túnel está activa.
     *
     * @return true si anti-túnel está activo
     */
    public boolean isAntiTunnelEnabled() {
        return antiTunnelEnabled;
    }

    /**
     * Retorna la política de explosiones de la arena.
     *
     * @return tipo de política de explosiones
     */
    public ExplosionPolicyType getExplosionPolicy() {
        return explosionPolicy;
    }

    /**
     * Indica si el mecanismo de Void Tether está habilitado.
     *
     * @return true si Void Tether está habilitado
     */
    public boolean isVoidTetherEnabled() {
        return voidTetherEnabled;
    }
}
