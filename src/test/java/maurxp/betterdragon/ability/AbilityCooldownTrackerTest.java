package maurxp.betterdragon.ability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link AbilityCooldownTracker}.
 *
 * @author maurxp
 */
class AbilityCooldownTrackerTest {

    private AbilityCooldownTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AbilityCooldownTracker();
    }

    @Test
    @DisplayName("Habilidad no registrada inicialmente está lista en cualquier tick")
    void testInitiallyReady() {
        assertTrue(tracker.isReady("test_ability", 0L));
        assertTrue(tracker.isReady("test_ability", 1000L));
    }

    @Test
    @DisplayName("Cooldown en ticks lógicos bloquea y luego libera en el tick esperado")
    void testCooldownTiming() {
        tracker.setCooldown("test_ability", 100L, 50L); // Disponible a partir del tick 150

        assertFalse(tracker.isReady("test_ability", 100L));
        assertFalse(tracker.isReady("test_ability", 149L));
        assertTrue(tracker.isReady("test_ability", 150L));
        assertTrue(tracker.isReady("test_ability", 151L));
    }

    @Test
    @DisplayName("Establecer cooldown <= 0 mantiene la habilidad lista inmediatamente")
    void testZeroCooldown() {
        tracker.setCooldown("test_ability", 100L, 0L);
        assertTrue(tracker.isReady("test_ability", 100L));
    }

    @Test
    @DisplayName("Reset borra todas las recargas pendientes")
    void testReset() {
        tracker.setCooldown("sweep", 100L, 500L);
        tracker.setCooldown("roar", 100L, 200L);

        assertFalse(tracker.isReady("sweep", 101L));
        assertFalse(tracker.isReady("roar", 101L));

        tracker.reset();

        assertTrue(tracker.isReady("sweep", 101L));
        assertTrue(tracker.isReady("roar", 101L));
    }

    @Test
    @DisplayName("Protección contra abilityId nulo")
    void testNullAbilityId() {
        assertThrows(NullPointerException.class, () -> tracker.isReady(null, 100L));
        assertThrows(NullPointerException.class, () -> tracker.setCooldown(null, 100L, 20L));
    }
}
