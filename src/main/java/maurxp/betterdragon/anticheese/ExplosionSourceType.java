package maurxp.betterdragon.anticheese;

/**
 * Categorización de origen de una detonación en el contexto de combate de BetterDragon.
 *
 * @author maurxp
 */
public enum ExplosionSourceType {
    /** Cama detonada por interacción en una dimensión distinta al Overworld (End/Nether). */
    BED,

    /** Ancla de reaparición detonada por interacción en el End o Overworld. */
    RESPAWN_ANCHOR,

    /** Habilidad explosiva originada y gestionada internamente por BetterDragon (ej. CarpetBomb). */
    BETTERDRAGON_ABILITY,

    /** Cualquier otra fuente de explosión (TNT vanilla, cristales del End, creepers, etc.). */
    OTHER
}
