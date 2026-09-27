package maurxp.betterdragon.anticheese;

/**
 * Modo de política de explosiones de la arena para mitigar estrategias de combate no autorizadas (cheese).
 * <p>
 * Semántica:
 * <ul>
 *   <li>{@link #ALLOW}: Comportamiento vanilla sin restricciones. Las explosiones detonan, dañan bloques,
 *       dañan jugadores y dañan al dragón.</li>
 *   <li>{@link #BLOCK}: Neutralización proactiva total. Se cancela la interacción con camas y anclas de respawn,
 *       impidiendo que la explosión se produzca.</li>
 *   <li>{@link #PROTECT_ARENA}: Modo equilibrado de protección de arena. La explosión detona (efecto sensorial y
 *       daño a jugadores), pero se suprime completamente la destrucción de bloques de terreno y el daño al dragón.</li>
 * </ul>
 *
 * @author maurxp
 */
public enum ExplosionPolicyType {
    ALLOW,
    BLOCK,
    PROTECT_ARENA
}
