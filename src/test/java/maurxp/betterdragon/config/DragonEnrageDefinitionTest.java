package maurxp.betterdragon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonEnrageDefinition (Fase 3.14)")
class DragonEnrageDefinitionTest {

    @Test
    @DisplayName("1. defaults() genera configuración por defecto válida con TUNING_CANDIDATES")
    void testDefaults() {
        DragonEnrageDefinition def = DragonEnrageDefinition.defaults();
        assertTrue(def.enabled());
        assertEquals(DragonEnrageDefinition.DEFAULT_THRESHOLD, def.threshold(), 0.0001);
        assertEquals(DragonEnrageDefinition.DEFAULT_COOLDOWN_MULTIPLIER, def.cooldownMultiplier(), 0.0001);
        assertEquals(0.20, def.threshold(), 0.0001);
        assertEquals(0.75, def.cooldownMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("2. disabled() genera configuración inactiva")
    void testDisabled() {
        DragonEnrageDefinition def = DragonEnrageDefinition.disabled();
        assertFalse(def.enabled());
        assertEquals(0.20, def.threshold(), 0.0001);
        assertEquals(0.75, def.cooldownMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("3. Constructor explícito asigna valores válidos correctamente")
    void testExplicitConstructor() {
        DragonEnrageDefinition def = new DragonEnrageDefinition(true, 0.35, 0.50);
        assertTrue(def.enabled());
        assertEquals(0.35, def.threshold(), 0.0001);
        assertEquals(0.50, def.cooldownMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("4. Umbrales límite: 1.0 y valores pequeños > 0 son válidos")
    void testValidThresholdBoundaries() {
        DragonEnrageDefinition maxThreshold = new DragonEnrageDefinition(true, 1.0, 0.8);
        assertEquals(1.0, maxThreshold.threshold(), 0.0001);

        DragonEnrageDefinition minThreshold = new DragonEnrageDefinition(true, 0.01, 0.8);
        assertEquals(0.01, minThreshold.threshold(), 0.0001);
    }

    @Test
    @DisplayName("5. Umbral <= 0.0, > 1.0 o no finito lanza IllegalArgumentException")
    void testInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, 0.0, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, -0.10, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, 1.01, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, Double.NaN, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, Double.POSITIVE_INFINITY, 0.75));
    }

    @Test
    @DisplayName("6. Multiplicador de cooldown <= 0.0 o no finito lanza IllegalArgumentException")
    void testInvalidCooldownMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, 0.20, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, 0.20, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, 0.20, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new DragonEnrageDefinition(true, 0.20, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("7. Multiplicador < 1.0 representa aceleración de cooldowns (cooldown más corto)")
    void testCooldownMultiplierShorter() {
        DragonEnrageDefinition defFast = new DragonEnrageDefinition(true, 0.20, 0.50);
        assertEquals(0.50, defFast.cooldownMultiplier(), 0.0001);
        assertTrue(defFast.cooldownMultiplier() < 1.0);
    }

    @Test
    @DisplayName("8. Multiplicador == 1.0 representa cooldowns idénticos sin cambio")
    void testCooldownMultiplierNeutral() {
        DragonEnrageDefinition defNeutral = new DragonEnrageDefinition(true, 0.20, 1.0);
        assertEquals(1.0, defNeutral.cooldownMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("9. Multiplicador > 1.0 representa desaceleración de cooldowns (cooldown más largo)")
    void testCooldownMultiplierLonger() {
        DragonEnrageDefinition defSlow = new DragonEnrageDefinition(true, 0.20, 1.50);
        assertEquals(1.50, defSlow.cooldownMultiplier(), 0.0001);
        assertTrue(defSlow.cooldownMultiplier() > 1.0);
    }
}
