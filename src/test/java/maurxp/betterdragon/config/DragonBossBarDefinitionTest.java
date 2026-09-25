package maurxp.betterdragon.config;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonBossBarDefinition (Fase 3.14)")
class DragonBossBarDefinitionTest {

    @Test
    @DisplayName("1. defaults() genera configuración por defecto válida")
    void testDefaults() {
        DragonBossBarDefinition def = DragonBossBarDefinition.defaults();
        assertTrue(def.enabled());
        assertEquals("{dragon_name} &7• &f{phase} {enrage}", def.title());
        assertEquals(BarColor.PURPLE, def.color());
        assertEquals(BarStyle.SOLID, def.style());
    }

    @Test
    @DisplayName("2. Constructor explícito asigna todos los valores correctamente")
    void testExplicitConstructor() {
        DragonBossBarDefinition def = new DragonBossBarDefinition(false, "Ancient Void Dragon", BarColor.RED, BarStyle.SEGMENTED_10);
        assertFalse(def.enabled());
        assertEquals("Ancient Void Dragon", def.title());
        assertEquals(BarColor.RED, def.color());
        assertEquals(BarStyle.SEGMENTED_10, def.style());
    }

    @Test
    @DisplayName("3. Título nulo o en blanco lanza IllegalArgumentException")
    void testInvalidTitle() {
        assertThrows(IllegalArgumentException.class, () ->
                new DragonBossBarDefinition(true, null, BarColor.PURPLE, BarStyle.SOLID));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonBossBarDefinition(true, "   ", BarColor.PURPLE, BarStyle.SOLID));
        assertThrows(IllegalArgumentException.class, () ->
                new DragonBossBarDefinition(true, "", BarColor.PURPLE, BarStyle.SOLID));
    }

    @Test
    @DisplayName("4. Color o estilo nulo adopta valores por defecto de forma resiliente")
    void testNullColorOrStyle() {
        DragonBossBarDefinition defColorNull = new DragonBossBarDefinition(true, "Title", null, BarStyle.SOLID);
        assertEquals(BarColor.PURPLE, defColorNull.color());

        DragonBossBarDefinition defStyleNull = new DragonBossBarDefinition(true, "Title", BarColor.PURPLE, null);
        assertEquals(BarStyle.SOLID, defStyleNull.style());
    }

    @Test
    @DisplayName("5. formatTitle sustituye placeholders {dragon_name} y {phase}")
    void testFormatTitlePlaceholders() {
        DragonBossBarDefinition def = new DragonBossBarDefinition(true, "{dragon_name} - {phase}", BarColor.PURPLE, BarStyle.SOLID);
        String formatted = def.formatTitle("Ancient Dragon", "Phase Two", false);
        assertEquals("Ancient Dragon - Phase Two", formatted);
    }

    @Test
    @DisplayName("6. formatTitle con placeholder {enrage} explícito")
    void testFormatTitleExplicitEnragePlaceholder() {
        DragonBossBarDefinition def = new DragonBossBarDefinition(true, "{dragon_name} [{phase}] {enrage}", BarColor.PURPLE, BarStyle.SOLID);
        String notEnraged = def.formatTitle("Dragon", "1", false);
        assertEquals("Dragon [1]", notEnraged);

        String enraged = def.formatTitle("Dragon", "1", true);
        assertEquals("Dragon [1] §c[ENRAGE]", enraged);
    }

    @Test
    @DisplayName("7. formatTitle NO anexa [ENRAGE] automáticamente si el título no contiene {enrage}")
    void testFormatTitleNoEnragePlaceholderDoesNotAppend() {
        DragonBossBarDefinition def = new DragonBossBarDefinition(true, "{dragon_name} • {phase}", BarColor.PURPLE, BarStyle.SOLID);
        String enraged = def.formatTitle("Dragon", "Phase 3", true);
        assertFalse(enraged.contains("[ENRAGE]"));
        assertFalse(enraged.contains("§c"));
        assertEquals("Dragon • Phase 3", enraged);
    }

    @Test
    @DisplayName("8. formatTitle traduce códigos de color ampersand (&)")
    void testFormatTitleColorCodes() {
        DragonBossBarDefinition def = new DragonBossBarDefinition(true, "&d&l{dragon_name} &7• &f{phase}", BarColor.PURPLE, BarStyle.SOLID);
        String formatted = def.formatTitle("Ender Dragon", "Fase 1", false);
        assertEquals("§d§lEnder Dragon §7• §fFase 1", formatted);
    }

    @Test
    @DisplayName("9. formatTitle con {enrage} al inicio o medio reemplaza exactamente según estado")
    void testFormatTitleEnrageSemanticsExact() {
        DragonBossBarDefinition def = new DragonBossBarDefinition(true, "{enrage} {dragon_name} ({phase})", BarColor.PURPLE, BarStyle.SOLID);
        assertEquals("Dragon (p1)", def.formatTitle("Dragon", "p1", false));
        assertEquals("§c[ENRAGE] Dragon (p1)", def.formatTitle("Dragon", "p1", true));
    }
}
