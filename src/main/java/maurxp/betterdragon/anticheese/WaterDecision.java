package maurxp.betterdragon.anticheese;

/**
 * Decisión adoptada por {@link WaterPolicy} ante intentos de colocación o flujo de agua.
 *
 * @author maurxp
 */
public enum WaterDecision {
    /** Se permite la colocación o flujo del fluido. */
    ALLOW,

    /** Se deniega y cancela la colocación o flujo del fluido para evitar trivializar mecánicas. */
    DENY
}
