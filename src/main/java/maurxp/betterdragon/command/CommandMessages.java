package maurxp.betterdragon.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

/**
 * Centralización de formato, estilos y mensajes para la interfaz de comandos de BetterDragon.
 * <p>
 * Proporciona consistencia visual y semántica sin acoplamiento a librerías externas.
 *
 * @author maurxp
 */
public final class CommandMessages {

    public static final String PREFIX = "§8[§5BetterDragon§8] §r";
    public static final String SEPARATOR = "§8§m--------------------------------------------------§r";

    private CommandMessages() {}

    public static String header(String title) {
        Objects.requireNonNull(title, "title no puede ser nulo");
        return "§8§m------§r §5§lBetterDragon §8» §d" + title + " §8§m------§r";
    }

    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + "§a" + message);
    }

    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + "§c" + message);
    }

    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + "§7" + message);
    }

    public static void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + "§e" + message);
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public static void sendNoPermission(CommandSender sender) {
        sendError(sender, "No tienes permiso para ejecutar este comando.");
    }

    public static void sendPlayerOnly(CommandSender sender) {
        sendError(sender, "Este comando solo puede ser ejecutado por un jugador dentro del servidor.");
    }

    public static void sendConsoleOnly(CommandSender sender) {
        sendError(sender, "Este comando solo puede ser ejecutado desde la consola del servidor.");
    }
}
