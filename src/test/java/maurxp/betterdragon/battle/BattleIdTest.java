package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleIdTest {

    @Test
    @DisplayName("BattleId.random genera identificadores únicos")
    void testRandomGeneration() {
        BattleId id1 = BattleId.random();
        BattleId id2 = BattleId.random();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertNotNull(id1.asUUID());
        assertNotNull(id1.asString());
    }

    @Test
    @DisplayName("BattleId igualdad basada en valor UUID")
    void testEquality() {
        UUID uuid = UUID.randomUUID();
        BattleId id1 = BattleId.fromUUID(uuid);
        BattleId id2 = BattleId.fromString(uuid.toString());

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
        assertEquals(id1.asString(), id2.asString());
        assertEquals(0, id1.compareTo(id2));
    }

    @Test
    @DisplayName("BattleId.fromString rechaza cadenas inválidas")
    void testInvalidStringThrows() {
        assertThrows(NullPointerException.class, () -> BattleId.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> BattleId.fromString("not-a-uuid"));
    }
}
