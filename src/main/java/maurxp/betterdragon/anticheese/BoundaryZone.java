package maurxp.betterdragon.anticheese;

/**
 * Zona espacial perimetral en la que se ubica una entidad respecto a los límites de la arena.
 *
 * @author maurxp
 */
public enum BoundaryZone {
    /** La entidad se encuentra de forma segura dentro del volumen interior de la arena. */
    INSIDE,

    /** La entidad se aproxima a los límites perimetrales de la arena dentro del margen de amortiguación (buffer). */
    NEAR_BOUNDARY,

    /** La entidad se encuentra fuera de los límites geométricos de la arena o en un mundo distinto. */
    OUTSIDE
}
