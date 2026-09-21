package maurxp.betterdragon.application.permission;

import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * Contrato desacoplado para la verificación de permisos por capacidad.
 * <p>
 * Permite que Commands y futuras GUIs consulten la misma lógica de autorización
 * sin duplicar sentencias ni asumir que el ejecutor es necesariamente OP.
 *
 * @author maurxp
 */
public interface PermissionChecker {

    /**
     * Verifica si el emisor posee el permiso de capacidad indicado.
     *
     * @param sender     emisor del comando o jugador
     * @param permission permiso de capacidad a evaluar
     * @return true si tiene el permiso asignado o cumple con el default
     */
    boolean hasPermission(CommandSender sender, CommandPermission permission);

    /**
     * Verifica si un jugador identificado por UUID posee el permiso de capacidad indicado.
     *
     * @param playerUuid UUID del jugador
     * @param permission permiso de capacidad a evaluar
     * @return true si tiene el permiso asignado
     */
    boolean hasPermission(UUID playerUuid, CommandPermission permission);
}
