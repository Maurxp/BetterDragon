package maurxp.betterdragon.ability;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link TelegraphDefinition}.
 *
 * @author maurxp
 */
class TelegraphDefinitionTest {

    @Test
    @DisplayName("Construcción nominal y verificación de valores tuning candidate")
    void testNominalConstructionAndTuningCandidates() {
        TelegraphDefinition def = new TelegraphDefinition(
                TelegraphDefinition.TICKS_MAJOR,
                Particle.FLAME,
                30,
                4.5,
                "ENTITY_ENDER_DRAGON_GROWL",
                1.8f,
                1.1f
        );

        assertEquals(40L, def.durationTicks());
        assertEquals(Particle.FLAME, def.particle());
        assertEquals(30, def.particleCount());
        assertEquals(4.5, def.particleRadius(), 0.001);
        assertEquals("ENTITY_ENDER_DRAGON_GROWL", def.sound());
        assertEquals(1.8f, def.soundVolume(), 0.001f);
        assertEquals(1.1f, def.soundPitch(), 0.001f);

        // Constantes formales TUNING_CANDIDATE
        assertEquals(16L, TelegraphDefinition.TICKS_MINOR);
        assertEquals(30L, TelegraphDefinition.TICKS_MODERATE);
        assertEquals(40L, TelegraphDefinition.TICKS_MAJOR);
        assertEquals(60L, TelegraphDefinition.TICKS_LETHAL);
    }

    @Test
    @DisplayName("Defaults devuelve configuración moderada consistente")
    void testDefaults() {
        TelegraphDefinition def = TelegraphDefinition.defaults();
        assertNotNull(def);
        assertEquals(TelegraphDefinition.TICKS_MODERATE, def.durationTicks());
        assertEquals(TelegraphDefinition.DEFAULT_PARTICLE, def.particle());
        assertEquals(TelegraphDefinition.DEFAULT_PARTICLE_COUNT, def.particleCount());
        assertEquals(TelegraphDefinition.DEFAULT_PARTICLE_RADIUS, def.particleRadius(), 0.001);
        assertEquals(TelegraphDefinition.DEFAULT_SOUND, def.sound());
        assertEquals(TelegraphDefinition.DEFAULT_VOLUME, def.soundVolume(), 0.001f);
        assertEquals(TelegraphDefinition.DEFAULT_PITCH, def.soundPitch(), 0.001f);
    }

    @Test
    @DisplayName("Constructor de conveniencia por duración asigna valores por defecto")
    void testDurationConvenienceConstructor() {
        TelegraphDefinition def = new TelegraphDefinition(25L);
        assertEquals(25L, def.durationTicks());
        assertEquals(TelegraphDefinition.DEFAULT_PARTICLE, def.particle());
        assertEquals(TelegraphDefinition.DEFAULT_SOUND, def.sound());
    }

    @Test
    @DisplayName("Rechazo de duración negativa")
    void testNegativeDuration() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TelegraphDefinition(-1L));
        assertTrue(ex.getMessage().contains("durationTicks"));
    }

    @Test
    @DisplayName("Duración 0 ticks es válida (telegraph instantáneo)")
    void testZeroDurationIsValid() {
        TelegraphDefinition def = new TelegraphDefinition(0L);
        assertEquals(0L, def.durationTicks());
    }

    @Test
    @DisplayName("Rechazo de conteo de partículas negativo")
    void testNegativeParticleCount() {
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, -5, 2.0, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f));
    }

    @Test
    @DisplayName("Rechazo de radio negativo o no finito")
    void testInvalidParticleRadius() {
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, -0.5, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, Double.NaN, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, Double.POSITIVE_INFINITY, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f));
    }

    @Test
    @DisplayName("Rechazo de volumen y pitch no finitos o negativos")
    void testInvalidSoundParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, 2.0, "ENTITY_ENDER_DRAGON_GROWL", -1.0f, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, 2.0, "ENTITY_ENDER_DRAGON_GROWL", Float.NaN, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, 2.0, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, -0.5f));
        assertThrows(IllegalArgumentException.class, () -> new TelegraphDefinition(
                20L, Particle.FLAME, 10, 2.0, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, Float.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("Partícula y sonido nulos adoptan fallbacks por defecto de forma segura")
    void testNullParticleAndSoundFallback() {
        TelegraphDefinition def = new TelegraphDefinition(15L, null, 10, 2.0, (String) null, 1.0f, 1.0f);
        assertEquals(TelegraphDefinition.DEFAULT_PARTICLE, def.particle());
        assertEquals(TelegraphDefinition.DEFAULT_SOUND, def.sound());
    }
}
