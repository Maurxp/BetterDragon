package maurxp.betterdragon.config;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para la deserialización y validación de fases y habilidades en {@link ConfigurationLoader}.
 *
 * @author maurxp
 */
class ConfigurationLoaderPhasesTest {

    @Test
    @DisplayName("Carga exitosa de YAML completo con fases y habilidades tipadas")
    void testLoadFullYaml() throws Exception {
        String yaml = """
                portal:
                  enabled: true
                logging:
                  level: "INFO"
                  debug: false

                abilities:
                  tail_sweep:
                    trigger: PERIODIC
                    cooldown: 160
                    target: NEAREST_PLAYER
                    origin: DRAGON_BODY
                    effect:
                      type: KNOCKBACK
                      strength: 1.5
                  dragon_roar:
                    trigger: ON_PHASE_ENTER
                    cooldown: 0
                    target: ALL_IN_ARENA
                    origin: DRAGON_HEAD
                    effect:
                      type: SOUND
                      sound: ENTITY_ENDER_DRAGON_GROWL

                dragons:
                  default:
                    phases:
                      - id: "phase_1"
                        threshold: 1.00
                        abilities:
                          - "dragon_roar"
                          - "tail_sweep"
                      - id: "phase_2"
                        threshold: 0.75
                        abilities:
                          - "tail_sweep"
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));

        assertTrue(config.portalEnabled());
        DragonDefinition dragon = config.dragonDefinition();
        assertNotNull(dragon);
        assertEquals("default", dragon.id());
        assertEquals(2, dragon.phases().size());
        assertEquals(2, dragon.abilities().size());

        PhaseDefinition p1 = dragon.phases().getFirst();
        assertEquals("phase_1", p1.id());
        assertEquals(1.0, p1.healthRatioThreshold(), 0.001);
        assertEquals(2, p1.abilityIds().size());

        AbilityDefinition sweep = dragon.abilities().get("tail_sweep");
        assertNotNull(sweep);
        assertEquals(AbilityTrigger.PERIODIC, sweep.trigger());
        assertEquals(160L, sweep.cooldownTicks());
        assertEquals(AbilityEffectType.KNOCKBACK, sweep.effectType());
        assertEquals(1.5, sweep.getDoubleProperty("strength", 0.0), 0.001);

        // Snapshot inmutable
        BattleConfigurationSnapshot snapshot = config.toBattleSnapshot();
        assertEquals(2, snapshot.dragonDefinition().phases().size());
    }

    @Test
    @DisplayName("Rechazo de YAML con trigger de habilidad inválido")
    void testInvalidTrigger() {
        String yaml = """
                abilities:
                  bad_ability:
                    trigger: INVALID_TRIGGER
                    cooldown: 50
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    effect: DAMAGE
                dragons:
                  default:
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities:
                          - "bad_ability"
                """;

        assertThrows(ConfigValidationException.class, () -> ConfigurationLoader.load(new StringReader(yaml)));
    }

    @Test
    @DisplayName("Rechazo de YAML con habilidad referenciada inexistente en fase")
    void testMissingReferencedAbility() {
        String yaml = """
                abilities:
                  existing:
                    trigger: PERIODIC
                    cooldown: 50
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    effect: DAMAGE
                dragons:
                  default:
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities:
                          - "non_existent_ability"
                """;

        assertThrows(ConfigValidationException.class, () -> ConfigurationLoader.load(new StringReader(yaml)));
    }

    @Test
    @DisplayName("YAML mínimo sin sección dragons inyecta definición por defecto segura")
    void testDefaultInjectedWhenMissing() throws Exception {
        String yaml = """
                portal:
                  enabled: false
                logging:
                  level: "INFO"
                  debug: false
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        assertNotNull(config.dragonDefinition());
        assertEquals("default", config.dragonDefinition().id());
        assertEquals(4, config.dragonDefinition().phases().size());
    }

    @Test
    @DisplayName("Configuración sin habilidades (abilities: {} y phases con abilities: []) no exige un moveset inventado")
    void testEmptyAbilitiesAndPhases() throws Exception {
        String yaml = """
                portal:
                  enabled: false
                logging:
                  level: "INFO"
                  debug: false

                abilities: {}

                dragons:
                  default:
                    phases:
                      - id: "phase_1"
                        threshold: 1.00
                        abilities: []
                      - id: "phase_2"
                        threshold: 0.75
                        abilities: []
                      - id: "phase_3"
                        threshold: 0.50
                        abilities: []
                      - id: "phase_4"
                        threshold: 0.25
                        abilities: []
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        assertNotNull(config.dragonDefinition());
        assertTrue(config.dragonDefinition().abilities().isEmpty(), "El catálogo debe estar vacío sin inventar habilidades");
        assertEquals(4, config.dragonDefinition().phases().size());
        for (PhaseDefinition p : config.dragonDefinition().phases()) {
            assertTrue(p.abilityIds().isEmpty(), "Las fases no deben requerir habilidades ficticias");
        }
    }
}
