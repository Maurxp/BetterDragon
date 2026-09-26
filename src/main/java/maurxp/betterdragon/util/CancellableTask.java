package maurxp.betterdragon.util;

/**
 * Representa una tarea programada que puede ser cancelada para evitar fugas de ciclo de vida.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface CancellableTask {

    /**
     * Cancela la tarea programada si aún no ha sido ejecutada.
     */
    void cancel();
}
