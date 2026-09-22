package maurxp.betterdragon.battle;

import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.battle.model.DragonIdentity;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;

/**
 * Gestor centralizado para la serialización, deserialización y validación de la identidad
 * persistente de un {@link EnderDragon} mediante {@link PersistentDataContainer} (PDC).
 * <p>
 * Principios:
 * <ul>
 *   <li><b>PDC como Fuente Primaria:</b> BetterDragon no identifica a sus jefes por nombre custom,
 *       coordenadas ni UUIDs externos, sino exclusivamente por sus etiquetas PDC.</li>
 *   <li><b>Aislamiento Absoluto:</b> Cero interacción con DragonBattle o NMS.</li>
 * </ul>
 *
 * @author maurxp
 */
public final class DragonPdcHandler {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    private DragonPdcHandler() {
        // Stateless / utilitario
    }

    /**
     * Escribe las claves persistentes de identidad de BetterDragon en el PDC del dragón.
     *
     * @param dragon   entidad EnderDragon a marcar
     * @param identity identidad inmutable de la batalla
     */
    public static void applyIdentity(EnderDragon dragon, DragonIdentity identity) {
        Objects.requireNonNull(dragon, "El dragón no puede ser nulo");
        Objects.requireNonNull(identity, "La identidad no puede ser nula");

        PersistentDataContainer pdc = dragon.getPersistentDataContainer();
        pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
        pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, identity.battleId().asString());
        pdc.set(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING, identity.definitionId());
        pdc.set(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER, identity.schemaVersion());
    }

    /**
     * Valida exhaustivamente si una entidad candidata corresponde al dragón legítimo
     * de una sesión de batalla específica.
     * <p>
     * Comprueba:
     * <ol>
     *   <li>Que la entidad no sea nula y sea una instancia de {@link EnderDragon}.</li>
     *   <li>Que la sesión tenga una {@link DragonIdentity} asignada.</li>
     *   <li>Que el UUID de la entidad coincida exactamente con el UUID esperado.</li>
     *   <li>Que el PDC contenga {@code betterdragon:managed == true}.</li>
     *   <li>Que el PDC contenga un {@code betterdragon:battle_id} idéntico al de la sesión.</li>
     *   <li>Que el PDC contenga un {@code betterdragon:definition_id} compatible con el snapshot de la sesión.</li>
     * </ol>
     *
     * @param entity  entidad física a validar
     * @param session sesión de batalla activa o diferida
     * @return true si la entidad es inequívocamente el dragón de la sesión indicada
     */
    public static boolean validateDragonForSession(Entity entity, BattleSession session) {
        if (entity == null || session == null) {
            return false;
        }

        if (!(entity instanceof EnderDragon dragon)) {
            return false;
        }

        Optional<DragonIdentity> expectedIdentityOpt = session.getDragonIdentity();
        if (expectedIdentityOpt.isEmpty()) {
            return false;
        }

        DragonIdentity expectedIdentity = expectedIdentityOpt.get();
        if (!dragon.getUniqueId().equals(expectedIdentity.entityUniqueId())) {
            return false;
        }

        Optional<DragonIdentity> extractedOpt = extractIdentity(dragon);
        if (extractedOpt.isEmpty()) {
            return false;
        }

        DragonIdentity extracted = extractedOpt.get();
        if (!extracted.battleId().equals(session.getBattleId())) {
            return false;
        }

        if (session.getConfigSnapshot() != null && session.getConfigSnapshot().dragonDefinition() != null) {
            String expectedDefinitionId = session.getConfigSnapshot().dragonDefinition().id();
            if (!expectedDefinitionId.equalsIgnoreCase(extracted.definitionId())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extrae y valida la identidad de un dragón a partir de su contenedor de datos persistentes.
     *
     * @param entity entidad a inspeccionar
     * @return Optional con la DragonIdentity si la entidad es un EnderDragon administrado y válido
     */
    public static Optional<DragonIdentity> extractIdentity(Entity entity) {
        if (!(entity instanceof EnderDragon dragon)) {
            return Optional.empty();
        }

        PersistentDataContainer pdc = dragon.getPersistentDataContainer();

        // 1. Validar bandera 'managed' (soporta BOOLEAN nativo y BYTE histórico)
        boolean isManaged = false;
        if (pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN)) {
            Boolean val = pdc.get(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN);
            isManaged = Boolean.TRUE.equals(val);
        } else if (pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BYTE)) {
            Byte b = pdc.get(BetterDragonKeys.MANAGED, PersistentDataType.BYTE);
            isManaged = b != null && b == (byte) 1;
        }

        if (!isManaged) {
            return Optional.empty();
        }

        // 2. Validar clave 'battle_id'
        if (!pdc.has(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING)) {
            return Optional.empty();
        }
        String rawBattleId = pdc.get(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING);
        if (rawBattleId == null || rawBattleId.isBlank()) {
            return Optional.empty();
        }

        BattleId battleId;
        try {
            battleId = BattleId.fromString(rawBattleId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        // 3. Obtener 'definition_id'
        String definitionId = pdc.getOrDefault(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING, "default");
        if (definitionId == null || definitionId.isBlank()) {
            definitionId = "default";
        }

        // 4. Validar 'schema_version' (ausencia = legacy v1; versión desconocida futura = rechazo)
        int schemaVersion = CURRENT_SCHEMA_VERSION;
        if (pdc.has(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER)) {
            Integer rawVersion = pdc.get(BetterDragonKeys.SCHEMA_VERSION, PersistentDataType.INTEGER);
            if (rawVersion == null || rawVersion < 1 || rawVersion > MAX_SUPPORTED_SCHEMA_VERSION) {
                return Optional.empty();
            }
            schemaVersion = rawVersion;
        }

        return Optional.of(new DragonIdentity(dragon.getUniqueId(), battleId, definitionId, schemaVersion));
    }

    /**
     * Comprueba de manera rápida y estricta si una entidad es un EnderDragon administrado por BetterDragon.
     *
     * @param entity entidad a comprobar
     * @return true si porta el PDC de BetterDragon válido
     */
    public static boolean isBetterDragon(Entity entity) {
        return extractIdentity(entity).isPresent();
    }
}
