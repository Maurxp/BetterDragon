package maurxp.betterdragon.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/**
 * Comando canónico {@code /betterdragon} con alias oficial {@code /bd}.
 * <p>
 * Se registra directamente en el {@code CommandMap} de Bukkit/Paper durante el arranque,
 * delegando toda la resolución y comprobación de permisos en {@link CommandRegistry}.
 *
 * @author maurxp
 */
public class BetterDragonCommand extends Command {

    private final CommandRegistry registry;

    public BetterDragonCommand(CommandRegistry registry) {
        super(
                "betterdragon",
                "Comando de administración y estadísticas de BetterDragon",
                "/betterdragon <subcomando>",
                List.of("bd")
        );
        this.registry = Objects.requireNonNull(registry, "registry no puede ser nulo");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        registry.dispatch(sender, commandLabel, args);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return registry.tabComplete(sender, alias, args);
    }

    public CommandRegistry getRegistry() {
        return registry;
    }
}
