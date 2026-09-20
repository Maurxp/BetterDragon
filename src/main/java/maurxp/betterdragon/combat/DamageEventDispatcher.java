package maurxp.betterdragon.combat;

/**
 * Estrategia de despacho para {@link BetterDragonDamageEvent}.
 * <p>
 * Permite desacoplar el {@link CombatRuntime} de la llamada estática a Bukkit
 * en entornos de pruebas unitarias puras.
 *
 * @author maurxp
 */
@FunctionalInterface
public interface DamageEventDispatcher {

    /**
     * Despacha el evento de daño y retorna si el evento fue aceptado (no cancelado).
     *
     * @param event evento a emitir
     * @return true si el evento procedió normalmente; false si fue cancelado
     */
    boolean dispatch(BetterDragonDamageEvent event);
}
