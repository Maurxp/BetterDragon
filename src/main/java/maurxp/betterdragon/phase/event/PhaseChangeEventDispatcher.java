package maurxp.betterdragon.phase.event;

/**
 * Interfaz funcional para el despacho desacoplado de eventos de cambio de fase.
 * <p>
 * Permite ejecutar pruebas unitarias puras sin necesidad de levantar el servidor Bukkit.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface PhaseChangeEventDispatcher {

    /**
     * Despacha el evento de cambio de fase.
     *
     * @param event evento de fase
     */
    void dispatch(BetterDragonPhaseChangeEvent event);
}
