package maurxp.betterdragon.ability;

import maurxp.betterdragon.ability.effect.AbilityEffect;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link AbilityEngine}.
 *
 * @author maurxp
 */
class AbilityEngineTest {

    private BattleSession session;
    private EnderDragon fakeDragon;
    private World fakeWorld;
    private PhaseDefinition phase;

    @BeforeEach
    void setUp() {
        session = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) return "world_the_end";
                    if (name.equals("getPlayers")) return List.of();
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        Location dragonLoc = new Location(fakeWorld, 0, 65, 0);
        Location eyeLoc = new Location(fakeWorld, 0, 66, 0);

        fakeDragon = (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getWorld")) return fakeWorld;
                    if (name.equals("getLocation")) return dragonLoc;
                    if (name.equals("getEyeLocation")) return eyeLoc;
                    if (name.equals("isValid")) return true;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        phase = new PhaseDefinition("phase_1", 0, 1.0, List.of("test_sweep", "test_roar"));
    }

    @Test
    @DisplayName("Ejecución nominal actualiza cooldown y retorna true")
    void testNominalExecution() {
        AtomicInteger executedCount = new AtomicInteger(0);
        AbilityEffect customEffect = ctx -> executedCount.incrementAndGet();

        AbilityDefinition sweep = new AbilityDefinition(
                "test_sweep",
                AbilityTrigger.PERIODIC,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.KNOCKBACK
        );

        AbilityEngine engine = new AbilityEngine(
                Map.of("test_sweep", sweep),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.KNOCKBACK, customEffect),
                null
        );

        boolean ok = engine.executeAbility("test_sweep", phase, session, fakeDragon,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 50L);

        assertTrue(ok);
        assertEquals(1, executedCount.get());

        // La habilidad ahora debe estar en cooldown (cooldownTicks=100 -> hasta tick 150)
        assertFalse(engine.getCooldownTracker().isReady("test_sweep", 100L));
        assertTrue(engine.getCooldownTracker().isReady("test_sweep", 150L));

        // Segundo intento en tick 100 es bloqueado
        boolean blocked = engine.executeAbility("test_sweep", phase, session, fakeDragon,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 100L);
        assertFalse(blocked);
        assertEquals(1, executedCount.get());
    }

    @Test
    @DisplayName("Habilidad no existente en el catálogo retorna false y no lanza excepción")
    void testUnknownAbility() {
        AbilityEngine engine = new AbilityEngine(Map.of(), null);

        boolean ok = engine.executeAbility("unknown", phase, session, fakeDragon,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 10L);

        assertFalse(ok);
    }

    @Test
    @DisplayName("Seguridad ante fallos en el efecto (no tumba el servidor ni arroja excepción)")
    void testEffectExceptionSafety() {
        AbilityEffect failingEffect = ctx -> {
            throw new RuntimeException("Simulated runtime error");
        };

        AbilityDefinition failingAbility = new AbilityDefinition(
                "failing",
                AbilityTrigger.PERIODIC,
                50L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.DAMAGE
        );

        AbilityEngine engine = new AbilityEngine(
                Map.of("failing", failingAbility),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.DAMAGE, failingEffect),
                null
        );

        // Debe capturar la excepción y retornar false limpiamente
        boolean ok = engine.executeAbility("failing", phase, session, fakeDragon,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 10L);

        assertFalse(ok);
    }

    @Test
    @DisplayName("tickPeriodicAbilities solo ejecuta habilidades de tipo PERIODIC")
    void testTickPeriodicOnly() {
        List<String> executed = new ArrayList<>();

        AbilityDefinition periodic = new AbilityDefinition(
                "p1", AbilityTrigger.PERIODIC, 10L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);
        AbilityDefinition onEnter = new AbilityDefinition(
                "e1", AbilityTrigger.ON_PHASE_ENTER, 10L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);

        PhaseDefinition testPhase = new PhaseDefinition("p", 0, 1.0, List.of("p1", "e1"));

        AbilityEngine engine = new AbilityEngine(
                Map.of("p1", periodic, "e1", onEnter),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.SOUND, ctx -> executed.add(ctx.ability().id())),
                null
        );

        engine.tickPeriodicAbilities(testPhase, session, fakeDragon, 10L);

        assertEquals(1, executed.size());
        assertEquals("p1", executed.getFirst());
    }

    @Test
    @DisplayName("triggerPhaseEnterAbilities solo ejecuta habilidades ON_PHASE_ENTER")
    void testTriggerPhaseEnterOnly() {
        List<String> executed = new ArrayList<>();

        AbilityDefinition periodic = new AbilityDefinition(
                "p1", AbilityTrigger.PERIODIC, 10L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);
        AbilityDefinition onEnter = new AbilityDefinition(
                "e1", AbilityTrigger.ON_PHASE_ENTER, 10L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);

        PhaseDefinition testPhase = new PhaseDefinition("p", 0, 1.0, List.of("p1", "e1"));

        AbilityEngine engine = new AbilityEngine(
                Map.of("p1", periodic, "e1", onEnter),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.SOUND, ctx -> executed.add(ctx.ability().id())),
                null
        );

        engine.triggerPhaseEnterAbilities(testPhase, session, fakeDragon, 10L);

        assertEquals(1, executed.size());
        assertEquals("e1", executed.getFirst());
    }

    @Test
    @DisplayName("Aislamiento estricto de cooldowns entre dos batallas independientes")
    void testIsolationBetweenBattles() {
        AbilityDefinition sweep = new AbilityDefinition(
                "sweep", AbilityTrigger.PERIODIC, 200L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.KNOCKBACK);

        AbilityEngine engine1 = new AbilityEngine(
                Map.of("sweep", sweep),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.KNOCKBACK, ctx -> {}),
                null);

        AbilityEngine engine2 = new AbilityEngine(
                Map.of("sweep", sweep),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.KNOCKBACK, ctx -> {}),
                null);

        // Ejecutar en engine1
        engine1.executeAbility("sweep", phase, session, fakeDragon, AbilityTrigger.PERIODIC,
                Optional.empty(), Optional.empty(), 50L);

        // engine1 debe estar en cooldown
        assertFalse(engine1.getCooldownTracker().isReady("sweep", 100L));

        // engine2 debe permanecer totalmente libre y listo
        assertTrue(engine2.getCooldownTracker().isReady("sweep", 100L));
    }

    @Test
    @DisplayName("Incompatibilidad de trigger rechaza la ejecución limpiamente")
    void testTriggerMismatchRejectsExecution() {
        AtomicInteger count = new AtomicInteger(0);
        AbilityDefinition periodicAbility = new AbilityDefinition(
                "periodic_only", AbilityTrigger.PERIODIC, 100L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);

        AbilityEngine engine = new AbilityEngine(
                Map.of("periodic_only", periodicAbility),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.SOUND, ctx -> count.incrementAndGet()),
                null);

        // Intentar ejecutar con ON_PHASE_ENTER -> debe rechazar
        boolean executed = engine.executeAbility("periodic_only", phase, session, fakeDragon,
                AbilityTrigger.ON_PHASE_ENTER, Optional.empty(), Optional.empty(), 10L);

        assertFalse(executed, "La habilidad no debe ejecutarse si el trigger invocado no coincide con el configurado");
        assertEquals(0, count.get(), "El efecto no debe haberse ejecutado");
        assertTrue(engine.getCooldownTracker().isReady("periodic_only", 10L), "No debe haber entrado en cooldown");
    }

    @Test
    @DisplayName("Errores graves de JVM (Error) no son silenciados por AbilityEngine")
    void testJvmErrorNotSwallowed() {
        AbilityDefinition errorAbility = new AbilityDefinition(
                "fatal", AbilityTrigger.PERIODIC, 100L,
                TargetSelectorType.ALL_IN_ARENA, EffectOriginType.DRAGON_BODY, AbilityEffectType.SOUND);

        AbilityEngine engine = new AbilityEngine(
                Map.of("fatal", errorAbility),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.SOUND, ctx -> {
                    throw new AssertionError("Simulated JVM assertion error");
                }),
                null);

        assertThrows(AssertionError.class, () -> engine.executeAbility(
                "fatal", phase, session, fakeDragon, AbilityTrigger.PERIODIC,
                Optional.empty(), Optional.empty(), 10L));
    }

    @Test
    @DisplayName("NEAREST_PLAYER calcula distancia respecto al EffectOrigin resuelto (ej. DRAGON_HEAD vs DRAGON_BODY)")
    void testNearestPlayerUsesResolvedOrigin() {
        List<Player> players = new ArrayList<>();
        World worldWithPlayers = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world_the_end";
                    if (method.getName().equals("getPlayers")) return players;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        Location dLoc = new Location(worldWithPlayers, 0.0, 65.0, 0.0);
        dLoc.setDirection(new Vector(0, 0, 1)); // mirando hacia +Z
        Location eye = new Location(worldWithPlayers, 0.0, 66.0, 0.0);
        eye.setDirection(new Vector(0, 0, 1));

        EnderDragon dragonWithWorld = (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getWorld")) return worldWithPlayers;
                    if (method.getName().equals("getLocation")) return dLoc.clone();
                    if (method.getName().equals("getEyeLocation")) return eye.clone();
                    if (method.getName().equals("isValid")) return true;
                    return null;
                }
        );

        // DRAGON_HEAD estará en (0, 66, 3)
        // Jugador A está en (0, 65, -4) -> distancia al body: 4.0, distancia a la cabeza: ~7.0
        // Jugador B está en (0, 66, 4)  -> distancia a la cabeza: 1.0, distancia al body: ~4.12
        Player playerNearBody = createFakeTestPlayer(UUID.randomUUID(), new Location(worldWithPlayers, 0, 65, -4));
        Player playerNearHead = createFakeTestPlayer(UUID.randomUUID(), new Location(worldWithPlayers, 0, 66, 4));
        players.addAll(List.of(playerNearBody, playerNearHead));

        List<Player> capturedTargets = new ArrayList<>();

        AbilityDefinition headAbility = new AbilityDefinition(
                "head_strike", AbilityTrigger.PERIODIC, 0L,
                TargetSelectorType.NEAREST_PLAYER, EffectOriginType.DRAGON_HEAD, AbilityEffectType.SOUND);

        AbilityEngine engine = new AbilityEngine(
                Map.of("head_strike", headAbility),
                new AbilityCooldownTracker(),
                new TargetSelector(),
                new LocationResolver(),
                Map.of(AbilityEffectType.SOUND, ctx -> capturedTargets.addAll(ctx.resolvedTargets())),
                null);

        boolean ok = engine.executeAbility("head_strike", phase, session, dragonWithWorld,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 10L);

        assertTrue(ok);
        assertEquals(1, capturedTargets.size());
        assertEquals(playerNearHead, capturedTargets.getFirst(),
                "Para DRAGON_HEAD, el jugador más cercano a la cabeza debe ser seleccionado, no el más cercano al cuerpo");
    }

    private Player createFakeTestPlayer(UUID uuid, Location loc) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return uuid;
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("getGameMode")) return GameMode.SURVIVAL;
                    if (name.equals("isDead")) return false;
                    if (name.equals("isOnline")) return true;
                    if (name.equals("isValid")) return true;
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("hashCode")) return uuid.hashCode();
                    return null;
                }
        );
    }
}
