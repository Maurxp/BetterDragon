package maurxp.betterdragon.arena;

import java.util.Objects;

/**
 * Representación inmutable y tipada del dominio para una Arena de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Aislamiento Físico:</b> No almacena referencias vivas a Bukkit (World, Player, Entity).</li>
 *   <li><b>Identidad Estable:</b> Posee un identificador único e inmutable configurable (ej. "default").</li>
 *   <li><b>Separación Semántica Estricta:</b> El centro de la arena ({@code center}) y el centro del podio ({@code podium})
 *       son coordenadas independientes; ninguno delega en el otro.</li>
 *   <li><b>Coherencia Espacial:</b> Valida que tanto el centro de la arena como el podio se ubiquen dentro de los límites
 *       definidos ({@link ArenaBounds}).</li>
 * </ul>
 *
 * @param id        identificador único de la arena (ej. "default")
 * @param worldName nombre del mundo de la dimensión del End (ej. "world_the_end")
 * @param center    coordenada central lógica de combate aéreo de la arena
 * @param podium    coordenada central del podio/portal a nivel del suelo del End
 * @param bounds    volumen delimitador de la arena (Axis-Aligned Bounding Box)
 * @param rules     conjunto de reglas geométricas aplicables a la arena
 * @author maurxp
 */
public record ArenaDefinition(
        String id,
        String worldName,
        Vector3d center,
        Vector3d podium,
        ArenaBounds bounds,
        ArenaRuleSet rules
) {

    public static final String DEFAULT_ARENA_ID = "default";
    public static final String DEFAULT_WORLD_NAME = "world_the_end";

    public static final Vector3d DEFAULT_ARENA_CENTER = new Vector3d(0.0, 100.0, 0.0);
    public static final Vector3d DEFAULT_PODIUM_CENTER = new Vector3d(0.0, 65.0, 0.0);

    public ArenaDefinition {
        Objects.requireNonNull(id, "El id de la arena no puede ser nulo");
        Objects.requireNonNull(worldName, "El worldName de la arena no puede ser nulo");
        Objects.requireNonNull(center, "El centro de la arena no puede ser nulo");
        Objects.requireNonNull(podium, "El podio de la arena no puede ser nulo");
        Objects.requireNonNull(bounds, "Los límites de la arena no pueden ser nulos");
        Objects.requireNonNull(rules, "Las reglas de la arena no pueden ser nulas");

        id = id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("El id de la arena no puede estar vacío");
        }

        worldName = worldName.trim();
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("El worldName de la arena no puede estar vacío");
        }

        if (!bounds.contains(center)) {
            throw new IllegalArgumentException("El centro de la arena (" + center + ") debe estar dentro de los límites definidos: " + bounds);
        }

        if (!bounds.contains(podium)) {
            throw new IllegalArgumentException("El podio de la arena (" + podium + ") debe estar dentro de los límites definidos: " + bounds);
        }
    }

    /**
     * Crea la definición predeterminada de arena de producción para el End vanilla.
     *
     * @return definición de arena default
     */
    public static ArenaDefinition defaults() {
        return new ArenaDefinition(
                DEFAULT_ARENA_ID,
                DEFAULT_WORLD_NAME,
                DEFAULT_ARENA_CENTER,
                DEFAULT_PODIUM_CENTER,
                ArenaBounds.defaults(),
                ArenaRuleSet.defaults()
        );
    }
}
