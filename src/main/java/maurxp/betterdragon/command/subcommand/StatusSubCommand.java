package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.battle.model.BattleStatusView;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.CommandMessages;
import maurxp.betterdragon.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Subcomando para consultar el estado en tiempo real de las batallas activas de BetterDragon.
 *
 * @author maurxp
 */
public class StatusSubCommand implements SubCommand {

    private final BattleAdminService battleAdminService;

    public StatusSubCommand(BattleAdminService battleAdminService) {
        this.battleAdminService = Objects.requireNonNull(battleAdminService, "battleAdminService no puede ser nulo");
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public List<String> aliases() {
        return List.of("estado");
    }

    @Override
    public String description() {
        return "Muestra el estado en tiempo real de la batalla activa de BetterDragon.";
    }

    @Override
    public String usage() {
        return "/bd status [mundo]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.ADMIN_STATUS;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();

        if (args.length > 0) {
            String worldName = args[0].trim();
            Optional<BattleStatusView> statusOpt = battleAdminService.getStatus(worldName);
            if (statusOpt.isEmpty()) {
                context.sendInfo("No hay ninguna batalla activa en el mundo '§e" + worldName + "§7'.");
                return;
            }
            renderStatus(context, statusOpt.get());
            return;
        }

        // Si es jugador y está en un mundo del End con batalla, priorizar su mundo
        if (context.isPlayer()) {
            World playerWorld = context.asPlayer().getWorld();
            Optional<BattleStatusView> statusOpt = battleAdminService.getStatus(playerWorld);
            if (statusOpt.isPresent()) {
                renderStatus(context, statusOpt.get());
                return;
            }
        }

        // Listar todas las batallas activas
        List<BattleStatusView> all = battleAdminService.getAllStatuses();
        if (all.isEmpty()) {
            context.sendInfo("No hay ninguna sesión de batalla activa en el servidor actualmente.");
            return;
        }

        for (BattleStatusView status : all) {
            renderStatus(context, status);
        }
    }

    private void renderStatus(CommandContext context, BattleStatusView status) {
        context.sendRaw(CommandMessages.header("Batalla en " + status.worldName()));
        context.sendRaw(" §8» §7ID: §8" + status.battleId());
        context.sendRaw(" §8» §7Estado: §e" + status.state() + " §7(Arena: §f" + status.arenaId() + "§7)");

        String healthBar = String.format("§a%,.0f§7/§a%,.0f §8(§e%.1f%%§8)",
                status.currentHealth(), status.maxHealth(), status.getHealthPercentage() * 100.0);
        context.sendRaw(" §8» §7Salud del Dragón: " + healthBar);
        context.sendRaw(" §8» §7Fase Activa: §d" + status.activePhaseId());

        long seconds = status.duration().getSeconds();
        String durationStr = String.format("%02d:%02d", seconds / 60, seconds % 60);
        context.sendRaw(" §8» §7Tiempo Transcurrido: §f" + durationStr);
        context.sendRaw(" §8» §7Participantes Registrados: §b" + status.participantCount());

        if (status.topDamagerName().isPresent()) {
            context.sendRaw(String.format(" §8» §7Top Daño Actual: §6%s §8(§a%,.1f§8)",
                    status.topDamagerName().get(), status.topDamage()));
        } else {
            context.sendRaw(" §8» §7Top Daño Actual: §8(sin daño registrado aún)");
        }
        context.sendRaw(CommandMessages.SEPARATOR);
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
