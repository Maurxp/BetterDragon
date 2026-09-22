package maurxp.betterdragon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Carga y Validación del Catálogo de Dragones (Fase 3.13)")
class DragonConfigurationLoaderTest {

    @Test
    @DisplayName("Carga exitosa de múltiples definiciones con atributos y escalado")
    void testLoadMultipleDragonsWithAttributesAndScaling() throws Exception {
        String yaml = """
                dragons:
                  default:
                    display_name: "Dragón Estándar"
                    attributes:
                      max_health: 500.0
                      movement_speed: 0.3
                    scaling:
                      enabled: false
                      mode: "NONE"
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                  nightmare:
                    display_name: "Dragón Pesadilla"
                    attributes:
                      max_health: 800.0
                      movement_speed: 0.45
                      follow_range: 96.0
                      attack_damage: 25.0
                    scaling:
                      enabled: true
                      mode: "LINEAR"
                      health_per_player: 0.3
                      max_health_multiplier: 4.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                      - id: "p2"
                        threshold: 0.5
                        abilities: []
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        DragonCatalog catalog = config.dragonCatalog();

        assertEquals(2, catalog.size());
        assertTrue(catalog.hasDefinition("default"));
        assertTrue(catalog.hasDefinition("nightmare"));

        DragonDefinition def = catalog.getDefinition("default").orElseThrow();
        assertEquals("default", def.id());
        assertEquals("Dragón Estándar", def.displayName());
        assertEquals(500.0, def.attributes().maxHealth());
        assertEquals(0.3, def.attributes().movementSpeed().orElseThrow());
        assertFalse(def.scaling().enabled());

        DragonDefinition nightmare = catalog.getDefinition("nightmare").orElseThrow();
        assertEquals("nightmare", nightmare.id());
        assertEquals("Dragón Pesadilla", nightmare.displayName());
        assertEquals(800.0, nightmare.attributes().maxHealth());
        assertEquals(0.45, nightmare.attributes().movementSpeed().orElseThrow());
        assertEquals(96.0, nightmare.attributes().followRange().orElseThrow());
        assertEquals(25.0, nightmare.attributes().attackDamage().orElseThrow());
        assertTrue(nightmare.scaling().enabled());
        assertEquals(ScalingMode.LINEAR, nightmare.scaling().mode());
        assertEquals(0.3, nightmare.scaling().healthPerPlayer());
        assertEquals(4.0, nightmare.scaling().maxHealthMultiplier());
    }

    @Test
    @DisplayName("Carga sin sección 'dragons' recurre al catálogo por defecto")
    void testMissingDragonsSectionUsesDefaults() throws Exception {
        String yaml = """
                portal:
                  enabled: true
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        DragonCatalog catalog = config.dragonCatalog();

        assertTrue(catalog.hasDefinition("default"));
        assertEquals(1, catalog.size());
        assertEquals(200.0, catalog.getDefaultDefinition().attributes().maxHealth());
    }

    @Test
    @DisplayName("Falla si 'dragons' está vacía")
    void testEmptyDragonsSectionThrows() {
        String yaml = """
                dragons: {}
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("está vacía")));
    }

    @Test
    @DisplayName("Falla si un ID de dragón contiene caracteres inválidos")
    void testInvalidDragonIdThrows() {
        String yaml = """
                dragons:
                  "invalid:dragon:id":
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("inválido")));
    }

    @Test
    @DisplayName("Falla si 'max_health' es negativo o menor igual a cero")
    void testNegativeMaxHealthThrows() {
        String yaml = """
                dragons:
                  default:
                    attributes:
                      max_health: -50.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("max_health")));
    }

    @Test
    @DisplayName("Falla si 'scaling.mode' no es reconocido")
    void testInvalidScalingModeThrows() {
        String yaml = """
                dragons:
                  default:
                    scaling:
                      enabled: true
                      mode: "EXPONENTIAL_ARBITRARY"
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("scaling.mode")));
    }

    @Test
    @DisplayName("Falla si 'scaling.max_health_multiplier' es menor a 1.0")
    void testInvalidMaxHealthMultiplierThrows() {
        String yaml = """
                dragons:
                  default:
                    scaling:
                      enabled: true
                      mode: "LINEAR"
                      max_health_multiplier: 0.5
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("max_health_multiplier")));
    }

    @Test
    @DisplayName("Opción A: 'scaling.enabled: true' sin 'mode' infiere automáticamente ScalingMode.LINEAR")
    void testEnabledScalingWithoutModeDefaultsToLinear() throws Exception {
        String yaml = """
                dragons:
                  default:
                    scaling:
                      enabled: true
                      health_per_player: 0.2
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        DragonDefinition def = config.dragonCatalog().getDefaultDefinition();
        assertTrue(def.scaling().enabled());
        assertEquals(ScalingMode.LINEAR, def.scaling().mode());
        assertEquals(0.2, def.scaling().healthPerPlayer());
    }

    @Test
    @DisplayName("Opción A: 'scaling.enabled: false' sin 'mode' infiere automáticamente ScalingMode.NONE")
    void testDisabledScalingWithoutModeDefaultsToNone() throws Exception {
        String yaml = """
                dragons:
                  default:
                    scaling:
                      enabled: false
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        DragonDefinition def = config.dragonCatalog().getDefaultDefinition();
        assertFalse(def.scaling().enabled());
        assertEquals(ScalingMode.NONE, def.scaling().mode());
    }

    @Test
    @DisplayName("Rechaza configuración inconsistente: 'scaling.enabled: true' con 'scaling.mode: NONE'")
    void testEnabledScalingWithModeNoneThrows() {
        String yaml = """
                dragons:
                  default:
                    scaling:
                      enabled: true
                      mode: "NONE"
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("scaling.mode")));
    }
}
