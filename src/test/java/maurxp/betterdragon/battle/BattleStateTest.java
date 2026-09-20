package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class BattleStateTest {

    @Test
    @DisplayName("Transición legal: IDLE -> PREPARING")
    void testIdleToPreparing() {
        assertTrue(BattleState.IDLE.canTransitionTo(BattleState.PREPARING));
        assertDoesNotThrow(() -> BattleState.IDLE.validateTransition(BattleState.PREPARING));
    }

    @Test
    @DisplayName("Transiciones legales desde PREPARING")
    void testPreparingTransitions() {
        assertTrue(BattleState.PREPARING.canTransitionTo(BattleState.ACTIVE));
        assertTrue(BattleState.PREPARING.canTransitionTo(BattleState.ABORTED));
        assertFalse(BattleState.PREPARING.canTransitionTo(BattleState.COMPLETED));
        assertFalse(BattleState.PREPARING.canTransitionTo(BattleState.IDLE));
    }

    @Test
    @DisplayName("Transiciones legales desde ACTIVE")
    void testActiveTransitions() {
        assertTrue(BattleState.ACTIVE.canTransitionTo(BattleState.DYING));
        assertTrue(BattleState.ACTIVE.canTransitionTo(BattleState.ABORTED));
        assertTrue(BattleState.ACTIVE.canTransitionTo(BattleState.DEFERRED_PENDING_CHUNK_LOAD));
        assertFalse(BattleState.ACTIVE.canTransitionTo(BattleState.COMPLETED));
        assertFalse(BattleState.ACTIVE.canTransitionTo(BattleState.PREPARING));
    }

    @Test
    @DisplayName("Transiciones legales desde DYING")
    void testDyingTransitions() {
        assertTrue(BattleState.DYING.canTransitionTo(BattleState.COMPLETED));
        assertTrue(BattleState.DYING.canTransitionTo(BattleState.ABORTED));
        assertTrue(BattleState.DYING.canTransitionTo(BattleState.DEFERRED_PENDING_CHUNK_LOAD));
        assertFalse(BattleState.DYING.canTransitionTo(BattleState.ACTIVE));
    }

    @Test
    @DisplayName("Transiciones legales desde DEFERRED_PENDING_CHUNK_LOAD")
    void testDeferredTransitions() {
        assertTrue(BattleState.DEFERRED_PENDING_CHUNK_LOAD.canTransitionTo(BattleState.ACTIVE));
        assertTrue(BattleState.DEFERRED_PENDING_CHUNK_LOAD.canTransitionTo(BattleState.DYING));
        assertTrue(BattleState.DEFERRED_PENDING_CHUNK_LOAD.canTransitionTo(BattleState.ABORTED));
        assertFalse(BattleState.DEFERRED_PENDING_CHUNK_LOAD.canTransitionTo(BattleState.COMPLETED));
        assertFalse(BattleState.DEFERRED_PENDING_CHUNK_LOAD.canTransitionTo(BattleState.IDLE));
    }

    @ParameterizedTest
    @EnumSource(value = BattleState.class, names = {"COMPLETED", "ABORTED"})
    @DisplayName("Estados terminales no pueden transicionar a ningún otro estado")
    void testTerminalStatesCannotTransition(BattleState terminalState) {
        assertTrue(terminalState.isTerminal());
        for (BattleState target : BattleState.values()) {
            assertFalse(terminalState.canTransitionTo(target));
            assertThrows(IllegalStateException.class, () -> terminalState.validateTransition(target));
        }
    }

    @Test
    @DisplayName("validateTransition lanza IllegalStateException con mensaje explicativo")
    void testInvalidTransitionThrowsException() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> BattleState.IDLE.validateTransition(BattleState.ACTIVE)
        );
        assertTrue(ex.getMessage().contains("Transición de estado ilegal"));
    }
}
