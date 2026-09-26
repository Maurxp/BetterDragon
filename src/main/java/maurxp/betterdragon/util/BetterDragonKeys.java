package maurxp.betterdragon.util;

import org.bukkit.NamespacedKey;

/**
 * Centraliza las instancias de {@link NamespacedKey} utilizadas por BetterDragon
 * para el etiquetado persistente de entidades y almacenamiento en {@code PersistentDataContainer} (PDC).
 *
 * @author maurxp
 */
public final class BetterDragonKeys {

    public static final String NAMESPACE = "betterdragon";

    /**
     * Clave que identifica que la entidad está administrada por BetterDragon.
     */
    public static final NamespacedKey MANAGED = new NamespacedKey(NAMESPACE, "managed");

    /**
     * Clave que asocia la entidad al {@code BattleId} de la batalla correspondiente.
     */
    public static final NamespacedKey BATTLE_ID = new NamespacedKey(NAMESPACE, "battle_id");

    /**
     * Clave que almacena el identificador del perfil/definición del dragón.
     */
    public static final NamespacedKey DEFINITION_ID = new NamespacedKey(NAMESPACE, "definition_id");

    /**
     * Clave que almacena la versión del esquema de persistencia PDC.
     */
    public static final NamespacedKey SCHEMA_VERSION = new NamespacedKey(NAMESPACE, "schema_version");

    /**
     * Clave que identifica que la entidad es un esbirro (minion) invocado por BetterDragon.
     */
    public static final NamespacedKey MINION = new NamespacedKey(NAMESPACE, "minion");

    /**
     * Clave que identifica el tipo/identificador de esbirro invocado.
     */
    public static final NamespacedKey MINION_TYPE = new NamespacedKey(NAMESPACE, "minion_type");

    /**
     * Clave que identifica que una entidad explosiva (ej. TNT) fue generada por BetterDragon
     * para la cancelación selectiva de daño a bloques.
     */
    public static final NamespacedKey EXPLOSIVE = new NamespacedKey(NAMESPACE, "explosive");

    private BetterDragonKeys() {}
}
