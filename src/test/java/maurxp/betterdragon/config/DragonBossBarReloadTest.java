package maurxp.betterdragon.config;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Reload e Inmutabilidad de Snapshot para BossBar y Soft Enrage (Fase 3.14)")
class DragonBossBarReloadTest {

    private final Logger logger = Logger.getLogger("DragonBossBarReloadTest");

    @Test
    @DisplayName("Reload no modifica BossBar ni Soft Enrage de batalla activa; nueva batalla sí adopta nueva configuración")
    void testReloadPreservesActiveBattlePresentationAndEnrage(@TempDir Path tempDir) throws Exception {
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

        // 1. Configuración V1 (Perfil A)
        // BossBar: PURPLE, SOLID, title="Ancient Dragon • {phase}"
        // Enrage: threshold=0.25, cooldown-multiplier=0.80
        Files.writeString(configFile, """
                dragons:
                  default:
                    display_name: "Ancient Dragon"
                    attributes:
                      max_health: 500.0
                    bossbar:
                      enabled: true
                      title: "Ancient Dragon • {phase}"
                      color: PURPLE
                      style: SOLID
                    enrage:
                      enabled: true
                      threshold: 0.25
                      cooldown-multiplier: 0.80
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """);

        ConfigurationService configService = new ConfigurationService(logger);
        configService.loadInitial(configFile.toFile(), arenasFile.toFile());

        maurxp.betterdragon.arena.ArenaDefinition arena = configService.getActiveArenas().getDefaultArena();
        BattleConfigurationSnapshot snapshot1 = configService.createBattleSnapshot(arena, "default", 1);
        BattleSession session1 = new BattleSession(BattleId.random(), "world_the_end", UUID.randomUUID(), snapshot1);

        // Validar sesión 1 antes del reload
        assertEquals("Ancient Dragon • {phase}", session1.getBossBar().getConfig().title());
        assertEquals(BarColor.PURPLE, session1.getBossBar().getConfig().color());
        assertEquals(BarStyle.SOLID, session1.getBossBar().getConfig().style());
        assertEquals(0.25, session1.getConfigSnapshot().dragonDefinition().enrage().threshold(), 0.0001);
        assertEquals(0.80, session1.getConfigSnapshot().dragonDefinition().enrage().cooldownMultiplier(), 0.0001);

        // 2. Modificar disco a Configuración V2 (Perfil B) y recargar (/bd reload)
        // BossBar: RED, SEGMENTED_10, title="Void Dragon • {phase}"
        // Enrage: threshold=0.15, cooldown-multiplier=0.60
        Files.writeString(configFile, """
                dragons:
                  default:
                    display_name: "Void Dragon"
                    attributes:
                      max_health: 800.0
                    bossbar:
                      enabled: true
                      title: "Void Dragon • {phase}"
                      color: RED
                      style: SEGMENTED_10
                    enrage:
                      enabled: true
                      threshold: 0.15
                      cooldown-multiplier: 0.60
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """);

        boolean reloaded = configService.reload(configFile.toFile(), arenasFile.toFile());
        assertTrue(reloaded);

        // 3. Crear nueva sesión 2 con la configuración recargada
        BattleConfigurationSnapshot snapshot2 = configService.createBattleSnapshot(arena, "default", 1);
        BattleSession session2 = new BattleSession(BattleId.random(), "world_the_end", UUID.randomUUID(), snapshot2);

        // 4. Verificación de Invariante de Aislamiento de Snapshot:
        // Sesión 1 DEBE conservar íntegramente la configuración original (Perfil A)
        assertEquals("Ancient Dragon • {phase}", session1.getBossBar().getConfig().title());
        assertEquals(BarColor.PURPLE, session1.getBossBar().getConfig().color());
        assertEquals(BarStyle.SOLID, session1.getBossBar().getConfig().style());
        assertEquals(0.25, session1.getConfigSnapshot().dragonDefinition().enrage().threshold(), 0.0001);
        assertEquals(0.80, session1.getConfigSnapshot().dragonDefinition().enrage().cooldownMultiplier(), 0.0001);

        // Sesión 2 DEBE adoptar la nueva configuración recargada (Perfil B)
        assertEquals("Void Dragon • {phase}", session2.getBossBar().getConfig().title());
        assertEquals(BarColor.RED, session2.getBossBar().getConfig().color());
        assertEquals(BarStyle.SEGMENTED_10, session2.getBossBar().getConfig().style());
        assertEquals(0.15, session2.getConfigSnapshot().dragonDefinition().enrage().threshold(), 0.0001);
        assertEquals(0.60, session2.getConfigSnapshot().dragonDefinition().enrage().cooldownMultiplier(), 0.0001);
    }
}
