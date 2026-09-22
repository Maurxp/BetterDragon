package maurxp.betterdragon.config;

import maurxp.betterdragon.phase.PhaseDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonCatalog (Fase 3.13)")
class DragonCatalogTest {

    private DragonDefinition createDragon(String id) {
        return new DragonDefinition(
                id,
                "Name " + id,
                DragonAttributes.defaults(),
                DragonScalingDefinition.defaults(),
                List.of(new PhaseDefinition("p1", 0, 1.0, List.of())),
                Map.of()
        );
    }

    @Test
    @DisplayName("Catálogo con una única definición")
    void testSingleDefinitionCatalog() {
        DragonDefinition def = createDragon("default");
        DragonCatalog catalog = DragonCatalog.of(def);

        assertEquals(1, catalog.size());
        assertTrue(catalog.hasDefinition("default"));
        assertEquals(def, catalog.getDefaultDefinition());
        assertTrue(catalog.getDefinition("default").isPresent());
    }

    @Test
    @DisplayName("Catálogo con múltiples definiciones")
    void testMultipleDefinitionsCatalog() {
        DragonDefinition def1 = createDragon("default");
        DragonDefinition def2 = createDragon("hardcore");
        DragonDefinition def3 = createDragon("raid");

        DragonCatalog catalog = DragonCatalog.of(def1, def2, def3);

        assertEquals(3, catalog.size());
        assertTrue(catalog.hasDefinition("default"));
        assertTrue(catalog.hasDefinition("hardcore"));
        assertTrue(catalog.hasDefinition("raid"));

        assertEquals(def2, catalog.getDefinition("hardcore").orElseThrow());
        assertEquals(def3, catalog.getDefinition("raid").orElseThrow());
    }

    @Test
    @DisplayName("Búsqueda insensible a mayúsculas y espacios")
    void testCaseInsensitiveLookup() {
        DragonDefinition def = createDragon("elder_dragon");
        DragonCatalog catalog = new DragonCatalog(Map.of("elder_dragon", def), "elder_dragon");

        assertTrue(catalog.hasDefinition("ELDER_DRAGON"));
        assertTrue(catalog.hasDefinition("  elder_dragon  "));
        assertTrue(catalog.getDefinition("Elder_Dragon").isPresent());
    }

    @Test
    @DisplayName("Búsqueda de ID desconocido retorna Optional.empty()")
    void testUnknownIdLookup() {
        DragonCatalog catalog = DragonCatalog.defaults();
        assertFalse(catalog.hasDefinition("unknown_dragon"));
        assertTrue(catalog.getDefinition("unknown_dragon").isEmpty());
    }

    @Test
    @DisplayName("getDefinitionOrDefault retorna default si ID es nulo, vacío o desconocido")
    void testGetDefinitionOrDefault() {
        DragonDefinition def1 = createDragon("default");
        DragonDefinition def2 = createDragon("nightmare");
        DragonCatalog catalog = DragonCatalog.of(def1, def2);

        assertEquals(def1, catalog.getDefinitionOrDefault(null));
        assertEquals(def1, catalog.getDefinitionOrDefault(""));
        assertEquals(def1, catalog.getDefinitionOrDefault("not_found"));
        assertEquals(def2, catalog.getDefinitionOrDefault("nightmare"));
    }

    @Test
    @DisplayName("Rechaza catálogo vacío o con defaultDefinitionId inexistente")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new DragonCatalog(Map.of(), "default"));

        DragonDefinition def = createDragon("boss");
        assertThrows(IllegalArgumentException.class, () ->
                new DragonCatalog(Map.of("boss", def), "non_existent_default"));
    }

    @Test
    @DisplayName("Permite defaultDefinitionId nulo si no existe un default formal")
    void testNullableDefault() {
        DragonDefinition def = createDragon("custom");
        DragonCatalog catalog = new DragonCatalog(Map.of("custom", def), null);

        assertFalse(catalog.hasDefaultDefinition());
        assertNull(catalog.getDefaultDefinition());
        assertTrue(catalog.defaultDefinition().isEmpty());
        assertEquals(def, catalog.getDefinition("custom").orElseThrow());
        assertThrows(IllegalStateException.class, () -> catalog.getDefinitionOrDefault("unknown"));
    }

    @Test
    @DisplayName("getAllDefinitions retorna un mapa inmutable")
    void testImmutability() {
        DragonCatalog catalog = DragonCatalog.defaults();
        assertThrows(UnsupportedOperationException.class, () ->
                catalog.getAllDefinitions().put("new", createDragon("new")));
    }
}
