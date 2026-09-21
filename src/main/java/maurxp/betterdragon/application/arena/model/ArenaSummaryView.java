package maurxp.betterdragon.application.arena.model;

import java.util.Objects;

/**
 * Resumen conciso de una arena para listados y vistas generales.
 *
 * @author maurxp
 */
public record ArenaSummaryView(
        String id,
        String worldName,
        boolean isDefault,
        String boundsDescription
) {
    public ArenaSummaryView {
        Objects.requireNonNull(id, "id no puede ser nulo");
        Objects.requireNonNull(worldName, "worldName no puede ser nulo");
        Objects.requireNonNull(boundsDescription, "boundsDescription no puede ser nulo");
    }
}
