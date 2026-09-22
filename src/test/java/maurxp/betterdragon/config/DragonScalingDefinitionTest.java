package maurxp.betterdragon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonScalingDefinition (Fase 3.13-R1)")
class DragonScalingDefinitionTest {

    @Test
    @DisplayName("1. disabled() genera configuración formalmente inactiva")
    void testDisabledFactory() {
        DragonScalingDefinition def = DragonScalingDefinition.disabled();
        assertFalse(def.enabled());
        assertEquals(ScalingMode.NONE, def.mode());
        assertEquals(0.0, def.healthPerPlayer());
        assertEquals(1.0, def.maxHealthMultiplier());
    }

    @Test
    @DisplayName("2. linear() genera configuración con escalado lineal activo")
    void testLinearFactory() {
        DragonScalingDefinition def = DragonScalingDefinition.linear(0.25, 3.5);
        assertTrue(def.enabled());
        assertEquals(ScalingMode.LINEAR, def.mode());
        assertEquals(0.25, def.healthPerPlayer());
        assertEquals(3.5, def.maxHealthMultiplier());
    }

    @Test
    @DisplayName("3. healthPerPlayer en cero es válido (sin incremento por jugador)")
    void testZeroHealthPerPlayer() {
        DragonScalingDefinition def = new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.0, 2.0);
        assertEquals(0.0, def.healthPerPlayer());
        assertEquals(2.0, def.maxHealthMultiplier());
    }

    @Test
    @DisplayName("4. maxHealthMultiplier exactamente en 1.0 es el límite inferior permitido")
    void testMultiplierCapBoundaries() {
        DragonScalingDefinition def = new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.1, 1.0);
        assertEquals(1.0, def.maxHealthMultiplier());
    }

    @Test
    @DisplayName("5. healthPerPlayer negativo o no finito lanza IllegalArgumentException")
    void testInvalidHealthPerPlayer() {
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.LINEAR, -0.01, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.LINEAR, Double.NaN, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.LINEAR, Double.POSITIVE_INFINITY, 2.0));
    }

    @Test
    @DisplayName("6. maxHealthMultiplier menor a 1.0 o no finito lanza IllegalArgumentException")
    void testInvalidMultiplierBelowOne() {
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.25, 0.99));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.25, -1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.25, Double.NaN));
    }

    @Test
    @DisplayName("7. enabled: true con mode nulo infiere LINEAR según semántica Opción A")
    void testEnabledWithMissingModeDefaultsToLinear() {
        DragonScalingDefinition def = new DragonScalingDefinition(true, null, 0.25, 3.0);
        assertTrue(def.enabled());
        assertEquals(ScalingMode.LINEAR, def.mode());
    }

    @Test
    @DisplayName("8. enabled: false con mode nulo infiere NONE")
    void testDisabledWithMissingModeDefaultsToNone() {
        DragonScalingDefinition def = new DragonScalingDefinition(false, null, 0.0, 1.0);
        assertFalse(def.enabled());
        assertEquals(ScalingMode.NONE, def.mode());
    }

    @Test
    @DisplayName("9. enabled: true con mode: NONE es inconsistente y lanza IllegalArgumentException")
    void testEnabledWithModeNoneThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new DragonScalingDefinition(true, ScalingMode.NONE, 0.25, 3.0));
    }

    @Test
    @DisplayName("10. Comportamiento determinista en equality e inmutabilidad de record")
    void testDeterministicBehavior() {
        DragonScalingDefinition def1 = new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.3, 2.5);
        DragonScalingDefinition def2 = new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.3, 2.5);
        assertEquals(def1, def2);
        assertEquals(def1.hashCode(), def2.hashCode());
    }
}
