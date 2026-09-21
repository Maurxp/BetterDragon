package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.application.reward.RewardApplicationService;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.SubCommand;
import maurxp.betterdragon.util.MainThreadDispatcher;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Subcomando para que los jugadores reclamen ítems resguardados en el buzón duradero de SQLite.
 *
 * @author maurxp
 */
public class ClaimSubCommand implements SubCommand {

    private final RewardApplicationService rewardService;
    private final MainThreadDispatcher mainThreadDispatcher;

    public ClaimSubCommand(RewardApplicationService rewardService, MainThreadDispatcher mainThreadDispatcher) {
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService no puede ser nulo");
        this.mainThreadDispatcher = Objects.requireNonNull(mainThreadDispatcher, "mainThreadDispatcher no puede ser nulo");
    }

    public ClaimSubCommand(RewardApplicationService rewardService) {
        this(rewardService, Runnable::run);
    }

    @Override
    public String name() {
        return "claim";
    }

    @Override
    public List<String> aliases() {
        return List.of("reclamar", "recompensas");
    }

    @Override
    public String description() {
        return "Reclama las recompensas pendientes guardadas en tu buzón duradero.";
    }

    @Override
    public String usage() {
        return "/bd claim";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.CLAIM;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.PLAYER_ONLY;
    }

    @Override
    public void execute(CommandContext context) {
        Player player = context.asPlayer();
        context.sendInfo("Comprobando buzón de recompensas pendientes en SQLite...");

        rewardService.claimPendingRewards(player.getUniqueId()).whenComplete((deliveredCount, ex) -> {
            mainThreadDispatcher.runOnMainThread(() -> {
                if (ex != null) {
                    context.sendError("Error al reclamar recompensas: " + ex.getMessage());
                    return;
                }

                if (deliveredCount != null && deliveredCount > 0) {
                    context.sendSuccess("¡Se han entregado §e" + deliveredCount + "§a ítems pendientes a tu inventario!");
                } else {
                    context.sendInfo("No tienes ítems de recompensa pendientes de entrega en este momento (o tu inventario sigue lleno).");
                }
            });
        });
    }
}
