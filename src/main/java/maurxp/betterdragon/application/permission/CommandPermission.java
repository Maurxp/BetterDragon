package maurxp.betterdragon.application.permission;

import java.util.Objects;

/**
 * Enumeración tipada de permisos de capacidad para BetterDragon.
 * <p>
 * Permite desacoplar las comprobaciones de autorización tanto para la interfaz
 * de comandos como para futuras interfaces gráficas (GUIs).
 *
 * @author maurxp
 */
public enum CommandPermission {

    USE("betterdragon.use", "Permiso base para interactuar con BetterDragon", false),
    LEADERBOARD("betterdragon.leaderboard", "Permite consultar el leaderboard global", false),
    STATS("betterdragon.stats", "Permite consultar las estadísticas de combate propias", false),
    STATS_OTHERS("betterdragon.stats.others", "Permite consultar las estadísticas de otros jugadores", true),
    CLAIM("betterdragon.claim", "Permite reclamar recompensas pendientes", false),
    ADMIN("betterdragon.admin", "Permiso administrativo general de BetterDragon", true),
    ADMIN_STATUS("betterdragon.admin.status", "Permite consultar el estado de batallas activas", true),
    ADMIN_START("betterdragon.admin.start", "Permite iniciar o forzar una batalla", true),
    ADMIN_ABORT("betterdragon.admin.abort", "Permite abortar o cancelar una batalla activa", true),
    ADMIN_RELOAD("betterdragon.admin.reload", "Permite recargar la configuración del plugin", true),
    ADMIN_ARENA("betterdragon.admin.arena", "Permite consultar la configuración de arenas", true);

    private final String node;
    private final String description;
    private final boolean defaultOp;

    CommandPermission(String node, String description, boolean defaultOp) {
        this.node = Objects.requireNonNull(node, "node no puede ser nulo");
        this.description = Objects.requireNonNull(description, "description no puede ser nulo");
        this.defaultOp = defaultOp;
    }

    public String node() {
        return node;
    }

    public String description() {
        return description;
    }

    public boolean isDefaultOp() {
        return defaultOp;
    }
}
