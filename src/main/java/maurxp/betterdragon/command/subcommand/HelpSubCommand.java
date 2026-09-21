package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.CommandMessages;
import maurxp.betterdragon.command.CommandRegistry;
import maurxp.betterdragon.command.SubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcomando para consultar la ayuda y sintaxis de comandos de BetterDragon.
 *
 * @author maurxp
 */
public class HelpSubCommand implements SubCommand {

    private final CommandRegistry registry;

    public HelpSubCommand(CommandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry no puede ser nulo");
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("ayuda", "?");
    }

    @Override
    public String description() {
        return "Muestra la lista de comandos disponibles o información de uno en específico.";
    }

    @Override
    public String usage() {
        return "/bd help [subcomando]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.USE;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();

        if (args.length > 0) {
            String query = args[0].toLowerCase();
            Optional<SubCommand> targetOpt = registry.getSubCommand(query);

            if (targetOpt.isEmpty() || !registry.getPermissionChecker().hasPermission(context.sender(), targetOpt.get().permission())) {
                context.sendError("No se encontró información para el subcomando '§e" + query + "§c'.");
                return;
            }

            SubCommand target = targetOpt.get();
            context.sendRaw(CommandMessages.header("Ayuda: /bd " + target.name()));
            context.sendRaw(" §8» §7Descripción: §f" + target.description());
            context.sendRaw(" §8» §7Uso: §e" + target.usage());
            if (!target.aliases().isEmpty()) {
                context.sendRaw(" §8» §7Alias: §d" + String.join(", ", target.aliases()));
            }
            context.sendRaw(" §8» §7Permiso: §b" + target.permission().node());
            context.sendRaw(" §8» §7Emisor admitido: §f" + formatAllowedSender(target.allowedSender()));
            context.sendRaw(CommandMessages.SEPARATOR);
            return;
        }

        // Listado general de comandos disponibles para este emisor
        context.sendRaw(CommandMessages.header("Comandos Disponibles"));
        int shownCount = 0;
        for (SubCommand sub : registry.getRegisteredSubCommands()) {
            if (registry.getPermissionChecker().hasPermission(context.sender(), sub.permission())) {
                context.sendRaw(" §8» §e" + sub.usage() + " §8— §7" + sub.description());
                shownCount++;
            }
        }

        if (shownCount == 0) {
            context.sendInfo("No tienes permisos para ver comandos adicionales.");
        } else {
            context.sendRaw(" §8Tip: Usa §e/bd help <subcomando> §7para ver detalles específicos.");
        }
        context.sendRaw(CommandMessages.SEPARATOR);
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        if (context.args().length == 1) {
            String prefix = context.args()[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (SubCommand sub : registry.getRegisteredSubCommands()) {
                if (registry.getPermissionChecker().hasPermission(context.sender(), sub.permission())) {
                    if (sub.name().toLowerCase().startsWith(prefix)) {
                        list.add(sub.name());
                    }
                }
            }
            return list;
        }
        return List.of();
    }

    private String formatAllowedSender(AllowedSender allowed) {
        return switch (allowed) {
            case PLAYER_ONLY -> "Solo Jugadores";
            case CONSOLE_ONLY -> "Solo Consola";
            case BOTH -> "Jugador y Consola";
        };
    }
}
