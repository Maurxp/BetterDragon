package maurxp.betterdragon.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link ArenaRuleEvaluator}.
 *
 * @author maurxp
 */
class ArenaRuleEvaluatorTest {

    private World fakeEndWorld;
    private World fakeOtherWorld;
    private ArenaDefinition arena;
    private ArenaRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        fakeEndWorld = createFakeWorld("world_the_end");
        fakeOtherWorld = createFakeWorld("world_overworld");

        arena = ArenaDefinition.defaults();
        evaluator = new ArenaRuleEvaluator(arena);
    }

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
    @DisplayName("isLocationInArena valida correctamente mundo y límites")
    void testIsLocationInArena() {
        Location inside = new Location(fakeEndWorld, 0.0, 70.0, 0.0);
        Location outsideBounds = new Location(fakeEndWorld, 500.0, 70.0, 500.0);
        Location wrongWorld = new Location(fakeOtherWorld, 0.0, 70.0, 0.0);

        assertTrue(evaluator.isLocationInArena(inside));
        assertFalse(evaluator.isLocationInArena(outsideBounds));
        assertFalse(evaluator.isLocationInArena(wrongWorld));
        assertFalse(evaluator.isLocationInArena(null));
    }

    @Test
    @DisplayName("isBoundaryViolated responde según el estado de la regla boundary")
    void testBoundaryEvaluation() {
        Location inside = new Location(fakeEndWorld, 0.0, 70.0, 0.0);
        Location outside = new Location(fakeEndWorld, 300.0, 70.0, 300.0);

        // Boundary habilitado por defecto
        assertFalse(evaluator.isBoundaryViolated(inside));
        assertTrue(evaluator.isBoundaryViolated(outside));

        // Boundary deshabilitado
        ArenaRuleSet noBoundaryRules = new ArenaRuleSet(false, false, true);
        ArenaDefinition arenaNoBoundary = new ArenaDefinition(
                arena.id(), arena.worldName(), arena.center(), arena.podium(), arena.bounds(), noBoundaryRules
        );
        ArenaRuleEvaluator evaluatorNoBoundary = new ArenaRuleEvaluator(arenaNoBoundary);

        assertFalse(evaluatorNoBoundary.isBoundaryViolated(outside), "Con boundary deshabilitado no debe reportar violación");
    }

    @Test
    @DisplayName("Water denial restringe agua únicamente dentro de los límites de la arena")
    void testWaterDenialEvaluation() {
        Location inside = new Location(fakeEndWorld, 10.0, 65.0, 10.0);
        Location outside = new Location(fakeEndWorld, 400.0, 65.0, 400.0);

        // Con waterAllowed = false (water denial activo)
        assertFalse(evaluator.isWaterAllowed(inside));
        assertTrue(evaluator.isWaterDenialActiveAt(inside));

        // Fuera de la arena, BetterDragon no interfiere con el juego
        assertTrue(evaluator.isWaterAllowed(outside));
        assertFalse(evaluator.isWaterDenialActiveAt(outside));

        // Con waterAllowed = true
        ArenaRuleSet allowWater = new ArenaRuleSet(true, true, true);
        ArenaDefinition arenaWater = new ArenaDefinition(
                arena.id(), arena.worldName(), arena.center(), arena.podium(), arena.bounds(), allowWater
        );
        ArenaRuleEvaluator evaluatorWater = new ArenaRuleEvaluator(arenaWater);

        assertTrue(evaluatorWater.isWaterAllowed(inside));
        assertFalse(evaluatorWater.isWaterDenialActiveAt(inside));
    }

    @Test
    @DisplayName("Anti-tunnel evalúa violaciones de encierro dentro de la arena")
    void testAntiTunnelEvaluation() {
        Location inside = new Location(fakeEndWorld, 0.0, 60.0, 0.0);
        Location outside = new Location(fakeEndWorld, 500.0, 60.0, 500.0);

        assertTrue(evaluator.isAntiTunnelActive());

        // Dentro de la arena y encerrado -> violación
        assertTrue(evaluator.isAntiTunnelViolation(inside, true));
        // Dentro de la arena pero al aire libre -> no violación
        assertFalse(evaluator.isAntiTunnelViolation(inside, false));
        // Fuera de la arena aunque esté bajo tierra -> no interfiere BetterDragon
        assertFalse(evaluator.isAntiTunnelViolation(outside, true));
    }
}
