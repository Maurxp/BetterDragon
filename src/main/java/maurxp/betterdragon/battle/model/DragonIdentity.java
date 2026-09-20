package maurxp.betterdragon.battle.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa la identidad inmutable y tipada de un Ender Dragon administrado
 * por BetterDragon en el mundo del juego.
 *
 * @param entityUniqueId UUID de la entidad Bukkit/Minecraft del dragón
 * @param battleId       identificador de la batalla a la que pertenece
 * @param definitionId   identificador del perfil o definición del jefe
 * @param schemaVersion  versión del esquema de persistencia PDC
 * @author maurxp
 */
public record DragonIdentity(
        UUID entityUniqueId,
        BattleId battleId,
        String definitionId,
        int schemaVersion
) implements Serializable {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public DragonIdentity {
        Objects.requireNonNull(entityUniqueId, "El entityUniqueId del dragón no puede ser nulo");
        Objects.requireNonNull(battleId, "El battleId asociado al dragón no puede ser nulo");
        Objects.requireNonNull(definitionId, "El definitionId del dragón no puede ser nulo");
        if (definitionId.isBlank()) {
            throw new IllegalArgumentException("El definitionId del dragón no puede estar vacío");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("La versión del esquema debe ser >= 1");
        }
    }

    /**
     * Crea una instancia de DragonIdentity utilizando la versión de esquema actual.
     *
     * @param entityUniqueId UUID de la entidad
     * @param battleId       identificador de batalla
     * @param definitionId   perfil del dragón
     * @return nueva identidad de dragón
     */
    public static DragonIdentity of(UUID entityUniqueId, BattleId battleId, String definitionId) {
        return new DragonIdentity(entityUniqueId, battleId, definitionId, CURRENT_SCHEMA_VERSION);
    }

    /**
     * Crea una instancia de DragonIdentity con el perfil predeterminado "default" y la versión actual de esquema.
     *
     * @param entityUniqueId UUID de la entidad
     * @param battleId       identificador de batalla
     * @return nueva identidad de dragón con perfil default
     */
    public static DragonIdentity of(UUID entityUniqueId, BattleId battleId) {
        return of(entityUniqueId, battleId, "default");
    }
}
