package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleSessionManagerTest {

    private BattleSessionManager manager;

    @BeforeEach
    void setUp() {
        this.manager = new BattleSessionManager();
    }

    @Test
    @DisplayName("Registro y consulta de sesiones")
    void testRegisterAndRetrieve() {
        BattleId id = BattleId.random();
        UUID worldId = UUID.randomUUID();
        BattleSession session = BattleSession.create(id, "world_the_end", worldId);

        manager.register(session);

        assertEquals(1, manager.getSessionCount());
        assertTrue(manager.hasActiveSession("world_the_end"));

        assertTrue(manager.getSession(id).isPresent());
        assertEquals(session, manager.getSession(id).get());

        assertTrue(manager.getActiveSessionByWorld("world_the_end").isPresent());
        assertEquals(session, manager.getActiveSessionByWorld("world_the_end").get());

        assertTrue(manager.getActiveSessionByWorld(worldId).isPresent());
        assertEquals(session, manager.getActiveSessionByWorld(worldId).get());
    }

    @Test
    @DisplayName("Rechazo de registro duplicado para el mismo mundo")
    void testRejectDuplicateWorldSession() {
        BattleSession s1 = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());
        BattleSession s2 = BattleSession.create(BattleId.random(), "world_the_end", UUID.randomUUID());

        manager.register(s1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> manager.register(s2));
        assertTrue(ex.getMessage().contains("Ya existe una sesión de batalla activa"));
    }

    @Test
    @DisplayName("Remover sesión limpia los índices de búsqueda")
    void testRemoveSession() {
        BattleId id = BattleId.random();
        UUID worldId = UUID.randomUUID();
        BattleSession session = BattleSession.create(id, "world_the_end", worldId);

        manager.register(session);
        assertEquals(1, manager.getSessionCount());

        var removed = manager.remove(id);
        assertTrue(removed.isPresent());
        assertEquals(session, removed.get());

        assertEquals(0, manager.getSessionCount());
        assertFalse(manager.hasActiveSession("world_the_end"));
        assertTrue(manager.getSession(id).isEmpty());
        assertTrue(manager.getActiveSessionByWorld("world_the_end").isEmpty());
        assertTrue(manager.getActiveSessionByWorld(worldId).isEmpty());
    }
}
