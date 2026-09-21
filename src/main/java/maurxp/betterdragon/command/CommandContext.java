package maurxp.betterdragon.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Objects;

/**
 * Contexto inmutable de ejecución para un subcomando de BetterDragon.
 *
 * @author maurxp
 */
public class CommandContext {

    private final CommandSender sender;
    private final String label;
    private final String[] rawArgs;
    private final String subCommandName;
    private final String[] subCommandArgs;

    public CommandContext(CommandSender sender, String label, String[] rawArgs, String subCommandName, String[] subCommandArgs) {
        this.sender = Objects.requireNonNull(sender, "sender no puede ser nulo");
        this.label = Objects.requireNonNull(label, "label no puede ser nulo");
        this.rawArgs = rawArgs != null ? Arrays.copyOf(rawArgs, rawArgs.length) : new String[0];
        this.subCommandName = Objects.requireNonNull(subCommandName, "subCommandName no puede ser nulo");
        this.subCommandArgs = subCommandArgs != null ? Arrays.copyOf(subCommandArgs, subCommandArgs.length) : new String[0];
    }

    public CommandSender sender() {
        return sender;
    }

    public String label() {
        return label;
    }

    public String[] rawArgs() {
        return Arrays.copyOf(rawArgs, rawArgs.length);
    }

    public String subCommandName() {
        return subCommandName;
    }

    public String[] args() {
        return Arrays.copyOf(subCommandArgs, subCommandArgs.length);
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    public Player asPlayer() {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalStateException("El emisor del comando no es un jugador (" + sender.getClass().getSimpleName() + ").");
    }

    public void sendSuccess(String message) {
        CommandMessages.sendSuccess(sender, message);
    }

    public void sendError(String message) {
        CommandMessages.sendError(sender, message);
    }

    public void sendInfo(String message) {
        CommandMessages.sendInfo(sender, message);
    }

    public void sendWarning(String message) {
        CommandMessages.sendWarning(sender, message);
    }

    public void sendRaw(String message) {
        CommandMessages.sendRaw(sender, message);
    }
}
