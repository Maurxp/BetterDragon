package maurxp.betterdragon.ability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link AbilityDefinition}.
 *
 * @author maurxp
 */
class AbilityDefinitionTest {

    @Test
    @DisplayName("Construcción nominal con propiedades y lectura tipada segura")
    void testValidConstruction() {
        AbilityDefinition ability = new AbilityDefinition(
                "flame_burst",
                AbilityTrigger.PERIODIC,
                100L,
                TargetSelectorType.NEAREST_PLAYER,
                EffectOriginType.DRAGON_HEAD,
                AbilityEffectType.DAMAGE,
                Map.of("damage", 8.5, "count", 3, "particle", "FLAME")
        );

        assertEquals("flame_burst", ability.id());
        assertEquals(AbilityTrigger.PERIODIC, ability.trigger());
        assertEquals(100L, ability.cooldownTicks());
        assertEquals(TargetSelectorType.NEAREST_PLAYER, ability.targetSelector());
        assertEquals(EffectOriginType.DRAGON_HEAD, ability.effectOrigin());
        assertEquals(AbilityEffectType.DAMAGE, ability.effectType());

        // Lectura de propiedades tipadas
        assertEquals(8.5, ability.getDoubleProperty("damage", 0.0), 0.0001);
        assertEquals(3, ability.getIntProperty("count", 0));
        assertEquals("FLAME", ability.getStringProperty("particle", "NONE"));

        // Fallbacks para propiedades ausentes
        assertEquals(5.0, ability.getDoubleProperty("missing", 5.0), 0.0001);
        assertEquals(10, ability.getIntProperty("missing", 10));
        assertEquals("DEFAULT", ability.getStringProperty("missing", "DEFAULT"));

        // Inmutabilidad del mapa
        assertThrows(UnsupportedOperationException.class, () -> ability.properties().put("new", "val"));
    }

    @Test
    @DisplayName("Rechazo de id en blanco o nulo")
    void testInvalidId() {
        assertThrows(NullPointerException.class, () -> new AbilityDefinition(
                null, AbilityTrigger.PERIODIC, 100L, TargetSelectorType.RANDOM_PLAYER,
                EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE));

        assertThrows(IllegalArgumentException.class, () -> new AbilityDefinition(
                "", AbilityTrigger.PERIODIC, 100L, TargetSelectorType.RANDOM_PLAYER,
                EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE));

        assertThrows(IllegalArgumentException.class, () -> new AbilityDefinition(
                "   ", AbilityTrigger.PERIODIC, 100L, TargetSelectorType.RANDOM_PLAYER,
                EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE));
    }

    @Test
    @DisplayName("Rechazo de cooldown negativo")
    void testInvalidCooldown() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityDefinition(
                "sweep", AbilityTrigger.PERIODIC, -1L, TargetSelectorType.RANDOM_PLAYER,
                EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE));
    }

    @Test
    @DisplayName("Rechazo de campos obligatorios nulos")
    void testNullFields() {
        assertThrows(NullPointerException.class, () -> new AbilityDefinition(
                "sweep", null, 10L, TargetSelectorType.RANDOM_PLAYER,
                EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE));

        assertThrows(NullPointerException.class, () -> new AbilityDefinition(
                "sweep", AbilityTrigger.PERIODIC, 10L, null,
                EffectOriginType.DRAGON_BODY, AbilityEffectType.DAMAGE));

        assertThrows(NullPointerException.class, () -> new AbilityDefinition(
                "sweep", AbilityTrigger.PERIODIC, 10L, TargetSelectorType.RANDOM_PLAYER,
                null, AbilityEffectType.DAMAGE));

        assertThrows(NullPointerException.class, () -> new AbilityDefinition(
                "sweep", AbilityTrigger.PERIODIC, 10L, TargetSelectorType.RANDOM_PLAYER,
                EffectOriginType.DRAGON_BODY, null));
    }
}
