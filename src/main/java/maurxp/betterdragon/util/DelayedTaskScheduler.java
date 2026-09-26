package maurxp.betterdragon.util;

/**
 * Contrato funcional para programar tareas en el hilo principal tras un retardo en ticks.
 * <p>
 * Permite desacoplar el motor de combate y telegrafiado de las llamadas estáticas a BukkitScheduler,
 * facilitando pruebas unitarias deterministas sin servidor en ejecución.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface DelayedTaskScheduler {

    /**
     * Programa una tarea para ejecutarse en el hilo principal tras {@code delayTicks} ticks.
     *
     * @param runnable   tarea a ejecutar
     * @param delayTicks retardo en ticks lógicos del servidor (>= 0)
     * @return un {@link CancellableTask} que permite cancelar la tarea programada
     */
    CancellableTask schedule(Runnable runnable, long delayTicks);
}
