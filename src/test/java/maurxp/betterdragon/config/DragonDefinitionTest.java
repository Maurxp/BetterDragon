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

@DisplayName("Pruebas Unitarias de DragonDefinition (Fase 3.13 / 3.13-R1)")
class DragonDefinitionTest {

    @Test
    @DisplayName("Creación exitosa con parámetros válidos")
    void testValidDragonDefinition() {
        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 1.0, List.of());
        PhaseDefinition p2 = new PhaseDefinition("p2", 1, 0.5, List.of());
        DragonAttributes attrs = new DragonAttributes(600.0, 0.35, 64.0, 15.0);
        DragonScalingDefinition scaling = new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.25, 4.0);

        DragonDefinition def = new DragonDefinition(
                "elder_dragon",
                "Dragón Anciano",
                attrs,
                scaling,
                List.of(p1, p2),
                Map.of()
        );

        assertEquals("elder_dragon", def.id());
        assertEquals("Dragón Anciano", def.displayName());
        assertEquals(600.0, def.attributes().maxHealth());
        assertTrue(def.scaling().enabled());
        assertEquals(ScalingMode.LINEAR, def.scaling().mode());
        assertEquals(2, def.phases().size());
    }

    @Test
    @DisplayName("Rechaza IDs nulos, vacíos o con caracteres inválidos")
    void testInvalidDragonIds() {
        List<PhaseDefinition> phases = List.of(new PhaseDefinition("p1", 0, 1.0, List.of()));
        DragonAttributes attrs = DragonAttributes.defaults();
        DragonScalingDefinition scaling = DragonScalingDefinition.defaults();

        assertThrows(NullPointerException.class, () ->
                new DragonDefinition(null, "Name", attrs, scaling, phases, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("", "Name", attrs, scaling, phases, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("   ", "Name", attrs, scaling, phases, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("dragon with spaces", "Name", attrs, scaling, phases, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("dragon:colon", "Name", attrs, scaling, phases, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("dragon.dot", "Name", attrs, scaling, phases, Map.of()));
    }

    @Test
    @DisplayName("Normaliza ID a minúsculas y sin espacios laterales")
    void testIdNormalization() {
        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 1.0, List.of());
        DragonDefinition def = new DragonDefinition(
                "  NIGHTMARE_DRAGON  ",
                null,
                DragonAttributes.defaults(),
                DragonScalingDefinition.defaults(),
                List.of(p1),
                Map.of()
        );

        assertEquals("nightmare_dragon", def.id());
    }

    @Test
    @DisplayName("DragonDefinition.defaults provee valores por defecto coherentes")
    void testDefaults() {
        DragonDefinition def = DragonDefinition.defaults();
        assertEquals("default", def.id());
        assertEquals(200.0, def.attributes().maxHealth());
        assertFalse(def.scaling().enabled());
        assertEquals(4, def.phases().size());
    }

    @Test
    @DisplayName("Inmutabilidad: phases() y abilities() retornan vistas no modificables")
    void testImmutability() {
        DragonDefinition def = DragonDefinition.defaults();
        assertThrows(UnsupportedOperationException.class, () -> def.phases().add(null));
        assertThrows(UnsupportedOperationException.class, () -> def.abilities().put("test", null));
    }

    @Test
    @DisplayName("Invariante: IDs de phase duplicados deben lanzar IllegalArgumentException")
    void testDuplicatePhaseIdsShouldThrow() {
        PhaseDefinition p1 = new PhaseDefinition("duplicate_id", 0, 1.0, List.of());
        PhaseDefinition p2 = new PhaseDefinition("duplicate_id", 1, 0.5, List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("test_dragon", List.of(p1, p2), Map.of()));
    }

    @Test
    @DisplayName("Invariante: thresholds no decrecientes deben lanzar IllegalArgumentException")
    void testNonDecreasingThresholdsShouldThrow() {
        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 0.5, List.of());
        PhaseDefinition p2 = new PhaseDefinition("p2", 1, 0.75, List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("test_dragon", List.of(p1, p2), Map.of()));
    }

    @Test
    @DisplayName("Invariante: thresholds idénticos duplicados deben lanzar IllegalArgumentException")
    void testDuplicateThresholdsShouldThrow() {
        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 0.5, List.of());
        PhaseDefinition p2 = new PhaseDefinition("p2", 1, 0.5, List.of());

        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("test_dragon", List.of(p1, p2), Map.of()));
    }

    @Test
    @DisplayName("Invariante: referencia a habilidad inexistente debe lanzar IllegalArgumentException")
    void testMissingReferencedAbilityShouldThrow() {
        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 1.0, List.of("unregistered_ability"));

        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("test_dragon", List.of(p1), Map.of()));
    }

    @Test
    @DisplayName("Invariante: lista de phases vacía debe lanzar IllegalArgumentException")
    void testEmptyPhasesShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                new DragonDefinition("test_dragon", List.of(), Map.of()));
    }

    @Test
    @DisplayName("Invariante: phases o abilities nulas deben lanzar NullPointerException")
    void testNullPhasesOrAbilitiesShouldThrow() {
        assertThrows(NullPointerException.class, () ->
                new DragonDefinition("test_dragon", null, Map.of()));
        assertThrows(NullPointerException.class, () ->
                new DragonDefinition("test_dragon", List.of(new PhaseDefinition("p1", 0, 1.0, List.of())), null));
    }

    @Test
    @DisplayName("Invariante: construcción válida con catálogo de abilities referenciadas")
    void testValidConstructionWithAbilities() {
        AbilityDefinition flame = new AbilityDefinition(
                "flame_burst",
                AbilityTrigger.PERIODIC,
                100L,
                TargetSelectorType.NEAREST_PLAYER,
                EffectOriginType.DRAGON_HEAD,
                AbilityEffectType.DAMAGE,
                Map.of("damage", 10.0)
        );

        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 1.0, List.of("flame_burst"));
        PhaseDefinition p2 = new PhaseDefinition("p2", 1, 0.5, List.of("flame_burst"));

        DragonDefinition def = new DragonDefinition(
                "elemental_dragon",
                "Dragón Elemental",
                new DragonAttributes(400.0),
                DragonScalingDefinition.disabled(),
                List.of(p1, p2),
                Map.of("flame_burst", flame)
        );

        assertEquals(2, def.phases().size());
        assertEquals(1, def.abilities().size());
        assertTrue(def.abilities().containsKey("flame_burst"));
        assertEquals(flame, def.abilities().get("flame_burst"));
    }

    @Test
    @DisplayName("Fallback: attributes o scaling nulos se inicializan con defaults")
    void testFallbackWhenAttributesOrScalingNull() {
        PhaseDefinition p1 = new PhaseDefinition("p1", 0, 1.0, List.of());
        DragonDefinition def = new DragonDefinition(
                "fallback_dragon",
                null,
                null,
                null,
                List.of(p1),
                Map.of()
        );

        assertNotNull(def.attributes());
        assertEquals(DragonAttributes.DEFAULT_MAX_HEALTH, def.attributes().maxHealth());
        assertNotNull(def.scaling());
        assertFalse(def.scaling().enabled());
    }
}
