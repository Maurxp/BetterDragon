package maurxp.betterdragon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonAttributes (Fase 3.13)")
class DragonAttributesTest {

    @Test
    @DisplayName("Construcción válida con todos los atributos")
    void testValidAttributes() {
        DragonAttributes attrs = new DragonAttributes(800.0, 0.4, 128.0, 20.0);
        assertEquals(800.0, attrs.maxHealth());
        assertTrue(attrs.movementSpeed().isPresent());
        assertEquals(0.4, attrs.movementSpeed().get());
        assertTrue(attrs.followRange().isPresent());
        assertEquals(128.0, attrs.followRange().get());
        assertTrue(attrs.attackDamage().isPresent());
        assertEquals(20.0, attrs.attackDamage().get());
    }

    @Test
    @DisplayName("Construcción válida con atributos opcionales nulos")
    void testValidAttributesWithOptionalsNull() {
        DragonAttributes attrs = new DragonAttributes(500.0, null, null, null);
        assertEquals(500.0, attrs.maxHealth());
        assertTrue(attrs.movementSpeed().isEmpty());
        assertTrue(attrs.followRange().isEmpty());
        assertTrue(attrs.attackDamage().isEmpty());
    }

    @Test
    @DisplayName("Rechazo de maxHealth <= 0 o no finitos")
    void testInvalidMaxHealth() {
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(0.0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(-100.0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(Double.NaN, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(Double.POSITIVE_INFINITY, null, null, null));
    }

    @Test
    @DisplayName("Rechazo de opcionales con valores no válidos")
    void testInvalidOptionalAttributes() {
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(500.0, 0.0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(500.0, -0.1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(500.0, null, -1.0, null));
        assertThrows(IllegalArgumentException.class, () -> new DragonAttributes(500.0, null, null, -0.5));
    }

    @Test
    @DisplayName("defaults() provee 200.0 HP y opcionales vacíos")
    void testDefaults() {
        DragonAttributes defaults = DragonAttributes.defaults();
        assertEquals(200.0, defaults.maxHealth());
        assertTrue(defaults.movementSpeed().isEmpty());
        assertTrue(defaults.followRange().isEmpty());
        assertTrue(defaults.attackDamage().isEmpty());
    }
}
