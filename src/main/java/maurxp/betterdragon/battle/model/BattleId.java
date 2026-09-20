package maurxp.betterdragon.battle.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador único, inmutable y tipado de una batalla de BetterDragon.
 *
 * @author maurxp
 */
public final class BattleId implements Comparable<BattleId>, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID value;

    private BattleId(UUID value) {
        this.value = Objects.requireNonNull(value, "El UUID de BattleId no puede ser nulo");
    }

    /**
     * Genera un nuevo BattleId con un UUID aleatorio.
     *
     * @return nueva instancia de BattleId
     */
    public static BattleId random() {
        return new BattleId(UUID.randomUUID());
    }

    /**
     * Crea un BattleId a partir de un UUID existente.
     *
     * @param uuid uuid base
     * @return nueva instancia de BattleId
     */
    public static BattleId fromUUID(UUID uuid) {
        return new BattleId(uuid);
    }

    /**
     * Parsea un BattleId a partir de su representación canónica en texto.
     *
     * @param text cadena con formato UUID
     * @return instancia de BattleId
     * @throws IllegalArgumentException si la cadena no tiene formato UUID válido
     */
    public static BattleId fromString(String text) {
        Objects.requireNonNull(text, "La cadena de BattleId no puede ser nula");
        return new BattleId(UUID.fromString(text.trim()));
    }

    /**
     * Obtiene el UUID subyacente.
     *
     * @return UUID
     */
    public UUID asUUID() {
        return value;
    }

    /**
     * Obtiene la representación canónica en texto del identificador.
     *
     * @return representación en cadena
     */
    public String asString() {
        return value.toString();
    }

    @Override
    public int compareTo(BattleId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BattleId battleId = (BattleId) o;
        return value.equals(battleId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return asString();
    }
}
