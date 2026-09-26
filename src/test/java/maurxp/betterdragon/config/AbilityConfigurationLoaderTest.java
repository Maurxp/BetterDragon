package maurxp.betterdragon.config;

import maurxp.betterdragon.ability.AbilityDefinition;
import maurxp.betterdragon.ability.AbilityEffectType;
import maurxp.betterdragon.ability.AbilityTrigger;
import maurxp.betterdragon.ability.TelegraphDefinition;
import maurxp.betterdragon.arena.ArenaDefinition;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de carga de configuración y aislamiento de snapshots para habilidades avanzadas
 * y telegrafiado sensorial.
 *
 * @author maurxp
 */
class AbilityConfigurationLoaderTest {

    private final Logger logger = Logger.getLogger("AbilityConfigLoaderTest");

    @Test
    @DisplayName("Carga nominal de habilidades de combate avanzado y telegrafiado sensorial")
    void testNominalParsingOfAdvancedAbilities() throws Exception {
        String yaml = """
                abilities:
                  aerial_bomb:
                    trigger: ON_FLIGHT_PHASE
                    cooldown: 120
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    flight_phase: "CIRCLING"
                    effect:
                      type: CARPET_BOMB
                      count: 4
                      fuse_ticks: 50
                      spread_radius: 6.0
                    telegraph:
                      duration: 40
                      particle: FLAME
                      particle_count: 35
                      particle_radius: 5.0
                      sound: ENTITY_ENDER_DRAGON_GROWL
                      volume: 2.0
                      pitch: 1.0
                  perch_quake:
                    trigger: ON_FLIGHT_PHASE
                    cooldown: 200
                    target: ALL_IN_ARENA
                    origin: PODIUM_CENTER
                    flight_phase: "LAND_ON_PORTAL"
                    effect:
                      type: SHOCKWAVE
                      radius: 25.0
                      damage: 12.0
                      knockback: 1.5
                    telegraph: "MAJOR"
                  counter_minions:
                    trigger: ON_DAMAGE
                    cooldown: 0
                    target: TRIGGERING_PLAYER
                    origin: DRAGON_BODY
                    attacker_cooldown_ticks: 100
                    ranged_only: true
                    chance: 0.8
                    effect:
                      type: SUMMON
                      minion_type: "ENDERMITE"
                      count: 2
                    telegraph: true
                dragons:
                  default:
                    display_name: "Dragón Épico"
                    attributes:
                      max_health: 500.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: ["aerial_bomb", "perch_quake", "counter_minions"]
                """;

        BetterDragonConfig loaded = ConfigurationLoader.load(new StringReader(yaml));
        assertNotNull(loaded);

        // 1. Verificar aerial_bomb
        AbilityDefinition bomb = loaded.dragonDefinition().abilities().get("aerial_bomb");
        assertNotNull(bomb);
        assertEquals(AbilityTrigger.ON_FLIGHT_PHASE, bomb.trigger());
        assertEquals(120L, bomb.cooldownTicks());
        assertEquals(AbilityEffectType.CARPET_BOMB, bomb.effectType());
        assertEquals("CIRCLING", bomb.getStringProperty("flight_phase", ""));
        assertEquals(4, bomb.getIntProperty("count", 0));
        assertEquals(50, bomb.getIntProperty("fuse_ticks", 0));
        assertEquals(6.0, bomb.getDoubleProperty("spread_radius", 0.0), 0.001);

        assertTrue(bomb.hasTelegraph());
        TelegraphDefinition bombTel = bomb.getTelegraph().orElseThrow();
        assertEquals(40L, bombTel.durationTicks());
        assertEquals(Particle.FLAME, bombTel.particle());
        assertEquals(35, bombTel.particleCount());
        assertEquals(5.0, bombTel.particleRadius(), 0.001);
        assertEquals("ENTITY_ENDER_DRAGON_GROWL", bombTel.sound());
        assertEquals(2.0f, bombTel.soundVolume(), 0.001f);
        assertEquals(1.0f, bombTel.soundPitch(), 0.001f);

        // 2. Verificar perch_quake (clasificación MAJOR)
        AbilityDefinition quake = loaded.dragonDefinition().abilities().get("perch_quake");
        assertNotNull(quake);
        assertEquals(AbilityEffectType.SHOCKWAVE, quake.effectType());
        assertEquals("LAND_ON_PORTAL", quake.getStringProperty("flight_phase", ""));
        assertEquals(25.0, quake.getDoubleProperty("radius", 0.0), 0.001);
        assertTrue(quake.hasTelegraph());
        assertEquals(TelegraphDefinition.TICKS_MAJOR, quake.getTelegraph().orElseThrow().durationTicks());

        // 3. Verificar counter_minions (telegraph booleano = true -> defaults)
        AbilityDefinition counter = loaded.dragonDefinition().abilities().get("counter_minions");
        assertNotNull(counter);
        assertEquals(AbilityTrigger.ON_DAMAGE, counter.trigger());
        assertEquals(AbilityEffectType.SUMMON, counter.effectType());
        assertEquals(100L, counter.getLongProperty("attacker_cooldown_ticks", 0L));
        assertTrue(counter.getBooleanProperty("ranged_only", false));
        assertEquals(0.8, counter.getDoubleProperty("chance", 0.0), 0.001);
        assertTrue(counter.hasTelegraph());
        assertEquals(TelegraphDefinition.TICKS_MODERATE, counter.getTelegraph().orElseThrow().durationTicks());
    }

    @Test
    @DisplayName("Detección de errores y fail-fast en parámetros de telegrafiado inválidos")
    void testInvalidTelegraphParametersFailFast() {
        String yaml = """
                abilities:
                  bad_dur:
                    trigger: ON_PHASE_ENTER
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    effect: KNOCKBACK
                    telegraph:
                      duration: -10
                  bad_part:
                    trigger: ON_PHASE_ENTER
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    effect: KNOCKBACK
                    telegraph:
                      particle: "PARTICULA_INVENTADA_XYZ"
                  bad_sound:
                    trigger: ON_PHASE_ENTER
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    effect: KNOCKBACK
                    telegraph:
                      sound: "SONIDO_INEXISTENTE_ABC"
                dragons:
                  default:
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> ConfigurationLoader.load(new StringReader(yaml)));

        String msg = ex.getMessage();
        assertTrue(msg.contains("telegraph.duration") || msg.contains("duration"));
        assertTrue(msg.contains("telegraph.particle") || msg.contains("particle"));
        assertTrue(msg.contains("telegraph.sound") || msg.contains("sound"));
    }

    @Test
    @DisplayName("Snapshot isolation preserva habilidades y telegrafiados frente a /bd reload")
    void testSnapshotIsolationWithAdvancedAbilities(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.yml");
        Path arenasFile = tempDir.resolve("arenas.yml");

        Files.writeString(arenasFile, """
                default_arena: "arena_end"
                arenas:
                  arena_end:
                    world: "world_the_end"
                    center: {x: 0.0, y: 80.0, z: 0.0}
                    podium: {x: 0.0, y: 65.0, z: 0.0}
                    bounds:
                      min: {x: -100.0, y: 0.0, z: -100.0}
                      max: {x: 100.0, y: 150.0, z: 100.0}
                    rules:
                      water_allowed: false
                      boundary:
                        enabled: true
                      anti_tunnel:
                        enabled: true
                """);

        // Versión 1: Bombardeo con 3 bombas y telegraph MINOR (16t)
        Files.writeString(configFile, """
                abilities:
                  aerial_bomb:
                    trigger: ON_FLIGHT_PHASE
                    cooldown: 100
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    flight_phase: "CIRCLING"
                    effect:
                      type: CARPET_BOMB
                      count: 3
                    telegraph: "MINOR"
                dragons:
                  default:
                    display_name: "Dragón V1"
                    attributes:
                      max_health: 500.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: ["aerial_bomb"]
                """);

        ConfigurationService configService = new ConfigurationService(logger);
        configService.loadInitial(configFile.toFile(), arenasFile.toFile());

        ArenaDefinition arena = configService.getActiveArenas().getDefaultArena();
        BattleConfigurationSnapshot snapshotA = configService.createBattleSnapshot(arena, "default", 1);

        AbilityDefinition bombA = snapshotA.dragonDefinition().abilities().get("aerial_bomb");
        assertNotNull(bombA);
        assertEquals(3, bombA.getIntProperty("count", 0));
        assertEquals(100L, bombA.cooldownTicks());
        assertEquals(TelegraphDefinition.TICKS_MINOR, bombA.getTelegraph().orElseThrow().durationTicks());

        // Modificar a Versión 2: Bombardeo con 8 bombas y telegraph LETHAL (60t)
        Files.writeString(configFile, """
                abilities:
                  aerial_bomb:
                    trigger: ON_FLIGHT_PHASE
                    cooldown: 250
                    target: ALL_IN_ARENA
                    origin: DRAGON_BODY
                    flight_phase: "CIRCLING"
                    effect:
                      type: CARPET_BOMB
                      count: 8
                    telegraph: "LETHAL"
                dragons:
                  default:
                    display_name: "Dragón V2"
                    attributes:
                      max_health: 1000.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: ["aerial_bomb"]
                """);

        // Ejecutar reload
        boolean reloaded = configService.reload(configFile.toFile(), arenasFile.toFile());
        assertTrue(reloaded);

        // Snapshot A permanece congelado e inalterado
        assertEquals(3, bombA.getIntProperty("count", 0));
        assertEquals(100L, bombA.cooldownTicks());
        assertEquals(TelegraphDefinition.TICKS_MINOR, bombA.getTelegraph().orElseThrow().durationTicks());

        // Nueva batalla recibe la nueva configuración V2
        BattleConfigurationSnapshot snapshotB = configService.createBattleSnapshot(arena, "default", 1);
        AbilityDefinition bombB = snapshotB.dragonDefinition().abilities().get("aerial_bomb");
        assertNotNull(bombB);
        assertEquals(8, bombB.getIntProperty("count", 0));
        assertEquals(250L, bombB.cooldownTicks());
        assertEquals(TelegraphDefinition.TICKS_LETHAL, bombB.getTelegraph().orElseThrow().durationTicks());
    }
}
