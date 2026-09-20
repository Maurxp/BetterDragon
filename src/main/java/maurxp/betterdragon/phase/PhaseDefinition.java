package maurxp.betterdragon.phase;

import java.util.List;
import java.util.Objects;

/**
 * Representa la definición inmutable de una fase de combate de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Inmutabilidad:</b> Objeto de configuración congelado consumido por {@code PhaseRuntime}.</li>
 * <li><b>Progresión por Health Ratio:</b> El {@code healthRatioThreshold} define el punto de salud relativa
 * (currentHealth / maxHealth) en el cual esta fase entra en vigor.</li>
 * <li><b>Validación de Integridad:</b> Garantiza que los thresholds sean finitos, se encuentren en el rango
 * {@code (0.0, 1.0]} y los identificadores no sean nulos ni vacíos.</li>
 * </ul>
 *
 * @param id                   identificador único de la fase (ej. "phase_1")
 * @param order                orden secuencial de la fase (0, 1, 2, ...)
 * @param healthRatioThreshold umbral de ratio de salud relativa (e.g. 1.0, 0.75, 0.50, 0.25)
 * @param abilityIds           lista inmutable de identificadores de habilidades activas en esta fase
 * @author maurxp
 */
public record PhaseDefinition(
        String id,
        int order,
        double healthRatioThreshold,
        List<String> abilityIds) {

    public PhaseDefinition {
        Objects.requireNonNull(id, "El id de la fase no puede ser nulo");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El id de la fase no puede estar vacío");
        }
        if (order < 0) {
            throw new IllegalArgumentException("El orden de la fase debe ser >= 0: " + order);
        }
        if (!Double.isFinite(healthRatioThreshold)) {
            throw new IllegalArgumentException("El threshold de salud debe ser un número finito: " + healthRatioThreshold);
        }
        if (healthRatioThreshold <= 0.0 || healthRatioThreshold > 1.0) {
            throw new IllegalArgumentException("El threshold de salud debe estar en el rango (0.0, 1.0]: " + healthRatioThreshold);
        }
        Objects.requireNonNull(abilityIds, "La lista de habilidades no puede ser nula");
        abilityIds = List.copyOf(abilityIds);
    }

    /**
     * Constructor de conveniencia con lista vacía de habilidades.
     *
     * @param id                   identificador de la fase
     * @param order                orden secuencial
     * @param healthRatioThreshold umbral de ratio de salud
     */
    public PhaseDefinition(String id, int order, double healthRatioThreshold) {
        this(id, order, healthRatioThreshold, List.of());
    }
}
