package maurxp.betterdragon.config;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.phase.PhaseDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Definición inmutable de configuración de un dragón de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Congelada en Snapshot:</b> Una vez instanciada dentro de {@link BattleConfigurationSnapshot},
 * no es afectada por recargas globales de configuración.</li>
 * <li><b>Validación de Integridad Referencial:</b> Toda habilidad asignada a una fase debe existir
 * en el catálogo tipado {@code abilities}.</li>
 * <li><b>Orden y Thresholds Coherentes:</b> Las fases deben tener umbrales descendentes
 * coherentes con su progresión monotónica.</li>
 * </ul>
 *
 * @param id        identificador de la definición (ej. "default")
 * @param phases    lista inmutable de fases de combate ordenadas
 * @param abilities catálogo inmutable de habilidades disponibles
 * @author maurxp
 */
public record DragonDefinition(
        String id,
        List<PhaseDefinition> phases,
        Map<String, AbilityDefinition> abilities) {

    public DragonDefinition {
        Objects.requireNonNull(id, "El id de la definición del dragón no puede ser nulo");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El id de la definición del dragón no puede estar vacío");
        }
        Objects.requireNonNull(phases, "La lista de fases no puede ser nula");
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("El dragón debe tener al menos una fase de combate configurada");
        }
        Objects.requireNonNull(abilities, "El mapa de habilidades no puede ser nulo");

        // Copias inmutables
        phases = phases.stream()
                .sorted(Comparator.comparingInt(PhaseDefinition::order))
                .toList();
        abilities = Map.copyOf(abilities);

        // 1. Validar unicidad de IDs de fase
        Set<String> phaseIds = phases.stream().map(PhaseDefinition::id).collect(Collectors.toSet());
        if (phaseIds.size() != phases.size()) {
            throw new IllegalArgumentException("Se detectaron identificadores de fase duplicados en el dragón '" + id + "'");
        }

        // 2. Validar monotonicidad descendente de thresholds
        for (int i = 0; i < phases.size() - 1; i++) {
            PhaseDefinition current = phases.get(i);
            PhaseDefinition next = phases.get(i + 1);
            if (current.healthRatioThreshold() <= next.healthRatioThreshold()) {
                throw new IllegalArgumentException("Los thresholds de fase deben ser estrictamente decrecientes: fase "
                        + current.id() + " (" + current.healthRatioThreshold() + ") <= fase "
                        + next.id() + " (" + next.healthRatioThreshold() + ")");
            }
        }

        // 3. Validar integridad referencial de habilidades
        for (PhaseDefinition phase : phases) {
            for (String abilityId : phase.abilityIds()) {
                if (!abilities.containsKey(abilityId)) {
                    throw new IllegalArgumentException("La fase '" + phase.id() + "' referencia una habilidad inexistente: '"
                            + abilityId + "'");
                }
            }
        }
    }

    /**
     * Genera la definición por defecto estándar de BetterDragon con 4 fases progresivas.
     */
    public static DragonDefinition defaults() {
        List<PhaseDefinition> defaultPhases = List.of(
                new PhaseDefinition("phase_1", 0, 1.0, List.of()),
                new PhaseDefinition("phase_2", 1, 0.75, List.of()),
                new PhaseDefinition("phase_3", 2, 0.50, List.of()),
                new PhaseDefinition("phase_4", 3, 0.25, List.of())
        );
        return new DragonDefinition("default", defaultPhases, Map.of());
    }
}
