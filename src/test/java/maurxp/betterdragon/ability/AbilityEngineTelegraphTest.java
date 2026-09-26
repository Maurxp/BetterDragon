package maurxp.betterdragon.ability;

import maurxp.betterdragon.ability.effect.AbilityEffect;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.PhaseDefinition;
import maurxp.betterdragon.util.CancellableTask;
import maurxp.betterdragon.util.DelayedTaskScheduler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragon.Phase;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para telegrafiado sensorial, tareas diferidas y disparadores avanzados de combate.
 *
 * @author maurxp
 */
class AbilityEngineTelegraphTest {

    private BattleSession session;
    private EnderDragon fakeDragon;
    private World fakeWorld;
    private PhaseDefinition phase;
    private List<Runnable> queuedTasks;
    private DelayedTaskScheduler testScheduler;

    @BeforeEach
    void setUp() {
        queuedTasks = new ArrayList<>();
        testScheduler = (runnable, delay) -> {
            queuedTasks.add(runnable);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            return () -> cancelled.set(true);
        };

        session = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID(),
                maurxp.betterdragon.config.BattleConfigurationSnapshot.defaults(), testScheduler);
        session.start();
        session.activate(maurxp.betterdragon.battle.model.DragonIdentity.of(UUID.randomUUID(), session.getBattleId()));

        fakeWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) return "world_the_end";
                    if (name.equals("getPlayers")) return List.of();
                    if (name.equals("spawnParticle")) return null;
                    if (name.equals("playSound")) return null;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        Location loc = new Location(fakeWorld, 0, 70, 0);

        fakeDragon = (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getWorld")) return fakeWorld;
                    if (name.equals("getLocation")) return loc;
                    if (name.equals("getEyeLocation")) return loc;
                    if (name.equals("isValid")) return true;
                    if (name.equals("getPhase")) return Phase.CIRCLING;
                    if (name.equals("equals")) return proxy == args[0];
                    return null;
                }
        );

        phase = new PhaseDefinition("phase_1", 0, 1.0, List.of("telegraphed_shockwave", "flight_bomb", "counter_strike"));
    }

    @Test
    @DisplayName("Habilidad con telegrafiado no ejecuta el efecto físico de inmediato sino tras la demora")
    void testTelegraphedExecutionIsDelayed() {
        AtomicBoolean physicalEffectExecuted = new AtomicBoolean(false);
        AbilityEffect physicalEffect = ctx -> physicalEffectExecuted.set(true);

        TelegraphDefinition telegraph = new TelegraphDefinition(TelegraphDefinition.TICKS_MODERATE); // 30 ticks
        AbilityDefinition ability = new AbilityDefinition(
                "telegraphed_shockwave",
                AbilityTrigger.PERIODIC,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.SHOCKWAVE,
                Map.of(),
                telegraph
        );

        AbilityEngine engine = new AbilityEngine(
                Map.of("telegraphed_shockwave", ability),
                new AbilityCooldownTracker(),
                new TargetSelector(session.getSpatialContext()),
                new LocationResolver(session.getSpatialContext()),
                Map.of(AbilityEffectType.SHOCKWAVE, physicalEffect),
                testScheduler,
                null
        );

        boolean scheduled = engine.executeAbility("telegraphed_shockwave", phase, session, fakeDragon,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 50L);

        assertTrue(scheduled, "La habilidad debe planificarse exitosamente");
        assertFalse(physicalEffectExecuted.get(), "El efecto físico NO debe ejecutarse inmediatamente durante el telegrafiado");
        assertEquals(1, queuedTasks.size(), "Debe haberse registrado una tarea diferida para el efecto físico");

        // Ejecutar la tarea diferida simulando el scheduler tras 30 ticks
        queuedTasks.get(0).run();
        assertTrue(physicalEffectExecuted.get(), "El efecto físico debe ejecutarse tras el telegrafiado");
    }

    @Test
    @DisplayName("Cancelación del ciclo de vida de la sesión invalida las tareas diferidas pendientes")
    void testPendingTaskCancelledOnSessionAbort() {
        AtomicBoolean physicalEffectExecuted = new AtomicBoolean(false);
        AbilityEffect physicalEffect = ctx -> physicalEffectExecuted.set(true);

        TelegraphDefinition telegraph = new TelegraphDefinition(30L);
        AbilityDefinition ability = new AbilityDefinition(
                "telegraphed_shockwave",
                AbilityTrigger.PERIODIC,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.SHOCKWAVE,
                Map.of(),
                telegraph
        );

        AtomicBoolean taskCancelled = new AtomicBoolean(false);
        DelayedTaskScheduler cancellableScheduler = (runnable, delay) -> {
            queuedTasks.add(runnable);
            return () -> taskCancelled.set(true);
        };

        BattleId customBattleId = BattleId.random();
        BattleSession customSession = BattleSession.create(customBattleId, "world_the_end", UUID.randomUUID(),
                maurxp.betterdragon.config.BattleConfigurationSnapshot.defaults(), cancellableScheduler);
        customSession.start();
        customSession.activate(maurxp.betterdragon.battle.model.DragonIdentity.of(UUID.randomUUID(), customBattleId));

        AbilityEngine engine = new AbilityEngine(
                Map.of("telegraphed_shockwave", ability),
                new AbilityCooldownTracker(),
                new TargetSelector(customSession.getSpatialContext()),
                new LocationResolver(customSession.getSpatialContext()),
                Map.of(AbilityEffectType.SHOCKWAVE, physicalEffect),
                cancellableScheduler,
                null
        );

        engine.executeAbility("telegraphed_shockwave", phase, customSession, fakeDragon,
                AbilityTrigger.PERIODIC, Optional.empty(), Optional.empty(), 50L);

        assertEquals(1, queuedTasks.size());

        // Al abortar la sesión, se deben cancelar las tareas pendientes
        customSession.abort();
        assertTrue(taskCancelled.get(), "La tarea diferida pendiente debe cancelarse al abortar la sesión");
    }

    @Test
    @DisplayName("Disparador ON_FLIGHT_PHASE ejecuta únicamente si coincide con la fase de vuelo configurada")
    void testFlightPhaseTriggerMatching() {
        AtomicInteger bombCount = new AtomicInteger(0);
        AbilityEffect bombEffect = ctx -> bombCount.incrementAndGet();

        AbilityDefinition circlingBomb = new AbilityDefinition(
                "flight_bomb",
                AbilityTrigger.ON_FLIGHT_PHASE,
                100L,
                TargetSelectorType.ALL_IN_ARENA,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.CARPET_BOMB,
                Map.of("flight_phase", "CIRCLING"),
                null
        );

        AbilityEngine engine = new AbilityEngine(
                Map.of("flight_bomb", circlingBomb),
                new AbilityCooldownTracker(),
                new TargetSelector(session.getSpatialContext()),
                new LocationResolver(session.getSpatialContext()),
                Map.of(AbilityEffectType.CARPET_BOMB, bombEffect),
                testScheduler,
                null
        );

        // Disparo con fase no coincidente: STRAFING
        engine.triggerFlightPhaseAbilities(Phase.STRAFING, phase, session, fakeDragon, 100L);
        assertEquals(0, bombCount.get(), "No debe disparar si la fase no coincide");

        // Disparo con fase coincidente: CIRCLING
        engine.triggerFlightPhaseAbilities(Phase.CIRCLING, phase, session, fakeDragon, 100L);
        assertEquals(1, bombCount.get(), "Debe disparar al coincidir con CIRCLING");
    }

    @Test
    @DisplayName("Disparador ON_DAMAGE respeta cooldown por atacante y condición ranged_only")
    void testCounterattackTriggerAndAttackerCooldown() {
        AtomicInteger counterHits = new AtomicInteger(0);
        AbilityEffect counterEffect = ctx -> counterHits.incrementAndGet();

        AbilityDefinition counterAbility = new AbilityDefinition(
                "counter_strike",
                AbilityTrigger.ON_DAMAGE,
                0L, // Cooldown global 0
                TargetSelectorType.TRIGGERING_PLAYER,
                EffectOriginType.DRAGON_BODY,
                AbilityEffectType.KNOCKBACK,
                Map.of("attacker_cooldown_ticks", 100L, "ranged_only", true, "chance", 1.0),
                null
        );

        AbilityEngine engine = new AbilityEngine(
                Map.of("counter_strike", counterAbility),
                new AbilityCooldownTracker(),
                new TargetSelector(session.getSpatialContext()),
                new LocationResolver(session.getSpatialContext()),
                Map.of(AbilityEffectType.KNOCKBACK, counterEffect),
                testScheduler,
                null
        );

        UUID attackerA = UUID.randomUUID();
        Player fakeAttackerA = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return attackerA;
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("isDead")) return false;
                    return null;
                }
        );

        UUID attackerB = UUID.randomUUID();
        Player fakeAttackerB = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return attackerB;
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("isDead")) return false;
                    return null;
                }
        );

        Location loc = new Location(fakeWorld, 10, 70, 10);

        // 1. Daño cuerpo a cuerpo no activa si está configurado ranged_only = true
        engine.triggerDamageAbilities(phase, session, fakeDragon, fakeAttackerA, false, loc, 100L);
        assertEquals(0, counterHits.get(), "Daño melee no debe disparar si ranged_only = true");

        // 2. Daño a distancia activa contrataque
        engine.triggerDamageAbilities(phase, session, fakeDragon, fakeAttackerA, true, loc, 100L);
        assertEquals(1, counterHits.get(), "Daño ranged debe disparar contrataque");

        // 3. Segundo golpe de Attacker A en tick 150 bloqueado por cooldown por atacante (100 ticks)
        engine.triggerDamageAbilities(phase, session, fakeDragon, fakeAttackerA, true, loc, 150L);
        assertEquals(1, counterHits.get(), "Segundo golpe de Attacker A bloqueado por cooldown individual");

        // 4. Golpe de Attacker B en tick 150 procede exitosamente (aislamiento por atacante)
        engine.triggerDamageAbilities(phase, session, fakeDragon, fakeAttackerB, true, loc, 150L);
        assertEquals(2, counterHits.get(), "Attacker B tiene recarga independiente y debe disparar");

        // 5. Attacker A vuelve a disparar en tick 201
        engine.triggerDamageAbilities(phase, session, fakeDragon, fakeAttackerA, true, loc, 201L);
        assertEquals(3, counterHits.get(), "Attacker A expira recarga en tick 201 y dispara");
    }
}
