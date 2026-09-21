package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.leaderboard.LeaderboardApplicationService;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.CommandMessages;
import maurxp.betterdragon.command.SubCommand;
import maurxp.betterdragon.leaderboard.model.LeaderboardPlayerStats;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Subcomando para consultar los rankings del Leaderboard persistente de BetterDragon.
 *
 * @author maurxp
 */
public class LeaderboardSubCommand implements SubCommand {

    private final LeaderboardApplicationService leaderboardService;

    public LeaderboardSubCommand(LeaderboardApplicationService leaderboardService) {
        this.leaderboardService = Objects.requireNonNull(leaderboardService, "leaderboardService no puede ser nulo");
    }

    @Override
    public String name() {
        return "leaderboard";
    }

    @Override
    public List<String> aliases() {
        return List.of("top", "lb");
    }

    @Override
    public String description() {
        return "Consulta los rankings históricos de combate respaldados por SQLite.";
    }

    @Override
    public String usage() {
        return "/bd leaderboard [damage|slayers|battles] [límite]";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.LEADERBOARD;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        String[] args = context.args();
        String category = "damage";
        int limit = 10;

        if (args.length > 0) {
            category = args[0].toLowerCase(Locale.ROOT);
        }

        if (args.length > 1) {
            try {
                limit = Integer.parseInt(args[1]);
                if (limit <= 0) {
                    context.sendError("El límite debe ser un número entero mayor a 0.");
                    return;
                }
                limit = Math.min(limit, 20); // Limitar a 20 líneas en chat para evitar spam
            } catch (NumberFormatException e) {
                context.sendError("Límite numérico inválido: '§e" + args[1] + "§c'.");
                return;
            }
        }

        final int finalLimit = limit;
        final String finalCategory = category;

        switch (category) {
            case "slayers", "slayer" -> {
                context.sendInfo("Consultando Top Slayers...");
                leaderboardService.getTopSlayers(finalLimit).whenComplete((statsList, ex) -> {
                    if (ex != null) {
                        context.sendError("Error al consultar el leaderboard: " + ex.getMessage());
                        return;
                    }
                    renderRanking(context, "Top Slayers (Victorias)", statsList, entry ->
                            String.format("§e%d §7victorias como Slayer", entry.slayerCount()));
                });
            }
            case "battles", "participations" -> {
                context.sendInfo("Consultando Top Participaciones...");
                leaderboardService.getTopParticipations(finalLimit).whenComplete((statsList, ex) -> {
                    if (ex != null) {
                        context.sendError("Error al consultar el leaderboard: " + ex.getMessage());
                        return;
                    }
                    renderRanking(context, "Top Participaciones en Batallas", statsList, entry ->
                            String.format("§e%d §7batallas", entry.battlesParticipated()));
                });
            }
            case "damage", "dmg" -> {
                context.sendInfo("Consultando Top Daño...");
                leaderboardService.getTopDamage(finalLimit).whenComplete((statsList, ex) -> {
                    if (ex != null) {
                        context.sendError("Error al consultar el leaderboard: " + ex.getMessage());
                        return;
                    }
                    renderRanking(context, "Top Daño Total Acumulado", statsList, entry ->
                            String.format("§e%,.1f §7daño (Media: §f%,.1f§7)", entry.totalDamage(), entry.getAverageDamage()));
                });
            }
            default -> {
                context.sendError("Categoría desconocida: '§e" + finalCategory + "§c'. Usa: §fdamage§c, §fslayers§c, o §fbattles§c.");
            }
        }
    }

    private void renderRanking(CommandContext context, String title, List<LeaderboardPlayerStats> list, ValueFormatter formatter) {
        context.sendRaw(CommandMessages.header(title));
        if (list.isEmpty()) {
            context.sendRaw(" §7No hay registros suficientes en el historial de batallas.");
        } else {
            int rank = 1;
            for (LeaderboardPlayerStats stats : list) {
                String medal = switch (rank) {
                    case 1 -> "§6[#1]";
                    case 2 -> "§7[#2]";
                    case 3 -> "§c[#3]";
                    default -> "§8[#" + rank + "]";
                };
                context.sendRaw(String.format(" %s §f%s §8» %s", medal, stats.lastKnownName(), formatter.format(stats)));
                rank++;
            }
        }
        context.sendRaw(CommandMessages.SEPARATOR);
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        String[] args = context.args();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("damage", "slayers", "battles").stream()
                    .filter(c -> c.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("5", "10", "15");
        }
        return List.of();
    }

    @FunctionalInterface
    private interface ValueFormatter {
        String format(LeaderboardPlayerStats stats);
    }
}
