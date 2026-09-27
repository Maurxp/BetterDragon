package maurxp.betterdragon.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba automatizada de integridad histórica para prevenir regresiones documentales en BetterDragon.
 * <p>
 * Verifica que ninguna actualización futura del README o de la documentación técnica elimine accidentalmente
 * entradas de fases completadas previamente.
 *
 * @author maurxp
 */
@DisplayName("Pruebas de Integridad Histórica y Anti-Regresión Documental")
class DocumentationHistoryTest {

    private static final List<String> REQUIRED_PHASES = List.of(
            "3.0",
            "3.0-R1",
            "3.1",
            "3.2",
            "3.3",
            "3.3-R1",
            "3.4",
            "3.5",
            "3.5-R1",
            "3.6",
            "3.6-R1",
            "3.7",
            "3.7-R1",
            "3.8",
            "3.9",
            "3.9-R1",
            "3.9-R2",
            "3.10",
            "3.11",
            "3.11-R1",
            "3.12",
            "3.12-R1",
            "3.13",
            "3.13-R1",
            "3.14",
            "3.14-R1",
            "3.15",
            "3.15-R1",
            "3.15-R2",
            "3.15-R3",
            "3.16",
            "3.16-R1"
    );

    @Test
    @DisplayName("README.md conserva íntegramente todas las fases históricas obligatorias")
    void testReadmeContainsAllHistoricalPhases() throws IOException {
        Path readmePath = findFile("README.md");
        assertTrue(Files.exists(readmePath), "README.md debe existir en la raíz del proyecto");

        String content = Files.readString(readmePath, StandardCharsets.UTF_8);

        for (String phase : REQUIRED_PHASES) {
            assertTrue(content.contains(phase),
                    "README.md debe contener la fase histórica: " + phase);
        }
    }

    @Test
    @DisplayName("README.md separa formalmente el Estado Actual del Historial de Fases")
    void testReadmeSeparatesCurrentStateFromHistory() throws IOException {
        Path readmePath = findFile("README.md");
        String content = Files.readString(readmePath, StandardCharsets.UTF_8);

        assertTrue(content.contains("## Estado Actual del Proyecto"),
                "README.md debe contener la sección de Estado Actual del Proyecto");
        assertTrue(content.contains("## Historial de Fases y Roadmap"),
                "README.md debe contener la sección canónica de Historial de Fases");
    }

    @Test
    @DisplayName("docs/IMPLEMENTATION.md contiene el roadmap completo con las fases obligatorias")
    void testImplementationRoadmapContainsAllPhases() throws IOException {
        Path implPath = findFile("docs/IMPLEMENTATION.md");
        assertTrue(Files.exists(implPath), "docs/IMPLEMENTATION.md debe existir");

        String content = Files.readString(implPath, StandardCharsets.UTF_8);

        for (String phase : REQUIRED_PHASES) {
            assertTrue(content.contains(phase),
                    "docs/IMPLEMENTATION.md debe incluir en su roadmap la fase: " + phase);
        }
    }

    private Path findFile(String relativePath) {
        Path direct = Paths.get(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = Paths.get("..", relativePath);
        if (Files.exists(parent)) {
            return parent;
        }
        return direct;
    }
}
