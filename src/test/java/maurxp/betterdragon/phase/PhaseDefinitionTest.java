package maurxp.betterdragon.phase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link PhaseDefinition}.
 *
 * @author maurxp
 */
class PhaseDefinitionTest {

    @Test
    @DisplayName("Creación válida con valores nominales e inmutabilidad de habilidades")
    void testValidPhaseDefinition() {
        PhaseDefinition phase = new PhaseDefinition("phase_1", 0, 1.0, List.of("roar", "sweep"));

        assertEquals("phase_1", phase.id());
        assertEquals(0, phase.order());
        assertEquals(1.0, phase.healthRatioThreshold(), 0.0001);
        assertEquals(2, phase.abilityIds().size());
        assertTrue(phase.abilityIds().contains("roar"));

        // Inmutabilidad
        assertThrows(UnsupportedOperationException.class, () -> phase.abilityIds().add("extra"));
    }

    @Test
    @DisplayName("Rechazo de id nulo o en blanco")
    void testInvalidId() {
        assertThrows(NullPointerException.class, () -> new PhaseDefinition(null, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("", 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("   ", 0, 1.0));
    }

    @Test
    @DisplayName("Rechazo de orden negativo")
    void testInvalidOrder() {
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("phase_x", -1, 0.5));
    }

    @Test
    @DisplayName("Rechazo de thresholds fuera de rango (<= 0.0 o > 1.0 o no finitos)")
    void testInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("phase_0", 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("phase_neg", 0, -0.25));
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("phase_high", 0, 1.05));
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("phase_nan", 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PhaseDefinition("phase_inf", 0, Double.POSITIVE_INFINITY));
    }
}
