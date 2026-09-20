package maurxp.betterdragon.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link ArenaRuleSet}.
 *
 * @author maurxp
 */
class ArenaRuleSetTest {

    @Test
    @DisplayName("ArenaRuleSet.defaults() provee valores predeterminados seguros")
    void testDefaults() {
        ArenaRuleSet rules = ArenaRuleSet.defaults();

        assertFalse(rules.waterAllowed(), "El agua debe estar denegada por defecto");
        assertTrue(rules.isWaterDenialEnabled(), "Water denial debe estar activo por defecto");
        assertTrue(rules.boundaryEnabled(), "Boundary debe estar activo por defecto");
        assertTrue(rules.isBoundaryEnabled());
        assertTrue(rules.antiTunnelEnabled(), "Anti-tunnel debe estar activo por defecto");
        assertTrue(rules.isAntiTunnelEnabled());
    }

    @Test
    @DisplayName("Water denial refleja inversamente la permisión de agua")
    void testWaterDenialLogic() {
        ArenaRuleSet denied = new ArenaRuleSet(false, true, true);
        assertFalse(denied.isWaterAllowed());
        assertTrue(denied.isWaterDenialEnabled());

        ArenaRuleSet allowed = new ArenaRuleSet(true, true, true);
        assertTrue(allowed.isWaterAllowed());
        assertFalse(allowed.isWaterDenialEnabled());
    }

    @Test
    @DisplayName("Boundary y AntiTunnel flags son configurables de forma independiente")
    void testBoundaryAndAntiTunnelFlags() {
        ArenaRuleSet custom = new ArenaRuleSet(false, false, false);
        assertFalse(custom.isBoundaryEnabled());
        assertFalse(custom.isAntiTunnelEnabled());

        ArenaRuleSet boundaryOnly = new ArenaRuleSet(false, true, false);
        assertTrue(boundaryOnly.isBoundaryEnabled());
        assertFalse(boundaryOnly.isAntiTunnelEnabled());

        ArenaRuleSet tunnelOnly = new ArenaRuleSet(false, false, true);
        assertFalse(tunnelOnly.isBoundaryEnabled());
        assertTrue(tunnelOnly.isAntiTunnelEnabled());
    }
}
