package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.arena.model.ArenaDetailView;
import maurxp.betterdragon.application.arena.model.ArenaSummaryView;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.CommandMessages;
import maurxp.betterdragon.command.SubCommand;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcomando para consultar información de arenas configuradas en BetterDragon.
 *
 * @author maurxp
 */
public class ArenaSubCommand implements SubCommand {

    private final ArenaQueryService arenaQueryService;

    public ArenaSubCommand(ArenaQueryService arenaQueryService) {
        this.arenaQueryService = Objects.requireNonNull(arenaQueryService, "arenaQueryService no puede ser nulo");
    }

    @Override
    public String name() {
        return "arena";
    }

    @Override
    public List<String> aliases() {
        return List.of("arenas");
    }

    @Override
    public String description() {
        return "Consulta la lista y detalles de las arenas configuradas en el sistema.";
    }

    @Override
    public String usage() {
        return "/bd arena <list|info> [id]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.ADMIN_ARENA;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            renderList(context);
            return;
        }

        if (args[0].equalsIgnoreCase("info")) {
            if (args.length < 2) {
                context.sendError("Debes especificar el ID de la arena: §e/bd arena info <id>§c.");
                return;
            }
            renderInfo(context, args[1].trim());
            return;
        }

        context.sendError("Acción de arena desconocida: '§e" + args[0] + "§c'. Usa §f/bd arena list§c o §f/bd arena info <id>§c.");
    }

    private void renderList(CommandContext context) {
        List<ArenaSummaryView> list = arenaQueryService.listArenas();
        context.sendRaw(CommandMessages.header("Arenas Configuradas (" + list.size() + ")"));
        if (list.isEmpty()) {
            context.sendRaw(" §7No hay arenas cargadas en memoria.");
        } else {
            for (ArenaSummaryView arena : list) {
                String defaultTag = arena.isDefault() ? " §6[DEFAULT]" : "";
                context.sendRaw(String.format(" §8» §e%s%s §8— §7Mundo: §f%s §8| §7Límites: §8%s",
                        arena.id(), defaultTag, arena.worldName(), arena.boundsDescription()));
            }
        }
        context.sendRaw(" §8Tip: Usa §e/bd arena info <id> §7para ver detalles y reglas.");
        context.sendRaw(CommandMessages.SEPARATOR);
    }

    private void renderInfo(CommandContext context, String arenaId) {
        Optional<ArenaDetailView> detailOpt = arenaQueryService.getArenaDetail(arenaId);
        if (detailOpt.isEmpty()) {
            context.sendError("No existe ninguna arena con el identificador '§e" + arenaId + "§c'.");
            return;
        }

        ArenaDetailView detail = detailOpt.get();
        String defaultTag = detail.isDefault() ? " §6[DEFAULT]" : "";
        context.sendRaw(CommandMessages.header("Detalle de Arena: " + detail.id() + defaultTag));
        context.sendRaw(" §8» §7Mundo: §f" + detail.worldName());
        context.sendRaw(String.format(" §8» §7Centro de Combate: §b(%.1f, %.1f, %.1f)",
                detail.center().x(), detail.center().y(), detail.center().z()));
        context.sendRaw(String.format(" §8» §7Centro del Podio: §b(%.1f, %.1f, %.1f)",
                detail.podium().x(), detail.podium().y(), detail.podium().z()));
        context.sendRaw(" §8» §7Límites AABB: §f" + detail.boundsDescription());
        context.sendRaw(" §8» §7Reglas Geométricas Activas:");
        for (String rule : detail.activeRules()) {
            context.sendRaw("    §8• §7" + rule);
        }
        context.sendRaw(CommandMessages.SEPARATOR);
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        String[] args = context.args();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "info").stream()
                    .filter(a -> a.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return arenaQueryService.listArenas().stream()
                    .map(ArenaSummaryView::id)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
