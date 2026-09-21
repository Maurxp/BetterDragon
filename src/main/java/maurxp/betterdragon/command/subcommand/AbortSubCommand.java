package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.battle.model.BattleOperationResult;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Subcomando administrativo para cancelar y abortar una batalla activa.
 *
 * @author maurxp
 */
public class AbortSubCommand implements SubCommand {

    private final BattleAdminService battleAdminService;

    public AbortSubCommand(BattleAdminService battleAdminService) {
        this.battleAdminService = Objects.requireNonNull(battleAdminService, "battleAdminService no puede ser nulo");
    }

    @Override
    public String name() {
        return "abort";
    }

    @Override
    public List<String> aliases() {
        return List.of("cancel", "cancelar", "stop");
    }

    @Override
    public String description() {
        return "Cancela y aborta manualmente una batalla activa, removiendo al dragón.";
    }

    @Override
    public String usage() {
        return "/bd abort [mundo|battleId]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.ADMIN_ABORT;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();
        BattleOperationResult result;

        if (args.length > 0) {
            String target = args[0].trim();

            // 1. Comprobar si es un BattleId directo
            try {
                UUID uuid = UUID.fromString(target);
                result = battleAdminService.abortBattle(BattleId.fromUUID(uuid), "Cancelado administrativamente por " + context.sender().getName());
            } catch (IllegalArgumentException notUuid) {
                // 2. Tratar como nombre de mundo
                World world = Bukkit.getWorld(target);
                if (world == null) {
                    context.sendError("El mundo o ID de batalla '§e" + target + "§c' no fue encontrado.");
                    return;
                }
                result = battleAdminService.abortBattle(world, "Cancelado administrativamente por " + context.sender().getName());
            }
        } else if (context.isPlayer()) {
            World playerWorld = context.asPlayer().getWorld();
            result = battleAdminService.abortBattle(playerWorld, "Cancelado administrativamente por " + context.asPlayer().getName());
        } else {
            context.sendError("Debes especificar el mundo o ID de la batalla a cancelar: §e/bd abort <mundo>§c.");
            return;
        }

        if (result.success()) {
            context.sendSuccess(result.message());
        } else {
            context.sendError(result.message());
        }
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        if (context.args().length == 1) {
            String prefix = context.args()[0].toLowerCase();
            return Bukkit.getWorlds().stream()
                    .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
