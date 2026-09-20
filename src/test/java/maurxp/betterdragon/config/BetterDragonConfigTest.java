package maurxp.betterdragon.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class BetterDragonConfigTest {

    @Test
    @DisplayName("Defaults de configuración son válidos y predecibles")
    void testDefaults() {
        BetterDragonConfig config = BetterDragonConfig.defaults();

        assertFalse(config.portalEnabled(), "portal.enabled por defecto debe ser false");
        assertEquals("INFO", config.loggingLevel(), "logging.level por defecto debe ser INFO");
        assertFalse(config.debugLogging(), "logging.debug por defecto debe ser false");
    }

    @Test
    @DisplayName("Carga y validación exitosa con valores personalizados válidos")
    void testValidConfigurationParsing() throws Exception {
        String yamlContent = """
                portal:
                  enabled: true
                logging:
                  level: "WARNING"
                  debug: true
                custom_extra_key: "permitida"
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yamlContent));

        assertTrue(config.portalEnabled());
        assertEquals("WARNING", config.loggingLevel());
        assertTrue(config.debugLogging());
    }

    @Test
    @DisplayName("portal.enabled con tipo incompatible (String en vez de boolean) lanza ConfigValidationException")
    void testInvalidPortalEnabledTypeThrows() {
        String yamlContent = """
                portal:
                  enabled: "banana"
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yamlContent))
        );

        assertTrue(ex.getMessage().contains("portal.enabled debe ser un booleano"),
                "El mensaje debe especificar el error en portal.enabled: " + ex.getMessage());
    }

    @Test
    @DisplayName("logging.level con valor fuera del conjunto permitido lanza ConfigValidationException")
    void testInvalidLogLevelThrows() {
        String yamlContent = """
                logging:
                  level: "SUPER_VERBOSE"
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yamlContent))
        );

        assertTrue(ex.getMessage().contains("logging.level inválido"),
                "El mensaje debe indicar nivel inválido: " + ex.getMessage());
    }

    @Test
    @DisplayName("logging.debug con tipo incompatible lanza ConfigValidationException")
    void testInvalidDebugLoggingTypeThrows() {
        String yamlContent = """
                logging:
                  debug: "yes"
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yamlContent))
        );

        assertTrue(ex.getMessage().contains("logging.debug debe ser un booleano"),
                "El mensaje debe especificar el error en logging.debug: " + ex.getMessage());
    }

    @Test
    @DisplayName("YAML sintácticamente corrupto lanza ConfigValidationException")
    void testMalformedYamlThrows() {
        String malformedYaml = """
                portal:
                  enabled: [unclosed list
                  - 1
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(malformedYaml))
        );

        assertTrue(ex.getMessage().contains("mal formado o corrupto"),
                "El mensaje debe advertir sobre YAML mal formado: " + ex.getMessage());
    }
}
