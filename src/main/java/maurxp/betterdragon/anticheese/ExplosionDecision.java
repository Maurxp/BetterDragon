package maurxp.betterdragon.anticheese;

/**
 * Decisión formal inmutable adoptada por {@link ExplosionPolicy} frente a una explosión potencial o en curso.
 * <p>
 * Desacopla explícitamente:
 * <ul>
 *   <li>{@code shouldExplode}: si la detonación se permite físicamente o es cancelada en el evento de interacción.</li>
 *   <li>{@code allowBlockDamage}: si los bloques del terreno pueden ser destruidos ({@code blockList().clear()}).</li>
 *   <li>{@code allowPlayerDamage}: si los jugadores cercanos sufren daño de la explosión.</li>
 *   <li>{@code allowDragonDamage}: si el dragón u otras entidades del encuentro sufren daño por esta explosión.</li>
 * </ul>
 *
 * @param shouldExplode     si la explosión debe detonar físicamente
 * @param allowBlockDamage  si se permite la alteración/rotura de bloques de terreno
 * @param allowPlayerDamage si se permite aplicar daño por explosión a jugadores
 * @param allowDragonDamage si se permite aplicar daño por explosión al dragón de la batalla
 * @author maurxp
 */
public record ExplosionDecision(
        boolean shouldExplode,
        boolean allowBlockDamage,
        boolean allowPlayerDamage,
        boolean allowDragonDamage
) {

    /**
     * Decisión vanilla sin restricciones: detona, daña terreno, daña jugadores y daña al dragón.
     */
    public static ExplosionDecision allow() {
        return new ExplosionDecision(true, true, true, true);
    }

    /**
     * Decisión de neutralización total: la explosión es abortada o cancelada proactivamente.
     */
    public static ExplosionDecision block() {
        return new ExplosionDecision(false, false, false, false);
    }

    /**
     * Decisión de protección de arena: detona físicamente y daña al jugador causante/cercano,
     * pero suprime el 100% del daño a bloques de la arena y el daño al dragón.
     */
    public static ExplosionDecision protectArena() {
        return new ExplosionDecision(true, false, true, false);
    }
}
