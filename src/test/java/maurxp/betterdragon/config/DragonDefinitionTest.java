package maurxp.betterdragon.config;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.EffectOriginType;
import maurxp.betterdragon.ability.TargetSelectorType;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link DragonDefinition}.
 *
 * @author maurxp
 */
class DragonDefinitionTest {

    private final AbilityDefinition roar = new AbilityDefinition(
            "roar", AbilityTrigger.ON_PHASE_ENTER, 0,
            TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_HEAD, AbilityEffectType.SOUND);

    private final AbilityDefinition sweep = new AbilityDefinition(
            "sweep", AbilityTrigger.PERIODIC, 100,
            TargetSelectorType.NEAREST_PLAYER, EffectOriginType.DRAGON_BODY, AbilityEffectType.KNOCKBACK);

    @Test
    @DisplayName("Construcción nominal con fases ordenadas e integridad de habilidades")
    void testValidDragonDefinition() {
        List<PhaseDefinition> phases = List.of(
                new PhaseDefinition("p1", 0, 1.0, List.of("roar")),
                new PhaseDefinition("p2", 1, 0.5, List.of("sweep"))
        );
        Map<String, AbilityDefinition> abilities = Map.of("roar", roar, "sweep", sweep);

        DragonDefinition def = new DragonDefinition("default", phases, abilities);

        assertEquals("default", def.id());
        assertEquals(2, def.phases().size());
        assertEquals(2, def.abilities().size());
    }

    @Test
    @DisplayName("Rechazo de identificadores de fase duplicados")
    void testDuplicatePhaseIds() {
        List<PhaseDefinition> phases = List.of(
                new PhaseDefinition("phase_same", 0, 1.0),
                new PhaseDefinition("phase_same", 1, 0.5)
        );

        assertThrows(IllegalArgumentException.class,
                () -> new DragonDefinition("default", phases, Map.of()));
    }

    @Test
    @DisplayName("Rechazo de thresholds no estrictamente decrecientes")
    void testNonDecreasingThresholds() {
        // Thresholds iguales (1.0 y 1.0)
        List<PhaseDefinition> equalThresholds = List.of(
                new PhaseDefinition("p1", 0, 1.0),
                new PhaseDefinition("p2", 1, 1.0)
        );
        assertThrows(IllegalArgumentException.class,
                () -> new DragonDefinition("default", equalThresholds, Map.of()));

        // Thresholds crecientes (0.50 y 0.75)
        List<PhaseDefinition> ascendingThresholds = List.of(
                new PhaseDefinition("p1", 0, 0.50),
                new PhaseDefinition("p2", 1, 0.75)
        );
        assertThrows(IllegalArgumentException.class,
                () -> new DragonDefinition("default", ascendingThresholds, Map.of()));
    }

    @Test
    @DisplayName("Rechazo de habilidades referenciadas que no existen en el catálogo")
    void testReferencedMissingAbility() {
        List<PhaseDefinition> phases = List.of(
                new PhaseDefinition("p1", 0, 1.0, List.of("missing_ability"))
        );

        assertThrows(IllegalArgumentException.class,
                () -> new DragonDefinition("default", phases, Map.of("roar", roar)));
    }

    @Test
    @DisplayName("Rechazo de lista de fases vacía")
    void testEmptyPhases() {
        assertThrows(IllegalArgumentException.class,
                () -> new DragonDefinition("default", List.of(), Map.of()));
    }
}
