package maurxp.betterdragon.config;

import maurxp.betterdragon.battle.BattleSession;
import maurxp.betterdragon.battle.model.BattleId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BattleSnapshotImmutabilityTest {

    private final Logger logger = Logger.getLogger("BattleSnapshotImmutabilityTest");

    @Test
    @DisplayName("Invariante de Snapshot: Cambiar la configuración global NO modifica el snapshot de BattleSession")
    void testBattleSessionRetainsSnapshotAcrossGlobalConfigChanges(@TempDir Path tempDir) throws Exception {
        // 1. Configuración Global A
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                portal:
                  enabled: false
                logging:
                  debug: false
                """);

        ConfigurationService service = new ConfigurationService(logger);
        service.loadInitial(configPath.toFile());

        // Verificar Estado A
        assertFalse(service.getActiveConfig().portalEnabled());
        assertFalse(service.getActiveConfig().debugLogging());

        // 2. Iniciar Batalla 1 con Snapshot A
        BattleId battleId = BattleId.random();
        BattleConfigurationSnapshot snapshotA = service.createBattleSnapshot();
        BattleSession session1 = BattleSession.create(battleId, "world_the_end", UUID.randomUUID(), snapshotA);

        assertFalse(session1.getConfigSnapshot().portalEnabled(), "Batalla 1 debe nacer con portal.enabled=false");
        assertFalse(session1.getConfigSnapshot().debugLogging(), "Batalla 1 debe nacer con debugLogging=false");

        // 3. Cambiar Configuración Global a B mediante reload
        Files.writeString(configPath, """
                portal:
                  enabled: true
                logging:
                  debug: true
                """);

        boolean reloaded = service.reload(configPath.toFile());
        assertTrue(reloaded, "La recarga a Config B debió ser exitosa");

        // Verificar que la configuración global es ahora B
        assertTrue(service.getActiveConfig().portalEnabled(), "Config global activa ahora es portal.enabled=true");
        assertTrue(service.getActiveConfig().debugLogging(), "Config global activa ahora es debugLogging=true");

        // 4. Invariante Crítica: El snapshot de Batalla 1 NO fue alterado
        assertFalse(session1.getConfigSnapshot().portalEnabled(),
                "Batalla 1 DEBE mantener su snapshot congelado original (portal.enabled=false)");
        assertFalse(session1.getConfigSnapshot().debugLogging(),
                "Batalla 1 DEBE mantener su snapshot congelado original (debugLogging=false)");

        // 5. Una nueva Batalla 2 que se cree ahora adoptará la Config B
        BattleConfigurationSnapshot snapshotB = service.createBattleSnapshot();
        BattleSession session2 = BattleSession.create(BattleId.random(), "world_the_end_nether", UUID.randomUUID(), snapshotB);

        assertTrue(session2.getConfigSnapshot().portalEnabled(), "Batalla 2 debe nacer con portal.enabled=true");
        assertTrue(session2.getConfigSnapshot().debugLogging(), "Batalla 2 debe nacer con debugLogging=true");

        // Y Batalla 1 sigue intacta
        assertFalse(session1.getConfigSnapshot().portalEnabled());
    }
}
