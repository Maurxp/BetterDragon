package maurxp.betterdragon.anticheese;

/**
 * Transición perimetral de un participante entre dos zonas espaciales consecutivas.
 *
 * @author maurxp
 */
public enum BoundaryTransition {
    /** Movimiento continuo en el interior seguro de la arena. */
    INSIDE_TO_INSIDE,

    /** El participante se aproxima al borde perimetral entrando en zona de advertencia. */
    INSIDE_TO_NEAR_BOUNDARY,

    /** El participante traspasa el límite de la arena hacia el exterior (brecha perimetral). */
    NEAR_BOUNDARY_TO_OUTSIDE,

    /** El participante traspasa directamente el límite de la arena hacia el exterior. */
    INSIDE_TO_OUTSIDE,

    /** El participante retorna desde el exterior hacia el interior de la arena. */
    OUTSIDE_TO_INSIDE,

    /** El participante permanece fuera de la arena. */
    OUTSIDE_TO_OUTSIDE
}
