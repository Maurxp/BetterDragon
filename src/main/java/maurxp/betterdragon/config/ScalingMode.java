package maurxp.betterdragon.config;

/**
 * Modos de escalado disponibles para los atributos del dragón según el número de participantes.
 *
 * @author maurxp
 */
public enum ScalingMode {

    /**
     * Sin escalado. Los atributos base son los efectivos independientemente de los jugadores.
     */
    NONE,

    /**
     * Escalado lineal progresivo según jugadores presentes en la arena al inicio de batalla:
     * Salud = Base * min(Cap, max(1.0, 1.0 + (N - 1) * alpha)).
     */
    LINEAR
}
