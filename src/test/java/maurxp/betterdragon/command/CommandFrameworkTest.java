package maurxp.betterdragon.command;

import maurxp.betterdragon.application.permission.BukkitPermissionChecker;
import maurxp.betterdragon.application.permission.CommandPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias del Framework de Comandos (Fase 3.11)")
class CommandFrameworkTest {

    private BukkitPermissionChecker permissionChecker;
    private CommandRegistry registry;
    private Logger logger;

    @BeforeEach
    void setUp() {
        permissionChecker = new BukkitPermissionChecker();
        logger = Logger.getLogger("CommandFrameworkTest");
        registry = new CommandRegistry(permissionChecker, logger);
    }

    @Test
    @DisplayName("C1: Validación de constantes y nodos de CommandPermission")
    void testCommandPermissionsIntegrity() {
        for (CommandPermission perm : CommandPermission.values()) {
            assertNotNull(perm.node(), "El nodo de permiso no puede ser nulo");
            assertTrue(perm.node().startsWith("betterdragon."), "El nodo debe comenzar con 'betterdragon.'");
            assertNotNull(perm.description(), "La descripción no puede ser nula");
            assertFalse(perm.description().isBlank(), "La descripción no puede estar en blanco");
        }

        assertEquals("betterdragon.use", CommandPermission.USE.node());
        assertEquals("betterdragon.leaderboard", CommandPermission.LEADERBOARD.node());
        assertEquals("betterdragon.stats", CommandPermission.STATS.node());
        assertEquals("betterdragon.admin", CommandPermission.ADMIN.node());
        assertEquals("betterdragon.admin.start", CommandPermission.ADMIN_START.node());
        assertEquals("betterdragon.admin.abort", CommandPermission.ADMIN_ABORT.node());
    }

    @Test
    @DisplayName("C2: BukkitPermissionChecker evalúa permisos directos, OP y jerarquía de admin")
    void testPermissionCheckerHierarchy() {
        TestCommandSender regularSender = new TestCommandSender("Player1", false);
        TestCommandSender adminSender = new TestCommandSender("AdminPlayer", false);
        TestCommandSender opSender = new TestCommandSender("OpPlayer", true);

        adminSender.addPermission(CommandPermission.ADMIN.node());
        regularSender.addPermission(CommandPermission.USE.node());

        // Regular sender
        assertTrue(permissionChecker.hasPermission(regularSender, CommandPermission.USE));
        assertFalse(permissionChecker.hasPermission(regularSender, CommandPermission.ADMIN));
        assertFalse(permissionChecker.hasPermission(regularSender, CommandPermission.ADMIN_START));

        // Admin sender (betterdragon.admin otorga subnodos admin.*)
        assertTrue(permissionChecker.hasPermission(adminSender, CommandPermission.ADMIN));
        assertTrue(permissionChecker.hasPermission(adminSender, CommandPermission.ADMIN_STATUS));
        assertTrue(permissionChecker.hasPermission(adminSender, CommandPermission.ADMIN_START));
        assertTrue(permissionChecker.hasPermission(adminSender, CommandPermission.ADMIN_ABORT));

        // OP sender
        assertTrue(permissionChecker.hasPermission(opSender, CommandPermission.USE));
        assertTrue(permissionChecker.hasPermission(opSender, CommandPermission.ADMIN_RELOAD));
    }

    @Test
    @DisplayName("C3: Registro de subcomandos y enrutamiento por nombre canónico y alias")
    void testSubCommandRegistrationAndRouting() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SubCommand dummy = new SubCommand() {
            @Override public String name() { return "dummy"; }
            @Override public List<String> aliases() { return List.of("d", "dum"); }
            @Override public String description() { return "Dummy command"; }
            @Override public String usage() { return "/bd dummy"; }
            @Override public CommandPermission permission() { return CommandPermission.USE; }
            @Override public AllowedSender allowedSender() { return AllowedSender.BOTH; }
            @Override public void execute(CommandContext context) { executed.set(true); }
        };

        registry.register(dummy);

        TestCommandSender sender = new TestCommandSender("Tester", true);

        // Despacho por nombre canónico
        registry.dispatch(sender, "bd", new String[]{"dummy"});
        assertTrue(executed.get(), "Debe haberse ejecutado por nombre canónico");

        // Despacho por alias
        executed.set(false);
        registry.dispatch(sender, "betterdragon", new String[]{"d"});
        assertTrue(executed.get(), "Debe haberse ejecutado por alias 'd'");

        executed.set(false);
        registry.dispatch(sender, "bd", new String[]{"dum"});
        assertTrue(executed.get(), "Debe haberse ejecutado por alias 'dum'");
    }

    @Test
    @DisplayName("C4: Rechazo fail-safe cuando el emisor carece del permiso requerido")
    void testPermissionDenied() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SubCommand secureCmd = new SubCommand() {
            @Override public String name() { return "secure"; }
            @Override public String description() { return "Secure command"; }
            @Override public String usage() { return "/bd secure"; }
            @Override public CommandPermission permission() { return CommandPermission.ADMIN_START; }
            @Override public AllowedSender allowedSender() { return AllowedSender.BOTH; }
            @Override public void execute(CommandContext context) { executed.set(true); }
        };

        registry.register(secureCmd);

        TestCommandSender senderWithoutPerm = new TestCommandSender("User", false);
        registry.dispatch(senderWithoutPerm, "bd", new String[]{"secure"});

        assertFalse(executed.get(), "No debe ejecutarse si no tiene permiso");
        assertFalse(senderWithoutPerm.getMessages().isEmpty(), "Debe enviar un mensaje de error");
        assertTrue(senderWithoutPerm.getMessages().getFirst().contains("No tienes permiso"),
                "El mensaje debe indicar falta de permisos");
    }

    @Test
    @DisplayName("C5: Console Safety bloquea consolas para subcomandos PLAYER_ONLY")
    void testConsoleSafetyPlayerOnly() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SubCommand playerOnlyCmd = new SubCommand() {
            @Override public String name() { return "claimtest"; }
            @Override public String description() { return "Claim test"; }
            @Override public String usage() { return "/bd claimtest"; }
            @Override public CommandPermission permission() { return CommandPermission.USE; }
            @Override public AllowedSender allowedSender() { return AllowedSender.PLAYER_ONLY; }
            @Override public void execute(CommandContext context) { executed.set(true); }
        };

        registry.register(playerOnlyCmd);

        // TestCommandSender no extiende de Player, por lo que actúa como emisor consola/no-player
        TestCommandSender consoleSender = new TestCommandSender("CONSOLE", true);
        registry.dispatch(consoleSender, "bd", new String[]{"claimtest"});

        assertFalse(executed.get(), "No debe ejecutarse desde consola");
        assertTrue(consoleSender.getMessages().getFirst().contains("solo puede ser ejecutado por un jugador"),
                "El mensaje debe advertir que solo es para jugadores");
    }

    @Test
    @DisplayName("C6: Manejo de subcomandos desconocidos sin excepciones no controladas")
    void testUnknownSubCommand() {
        TestCommandSender sender = new TestCommandSender("User", true);
        registry.dispatch(sender, "bd", new String[]{"inexistente"});

        assertFalse(sender.getMessages().isEmpty());
        assertTrue(sender.getMessages().getFirst().contains("Subcomando desconocido"),
                "Debe advertir de subcomando desconocido");
    }

    @Test
    @DisplayName("C7: Tab completion filtra por permisos y prefijo de caracteres")
    void testTabCompletionFiltering() {
        SubCommand publicCmd = new SubCommand() {
            @Override public String name() { return "leaderboard"; }
            @Override public List<String> aliases() { return List.of("top"); }
            @Override public String description() { return "Public lb"; }
            @Override public String usage() { return "/bd leaderboard"; }
            @Override public CommandPermission permission() { return CommandPermission.LEADERBOARD; }
            @Override public AllowedSender allowedSender() { return AllowedSender.BOTH; }
            @Override public void execute(CommandContext context) {}
        };

        SubCommand adminCmd = new SubCommand() {
            @Override public String name() { return "abort"; }
            @Override public String description() { return "Admin abort"; }
            @Override public String usage() { return "/bd abort"; }
            @Override public CommandPermission permission() { return CommandPermission.ADMIN_ABORT; }
            @Override public AllowedSender allowedSender() { return AllowedSender.BOTH; }
            @Override public void execute(CommandContext context) {}
        };

        registry.register(publicCmd);
        registry.register(adminCmd);

        TestCommandSender user = new TestCommandSender("User", false);
        user.addPermission(CommandPermission.LEADERBOARD.node());

        // Usuario regular no ve "abort"
        List<String> userCompletions = registry.tabComplete(user, "bd", new String[]{""});
        assertTrue(userCompletions.contains("leaderboard"));
        assertTrue(userCompletions.contains("top"));
        assertFalse(userCompletions.contains("abort"));

        // Filtro por prefijo "lea"
        List<String> prefixCompletions = registry.tabComplete(user, "bd", new String[]{"lea"});
        assertEquals(List.of("leaderboard"), prefixCompletions);

        // Admin sí ve "abort"
        TestCommandSender admin = new TestCommandSender("Admin", true);
        List<String> adminCompletions = registry.tabComplete(admin, "bd", new String[]{"ab"});
        assertEquals(List.of("abort"), adminCompletions);
    }

    @Test
    @DisplayName("C8: BetterDragonCommand expone los metadatos y alias oficiales (/bd)")
    void testBetterDragonCommandProperties() {
        BetterDragonCommand command = new BetterDragonCommand(registry);

        assertEquals("betterdragon", command.getName());
        assertEquals(List.of("bd"), command.getAliases());
        assertNotNull(command.getDescription());
        assertNotNull(command.getUsage());

        TestCommandSender sender = new TestCommandSender("Sender", true);
        boolean result = command.execute(sender, "bd", new String[0]);
        assertTrue(result);
    }
}
