package maurxp.betterdragon.command.subcommand;

import maurxp.betterdragon.application.admin.AdminApplicationService;
import maurxp.betterdragon.application.admin.model.ReloadResult;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.command.AllowedSender;
import maurxp.betterdragon.command.CommandContext;
import maurxp.betterdragon.command.SubCommand;

import java.util.List;
import java.util.Objects;

/**
 * Subcomando administrativo para recargar la configuración del plugin de forma atómica y fail-safe.
 *
 * @author maurxp
 */
public class ReloadSubCommand implements SubCommand {

    private final AdminApplicationService adminService;

    public ReloadSubCommand(AdminApplicationService adminService) {
        this.adminService = Objects.requireNonNull(adminService, "adminService no puede ser nulo");
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public List<String> aliases() {
        return List.of("recargar");
    }

    @Override
    public String description() {
        return "Recarga de forma atómica config.yml y arenas.yml preservando la sesión activa.";
    }

    @Override
    public String usage() {
        return "/bd reload";
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.ADMIN_RELOAD;
    }

    @Override
    public AllowedSender allowedSender() {
        return AllowedSender.BOTH;
    }

    @Override
    public void execute(CommandContext context) {
        context.sendInfo("Recargando configuración de BetterDragon...");
        ReloadResult result = adminService.reloadConfiguration();

        if (result.success()) {
            context.sendSuccess(result.message() + " §8(§7" + result.elapsedMillis() + " ms§8)");
        } else {
            context.sendError(result.message());
        }
    }
}
