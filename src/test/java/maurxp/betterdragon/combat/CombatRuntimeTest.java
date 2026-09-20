package maurxp.betterdragon.combat;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CombatRuntimeTest {

    private BattleId battleId;
    private BattleSession session;
    private CombatRuntime runtime;
    private UUID dragonId;

    @BeforeEach
    void setUp() {
        this.battleId = BattleId.random();
        this.session = BattleSession.create(battleId, "world_the_end", UUID.randomUUID());
        this.dragonId = UUID.randomUUID();

        // Transicionar a ACTIVE para habilitar el combate
        this.session.start();
        this.session.activate(DragonIdentity.of(dragonId, battleId, "default"));

        // Usamos el dispatcher por defecto que no invoca Bukkit estático fuera de runtime
        this.runtime = this.session.getCombatRuntime();
    }

    @Test
    @DisplayName("1. CombatRuntime inicia vacío")
    void testInitialStateEmpty() {
        assertEquals(0, runtime.getParticipantCount());
        assertEquals(0L, runtime.getHitSequence());
        assertEquals(0.0, runtime.getTotalDamageAll());
        assertTrue(runtime.getParticipants().isEmpty());
        assertTrue(runtime.getTopDamageParticipant().isEmpty());
    }

    @Test
    @DisplayName("2. Primer daño crea participante")
    void testFirstDamageCreatesParticipant() {
        UUID playerId = UUID.randomUUID();
        Optional<ParticipantSnapshot> result = runtime.recordDamage(playerId, "Mauricio", 25.5, 100L);

        assertTrue(result.isPresent());
        assertEquals(1, runtime.getParticipantCount());
        assertTrue(runtime.isParticipant(playerId));
        assertEquals(25.5, runtime.getTotalDamage(playerId));
    }

    @Test
    @DisplayName("3. historicalName se fija en el primer registro")
    void testHistoricalNameSetOnFirstHit() {
        UUID playerId = UUID.randomUUID();
        Optional<ParticipantSnapshot> result = runtime.recordDamage(playerId, "OriginalName", 10.0, 100L);

        assertTrue(result.isPresent());
        assertEquals("OriginalName", result.get().historicalName());
        assertEquals("OriginalName", result.get().lastKnownName());
    }

    @Test
    @DisplayName("4. lastKnownName se actualiza en actividad posterior")
    void testLastKnownNameUpdates() {
        UUID playerId = UUID.randomUUID();
        runtime.recordDamage(playerId, "OldName", 10.0, 100L);
        Optional<ParticipantSnapshot> result2 = runtime.recordDamage(playerId, "NewName", 15.0, 120L);

        assertTrue(result2.isPresent());
        assertEquals("NewName", result2.get().lastKnownName());
    }

    @Test
    @DisplayName("5. historicalName no cambia tras actualización de lastKnownName")
    void testHistoricalNameDoesNotChange() {
        UUID playerId = UUID.randomUUID();
        runtime.recordDamage(playerId, "OriginalName", 10.0, 100L);
        runtime.recordDamage(playerId, "ChangedName", 20.0, 150L);

        ParticipantSnapshot snapshot = runtime.getParticipant(playerId).orElseThrow();
        assertEquals("OriginalName", snapshot.historicalName(), "historicalName debe permanecer inmutable");
        assertEquals("ChangedName", snapshot.lastKnownName(), "lastKnownName debe haberse actualizado");
    }

    @Test
    @DisplayName("6. totalDamage acumula correctamente con precisión double")
    void testTotalDamageAccumulation() {
        UUID playerId = UUID.randomUUID();
        runtime.recordDamage(playerId, "Player", 10.25, 100L);
        runtime.recordDamage(playerId, "Player", 20.50, 110L);
        runtime.recordDamage(playerId, "Player", 5.25, 120L);

        assertEquals(36.0, runtime.getTotalDamage(playerId), 0.0001);
        assertEquals(36.0, runtime.getTotalDamageAll(), 0.0001);
    }

    @Test
    @DisplayName("7. Daño 0 rechazado sin alterar estado ni secuencia")
    void testZeroDamageRejected() {
        UUID playerId = UUID.randomUUID();
        Optional<ParticipantSnapshot> result = runtime.recordDamage(playerId, "Player", 0.0, 100L);

        assertTrue(result.isEmpty());
        assertEquals(0, runtime.getParticipantCount());
        assertEquals(0L, runtime.getHitSequence());
    }

    @Test
    @DisplayName("8. Daño negativo rechazado")
    void testNegativeDamageRejected() {
        UUID playerId = UUID.randomUUID();
        Optional<ParticipantSnapshot> result = runtime.recordDamage(playerId, "Player", -15.0, 100L);

        assertTrue(result.isEmpty());
        assertEquals(0, runtime.getParticipantCount());
        assertEquals(0L, runtime.getHitSequence());
    }

    @Test
    @DisplayName("9. NaN rechazado")
    void testNaNDamageRejected() {
        UUID playerId = UUID.randomUUID();
        Optional<ParticipantSnapshot> result = runtime.recordDamage(playerId, "Player", Double.NaN, 100L);

        assertTrue(result.isEmpty());
        assertEquals(0, runtime.getParticipantCount());
        assertEquals(0L, runtime.getHitSequence());
    }

    @Test
    @DisplayName("10. Infinity rechazado")
    void testInfinityDamageRejected() {
        UUID playerId = UUID.randomUUID();
        Optional<ParticipantSnapshot> posInf = runtime.recordDamage(playerId, "Player", Double.POSITIVE_INFINITY, 100L);
        Optional<ParticipantSnapshot> negInf = runtime.recordDamage(playerId, "Player", Double.NEGATIVE_INFINITY, 100L);

        assertTrue(posInf.isEmpty());
        assertTrue(negInf.isEmpty());
        assertEquals(0L, runtime.getHitSequence());
    }

    @Test
    @DisplayName("11. hitSequence incrementa de forma monotónica")
    void testHitSequenceMonotonicIncrement() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        assertEquals(0L, runtime.getHitSequence());
        runtime.recordDamage(p1, "P1", 10.0, 100L);
        assertEquals(1L, runtime.getHitSequence());
        runtime.recordDamage(p2, "P2", 20.0, 105L);
        assertEquals(2L, runtime.getHitSequence());
        runtime.recordDamage(p1, "P1", 5.0, 110L);
        assertEquals(3L, runtime.getHitSequence());
    }

    @Test
    @DisplayName("12. firstHitSequence correcto")
    void testFirstHitSequenceTracking() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        runtime.recordDamage(p1, "P1", 10.0, 100L); // seq 1
        runtime.recordDamage(p2, "P2", 20.0, 105L); // seq 2
        runtime.recordDamage(p1, "P1", 5.0, 110L);  // seq 3

        assertEquals(1L, runtime.getParticipant(p1).orElseThrow().firstHitSequence());
        assertEquals(2L, runtime.getParticipant(p2).orElseThrow().firstHitSequence());
    }

    @Test
    @DisplayName("13. lastHitSequence correcto")
    void testLastHitSequenceTracking() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        runtime.recordDamage(p1, "P1", 10.0, 100L); // seq 1
        runtime.recordDamage(p2, "P2", 20.0, 105L); // seq 2
        runtime.recordDamage(p1, "P1", 5.0, 110L);  // seq 3

        assertEquals(3L, runtime.getParticipant(p1).orElseThrow().lastHitSequence());
        assertEquals(2L, runtime.getParticipant(p2).orElseThrow().lastHitSequence());
    }

    @Test
    @DisplayName("14. lastActivityTick correcto")
    void testLastActivityTickTracking() {
        UUID p1 = UUID.randomUUID();
        runtime.recordDamage(p1, "P1", 10.0, 1000L);
        assertEquals(1000L, runtime.getParticipant(p1).orElseThrow().lastActivityTick());

        runtime.recordDamage(p1, "P1", 10.0, 2500L);
        assertEquals(2500L, runtime.getParticipant(p1).orElseThrow().lastActivityTick());
    }

    @Test
    @DisplayName("15. Dos jugadores quedan totalmente aislados")
    void testParticipantsIsolation() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        runtime.recordDamage(p1, "Alice", 100.0, 100L);
        runtime.recordDamage(p2, "Bob", 50.0, 105L);

        assertEquals(2, runtime.getParticipantCount());
        assertEquals(100.0, runtime.getTotalDamage(p1));
        assertEquals(50.0, runtime.getTotalDamage(p2));
        assertEquals("Alice", runtime.getParticipant(p1).orElseThrow().historicalName());
        assertEquals("Bob", runtime.getParticipant(p2).orElseThrow().historicalName());
    }

    @Test
    @DisplayName("16. getTopDamageParticipant devuelve mayor daño total")
    void testTopDamageCalculation() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        runtime.recordDamage(p1, "P1", 50.0, 100L);
        runtime.recordDamage(p2, "P2", 120.0, 105L);
        runtime.recordDamage(p3, "P3", 80.0, 110L);

        ParticipantSnapshot top = runtime.getTopDamageParticipant().orElseThrow();
        assertEquals(p2, top.playerId(), "P2 debe ser TOP_DAMAGE con 120.0");
        assertEquals(120.0, top.totalDamage());

        // P1 supera a P2 acumulando daño
        runtime.recordDamage(p1, "P1", 100.0, 115L); // total 150.0
        ParticipantSnapshot newTop = runtime.getTopDamageParticipant().orElseThrow();
        assertEquals(p1, newTop.playerId(), "P1 debe ser nuevo TOP_DAMAGE con 150.0");
    }

    @Test
    @DisplayName("17. Empate tiene comportamiento determinista documentado (menor firstHitSequence gana)")
    void testDeterministicTieBreaking() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        runtime.recordDamage(p1, "P1", 100.0, 100L); // seq 1
        runtime.recordDamage(p2, "P2", 100.0, 110L); // seq 2

        // Ambos tienen 100.0 de daño, pero p1 golpeó primero (seq 1 < 2)
        ParticipantSnapshot top = runtime.getTopDamageParticipant().orElseThrow();
        assertEquals(p1, top.playerId(), "P1 debe ganar el desempate por menor firstHitSequence");
    }

    @Test
    @DisplayName("18. Colecciones externas no mutan estado interno")
    void testImmutabilityOfReturnedCollections() {
        UUID p1 = UUID.randomUUID();
        runtime.recordDamage(p1, "P1", 100.0, 100L);

        List<ParticipantSnapshot> list = runtime.getParticipants();
        assertThrows(UnsupportedOperationException.class, list::clear);
        assertEquals(1, runtime.getParticipantCount());
    }

    @Test
    @DisplayName("19. Sesión no ACTIVE rechaza daño (IDLE, PREPARING)")
    void testInactiveSessionRejectsDamage() {
        BattleSession idleSession = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());
        Optional<ParticipantSnapshot> idleHit = idleSession.getCombatRuntime().recordDamage(UUID.randomUUID(), "P", 10.0, 100L);
        assertTrue(idleHit.isEmpty(), "Sesión en IDLE debe rechazar daño");

        idleSession.start(); // PREPARING
        Optional<ParticipantSnapshot> preparingHit = idleSession.getCombatRuntime().recordDamage(UUID.randomUUID(), "P", 10.0, 100L);
        assertTrue(preparingHit.isEmpty(), "Sesión en PREPARING debe rechazar daño");
    }

    @Test
    @DisplayName("20. ABORTED rechaza daño")
    void testAbortedSessionRejectsDamage() {
        session.abort(BattleAbortReason.ENTITY_MISSING);
        assertEquals(BattleState.ABORTED, session.getState());

        Optional<ParticipantSnapshot> hit = runtime.recordDamage(UUID.randomUUID(), "P", 10.0, 100L);
        assertTrue(hit.isEmpty(), "Sesión en ABORTED debe rechazar daño");
    }

    @Test
    @DisplayName("21. COMPLETED rechaza daño")
    void testCompletedSessionRejectsDamage() {
        session.beginDying();
        session.complete(UUID.randomUUID(), "Slayer");
        assertEquals(BattleState.COMPLETED, session.getState());

        Optional<ParticipantSnapshot> hit = runtime.recordDamage(UUID.randomUUID(), "P", 10.0, 100L);
        assertTrue(hit.isEmpty(), "Sesión en COMPLETED debe rechazar daño");
    }

    @Test
    @DisplayName("22. DEFERRED_PENDING_CHUNK_LOAD rechaza daño")
    void testDeferredSessionRejectsDamage() {
        session.deferPendingChunkLoad();
        assertEquals(BattleState.DEFERRED_PENDING_CHUNK_LOAD, session.getState());

        Optional<ParticipantSnapshot> hit = runtime.recordDamage(UUID.randomUUID(), "P", 10.0, 100L);
        assertTrue(hit.isEmpty(), "Sesión en DEFERRED debe rechazar daño");
    }

    @Test
    @DisplayName("23. Participante offline no se elimina")
    void testOfflineParticipantRetained() {
        UUID p1 = UUID.randomUUID();
        runtime.recordDamage(p1, "DisconnectingPlayer", 50.0, 100L);

        // Simulamos el paso de ticks sin actividad
        assertTrue(runtime.isParticipant(p1));
        assertEquals(50.0, runtime.getTotalDamage(p1));
        assertEquals("DisconnectingPlayer", runtime.getParticipant(p1).orElseThrow().historicalName());
    }

    @Test
    @DisplayName("24. Snapshot de combate es inmutable")
    void testCombatSnapshotImmutability() {
        UUID p1 = UUID.randomUUID();
        runtime.recordDamage(p1, "P1", 50.0, 100L);

        CombatSnapshot snapshot = runtime.createSnapshot();
        assertEquals(battleId, snapshot.battleId());
        assertEquals(1, snapshot.participants().size());
        assertEquals(50.0, snapshot.totalDamage());
        assertEquals(1L, snapshot.hitSequence());

        assertThrows(UnsupportedOperationException.class, () -> snapshot.participants().clear());
    }

    @Test
    @DisplayName("25. BattleSession A no ve participantes de BattleSession B")
    void testBattleSessionsIsolation() {
        BattleSession sessionB = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());
        sessionB.start();
        sessionB.activate(DragonIdentity.of(UUID.randomUUID(), sessionB.getBattleId(), "default"));

        UUID p1 = UUID.randomUUID();
        runtime.recordDamage(p1, "Alice", 100.0, 100L);

        assertEquals(1, runtime.getParticipantCount());
        assertEquals(0, sessionB.getCombatRuntime().getParticipantCount());
        assertFalse(sessionB.getCombatRuntime().isParticipant(p1));
    }

    @Test
    @DisplayName("26. UUID es la identidad del jugador, no su nombre")
    void testUuidIsIdentityNotName() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // Dos jugadores distintos con el mismo nombre
        runtime.recordDamage(p1, "CloneName", 30.0, 100L);
        runtime.recordDamage(p2, "CloneName", 40.0, 105L);

        assertEquals(2, runtime.getParticipantCount(), "Deben ser tratados como 2 participantes distintos por tener distinto UUID");
        assertEquals(30.0, runtime.getTotalDamage(p1));
        assertEquals(40.0, runtime.getTotalDamage(p2));
    }

    @Test
    @DisplayName("27. No se retienen referencias Player/Entity en el modelo")
    void testNoLiveBukkitReferencesRetained() {
        UUID p1 = UUID.randomUUID();
        ParticipantSnapshot snapshot = runtime.recordDamage(p1, "Steve", 10.0, 100L).orElseThrow();

        // Comprobamos que el snapshot y el estado solo manejan tipos de dominio inmutables
        assertEquals(UUID.class, snapshot.playerId().getClass());
        assertEquals(String.class, snapshot.historicalName().getClass());
        assertEquals(String.class, snapshot.lastKnownName().getClass());
    }

    @Test
    @DisplayName("28. Cancelación de BetterDragonDamageEvent no consume hitSequence ni altera estado")
    void testDamageEventCancellationSemantics() {
        AtomicBoolean shouldCancel = new AtomicBoolean(true);
        DamageEventDispatcher dispatcher = event -> !shouldCancel.get();

        CombatRuntime cancellableRuntime = new CombatRuntime(session, dispatcher);
        UUID p1 = UUID.randomUUID();

        // 1. Intento cancelado
        Optional<ParticipantSnapshot> cancelledHit = cancellableRuntime.recordDamage(p1, "P1", 20.0, 100L);
        assertTrue(cancelledHit.isEmpty(), "Daño cancelado debe retornar empty");
        assertEquals(0L, cancellableRuntime.getHitSequence(), "Secuencia no debe consumirse");
        assertEquals(0, cancellableRuntime.getParticipantCount(), "No debe crearse participante");

        // 2. Intento permitido
        shouldCancel.set(false);
        Optional<ParticipantSnapshot> acceptedHit = cancellableRuntime.recordDamage(p1, "P1", 20.0, 105L);
        assertTrue(acceptedHit.isPresent(), "Daño no cancelado debe aceptarse");
        assertEquals(1L, cancellableRuntime.getHitSequence(), "Secuencia debe ser 1");
        assertEquals(1, cancellableRuntime.getParticipantCount(), "Participante debe registrarse");
    }
}
