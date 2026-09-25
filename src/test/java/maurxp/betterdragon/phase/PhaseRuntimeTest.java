package maurxp.betterdragon.phase;

import maurxp.betterdragon.ability.AbilityEngine;
import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.phase.event.BetterDragonPhaseChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link PhaseRuntime}.
 *
 * @author maurxp
 */
class PhaseRuntimeTest {

    private BattleSession session;
    private List<PhaseDefinition> phases;
    private AbilityEngine abilityEngine;
    private List<BetterDragonPhaseChangeEvent> emittedEvents;

    @BeforeEach
    void setUp() {
        session = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());
        session.start(); // IDLE -> PREPARING
        // Note: session is not ACTIVE yet unless activate() is called, but we can test PhaseRuntime directly or with ACTIVE state.
        phases = List.of(
                new PhaseDefinition("phase_1", 0, 1.0),
                new PhaseDefinition("phase_2", 1, 0.75),
                new PhaseDefinition("phase_3", 2, 0.50),
                new PhaseDefinition("phase_4", 3, 0.25)
        );
        abilityEngine = new AbilityEngine(Map.of(), null);
        emittedEvents = new ArrayList<>();
    }

    @Test
    @DisplayName("Cálculo robusto de health ratio con valores límite y atípicos")
    void testCalculateHealthRatio() {
        assertEquals(1.0, PhaseRuntime.calculateHealthRatio(200.0, 200.0), 0.0001);
        assertEquals(0.5, PhaseRuntime.calculateHealthRatio(100.0, 200.0), 0.0001);
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(0.0, 200.0), 0.0001);

        // Clamping
        assertEquals(1.0, PhaseRuntime.calculateHealthRatio(250.0, 200.0), 0.0001);
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(-10.0, 200.0), 0.0001);

        // Protección contra división por cero, valores no finitos
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(100.0, 0.0), 0.0001);
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(100.0, -200.0), 0.0001);
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(Double.NaN, 200.0), 0.0001);
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(100.0, Double.NaN), 0.0001);
        assertEquals(0.0, PhaseRuntime.calculateHealthRatio(Double.POSITIVE_INFINITY, 200.0), 0.0001);
    }

    @Test
    @DisplayName("Inicialización en salud completa fija la fase 1 y emite evento con previousPhase nulo")
    void testInitializeFullHealth() {
        PhaseRuntime runtime = new PhaseRuntime(session, phases, abilityEngine, emittedEvents::add, null);

        runtime.initialize(200.0, 200.0, 10L, null);

        assertTrue(runtime.isInitialized());
        assertEquals("phase_1", runtime.getCurrentPhase().id());
        assertEquals(0, runtime.getCurrentPhaseIndex());
        assertEquals(10L, runtime.getPhaseStartTick());
        assertEquals(0, runtime.getTransitionCount());

        assertEquals(1, emittedEvents.size());
        assertTrue(emittedEvents.getFirst().getPreviousPhase().isEmpty());
        assertEquals("phase_1", emittedEvents.getFirst().getNewPhase().id());
    }

    @Test
    @DisplayName("Inicialización con salud reducida califica directamente para una fase avanzada")
    void testInitializePartialHealth() {
        PhaseRuntime runtime = new PhaseRuntime(session, phases, abilityEngine, emittedEvents::add, null);

        // Salud 80.0 / 200.0 = 40% (<= 50% threshold de phase_3)
        runtime.initialize(80.0, 200.0, 15L, null);

        assertTrue(runtime.isInitialized());
        assertEquals("phase_3", runtime.getCurrentPhase().id());
        assertEquals(2, runtime.getCurrentPhaseIndex());
    }

    @Test
    @DisplayName("Transición normal por daño hacia adelante")
    void testNormalTransition() {
        PhaseRuntime runtime = new PhaseRuntime(session, phases, abilityEngine, emittedEvents::add, null);
        runtime.initialize(200.0, 200.0, 10L, null);

        // Simular que la sesión está activa
        session.activate(new maurxp.betterdragon.battle.model.DragonIdentity(
                UUID.randomUUID(), session.getBattleId(), "default", 1));

        // Bajar salud a 140.0 / 200.0 = 70% (<= 0.75 de phase_2)
        boolean transitioned = runtime.updateHealth(140.0, 200.0, 50L, null);

        assertTrue(transitioned);
        assertEquals("phase_2", runtime.getCurrentPhase().id());
        assertEquals(1, runtime.getCurrentPhaseIndex());
        assertEquals(50L, runtime.getPhaseStartTick());
        assertEquals(1, runtime.getTransitionCount());

        assertEquals(2, emittedEvents.size());
        assertEquals("phase_1", emittedEvents.get(1).getPreviousPhase().orElseThrow().id());
        assertEquals("phase_2", emittedEvents.get(1).getNewPhase().id());
    }

    @Test
    @DisplayName("Monotonicidad estricta: curación del dragón no revierte la fase")
    void testMonotonicityNoRegression() {
        PhaseRuntime runtime = new PhaseRuntime(session, phases, abilityEngine, emittedEvents::add, null);
        runtime.initialize(200.0, 200.0, 10L, null);
        session.activate(new maurxp.betterdragon.battle.model.DragonIdentity(
                UUID.randomUUID(), session.getBattleId(), "default", 1));

        // Avanzar a fase 2
        runtime.updateHealth(140.0, 200.0, 50L, null);
        assertEquals("phase_2", runtime.getCurrentPhase().id());

        // Curar dragón a 190.0 / 200.0 = 95%
        boolean transitioned = runtime.updateHealth(190.0, 200.0, 60L, null);

        assertFalse(transitioned, "La curación no debe provocar una transición");
        assertEquals("phase_2", runtime.getCurrentPhase().id(), "La fase debe permanecer en phase_2 sin retroceder");
        assertEquals(1, runtime.getCurrentPhaseIndex());
        assertEquals(1, runtime.getTransitionCount());
    }

    @Test
    @DisplayName("Salto masivo de fases: impacto masivo salta múltiples umbrales deterministamente")
    void testMultiPhaseSkip() {
        PhaseRuntime runtime = new PhaseRuntime(session, phases, abilityEngine, emittedEvents::add, null);
        runtime.initialize(200.0, 200.0, 10L, null);
        session.activate(new maurxp.betterdragon.battle.model.DragonIdentity(
                UUID.randomUUID(), session.getBattleId(), "default", 1));

        // Caer de 100% a 20% (<= 0.25 de phase_4)
        boolean transitioned = runtime.updateHealth(40.0, 200.0, 100L, null);

        assertTrue(transitioned);
        assertEquals("phase_4", runtime.getCurrentPhase().id());
        assertEquals(3, runtime.getCurrentPhaseIndex());
        assertEquals(1, runtime.getTransitionCount());
    }

    @Test
    @DisplayName("Fase final: reducción posterior de salud no provoca más transiciones")
    void testFinalPhase() {
        PhaseRuntime runtime = new PhaseRuntime(session, phases, abilityEngine, emittedEvents::add, null);
        runtime.initialize(200.0, 200.0, 10L, null);
        session.activate(new maurxp.betterdragon.battle.model.DragonIdentity(
                UUID.randomUUID(), session.getBattleId(), "default", 1));

        // Avanzar a fase 4
        runtime.updateHealth(40.0, 200.0, 100L, null);
        assertEquals("phase_4", runtime.getCurrentPhase().id());

        // Bajar aún más a 10.0
        boolean transitioned = runtime.updateHealth(10.0, 200.0, 150L, null);
        assertFalse(transitioned);
        assertEquals("phase_4", runtime.getCurrentPhase().id());
    }

    @Test
    @DisplayName("Rechazo de lista de fases vacía")
    void testEmptyPhasesRejection() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseRuntime(session, List.of(), abilityEngine, emittedEvents::add, null));
    }
}
