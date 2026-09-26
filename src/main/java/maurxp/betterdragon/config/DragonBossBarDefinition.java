package maurxp.betterdragon.config;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

/**
 * Configuración inmutable para la BossBar de BetterDragon.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Inmutabilidad y Snapshot:</b> Se congela dentro de {@link DragonDefinition} y {@link BattleConfigurationSnapshot}.</li>
 *   <li><b>Placeholders Mínimos:</b> Soporta {@code {dragon_name}}, {@code {phase}} y opcionalmente {@code {enrage}}.</li>
 *   <li><b>Soberanía Visual:</b> Controla la barra propia del plugin mientras la barra vanilla permanece suprimida.</li>
 * </ul>
 *
 * @param enabled si la BossBar propia de BetterDragon debe mostrarse durante el combate
 * @param title   plantilla de título con placeholders soportados
 * @param color   color de la barra (PURPLE por defecto)
 * @param style   estilo/segmentación de la barra (SOLID por defecto)
 * @author maurxp
 */
public record DragonBossBarDefinition(
        boolean enabled,
        String title,
        BarColor color,
        BarStyle style
) {
    public static final String DEFAULT_TITLE = "{dragon_name} &7• &f{phase} {enrage}";
    public static final BarColor DEFAULT_COLOR = BarColor.PURPLE;
    public static final BarStyle DEFAULT_STYLE = BarStyle.SOLID;

    public DragonBossBarDefinition {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El título de la BossBar no puede ser nulo ni estar vacío");
        }
        title = title.trim();
        color = color != null ? color : DEFAULT_COLOR;
        style = style != null ? style : DEFAULT_STYLE;
    }

    /**
     * Construye la configuración por defecto estándar para la BossBar.
     */
    public static DragonBossBarDefinition defaults() {
        return new DragonBossBarDefinition(true, DEFAULT_TITLE, DEFAULT_COLOR, DEFAULT_STYLE);
    }

    /**
     * Genera el título final formateado reemplazando los placeholders requeridos
     * y traduciendo códigos de color de estilo ampersand ('&' -> '§').
     * <p>
     * Semántica explícita de Enrage:
     * <ul>
     *   <li>Si {@code title} contiene {@code {enrage}}, se reemplaza por {@code &c[ENRAGE]} si está activo,
     *       o por cadena vacía {@code ""} si está inactivo.</li>
     *   <li>Si {@code title} NO contiene {@code {enrage}}, no se anexa ninguna etiqueta automáticamente.</li>
     * </ul>
     *
     * @param dragonName   nombre visible del dragón
     * @param phaseName    identificador o nombre visible de la fase de combate
     * @param enrageActive estado actual de Soft Enrage
     * @return texto formateado y coloreado para la BossBar
     */
    public String formatTitle(String dragonName, String phaseName, boolean enrageActive) {
        String effectiveDragonName = (dragonName != null && !dragonName.isBlank()) ? dragonName.trim() : "Ender Dragon";
        String effectivePhase = (phaseName != null && !phaseName.isBlank()) ? phaseName.trim() : "Combat";

        String formatted = title
                .replace("{dragon_name}", effectiveDragonName)
                .replace("{phase}", effectivePhase);

        if (formatted.contains("{enrage}")) {
            formatted = formatted.replace("{enrage}", enrageActive ? "&c[ENRAGE]" : "");
        }

        return formatted.trim().replace('&', '§');
    }
}
