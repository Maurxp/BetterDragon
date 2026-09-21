package maurxp.betterdragon.application.permission;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Implementación de {@link PermissionChecker} sobre la API estándar de Bukkit/Paper.
 * <p>
 * Se integra naturalmente con el sistema nativo de permisos y gestores como LuckPerms
 * sin introducir dependencias duras externas.
 *
 * @author maurxp
 */
public class BukkitPermissionChecker implements PermissionChecker {

    @Override
    public boolean hasPermission(CommandSender sender, CommandPermission permission) {
        if (sender == null || permission == null) {
            return false;
        }

        // Permiso padre de administración otorga acceso a subnodos admin
        if (permission.node().startsWith("betterdragon.admin.") && sender.hasPermission(CommandPermission.ADMIN.node())) {
            return true;
        }

        return sender.hasPermission(permission.node());
    }

    @Override
    public boolean hasPermission(UUID playerUuid, CommandPermission permission) {
        if (playerUuid == null || permission == null) {
            return false;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            return hasPermission(player, permission);
        }

        return false;
    }
}
