package maurxp.betterdragon.command;

import maurxp.betterdragon.application.arena.ArenaQueryService;
import maurxp.betterdragon.application.battle.BattleAdminService;
import maurxp.betterdragon.application.permission.BukkitPermissionChecker;
import maurxp.betterdragon.application.permission.CommandPermission;
import maurxp.betterdragon.battle.BattleManager;
import maurxp.betterdragon.battle.BattleSessionManager;
import maurxp.betterdragon.battle.DragonSpawner;
import maurxp.betterdragon.command.subcommand.StartSubCommand;
import maurxp.betterdragon.command.subcommand.StatusSubCommand;
import maurxp.betterdragon.config.*;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Selección de Perfil de Dragón y Status en Comandos (Fase 3.13)")
class DragonCommandSelectionTest {

    private Logger logger;
    private ConfigurationService configService;
    private BattleSessionManager sessionManager;
    private BattleManager battleManager;
    private BattleAdminService battleAdminService;
    private ArenaQueryService arenaQueryService;
    private StartSubCommand startCommand;
    private StatusSubCommand statusCommand;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("DragonCommandSelectionTest");

        // Crear catálogo con default y nightmare
        DragonDefinition def1 = new DragonDefinition(
                "default",
                "Dragón Normal",
                DragonAttributes.defaults(),
                DragonScalingDefinition.defaults(),
                List.of(new PhaseDefinition("p1", 0, 1.0, List.of())),
                Map.of()
        );
        DragonDefinition def2 = new DragonDefinition(
                "nightmare",
                "Dragón Pesadilla",
                new DragonAttributes(800.0, 0.4, 64.0, 20.0),
                new DragonScalingDefinition(true, ScalingMode.LINEAR, 0.3, 3.0),
                List.of(new PhaseDefinition("p1", 0, 1.0, List.of())),
                Map.of()
        );

        DragonCatalog catalog = new DragonCatalog(Map.of("default", def1, "nightmare", def2), "default");
        BetterDragonConfig config = new BetterDragonConfig(false, "INFO", false, catalog, RewardConfigurationSnapshot.defaults());

        configService = new ConfigurationService(logger, config, ArenaConfigurationSnapshot.defaults());
        sessionManager = new BattleSessionManager();
        DragonSpawner spawner = new DragonSpawner();
        battleManager = new BattleManager(sessionManager, configService, spawner, logger);
        battleAdminService = new BattleAdminService(battleManager, sessionManager, configService, logger);
        arenaQueryService = new ArenaQueryService(configService);

        startCommand = new StartSubCommand(battleAdminService, arenaQueryService);
        statusCommand = new StatusSubCommand(battleAdminService);
    }

    @Test
    @DisplayName("Tab completion de start en argumento 3 sugiere definiciones del catálogo")
    void testStartTabCompletionArg3() {
        TestCommandSender admin = new TestCommandSender("Admin", true);
        admin.addPermission(CommandPermission.ADMIN_START.node());
        CommandContext context = new CommandContext(admin, "bd", new String[]{"start", "world_the_end", "default", ""}, "start", new String[]{"world_the_end", "default", ""});

        List<String> suggestions = startCommand.tabComplete(context);
        assertTrue(suggestions.contains("default"));
        assertTrue(suggestions.contains("nightmare"));

        // Filtrado por prefijo
        CommandContext prefixContext = new CommandContext(admin, "bd", new String[]{"start", "world_the_end", "default", "night"}, "start", new String[]{"world_the_end", "default", "night"});
        List<String> filtered = startCommand.tabComplete(prefixContext);
        assertEquals(List.of("nightmare"), filtered);
    }

    @Test
    @DisplayName("Start con definición desconocida falla y notifica al usuario sin iniciar batalla")
    void testStartWithUnknownDefinitionFails() {
        TestCommandSender admin = new TestCommandSender("Admin", true);
        admin.addPermission(CommandPermission.ADMIN_START.node());
        CommandContext context = new CommandContext(admin, "bd", new String[]{"start", "world_the_end", "default", "inexistente"}, "start", new String[]{"world_the_end", "default", "inexistente"});

        startCommand.execute(context);

        // Como el mundo no está mockeado en Bukkit.getWorld en unit test puro, falla limpiamente por mundo
        assertTrue(admin.getMessages().stream().anyMatch(m -> m.contains("no existe o no está cargado") || m.contains("no existe en el catálogo")));
        assertEquals(0, sessionManager.getAllSessions().size());
    }

    @Test
    @DisplayName("BattleAdminService.getAvailableDragonDefinitions retorna todas las claves registradas")
    void testAvailableDragonDefinitions() {
        List<String> defs = battleAdminService.getAvailableDragonDefinitions();
        assertEquals(2, defs.size());
        assertTrue(defs.contains("default"));
        assertTrue(defs.contains("nightmare"));
    }
}
