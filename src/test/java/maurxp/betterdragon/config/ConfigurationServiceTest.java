package maurxp.betterdragon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationServiceTest {

    private final Logger logger = Logger.getLogger("BetterDragonConfigTest");

    @Test
    @DisplayName("Carga inicial exitosa desde archivo")
    void testInitialLoad(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                portal:
                  enabled: true
                logging:
                  level: "INFO"
                  debug: false
                """);

        ConfigurationService service = new ConfigurationService(logger);
        service.loadInitial(configPath.toFile());

        BetterDragonConfig active = service.getActiveConfig();
        assertNotNull(active);
        assertTrue(active.portalEnabled());
        assertEquals("INFO", active.loggingLevel());
        assertFalse(active.debugLogging());
    }

    @Test
    @DisplayName("Recarga atómica exitosa reemplaza configuración activa")
    void testAtomicReloadSuccess(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                portal:
                  enabled: false
                logging:
                  level: "INFO"
                  debug: false
                """);

        ConfigurationService service = new ConfigurationService(logger);
        service.loadInitial(configPath.toFile());
        assertFalse(service.getActiveConfig().portalEnabled());

        // Actualizar archivo a nueva configuración válida
        Files.writeString(configPath, """
                portal:
                  enabled: true
                logging:
                  level: "WARNING"
                  debug: true
                """);

        boolean ok = service.reload(configPath.toFile());
        assertTrue(ok, "La recarga debió ser exitosa");

        BetterDragonConfig updated = service.getActiveConfig();
        assertTrue(updated.portalEnabled());
        assertEquals("WARNING", updated.loggingLevel());
        assertTrue(updated.debugLogging());
    }

    @Test
    @DisplayName("Fail-Safe: Recarga con configuración inválida rechaza el cambio y conserva la anterior intacta")
    void testAtomicReloadFailSafePreservesOldConfig(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                portal:
                  enabled: true
                logging:
                  level: "INFO"
                """);

        ConfigurationService service = new ConfigurationService(logger);
        service.loadInitial(configPath.toFile());
        assertTrue(service.getActiveConfig().portalEnabled());

        // Actualizar archivo con valor inválido (portal.enabled = "banana")
        Files.writeString(configPath, """
                portal:
                  enabled: "banana"
                """);

        boolean ok = service.reload(configPath.toFile());
        assertFalse(ok, "La recarga debió ser rechazada por datos inválidos");

        // La configuración previa permanece activa e inalterada
        BetterDragonConfig active = service.getActiveConfig();
        assertNotNull(active);
        assertTrue(active.portalEnabled(), "portal.enabled debe continuar siendo true (valor anterior intacto)");
        assertEquals("INFO", active.loggingLevel(), "logging.level debe continuar siendo INFO");
    }

    @Test
    @DisplayName("Fail-Safe con reloadOrThrow: Lanza excepción pero mantiene configuración activa anterior")
    void testReloadOrThrowPreservesConfigOnError(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                portal:
                  enabled: false
                """);

        ConfigurationService service = new ConfigurationService(logger);
        service.loadInitial(configPath.toFile());
        assertFalse(service.getActiveConfig().portalEnabled());

        // Archivo corrupto
        Files.writeString(configPath, "corrupt: [unclosed");

        assertThrows(ConfigValidationException.class, () -> service.reloadOrThrow(configPath.toFile()));

        // Configuración activa intacta
        assertFalse(service.getActiveConfig().portalEnabled());
    }

    @Test
    @DisplayName("Fail-Safe: Recarga con fases de dragón inválidas rechaza el cambio y conserva las fases anteriores")
    void testReloadFailSafeWithInvalidPhasesPreservesPreviousPhases(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                portal:
                  enabled: true
                dragons:
                  default:
                    phases:
                      - id: "p1"
                        threshold: 1.00
                        abilities: []
                      - id: "p2"
                        threshold: 0.50
                        abilities: []
                """);

        ConfigurationService service = new ConfigurationService(logger);
        service.loadInitial(configPath.toFile());
        assertEquals(2, service.getActiveConfig().dragonDefinition().phases().size());
        assertEquals("p2", service.getActiveConfig().dragonDefinition().phases().get(1).id());

        // Modificar a YAML con thresholds no decrecientes (0.50 <= 0.80)
        Files.writeString(configPath, """
                portal:
                  enabled: true
                dragons:
                  default:
                    phases:
                      - id: "p1"
                        threshold: 0.50
                        abilities: []
                      - id: "p2"
                        threshold: 0.80
                        abilities: []
                """);

        boolean ok = service.reload(configPath.toFile());
        assertFalse(ok, "La recarga debió ser rechazada por thresholds inválidos");

        // Fases anteriores intactas (p1=1.0, p2=0.5)
        assertEquals(2, service.getActiveConfig().dragonDefinition().phases().size());
        assertEquals(0.50, service.getActiveConfig().dragonDefinition().phases().get(1).healthRatioThreshold(), 0.001);
    }
}
