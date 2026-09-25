package maurxp.betterdragon.presentation;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.config.DragonBossBarDefinition;
import maurxp.betterdragon.phase.PhaseDefinition;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonBossBar y Cálculo de Progreso (Fase 3.14)")
class DragonBossBarTest {

    @Test
    @DisplayName("1. calculateProgress calcula valores proporcionales exactos")
    void testCalculateProgressNormal() {
        assertEquals(0.0, DragonBossBar.calculateProgress(0.0, 200.0), 0.0001);
        assertEquals(1.0, DragonBossBar.calculateProgress(200.0, 200.0), 0.0001);
        assertEquals(0.5, DragonBossBar.calculateProgress(100.0, 200.0), 0.0001);
        assertEquals(0.25, DragonBossBar.calculateProgress(50.0, 200.0), 0.0001);
    }

    @Test
    @DisplayName("2. calculateProgress restringe estrictamente valores excedentes (clamping en 1.0)")
    void testCalculateProgressOverflowClamping() {
        assertEquals(1.0, DragonBossBar.calculateProgress(250.0, 200.0), 0.0001);
        assertEquals(1.0, DragonBossBar.calculateProgress(1000.0, 200.0), 0.0001);
    }

    @Test
    @DisplayName("3. calculateProgress restringe estrictamente valores negativos (clamping en 0.0)")
    void testCalculateProgressNegativeClamping() {
        assertEquals(0.0, DragonBossBar.calculateProgress(-1.0, 200.0), 0.0001);
        assertEquals(0.0, DragonBossBar.calculateProgress(-200.0, 200.0), 0.0001);
    }

    @Test
    @DisplayName("4. calculateProgress maneja con seguridad NaN, Infinity y denominadores no válidos")
    void testCalculateProgressNonFinite() {
        assertEquals(0.0, DragonBossBar.calculateProgress(Double.NaN, 200.0), 0.0001);
        assertEquals(0.0, DragonBossBar.calculateProgress(100.0, Double.NaN), 0.0001);
        assertEquals(0.0, DragonBossBar.calculateProgress(Double.POSITIVE_INFINITY, 200.0), 0.0001);
        assertEquals(0.0, DragonBossBar.calculateProgress(100.0, Double.POSITIVE_INFINITY), 0.0001);
        assertEquals(0.0, DragonBossBar.calculateProgress(100.0, 0.0), 0.0001);
        assertEquals(0.0, DragonBossBar.calculateProgress(100.0, -200.0), 0.0001);
    }

    @Test
    @DisplayName("5. DragonBossBar se inicializa con salud completa (1.0) y título correcto")
    void testBossBarInitialization() {
        BattleId battleId = BattleId.random();
        DragonBossBarDefinition config = DragonBossBarDefinition.defaults();
        DragonBossBar bar = new DragonBossBar(battleId, config, "Ancient Dragon", null);

        assertEquals(battleId, bar.getBattleId());
        assertEquals("Ancient Dragon", bar.getDragonDisplayName());
        assertEquals(1.0, bar.getProgress(), 0.0001);
        assertEquals("phase_1", bar.getCurrentPhaseId());
        assertFalse(bar.isEnraged());
        assertTrue(bar.getTitle().contains("Ancient Dragon"));
        assertTrue(bar.getTitle().contains("phase_1"));
    }

    @Test
    @DisplayName("6. updateHealth actualiza el progreso interno")
    void testUpdateHealth() {
        DragonBossBar bar = new DragonBossBar(BattleId.random(), DragonBossBarDefinition.defaults(), "Dragon", null);
        bar.updateHealth(150.0, 300.0);
        assertEquals(0.5, bar.getProgress(), 0.0001);

        bar.updateHealth(0.0, 300.0);
        assertEquals(0.0, bar.getProgress(), 0.0001);
    }

    @Test
    @DisplayName("7. updatePhase refresca el id de fase y el título")
    void testUpdatePhase() {
        DragonBossBar bar = new DragonBossBar(BattleId.random(), DragonBossBarDefinition.defaults(), "Dragon", null);
        PhaseDefinition phase2 = new PhaseDefinition("phase_2", 2, 0.75);
        bar.updatePhase(phase2);

        assertEquals("phase_2", bar.getCurrentPhaseId());
        assertTrue(bar.getTitle().contains("phase_2"));
    }

    @Test
    @DisplayName("8. setEnraged activa bandera y anexa [ENRAGE] al título")
    void testSetEnraged() {
        DragonBossBar bar = new DragonBossBar(BattleId.random(), DragonBossBarDefinition.defaults(), "Dragon", null);
        assertFalse(bar.isEnraged());
        assertFalse(bar.getTitle().contains("[ENRAGE]"));

        bar.setEnraged(true);
        assertTrue(bar.isEnraged());
        assertTrue(bar.getTitle().contains("[ENRAGE]"));
    }

    @Test
    @DisplayName("9. cleanup limpia los espectadores registrados y resetea estado")
    void testCleanup() {
        DragonBossBar bar = new DragonBossBar(BattleId.random(), DragonBossBarDefinition.defaults(), "Dragon", null);
        assertTrue(bar.getViewers().isEmpty());

        bar.cleanup();
        assertTrue(bar.getViewers().isEmpty());
    }

    @Test
    @DisplayName("10. addViewer, removeViewer y updateViewers manejan entradas nulas sin excepciones")
    void testViewerLifecycleNullSafe() {
        DragonBossBar bar = new DragonBossBar(BattleId.random(), DragonBossBarDefinition.defaults(), "Dragon", null);

        assertDoesNotThrow(() -> bar.addViewer(null));
        assertDoesNotThrow(() -> bar.removeViewer(null));
        assertDoesNotThrow(() -> bar.updateViewers(null));
        assertDoesNotThrow(() -> bar.playPhaseFeedback(null));
        assertDoesNotThrow(bar::playEnrageFeedback);
        assertTrue(bar.getViewers().isEmpty());

        // getViewers() es inmutable
        assertThrows(UnsupportedOperationException.class, () -> bar.getViewers().clear());
    }

    @Test
    @DisplayName("11. setVisible respeta el flag enabled() de la configuración")
    void testVisibilityLifecycle() {
        DragonBossBarDefinition disabledConfig = new DragonBossBarDefinition(false, "Title {enrage}", BarColor.PURPLE, BarStyle.SOLID);
        DragonBossBar disabledBar = new DragonBossBar(BattleId.random(), disabledConfig, "Dragon", null);

        // Sin bukkitBossBar (headless)
        assertFalse(disabledBar.isVisible());
        assertDoesNotThrow(() -> disabledBar.setVisible(true));
        assertFalse(disabledBar.isVisible());
    }
}
