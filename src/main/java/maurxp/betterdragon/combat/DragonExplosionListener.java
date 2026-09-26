package maurxp.betterdragon.combat;

import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Escucha las detonaciones de entidades en Paper (EntityExplodeEvent) para suprimir
 * exclusivamente la destrucción de bloques de terreno generada por BetterDragon (CAND-03).
 * <p>
 * Principios:
 * <ul>
 * <li><b>Firma PDC Específica:</b> Interviene única y exclusivamente sobre entidades que portan
 * las claves {@code betterdragon:managed = true} y {@code betterdragon:battle_id}.</li>
 * <li><b>Terreno Inmune sin Alterar Daño a Jugadores:</b> Vence la destrucción de la isla mediante
 * {@code event.blockList().clear()}, preservando el daño físico y empuje a jugadores.</li>
 * <li><b>Cero Interferencia Externa:</b> Explosiones causadas por camas, anclas de respawn, creepers
 * o cristales de End vanilla no administrados se mantienen al 100% inalteradas.</li>
 * <li><b>0% NMS:</b> Utiliza exclusivamente Paper API pública.</li>
 * </ul>
 *
 * @author maurxp
 */
public class DragonExplosionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        boolean isManaged = false;
        try {
            if (pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BYTE)) {
                Byte b = pdc.get(BetterDragonKeys.MANAGED, PersistentDataType.BYTE);
                isManaged = (b != null && b == (byte) 1);
            }
        } catch (Exception ignored) {
        }
        if (!isManaged) {
            try {
                if (pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN)) {
                    Boolean val = pdc.get(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN);
                    isManaged = Boolean.TRUE.equals(val);
                }
            } catch (Exception ignored) {
            }
        }

        if (!isManaged) {
            return;
        }

        // Si la entidad explosiva pertenece a una batalla activa de BetterDragon, anular la rotura de bloques
        if (pdc.has(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING)) {
            event.blockList().clear();
        }
    }
}
