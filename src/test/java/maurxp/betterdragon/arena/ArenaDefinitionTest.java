package maurxp.betterdragon.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link ArenaDefinition}.
 *
 * @author maurxp
 */
class ArenaDefinitionTest {

    @Test
    @DisplayName("ArenaDefinition válida con atributos correctos")
    void testValidArenaDefinition() {
        Vector3d center = new Vector3d(0.0, 95.0, 0.0);
        Vector3d podium = new Vector3d(0.0, 64.0, 0.0);
        ArenaBounds bounds = new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 200.0, 100.0);
        ArenaRuleSet rules = ArenaRuleSet.defaults();

        ArenaDefinition arena = new ArenaDefinition("custom", "world_the_end", center, podium, bounds, rules);

        assertEquals("custom", arena.id());
        assertEquals("world_the_end", arena.worldName());
        assertEquals(center, arena.center());
        assertEquals(podium, arena.podium());
        assertEquals(bounds, arena.bounds());
        assertEquals(rules, arena.rules());
    }

    @Test
    @DisplayName("ArenaDefinition inválida rechaza ID o WorldName nulo, vacío o en blanco")
    void testInvalidIdOrWorldName() {
        ArenaBounds bounds = ArenaBounds.defaults();
        Vector3d center = new Vector3d(0.0, 100.0, 0.0);
        Vector3d podium = new Vector3d(0.0, 65.0, 0.0);
        ArenaRuleSet rules = ArenaRuleSet.defaults();

        assertThrows(NullPointerException.class, () -> new ArenaDefinition(null, "world_the_end", center, podium, bounds, rules));
        assertThrows(IllegalArgumentException.class, () -> new ArenaDefinition("", "world_the_end", center, podium, bounds, rules));
        assertThrows(IllegalArgumentException.class, () -> new ArenaDefinition("   ", "world_the_end", center, podium, bounds, rules));

        assertThrows(NullPointerException.class, () -> new ArenaDefinition("default", null, center, podium, bounds, rules));
        assertThrows(IllegalArgumentException.class, () -> new ArenaDefinition("default", "", center, podium, bounds, rules));
        assertThrows(IllegalArgumentException.class, () -> new ArenaDefinition("default", "   ", center, podium, bounds, rules));
    }

    @Test
    @DisplayName("ArenaDefinition inválida rechaza centro fuera de los límites de la arena")
    void testCenterOutOfBoundsRejected() {
        ArenaBounds bounds = new ArenaBounds(-50.0, 0.0, -50.0, 50.0, 100.0, 50.0);
        Vector3d centerOutside = new Vector3d(150.0, 100.0, 0.0);
        Vector3d podium = new Vector3d(0.0, 65.0, 0.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new ArenaDefinition("test", "world_the_end", centerOutside, podium, bounds, ArenaRuleSet.defaults())
        );
        assertTrue(ex.getMessage().contains("límites definidos"));
    }

    @Test
    @DisplayName("ArenaDefinition inválida rechaza podio fuera de los límites de la arena")
    void testPodiumOutOfBoundsRejected() {
        ArenaBounds bounds = new ArenaBounds(-50.0, 50.0, -50.0, 50.0, 100.0, 50.0);
        Vector3d center = new Vector3d(0.0, 90.0, 0.0);
        Vector3d podiumOutside = new Vector3d(0.0, 40.0, 0.0); // Y < 50.0

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new ArenaDefinition("test", "world_the_end", center, podiumOutside, bounds, ArenaRuleSet.defaults())
        );
        assertTrue(ex.getMessage().contains("límites definidos"));
    }


    @Test
    @DisplayName("Arena center es estrictamente independiente del podium center")
    void testCenterAndPodiumIndependence() {
        Vector3d center = new Vector3d(10.0, 110.0, -5.0);
        Vector3d podium = new Vector3d(0.0, 64.0, 0.0);
        ArenaBounds bounds = new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 200.0, 100.0);

        ArenaDefinition arena = new ArenaDefinition("arena1", "world_the_end", center, podium, bounds, ArenaRuleSet.defaults());

        assertNotEquals(arena.center(), arena.podium(), "Arena center y podium center no deben ser iguales");
        assertEquals(110.0, arena.center().y());
        assertEquals(64.0, arena.podium().y());
        assertEquals(10.0, arena.center().x());
        assertEquals(0.0, arena.podium().x());
    }

    @Test
    @DisplayName("ArenaDefinition.defaults() provee la configuración estándar de producción")
    void testDefaults() {
        ArenaDefinition defaults = ArenaDefinition.defaults();

        assertEquals("default", defaults.id());
        assertEquals("world_the_end", defaults.worldName());
        assertEquals(new Vector3d(0.0, 100.0, 0.0), defaults.center());
        assertEquals(new Vector3d(0.0, 65.0, 0.0), defaults.podium());
        assertTrue(defaults.bounds().contains(defaults.center()));
        assertTrue(defaults.bounds().contains(defaults.podium()));
    }
}
