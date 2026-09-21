package maurxp.betterdragon.command;

import maurxp.betterdragon.application.permission.PermissionChecker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro central y despachador de subcomandos para BetterDragon.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Enrutamiento por nombre canónico y alias.</li>
 *   <li>Validación centralizada de permisos mediante {@link PermissionChecker}.</li>
 *   <li>Comprobación de compatibilidad de emisor (jugador vs consola).</li>
 *   <li>Captura segura de excepciones sin mostrar trazas de error al usuario.</li>
 *   <li>Autocompletado (Tab Completion) sensible al contexto y permisos.</li>
 * </ul>
 *
 * @author maurxp
 */
public class CommandRegistry {

    private final PermissionChecker permissionChecker;
    private final Logger logger;
    private final Map<String, SubCommand> subCommandsByName = new LinkedHashMap<>();
    private final Map<String, SubCommand> routingMap = new LinkedHashMap<>();

    public CommandRegistry(PermissionChecker permissionChecker, Logger logger) {
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker no puede ser nulo");
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
    }

    /**
     * Registra un nuevo subcomando en el sistema.
     *
     * @param subCommand instancia del subcomando
     */
    public void register(SubCommand subCommand) {
        Objects.requireNonNull(subCommand, "subCommand no puede ser nulo");

        String canonicalName = subCommand.name().toLowerCase(Locale.ROOT);
        subCommandsByName.put(canonicalName, subCommand);
        routingMap.put(canonicalName, subCommand);

        for (String alias : subCommand.aliases()) {
            if (alias != null && !alias.isBlank()) {
                routingMap.put(alias.toLowerCase(Locale.ROOT), subCommand);
            }
        }
    }

    /**
     * Despacha y ejecuta la llamada de comando.
     *
     * @param sender emisor (jugador o consola)
     * @param label  etiqueta utilizada (/betterdragon o /bd)
     * @param args   argumentos recibidos
     */
    public void dispatch(CommandSender sender, String label, String[] args) {
        if (args == null || args.length == 0) {
            // Mostrar resumen por defecto o redirigir a help
            Optional<SubCommand> helpCmd = getSubCommand("help");
            if (helpCmd.isPresent()) {
                CommandContext ctx = new CommandContext(sender, label, new String[]{"help"}, "help", new String[0]);
                helpCmd.get().execute(ctx);
            } else {
                CommandMessages.sendInfo(sender, "BetterDragon — Usa §e/" + label + " help§7 para ver los comandos disponibles.");
            }
            return;
        }

        String subName = args[0].toLowerCase(Locale.ROOT);
        SubCommand subCommand = routingMap.get(subName);

        if (subCommand == null) {
            CommandMessages.sendError(sender, "Subcomando desconocido: '§e" + args[0] + "§c'. Usa §e/" + label + " help§c.");
            return;
        }

        // 1. Verificación de permisos de capacidad
        if (!permissionChecker.hasPermission(sender, subCommand.permission())) {
            CommandMessages.sendNoPermission(sender);
            return;
        }

        // 2. Validación de emisor admitido (Console Safety)
        AllowedSender allowed = subCommand.allowedSender();
        if (allowed == AllowedSender.PLAYER_ONLY && !(sender instanceof Player)) {
            CommandMessages.sendPlayerOnly(sender);
            return;
        }
        if (allowed == AllowedSender.CONSOLE_ONLY && (sender instanceof Player)) {
            CommandMessages.sendConsoleOnly(sender);
            return;
        }

        // 3. Ejecución protegida
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        CommandContext context = new CommandContext(sender, label, args, subCommand.name(), subArgs);

        try {
            subCommand.execute(context);
        } catch (IllegalArgumentException | IllegalStateException e) {
            CommandMessages.sendError(sender, e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[BetterDragon] Error al ejecutar subcomando '" + subName + "': " + e.getMessage(), e);
            CommandMessages.sendError(sender, "Ocurrió un error inesperado al procesar el comando. Revisa los registros del servidor.");
        }
    }

    /**
     * Resuelve sugerencias de autocompletado para los argumentos actuales.
     *
     * @param sender emisor
     * @param label  alias o comando raíz
     * @param args   argumentos actuales
     * @return lista de sugerencias filtradas
     */
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();

            for (SubCommand sub : subCommandsByName.values()) {
                if (permissionChecker.hasPermission(sender, sub.permission())) {
                    if (sub.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        matches.add(sub.name());
                    }
                    for (String alias : sub.aliases()) {
                        if (alias.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            matches.add(alias);
                        }
                    }
                }
            }
            Collections.sort(matches);
            return List.copyOf(matches);
        }

        String subName = args[0].toLowerCase(Locale.ROOT);
        SubCommand subCommand = routingMap.get(subName);

        if (subCommand == null || !permissionChecker.hasPermission(sender, subCommand.permission())) {
            return List.of();
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        CommandContext context = new CommandContext(sender, label, args, subCommand.name(), subArgs);

        try {
            return subCommand.tabComplete(context);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<SubCommand> getSubCommand(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(routingMap.get(name.toLowerCase(Locale.ROOT)));
    }

    public List<SubCommand> getRegisteredSubCommands() {
        return List.copyOf(subCommandsByName.values());
    }

    public PermissionChecker getPermissionChecker() {
        return permissionChecker;
    }
}
