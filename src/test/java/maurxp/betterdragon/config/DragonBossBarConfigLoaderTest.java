package maurxp.betterdragon.config;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Carga y Validación de BossBar y Soft Enrage en ConfigurationLoader (Fase 3.14)")
class DragonBossBarConfigLoaderTest {

    @Test
    @DisplayName("1. Carga explícita y completa de secciones bossbar y enrage")
    void testLoadExplicitBossBarAndEnrage() throws Exception {
        String yaml = """
                dragons:
                  default:
                    display_name: "Ancient Dragon"
                    bossbar:
                      enabled: true
                      title: "&5{dragon_name} &8- &dFase {phase}"
                      color: PINK
                      style: SEGMENTED_20
                    enrage:
                      enabled: true
                      threshold: 0.25
                      cooldown-multiplier: 0.65
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        DragonDefinition def = config.dragonCatalog().getDefinition("default").orElseThrow();

        // BossBar
        DragonBossBarDefinition bossbar = def.bossbar();
        assertNotNull(bossbar);
        assertTrue(bossbar.enabled());
        assertEquals("&5{dragon_name} &8- &dFase {phase}", bossbar.title());
        assertEquals(BarColor.PINK, bossbar.color());
        assertEquals(BarStyle.SEGMENTED_20, bossbar.style());

        // Enrage
        DragonEnrageDefinition enrage = def.enrage();
        assertNotNull(enrage);
        assertTrue(enrage.enabled());
        assertEquals(0.25, enrage.threshold(), 0.0001);
        assertEquals(0.65, enrage.cooldownMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("2. Omisión de secciones bossbar y enrage aplica valores por defecto válidos")
    void testDefaultsWhenOmitted() throws Exception {
        String yaml = """
                dragons:
                  default:
                    display_name: "Standard Dragon"
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        BetterDragonConfig config = ConfigurationLoader.load(new StringReader(yaml));
        DragonDefinition def = config.dragonCatalog().getDefinition("default").orElseThrow();

        DragonBossBarDefinition bossbar = def.bossbar();
        assertNotNull(bossbar);
        assertTrue(bossbar.enabled());
        assertEquals(BarColor.PURPLE, bossbar.color());
        assertEquals(BarStyle.SOLID, bossbar.style());

        DragonEnrageDefinition enrage = def.enrage();
        assertNotNull(enrage);
        assertTrue(enrage.enabled());
        assertEquals(0.20, enrage.threshold(), 0.0001);
        assertEquals(0.75, enrage.cooldownMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("3. Color de BossBar inválido acumula error de validación")
    void testInvalidBossBarColor() {
        String yaml = """
                dragons:
                  default:
                    bossbar:
                      color: "ULTRA_VIOLET"
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
    }

    @Test
    @DisplayName("4. Estilo de BossBar inválido acumula error de validación")
    void testInvalidBossBarStyle() {
        String yaml = """
                dragons:
                  default:
                    bossbar:
                      style: "CIRCULAR_PROGRESS"
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
    }

    @Test
    @DisplayName("5. Threshold de Enrage fuera de rango (> 1.0) acumula error de validación")
    void testInvalidEnrageThreshold() {
        String yaml = """
                dragons:
                  default:
                    enrage:
                      threshold: 1.50
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
    }

    @Test
    @DisplayName("6. Multiplicador de cooldown no positivo (<= 0) acumula error de validación")
    void testInvalidEnrageMultiplier() {
        String yaml = """
                dragons:
                  default:
                    enrage:
                      cooldown-multiplier: 0.0
                    phases:
                      - id: "p1"
                        threshold: 1.0
                        abilities: []
                """;

        assertThrows(ConfigValidationException.class, () ->
                ConfigurationLoader.load(new StringReader(yaml)));
    }
}
