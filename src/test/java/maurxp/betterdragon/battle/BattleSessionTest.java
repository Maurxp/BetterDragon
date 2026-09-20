package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.BattleResult;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleSessionTest {

    private BattleId battleId;
    private String worldName;
    private UUID worldId;
    private BattleSession session;

    @BeforeEach
    void setUp() {
        this.battleId = BattleId.random();
        this.worldName = "world_the_end";
        this.worldId = UUID.randomUUID();
        this.session = BattleSession.create(battleId, worldName, worldId);
    }

    @Test
    @DisplayName("Sesión recién creada arranca en estado IDLE")
    void testInitialState() {
        assertEquals(BattleState.IDLE, session.getState());
        assertEquals(battleId, session.getBattleId());
        assertEquals(worldName, session.getWorldName());
        assertEquals(worldId, session.getWorldUniqueId());
        assertTrue(session.getDragonIdentity().isEmpty());
        assertFalse(session.isActive());
        assertFalse(session.isTerminal());
        assertNotNull(session.getCombatRuntime(), "CombatRuntime debe instanciarse junto a la sesión");
        assertEquals(0, session.getCombatRuntime().getParticipantCount());
    }

    @Test
    @DisplayName("Flujo exitoso completo de ciclo de vida")
    void testFullLifecycleVictory() {
        // 1. Iniciar preparación
        session.start();
        assertEquals(BattleState.PREPARING, session.getState());

        // 2. Activar con dragón
        UUID dragonEntityId = UUID.randomUUID();
        DragonIdentity identity = DragonIdentity.of(dragonEntityId, battleId, "default_profile");
        session.activate(identity);
        assertEquals(BattleState.ACTIVE, session.getState());
        assertTrue(session.isActive());
        assertTrue(session.getDragonIdentity().isPresent());
        assertEquals(identity, session.getDragonIdentity().get());

        // 3. Iniciar muerte
        session.beginDying();
        assertEquals(BattleState.DYING, session.getState());

        // 4. Completar con Slayer
        UUID slayerId = UUID.randomUUID();
        String slayerName = "SteveSlayer";
        BattleResult result = session.complete(slayerId, slayerName);

        assertEquals(BattleState.COMPLETED, session.getState());
        assertTrue(session.isTerminal());
        assertNotNull(result);
        assertEquals(battleId, result.battleId());
        assertEquals(BattleState.COMPLETED, result.finalState());
        assertTrue(result.isVictory());
        assertEquals(slayerId, result.slayerUniqueId());
        assertEquals(slayerName, result.slayerLastKnownName());
        assertTrue(result.getAbortReason().isEmpty());
    }

    @Test
    @DisplayName("Flujo de cancelación o aborto")
    void testAbortLifecycle() {
        session.start();
        BattleResult result = session.abort("Mundo descargado por administrador");

        assertEquals(BattleState.ABORTED, session.getState());
        assertTrue(session.isTerminal());
        assertFalse(result.isVictory());
        assertEquals("Mundo descargado por administrador", result.getAbortReason().orElse(""));
    }

    @Test
    @DisplayName("Pausa por descarga de chunk y posterior reanudación")
    void testChunkDeferralLifecycle() {
        session.start();
        DragonIdentity identity = DragonIdentity.of(UUID.randomUUID(), battleId, "elder_dragon");
        session.activate(identity);

        // Chunk se descarga -> DEFERRED_PENDING_CHUNK_LOAD
        session.deferPendingChunkLoad();
        assertEquals(BattleState.DEFERRED_PENDING_CHUNK_LOAD, session.getState());

        // Chunk se vuelve a cargar -> reanuda a ACTIVE
        session.resumeFromChunkLoad();
        assertEquals(BattleState.ACTIVE, session.getState());
    }

    @Test
    @DisplayName("activate rechaza DragonIdentity de otra batalla")
    void testActivateRejectsMismatchedBattleId() {
        session.start();
        BattleId otherBattleId = BattleId.random();
        DragonIdentity mismatchedIdentity = DragonIdentity.of(UUID.randomUUID(), otherBattleId, "default_profile");

        assertThrows(IllegalArgumentException.class, () -> session.activate(mismatchedIdentity));
    }

    @Test
    @DisplayName("Violación de máquina de estados arroja IllegalStateException")
    void testIllegalStateTransitionThrows() {
        // No se puede pasar de IDLE a ACTIVE directamente
        assertThrows(IllegalStateException.class, () -> session.activate(DragonIdentity.of(UUID.randomUUID(), battleId, "p")));
        // No se puede pasar de IDLE a COMPLETED
        assertThrows(IllegalStateException.class, () -> session.complete(UUID.randomUUID(), "Steve"));
    }
}
