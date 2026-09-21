package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.arena.model.ArenaSummaryView;
import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.battle.model.BattleOperationResult;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;

/**
 * Subcomando administrativo para iniciar o forzar una batalla de BetterDragon.
 *
 * @author maurxp
 */
public class StartSubCommand implements SubCommand {

    private final BattleAdminService battleAdminService;
    private final ArenaQueryService arenaQueryService;

    public StartSubCommand(BattleAdminService battleAdminService, ArenaQueryService arenaQueryService) {
        this.battleAdminService = Objects.requireNonNull(battleAdminService, "battleAdminService no puede ser nulo");
        this.arenaQueryService = Objects.requireNonNull(arenaQueryService, "arenaQueryService no puede ser nulo");
    }

    @Override
    public String name() {
        return "start";
    }

    @Override
    public List<String> aliases() {
        return List.of("spawn", "iniciar");
    }

    @Override
    public String description() {
        return "Inicia una batalla formal y controlada de BetterDragon en el End.";
    }

    @Override
    public String usage() {
        return "/bd start [mundo] [arena] [perfil]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.ADMIN_START;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();
        World targetWorld = null;
        String arenaId = "default";
        String definitionId = "default";

        if (args.length > 0) {
            String worldName = args[0].trim();
            targetWorld = Bukkit.getWorld(worldName);
            if (targetWorld == null) {
                context.sendError("El mundo '§e" + worldName + "§c' no existe o no está cargado.");
                return;
            }
        } else if (context.isPlayer()) {
            World playerWorld = context.asPlayer().getWorld();
            if (playerWorld.getEnvironment() == World.Environment.THE_END) {
                targetWorld = playerWorld;
            } else {
                context.sendError("Debes encontrarte en una dimensión del End o especificar el nombre del mundo: §e/bd start <mundo>§c.");
                return;
            }
        } else {
            context.sendError("Debes especificar el mundo del End donde iniciar la batalla: §e/bd start <mundo>§c.");
            return;
        }

        if (args.length > 1) {
            arenaId = args[1].trim();
        }

        if (args.length > 2) {
            definitionId = args[2].trim();
        }

        context.sendInfo("Iniciando batalla en el mundo '§e" + targetWorld.getName() + "§7'...");
        BattleOperationResult result = battleAdminService.startBattle(targetWorld, definitionId, arenaId);

        if (result.success()) {
            context.sendSuccess(result.message());
        } else {
            context.sendError(result.message());
        }
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        String[] args = context.args();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getWorlds().stream()
                    .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return arenaQueryService.listArenas().stream()
                    .map(ArenaSummaryView::id)
                    .filter(id -> id.toLowerCase().startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            return List.of("default");
        }
        return List.of();
    }
}
