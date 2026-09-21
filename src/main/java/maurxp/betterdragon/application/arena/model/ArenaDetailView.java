package maurxp.betterdragon.application.arena.model;

import maurxp.betterdragon.arena.Vector3d;

import java.util.List;
import java.util.Objects;

/**
 * Detalle exhaustivo de una arena de BetterDragon.
 *
 * @author maurxp
 */
public record ArenaDetailView(
        String id,
        String worldName,
        boolean isDefault,
        Vector3d center,
        Vector3d podium,
        String boundsDescription,
        List<String> activeRules
) {
    public ArenaDetailView {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(worldName, "worldName no puede ser nulo");
        Objects.requireNonNull(center, "center no puede ser nulo");
        Objects.requireNonNull(podium, "podium no puede ser nulo");
        Objects.requireNonNull(boundsDescription, "boundsDescription no puede ser nulo");
        Objects.requireNonNull(activeRules, "activeRules no puede ser nulo");
        activeRules = List.copyOf(activeRules);
    }
}
