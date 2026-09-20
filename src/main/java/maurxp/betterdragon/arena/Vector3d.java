package maurxp.betterdragon.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Representación inmutable de una coordenada tridimensional en el espacio de dominio.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Aislamiento de Bukkit:</b> No retiene referencias a instancias de {@link World} o entidades vivas.</li>
 *   <li><b>Validación Numérica Estricta:</b> Rechaza valores no finitos (NaN, Infinity).</li>
 *   <li><b>Resolución en la Capa Adecuada:</b> Se convierte a {@link Location} solo cuando se proporciona un {@link World} válido en tiempo de ejecución.</li>
 * </ul>
 *
 * @param x coordenada X finita
 * @param y coordenada Y finita
 * @param z coordenada Z finita
 * @author maurxp
 */
public record Vector3d(double x, double y, double z) {

    public Vector3d {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "Las coordenadas de Vector3d deben ser números finitos. Recibido: (" + x + ", " + y + ", " + z + ")");
        }
    }

    /**
     * Convierte este vector a una {@link Location} de Bukkit para el mundo proporcionado.
     *
     * @param world mundo Bukkit de destino
     * @return nueva Location en el mundo dado
     */
    public Location toLocation(World world) {
        Objects.requireNonNull(world, "El mundo no puede ser nulo para resolver una Location");
        return new Location(world, x, y, z);
    }

    /**
     * Crea un {@link Vector3d} a partir de una {@link Location} de Bukkit.
     *
     * @param location ubicación de origen
     * @return Vector3d con las coordenadas de la ubicación
     */
    public static Vector3d fromLocation(Location location) {
        Objects.requireNonNull(location, "La ubicación no puede ser nula");
        return new Vector3d(location.getX(), location.getY(), location.getZ());
    }

    /**
     * Calcula la distancia cuadrada a otro vector tridimensional.
     *
     * @param other otro vector
     * @return distancia cuadrada
     */
    public double distanceSquared(Vector3d other) {
        Objects.requireNonNull(other, "El vector destino no puede ser nulo");
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    /**
     * Calcula la distancia euclidiana a otro vector tridimensional.
     *
     * @param other otro vector
     * @return distancia euclidiana
     */
    public double distance(Vector3d other) {
        return Math.sqrt(distanceSquared(other));
    }
}
