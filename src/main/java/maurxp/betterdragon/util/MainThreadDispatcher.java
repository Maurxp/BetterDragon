package maurxp.betterdragon.util;

/**
 * Interfaz funcional para despachar tareas al hilo principal del servidor.
 * <p>
 * Desacopla los servicios asíncronos y componentes de persistencia de las APIs directas
 * de BukkitScheduler, permitiendo pruebas unitarias deterministas sin servidor en ejecución.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface MainThreadDispatcher {

    /**
     * Ejecuta la tarea indicada en el hilo principal.
     *
     * @param runnable tarea a ejecutar
     */
    void runOnMainThread(Runnable runnable);
}
