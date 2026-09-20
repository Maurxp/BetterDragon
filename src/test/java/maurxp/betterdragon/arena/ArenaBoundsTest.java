package maurxp.betterdragon.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link ArenaBounds}.
 *
 * @author maurxp
 */
class ArenaBoundsTest {

    private World createFakeWorld(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Bounds min/max válidos y cálculos geométricos")
    void testValidBoundsCreationAndDimensions() {
        ArenaBounds bounds = new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 200.0, 100.0);

        assertEquals(-100.0, bounds.minX());
        assertEquals(0.0, bounds.minY());
        assertEquals(-100.0, bounds.minZ());
        assertEquals(100.0, bounds.maxX());
        assertEquals(200.0, bounds.maxY());
        assertEquals(100.0, bounds.maxZ());

        assertEquals(200.0, bounds.widthX());
        assertEquals(200.0, bounds.heightY());
        assertEquals(200.0, bounds.depthZ());
        assertEquals(8_000_000.0, bounds.volume());
    }

    @Test
    @DisplayName("Bounds inválidos lanzan IllegalArgumentException cuando min > max")
    void testInvalidBoundsMinGreaterThanMax() {
        // minX > maxX
        assertThrows(IllegalArgumentException.class, () -> new ArenaBounds(10.0, 0.0, 0.0, 5.0, 10.0, 10.0));
        // minY > maxY
        assertThrows(IllegalArgumentException.class, () -> new ArenaBounds(0.0, 50.0, 0.0, 10.0, 10.0, 10.0));
        // minZ > maxZ
        assertThrows(IllegalArgumentException.class, () -> new ArenaBounds(0.0, 0.0, 20.0, 10.0, 10.0, 10.0));
    }

    @Test
    @DisplayName("Bounds rechazan coordenadas no finitas (NaN, Infinity)")
    void testBoundsRejectNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new ArenaBounds(Double.NaN, 0.0, 0.0, 10.0, 10.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new ArenaBounds(0.0, Double.POSITIVE_INFINITY, 0.0, 10.0, 10.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new ArenaBounds(0.0, 0.0, Double.NEGATIVE_INFINITY, 10.0, 10.0, 10.0));
    }

    @Test
    @DisplayName("contains(x,y,z) evalúa correctamente posiciones interiores, exteriores y en bordes")
    void testContainsCoordinates() {
        ArenaBounds bounds = new ArenaBounds(-50.0, 10.0, -50.0, 50.0, 100.0, 50.0);

        // Centro interior
        assertTrue(bounds.contains(0.0, 50.0, 0.0));
        // En los límites exactos (inclusivo)
        assertTrue(bounds.contains(-50.0, 10.0, -50.0));
        assertTrue(bounds.contains(50.0, 100.0, 50.0));

        // Fuera por cada eje
        assertFalse(bounds.contains(-50.1, 50.0, 0.0));
        assertFalse(bounds.contains(50.1, 50.0, 0.0));
        assertFalse(bounds.contains(0.0, 9.9, 0.0));
        assertFalse(bounds.contains(0.0, 100.1, 0.0));
        assertFalse(bounds.contains(0.0, 50.0, -50.1));
        assertFalse(bounds.contains(0.0, 50.0, 50.1));
    }

    @Test
    @DisplayName("contains(Location) evalúa correctamente ubicaciones Bukkit y null safety")
    void testContainsLocation() {
        ArenaBounds bounds = new ArenaBounds(-50.0, 10.0, -50.0, 50.0, 100.0, 50.0);
        World world = createFakeWorld("world_the_end");

        Location inside = new Location(world, 10.0, 50.0, -20.0);
        Location outside = new Location(world, 100.0, 50.0, 0.0);

        assertTrue(bounds.contains(inside));
        assertFalse(bounds.contains(outside));
        assertFalse(bounds.contains((Location) null));
    }

    @Test
    @DisplayName("of(Vector3d min, Vector3d max) y defaults() operan correctamente")
    void testOfVectorsAndDefaults() {
        Vector3d min = new Vector3d(-30.0, 10.0, -30.0);
        Vector3d max = new Vector3d(30.0, 80.0, 30.0);
        ArenaBounds bounds = ArenaBounds.of(min, max);

        assertEquals(-30.0, bounds.minX());
        assertEquals(30.0, bounds.maxX());

        ArenaBounds defaults = ArenaBounds.defaults();
        assertNotNull(defaults);
        assertTrue(defaults.contains(0.0, 100.0, 0.0));
        assertTrue(defaults.contains(0.0, 65.0, 0.0));
    }
}
