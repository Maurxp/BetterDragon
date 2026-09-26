package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleAbortReason;
import maurxp.betterdragon.battle.model.BattleState;
import maurxp.betterdragon.battle.model.DragonIdentity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Escucha los eventos del ciclo de vida físico de los dragones administrados por BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Detección Estricta por PDC:</b> Ignora cualquier dragón que no porte {@code managed=true}
 *       y un {@code battle_id} válido.</li>
 *   <li><b>Separación de DragonBattle:</b> La muerte se gestiona enteramente en {@link BattleSession},
 *       sin invocar {@code DragonBattle.setDragonKilled()} ni internals vanilla.</li>
 *   <li><b>Supresión de XP Vanilla Duplicada:</b> Cancela los drops y la experiencia vanilla masiva
 *       ({@code setDroppedExp(0)}) para permitir que el sistema propio de recompensas tome el control.</li>
 *   <li><b>Semántica de Descarga de Chunks:</b> Distingue {@code Cause.UNLOAD} (pausa a {@code DEFERRED_PENDING_CHUNK_LOAD})
 *       de desapariciones inesperadas (aborto a {@code ENTITY_MISSING}).</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonLifecycleListener implements Listener {

    private final BattleSessionManager sessionManager;
    private final BattleManager battleManager;
    private final Logger logger;

    public DragonLifecycleListener(BattleSessionManager sessionManager, BattleManager battleManager, Logger logger) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager no puede ser nulo");
        this.battleManager = Objects.requireNonNull(battleManager, "battleManager no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Detecta la muerte natural del EnderDragon administrado por BetterDragon y delega
     * en BattleManager para finalizar la batalla con victoria.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(dragon);
        if (identityOpt.isEmpty()) {
            // No es un dragón de BetterDragon; ignorar completamente sin spam
            return;
        }

        battleManager.handleDragonDeath(dragon, event);
    }

    /**
     * Detecta la remoción de la entidad del mundo, distinguiendo descarga de chunk de pérdida de entidad.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(entity);

        if (identityOpt.isEmpty()) {
            return;
        }

        DragonIdentity identity = identityOpt.get();
        Optional<BattleSession> sessionOpt = sessionManager.getSession(identity.battleId());

        if (sessionOpt.isEmpty()) {
            return;
        }

        BattleSession session = sessionOpt.get();
        EntityRemoveEvent.Cause cause = event.getCause();

        if (cause == EntityRemoveEvent.Cause.UNLOAD) {
            // El chunk del dragón fue descargado; pausar a DEFERRED_PENDING_CHUNK_LOAD
            if (session.getState() == BattleState.ACTIVE || session.getState() == BattleState.DYING) {
                logger.info("[BetterDragon] Chunk del dragón descargado para la batalla " + identity.battleId()
                        + ". Transicionando a DEFERRED_PENDING_CHUNK_LOAD.");
                session.deferPendingChunkLoad();
            }
        } else if (cause == EntityRemoveEvent.Cause.DEATH) {
            // Muerte física procesada por onEntityDeath; no requiere acción adicional aquí
        } else {
            // La entidad fue descartada, destruida o despawneada inesperadamente mientras el chunk estaba cargado
            if (!session.isTerminal()) {
                logger.warning("[BetterDragon] Dragón de la batalla " + identity.battleId()
                        + " desapareció inesperadamente (Causa: " + cause + "). Abortando por ENTITY_MISSING.");
                session.abort(BattleAbortReason.ENTITY_MISSING);
                sessionManager.remove(identity.battleId());
            }
        }
    }

    /**
     * Notifica que un conjunto de entidades ha sido cargado con un chunk, permitiendo
     * intentar resolver la identidad de cualquier dragón diferido.
     * <p>
     * <b>Principio:</b> La carga de entidades NO reactiva automáticamente la batalla;
     * delega en {@link #resolveDeferredDragon(Entity)} para buscar y validar la entidad esperada.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof EnderDragon) {
                resolveDeferredDragon(entity);
            }
        }
    }

    /**
     * Intenta resolver una sesión de batalla en espera de chunk a partir de una entidad cargada.
     *
     * @param entity entidad física cargada en el mundo
     */
    public void resolveDeferredDragon(Entity entity) {
        Optional<DragonIdentity> identityOpt = DragonPdcHandler.extractIdentity(entity);
        if (identityOpt.isEmpty()) {
            return;
        }

        DragonIdentity identity = identityOpt.get();
        Optional<BattleSession> sessionOpt = sessionManager.getSession(identity.battleId());
        if (sessionOpt.isPresent()) {
            BattleSession session = sessionOpt.get();
            if (session.getState() == BattleState.DEFERRED_PENDING_CHUNK_LOAD) {
                battleManager.resolveDeferredDragon(session, entity);
            }
        }
    }
}
