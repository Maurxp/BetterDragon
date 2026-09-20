package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link ArenaConfigurationLoader}.
 *
 * @author maurxp
 */
class ArenaConfigurationLoaderTest {

    private static final String VALID_ARENAS_YAML = """
            arenas:
              default:
                world: "world_the_end"
                center:
                  x: 0.5
                  y: 100.0
                  z: 0.5
                podium:
                  x: 0.5
                  y: 65.0
                  z: 0.5
                bounds:
                  min:
                    x: -120.0
                    y: 10.0
                    z: -120.0
                  max:
                    x: 120.0
                    y: 200.0
                    z: 120.0
                rules:
                  water_allowed: false
                  boundary:
                    enabled: true
                  anti_tunnel:
                    enabled: true
              second:
                world: "world_the_end_nether"
                center:
                  x: 10.0
                  y: 90.0
                  z: 10.0
                podium:
                  x: 10.0
                  y: 60.0
                  z: 10.0
                bounds:
                  min:
                    x: -50.0
                    y: 0.0
                    z: -50.0
                  max:
                    x: 50.0
                    y: 150.0
                    z: 50.0
                rules:
                  water_allowed: true
                  boundary:
                    enabled: false
                  anti_tunnel:
                    enabled: false
            """;

    @Test
    @DisplayName("Carga válida de arenas.yml desde Reader produce snapshot inmutable")
    void testValidArenasLoad() throws Exception {
        ArenaConfigurationSnapshot snapshot = ArenaConfigurationLoader.load(new StringReader(VALID_ARENAS_YAML));

        assertNotNull(snapshot);
        assertEquals(2, snapshot.arenas().size());
        assertTrue(snapshot.getArena("default").isPresent());
        assertTrue(snapshot.getArena("second").isPresent());

        ArenaDefinition def = snapshot.getArena("default").get();
        assertEquals("default", def.id());
        assertEquals("world_the_end", def.worldName());
        assertEquals(0.5, def.center().x());
        assertEquals(100.0, def.center().y());
        assertEquals(65.0, def.podium().y());
        assertFalse(def.rules().isWaterAllowed());
        assertTrue(def.rules().isBoundaryEnabled());
        assertTrue(def.rules().isAntiTunnelEnabled());

        ArenaDefinition second = snapshot.getArena("second").get();
        assertEquals("second", second.id());
        assertTrue(second.rules().isWaterAllowed());
        assertFalse(second.rules().isBoundaryEnabled());

        // Inmutabilidad del mapa
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.arenas().put("injected", def)
        );
    }

    @Test
    @DisplayName("YAML sin sección 'arenas' es rechazado con ConfigValidationException")
    void testMissingArenasSection() {
        String yaml = "other_config: true\n";
        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ArenaConfigurationLoader.load(new StringReader(yaml))
        );
        assertTrue(ex.getMessage().contains("arenas"));
    }

    @Test
    @DisplayName("YAML con campos faltantes o inválidos recolecta todos los errores conjuntamente")
    void testValidationErrorsCollection() {
        String invalidYaml = """
                arenas:
                  invalid_arena:
                    world: ""
                    center:
                      x: "not_a_number"
                      y: 100.0
                      z: 0.0
                    podium:
                      x: 0.0
                      y: 65.0
                      z: 0.0
                    bounds:
                      min:
                        x: 50.0
                        y: 0.0
                        z: 0.0
                      max:
                        x: 10.0
                        y: 200.0
                        z: 100.0
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ArenaConfigurationLoader.load(new StringReader(invalidYaml))
        );

        List<String> errors = ex.getErrors();
        assertNotNull(errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("world")), "Debe reportar error en world");
        assertTrue(errors.stream().anyMatch(e -> e.contains("center.x")), "Debe reportar error en center.x");
        assertTrue(errors.stream().anyMatch(e -> e.contains("min.x")), "Debe reportar error en min.x > max.x");
    }

    @Test
    @DisplayName("YAML con centro fuera de los límites de la arena es rechazado")
    void testCenterOutOfBoundsRejectedInYaml() {
        String outOfBoundsYaml = """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 500.0
                      y: 100.0
                      z: 0.0
                    podium:
                      x: 0.0
                      y: 65.0
                      z: 0.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                    rules:
                      water_allowed: false
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ArenaConfigurationLoader.load(new StringReader(outOfBoundsYaml))
        );
        assertTrue(ex.getMessage().contains("fuera de los límites"));
    }

    @Test
    @DisplayName("YAML con water_allowed y water_denial simultáneos es rechazado por ambigüedad")
    void testWaterAllowedAndWaterDenialConflictRejected() {
        String conflictingYaml = """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 0.0
                      y: 100.0
                      z: 0.0
                    podium:
                      x: 0.0
                      y: 65.0
                      z: 0.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                    rules:
                      water_allowed: true
                      water_denial: true
                      boundary_enabled: true
                      anti_tunnel_enabled: true
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ArenaConfigurationLoader.load(new StringReader(conflictingYaml))
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("ambigua")),
                "Debe reportar configuración ambigua de agua: " + ex.getErrors());
    }

    @Test
    @DisplayName("YAML sin sección 'rules' es rechazado sin inventar defaults de gameplay")
    void testMissingRulesSectionRejected() {
        String noRulesYaml = """
                arenas:
                  default:
                    world: "world_the_end"
                    center:
                      x: 0.0
                      y: 100.0
                      z: 0.0
                    podium:
                      x: 0.0
                      y: 65.0
                      z: 0.0
                    bounds:
                      min:
                        x: -100.0
                        y: 0.0
                        z: -100.0
                      max:
                        x: 100.0
                        y: 200.0
                        z: 100.0
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class, () ->
                ArenaConfigurationLoader.load(new StringReader(noRulesYaml))
        );
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("rules")),
                "Debe exigir explícitamente la sección rules: " + ex.getErrors());
    }
}
