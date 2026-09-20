package maurxp.betterdragon.ability;

/**
 * Puntos de origen geométricos para el despliegue de habilidades.
 *
 * @author maurxp
 */
public enum EffectOriginType {
    /**
     * Posición de la cabeza del dragón mediante Bukkit API pública.
     */
    DRAGON_HEAD,

    /**
     * Posición del cuerpo central del dragón (Location base de la entidad).
     */
    DRAGON_BODY,

    /**
     * Posición a los pies del objetivo primario resuelto.
     */
    TARGET_FEET,

    /**
     * Coordenada central del podio del End (por defecto 0, 65, 0).
     */
    PODIUM_CENTER,

    /**
     * Coordenada central del área de la arena (por defecto 0, 65, 0).
     */
    ARENA_CENTER,

    /**
     * Coordenada asociada al evento o causante que disparó el trigger.
     */
    TRIGGER_LOCATION
}
