package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.arena.ArenaBounds;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de BoundaryPolicy")
class BoundaryPolicyTest {

    private BoundaryPolicy boundaryPolicy;
    private ArenaBounds bounds;
    private World fakeWorld;

    @BeforeEach
    void setUp() {
        boundaryPolicy = new BoundaryPolicy(5.0);
        bounds = new ArenaBounds(-100.0, 0.0, -100.0, 100.0, 200.0, 100.0);
        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> null
        );
    }

    @Test
    @DisplayName("Zona Interior: coordenadas centradas y alejadas de los bordes")
    void testInsideZone() {
        Location center = new Location(fakeWorld, 0.0, 65.0, 0.0);
        Location wellInside = new Location(fakeWorld, 50.0, 100.0, -50.0);

        assertEquals(BoundaryZone.INSIDE, boundaryPolicy.evaluateZone(center, bounds));
        assertEquals(BoundaryZone.INSIDE, boundaryPolicy.evaluateZone(wellInside, bounds));
        assertFalse(boundaryPolicy.isBreach(center, bounds));
        assertFalse(boundaryPolicy.isBreach(wellInside, bounds));
    }

    @Test
    @DisplayName("Zona de Límite / Advertencia (NEAR_BOUNDARY): coordenadas dentro de los 5 bloques del perímetro")
    void testNearBoundaryZone() {
        // Cerca del maxX (100.0 - 5.0 = 95.0) -> 98.0
        Location nearMaxX = new Location(fakeWorld, 98.0, 65.0, 0.0);
        // Cerca del minZ (-100.0 + 5.0 = -95.0) -> -98.0
        Location nearMinZ = new Location(fakeWorld, 0.0, 65.0, -98.0);
        // Cerca del maxY (200.0 - 5.0 = 195.0) -> 197.0
        Location nearMaxY = new Location(fakeWorld, 0.0, 197.0, 0.0);

        assertEquals(BoundaryZone.NEAR_BOUNDARY, boundaryPolicy.evaluateZone(nearMaxX, bounds));
        assertEquals(BoundaryZone.NEAR_BOUNDARY, boundaryPolicy.evaluateZone(nearMinZ, bounds));
        assertEquals(BoundaryZone.NEAR_BOUNDARY, boundaryPolicy.evaluateZone(nearMaxY, bounds));
        assertFalse(boundaryPolicy.isBreach(nearMaxX, bounds), "NEAR_BOUNDARY sigue estando técnicamente dentro de la arena");
    }

    @Test
    @DisplayName("Zona Exterior (OUTSIDE) y Brecha: coordenadas fuera del Axis-Aligned Bounding Box")
    void testOutsideZoneAndBreach() {
        Location beyondMaxX = new Location(fakeWorld, 105.0, 65.0, 0.0);
        Location belowMinY = new Location(fakeWorld, 0.0, -5.0, 0.0);
        Location beyondMaxZ = new Location(fakeWorld, 0.0, 65.0, 150.0);

        assertEquals(BoundaryZone.OUTSIDE, boundaryPolicy.evaluateZone(beyondMaxX, bounds));
        assertEquals(BoundaryZone.OUTSIDE, boundaryPolicy.evaluateZone(belowMinY, bounds));
        assertEquals(BoundaryZone.OUTSIDE, boundaryPolicy.evaluateZone(beyondMaxZ, bounds));

        assertTrue(boundaryPolicy.isBreach(beyondMaxX, bounds));
        assertTrue(boundaryPolicy.isBreach(belowMinY, bounds));
        assertTrue(boundaryPolicy.isBreach(beyondMaxZ, bounds));
    }

    @Test
    @DisplayName("Transición de Movimiento: evalúa transiciones interiores, de aproximación y brechas")
    void testTransitions() {
        Location inside = new Location(fakeWorld, 0.0, 65.0, 0.0);
        Location near = new Location(fakeWorld, 98.0, 65.0, 0.0);
        Location outside = new Location(fakeWorld, 110.0, 65.0, 0.0);

        assertEquals(BoundaryTransition.INSIDE_TO_INSIDE, boundaryPolicy.evaluateTransition(inside, inside, bounds));
        assertEquals(BoundaryTransition.INSIDE_TO_NEAR_BOUNDARY, boundaryPolicy.evaluateTransition(inside, near, bounds));
        assertEquals(BoundaryTransition.NEAR_BOUNDARY_TO_OUTSIDE, boundaryPolicy.evaluateTransition(near, outside, bounds));
        assertEquals(BoundaryTransition.INSIDE_TO_OUTSIDE, boundaryPolicy.evaluateTransition(inside, outside, bounds));
        assertEquals(BoundaryTransition.OUTSIDE_TO_INSIDE, boundaryPolicy.evaluateTransition(outside, inside, bounds));
        assertEquals(BoundaryTransition.OUTSIDE_TO_OUTSIDE, boundaryPolicy.evaluateTransition(outside, outside, bounds));
    }

    @Test
    @DisplayName("clampInside: restringe cualquier coordenada fuera de la arena con un inset de seguridad")
    void testClampInside() {
        Location outside = new Location(fakeWorld, 150.0, -10.0, -180.0, 90.0f, 0.0f);
        Location clamped = boundaryPolicy.clampInside(outside, bounds, 10.0);

        // Bounds: [-100, 100], [-100, 100], [0, 200]
        // Inset 10.0 -> X in [-90, 90], Y in [10, 190], Z in [-90, 90]
        assertEquals(90.0, clamped.getX());
        assertEquals(10.0, clamped.getY());
        assertEquals(-90.0, clamped.getZ());
        assertEquals(90.0f, clamped.getYaw());
        assertEquals(0.0f, clamped.getPitch());
    }

    @Test
    @DisplayName("distanceToNearestBoundary: calcula la distancia euclídea mínima al plano perimetral")
    void testDistanceToNearestBoundary() {
        Location loc = new Location(fakeWorld, 90.0, 65.0, 0.0);
        // Distancia a maxX (100.0 - 90.0 = 10.0)
        double dist = boundaryPolicy.distanceToNearestBoundary(loc, bounds);
        assertEquals(10.0, dist, 0.001);
    }

    @Test
    @DisplayName("clampInside: arena estrecha (dimensión < 2 * safeInset) no produce inversión de cotas")
    void testClampInsideNarrowArenaNoInversion() {
        // Dimensión X = 8.0, Z = 8.0. Inset solicitado = 5.0 (2 * 5.0 = 10.0 > 8.0)
        ArenaBounds narrowBounds = new ArenaBounds(0.0, 0.0, 0.0, 8.0, 100.0, 8.0);
        Location outsideFar = new Location(fakeWorld, -100.0, 50.0, 100.0, 45.0f, 10.0f);

        Location clamped = boundaryPolicy.clampInside(outsideFar, narrowBounds, 5.0);

        assertNotNull(clamped);
        // effectiveInset = min(5.0, 8.0 / 4.0) = 2.0. Rango seguro [2.0, 6.0]
        assertTrue(clamped.getX() >= narrowBounds.minX(), "X clamped no debe ser menor a minX");
        assertTrue(clamped.getX() <= narrowBounds.maxX(), "X clamped no debe ser mayor a maxX");
        assertTrue(clamped.getZ() >= narrowBounds.minZ(), "Z clamped no debe ser menor a minZ");
        assertTrue(clamped.getZ() <= narrowBounds.maxZ(), "Z clamped no debe ser mayor a maxZ");
        assertEquals(2.0, clamped.getX(), 0.001);
        assertEquals(6.0, clamped.getZ(), 0.001);
        assertEquals(50.0, clamped.getY(), 0.001);
        assertEquals(45.0f, clamped.getYaw());
        assertEquals(10.0f, clamped.getPitch());
    }
}

