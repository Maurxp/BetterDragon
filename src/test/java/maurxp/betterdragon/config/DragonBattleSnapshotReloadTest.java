package maurxp.betterdragon.config;

import maurxp.betterdragon.arena.ArenaDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Inmutabilidad de Snapshots y Reload de Dragones (Fase 3.13)")
class DragonBattleSnapshotReloadTest {

    private final Logger logger = Logger.getLogger("DragonSnapshotReloadTest");

    @Test
    @DisplayName("Reload no muta el snapshot de una batalla activa; nueva batalla recibe nueva configuración")
    void testReloadDoesNotMutateActiveBattleSnapshot(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.yml");
        Path arenasFile = tempDir.resolve("arenas.yml");

        Files.writeString(arenasFile, """
                default_arena: "arena_end"
                arenas:
                  arena_end:
                    world: "world_the_end"
                    center:
                      x: 0.0
                      y: 80.0
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
                        y: 150.0
                        z: 100.0
                    rules:
                      water_allowed: false
                      boundary:
                        enabled: true
                      anti_tunnel:
                        enabled: true
                """);

        // 1. Configuración versión 1: default con 500 HP
        Files.writeString(configFile, """
                dragons:
                  default:
                    display_name: "Dragón V1"
                    attributes:
                      max_health: 500.0
                    scaling:
                      enabled: false
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """);

        ConfigurationService configService = new ConfigurationService(logger);
        configService.loadInitial(configFile.toFile(), arenasFile.toFile());

        ArenaDefinition arena = configService.getActiveArenas().getDefaultArena();

        // 2. Crear snapshot para Battle A (500 HP)
        BattleConfigurationSnapshot snapshotA = configService.createBattleSnapshot(arena, "default", 3);
        assertEquals(500.0, snapshotA.effectiveDragonStats().maxHealth());
        assertEquals("Dragón V1", snapshotA.dragonDefinition().displayName());

        // 3. Modificar archivo para versión 2: default con 1200 HP y escalado activado
        Files.writeString(configFile, """
                dragons:
                  default:
                    display_name: "Dragón V2 Actualizado"
                    attributes:
                      max_health: 1200.0
                    scaling:
                      enabled: true
                      mode: "LINEAR"
                      health_per_player: 0.25
                      max_health_multiplier: 3.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """);

        // 4. Ejecutar reload
        boolean reloaded = configService.reload(configFile.toFile(), arenasFile.toFile());
        assertTrue(reloaded);

        // 5. Invariante fundamental: Snapshot A permanece 100% inmutable
        assertEquals(500.0, snapshotA.effectiveDragonStats().maxHealth());
        assertEquals("Dragón V1", snapshotA.dragonDefinition().displayName());
        assertFalse(snapshotA.dragonDefinition().scaling().enabled());

        // 6. Nueva batalla recibe la nueva configuración y nuevo escalado (con 3 jugadores: 1200 * (1 + 2 * 0.25) = 1800 HP)
        BattleConfigurationSnapshot snapshotB = configService.createBattleSnapshot(arena, "default", 3);
        assertEquals("Dragón V2 Actualizado", snapshotB.dragonDefinition().displayName());
        assertEquals(1800.0, snapshotB.effectiveDragonStats().maxHealth(), 0.0001);
        assertEquals(1.5, snapshotB.effectiveDragonStats().healthMultiplier(), 0.0001);
    }
}
