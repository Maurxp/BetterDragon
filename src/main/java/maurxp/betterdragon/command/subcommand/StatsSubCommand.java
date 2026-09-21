package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.leaderboard.LeaderboardApplicationService;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.application.permission.PermissionChecker;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.CommandMessages;
import maurxp.betterdragon.command.SubCommand;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Subcomando para consultar las estadísticas de combate individuales de un jugador.
 *
 * @author maurxp
 */
public class StatsSubCommand implements SubCommand {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final LeaderboardApplicationService leaderboardService;
    private final PermissionChecker permissionChecker;

    public StatsSubCommand(LeaderboardApplicationService leaderboardService, PermissionChecker permissionChecker) {
        this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService no puede ser nulo");
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker no puede ser nulo");
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public List<String> aliases() {
        return List.of("perfil", "estadisticas");
    }

    @Override
    public String description() {
        return "Consulta tus estadísticas de combate o las de otro jugador.";
    }

    @Override
    public String usage() {
        return "/bd stats [jugador]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.STATS;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();

        if (args.length == 0) {
            if (!context.isPlayer()) {
                context.sendError("Debes especificar un jugador o UUID al ejecutar este comando desde la consola.");
                return;
            }

            Player player = context.asPlayer();
            context.sendInfo("Consultando tus estadísticas...");
            queryAndRender(context, player.getUniqueId(), player.getName());
        } else {
            // Consulta de otro jugador
            if (!permissionChecker.hasPermission(context.sender(), CommandPermission.STATS_OTHERS)) {
                context.sendError("No tienes permiso para consultar las estadísticas de otros jugadores.");
                return;
            }

            String query = args[0].trim();
            context.sendInfo("Buscando estadísticas para '§e" + query + "§7'...");
            leaderboardService.getPlayerStatsByQuery(query).whenComplete((statsOpt, ex) -> {
                if (ex != null) {
                    context.sendError("Error al consultar estadísticas: " + ex.getMessage());
                    return;
                }
                if (statsOpt.isEmpty()) {
                    context.sendError("No se encontraron registros de combate para '§e" + query + "§c'.");
                    return;
                }
                renderStats(context, statsOpt.get());
            });
        }
    }

    private void queryAndRender(CommandContext context, UUID targetUuid, String fallbackName) {
        leaderboardService.getPlayerStats(targetUuid).whenComplete((statsOpt, ex) -> {
            if (ex != null) {
                context.sendError("Error al consultar estadísticas: " + ex.getMessage());
                return;
            }
            if (statsOpt.isEmpty()) {
                context.sendRaw(CommandMessages.header("Estadísticas: " + fallbackName));
                context.sendRaw(" §7Aún no has participado en ninguna batalla victoriosa de BetterDragon.");
                context.sendRaw(CommandMessages.SEPARATOR);
                return;
            }
            renderStats(context, statsOpt.get());
        });
    }

    private void renderStats(CommandContext context, LeaderboardPlayerStats stats) {
        context.sendRaw(CommandMessages.header("Estadísticas: " + stats.lastKnownName()));
        context.sendRaw(" §8» §7UUID: §8" + stats.playerUuid());
        context.sendRaw(" §8» §7Batallas Participadas: §e" + stats.battlesParticipated());
        context.sendRaw(" §8» §7Victorias como Slayer: §6" + stats.slayerCount());
        context.sendRaw(String.format(" §8» §7Daño Total Acumulado: §a%,.1f", stats.totalDamage()));
        context.sendRaw(String.format(" §8» §7Daño Máximo en 1 Batalla: §d%,.1f", stats.highestDamage()));
        context.sendRaw(String.format(" §8» §7Daño Promedio por Batalla: §b%,.1f", stats.getAverageDamage()));

        String firstDate = DATE_FORMATTER.format(stats.firstParticipationAt());
        String lastDate = DATE_FORMATTER.format(stats.lastParticipationAt());
        context.sendRaw(" §8» §7Primera Batalla: §f" + firstDate);
        context.sendRaw(" §8» §7Última Batalla: §f" + lastDate);
        context.sendRaw(CommandMessages.SEPARATOR);
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        if (context.args().length == 1) {
            String prefix = context.args()[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
