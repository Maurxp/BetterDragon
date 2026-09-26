package maurxp.betterdragon.ability;

import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.Objects;

/**
 * Definición inmutable y tipada del aviso telegrafiado previo a una habilidad de combate.
 * <p>
 * Principios:
 * <ul>
 * <li><b>Telegrafiado Sensorial (PRIN-02):</b> Ataques de alto impacto advierten visual y sonoramente
 * antes de desplegar el efecto físico real para premiar la habilidad y capacidad de esquiva.</li>
 * <li><b>Valores Tuning Candidate:</b> Las duraciones MINOR (16t), MODERATE (30t), MAJOR (40t) y LETHAL (60t)
 * están clasificadas formalmente como {@code TUNING_CANDIDATE} sujetas a calibración empírica en playtesting (EXP-006).</li>
 * <li><b>Inmutabilidad:</b> Modelo inmutable congelado por snapshot al inicio de la batalla.</li>
 * <li><b>0% NMS y Compatibilidad:</b> El identificador sonoro se almacena de forma tipada resiliente
 * para admitir tanto nombres de la API como identificadores canónicos de Minecraft 26.1.2.</li>
 * </ul>
 *
 * @param durationTicks   duración del telegrafiado en ticks lógicos antes del efecto físico (>= 0)
 * @param particle        tipo de partícula visual proyectada
 * @param particleCount   cantidad de partículas emitidas (>= 0)
 * @param particleRadius  radio espacial de emisión de partículas (>= 0.0)
 * @param sound           identificador del sonido reproducido como señal auditiva
 * @param soundVolume     volumen del sonido (>= 0.0f)
 * @param soundPitch      tono del sonido (>= 0.0f)
 * @author maurxp
 */
public record TelegraphDefinition(
        long durationTicks,
        Particle particle,
        int particleCount,
        double particleRadius,
        String sound,
        float soundVolume,
        float soundPitch) {

    /** Duración breve para desventajas leves o daño menor (~0.8s). TUNING_CANDIDATE. */
    public static final long TICKS_MINOR = 16L;

    /** Duración estándar para ataques medios y empujes (~1.5s). TUNING_CANDIDATE. */
    public static final long TICKS_MODERATE = 30L;

    /** Duración visible para ataques severos y grandes áreas (~2.0s). TUNING_CANDIDATE. */
    public static final long TICKS_MAJOR = 40L;

    /** Alerta máxima para mecánicas letales (~3.0s). TUNING_CANDIDATE. */
    public static final long TICKS_LETHAL = 60L;

    public static final Particle DEFAULT_PARTICLE = Particle.DRAGON_BREATH;
    public static final int DEFAULT_PARTICLE_COUNT = 25;
    public static final double DEFAULT_PARTICLE_RADIUS = 3.0;
    public static final String DEFAULT_SOUND = "ENTITY_ENDER_DRAGON_GROWL";
    public static final float DEFAULT_VOLUME = 1.5f;
    public static final float DEFAULT_PITCH = 1.2f;

    public TelegraphDefinition {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks debe ser >= 0: " + durationTicks);
        }
        if (particleCount < 0) {
            throw new IllegalArgumentException("particleCount debe ser >= 0: " + particleCount);
        }
        if (!Double.isFinite(particleRadius) || particleRadius < 0.0) {
            throw new IllegalArgumentException("particleRadius debe ser un número finito >= 0.0: " + particleRadius);
        }
        if (!Float.isFinite(soundVolume) || soundVolume < 0.0f) {
            throw new IllegalArgumentException("soundVolume debe ser finito >= 0.0f: " + soundVolume);
        }
        if (!Float.isFinite(soundPitch) || soundPitch < 0.0f) {
            throw new IllegalArgumentException("soundPitch debe ser finito >= 0.0f: " + soundPitch);
        }
        particle = particle != null ? particle : DEFAULT_PARTICLE;
        sound = sound != null && !sound.isBlank() ? sound.trim() : DEFAULT_SOUND;
    }

    /**
     * Constructor de conveniencia aceptando instancia Sound de Bukkit/Paper.
     */
    public TelegraphDefinition(long durationTicks, Particle particle, int particleCount,
                               double particleRadius, Sound sound, float soundVolume, float soundPitch) {
        this(durationTicks, particle, particleCount, particleRadius,
                sound != null ? sound.name() : DEFAULT_SOUND, soundVolume, soundPitch);
    }

    /**
     * Constructor de conveniencia con valores por defecto y duración indicada.
     */
    public TelegraphDefinition(long durationTicks) {
        this(durationTicks, DEFAULT_PARTICLE, DEFAULT_PARTICLE_COUNT, DEFAULT_PARTICLE_RADIUS,
                DEFAULT_SOUND, DEFAULT_VOLUME, DEFAULT_PITCH);
    }

    /**
     * Instancia por defecto con clasificación MODERATE (30 ticks).
     */
    public static TelegraphDefinition defaults() {
        return new TelegraphDefinition(TICKS_MODERATE, DEFAULT_PARTICLE, DEFAULT_PARTICLE_COUNT,
                DEFAULT_PARTICLE_RADIUS, DEFAULT_SOUND, DEFAULT_VOLUME, DEFAULT_PITCH);
    }

    /**
     * Resuelve de forma segura el valor {@link Sound} de Bukkit/Paper si es reconocido.
     */
    public Sound resolveSound() {
        try {
            return Sound.valueOf(sound.toUpperCase().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
