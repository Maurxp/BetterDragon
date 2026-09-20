package maurxp.betterdragon.battle.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Representa los estados del ciclo de vida de una batalla en BetterDragon.
 * <p>
 * BetterDragon mantiene su propia máquina de estados finitos (FSM) y <b>no</b>
 * utiliza {@code DragonBattle} vanilla como fuente de verdad.
 *
 * @author maurxp
 */
public enum BattleState {

    /**
     * Estado inicial de reposo. No existe una batalla activa.
     */
    IDLE,

    /**
     * La batalla se está inicializando o configurando. La entidad del dragón
     * todavía no ha sido generada o vinculada.
     */
    PREPARING,

    /**
     * Batalla en curso activo con un dragón administrado por BetterDragon.
     */
    ACTIVE,

    /**
     * El dragón ha recibido el golpe fatal y se encuentra en resolución
     * de muerte y cierre de combate.
     */
    DYING,

    /**
     * Estado terminal de victoria. La batalla concluyó satisfactoriamente.
     */
    COMPLETED,

    /**
     * Estado terminal abortado. La batalla finalizó prematuramente por una
     * condición irrecuperable o cancelación administrativa.
     */
    ABORTED,

    /**
     * Estado de recuperación diferida. El combate se encuentra en pausa debido
     * a que el chunk donde reside el dragón se descargó, esperando a que vuelva a cargarse.
     */
    DEFERRED_PENDING_CHUNK_LOAD;

    private static final Set<BattleState> TERMINAL_STATES = EnumSet.of(COMPLETED, ABORTED);

    /**
     * Determina si la transición hacia el estado especificado es válida.
     *
     * @param next siguiente estado deseado
     * @return true si la transición es permitida por la máquina de estados, false en caso contrario
     */
    public boolean canTransitionTo(BattleState next) {
        if (next == null) {
            return false;
        }

        return switch (this) {
            case IDLE -> next == PREPARING;
            case PREPARING -> next == ACTIVE || next == ABORTED;
            case ACTIVE -> next == DYING || next == ABORTED || next == DEFERRED_PENDING_CHUNK_LOAD;
            case DYING -> next == COMPLETED || next == ABORTED || next == DEFERRED_PENDING_CHUNK_LOAD;
            case DEFERRED_PENDING_CHUNK_LOAD -> next == ACTIVE || next == DYING || next == ABORTED;
            case COMPLETED, ABORTED -> false; // Estados terminales inmutables
        };
    }

    /**
     * Valida que la transición hacia el estado especificado sea legal.
     *
     * @param next siguiente estado deseado
     * @throws IllegalStateException si la transición es inválida
     */
    public void validateTransition(BattleState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("Transición de estado ilegal en BetterDragon: " + this + " -> " + next);
        }
    }

    /**
     * Indica si este estado es terminal (la batalla ha concluido definitivamente).
     *
     * @return true si es COMPLETED o ABORTED
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    /**
     * Indica si la batalla se encuentra actualmente en ejecución operativa (no inactiva ni terminal).
     *
     * @return true si es PREPARING, ACTIVE, DYING o DEFERRED_PENDING_CHUNK_LOAD
     */
    public boolean isRunning() {
        return this == PREPARING || this == ACTIVE || this == DYING || this == DEFERRED_PENDING_CHUNK_LOAD;
    }
}
