package maurxp.betterdragon.arena;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Representación inmutable de los límites geométricos de una arena de combate (Axis-Aligned Bounding Box).
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Determinismo Espacial:</b> Define de manera estricta el volumen dentro del cual transcurre la batalla.</li>
 *   <li><b>Invariantes Matemáticas:</b> Exige {@code min <= max} en cada uno de los tres ejes espaciales y valores finitos.</li>
 *   <li><b>Consultas de Contención Rápidas:</b> Permite determinar en O(1) si una coordenada o ubicación pertenece a la arena.</li>
 * </ul>
 *
 * @param minX límite inferior en el eje X
 * @param minY límite inferior en el eje Y
 * @param minZ límite inferior en el eje Z
 * @param maxX límite superior en el eje X
 * @param maxY límite superior en el eje Y
 * @param maxZ límite superior en el eje Z
 * @author maurxp
 */
public record ArenaBounds(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
) {

    public static final double DEFAULT_MIN_XZ = -150.0;
    public static final double DEFAULT_MAX_XZ = 150.0;
    public static final double DEFAULT_MIN_Y = 0.0;
    public static final double DEFAULT_MAX_Y = 256.0;

    public ArenaBounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
            throw new IllegalArgumentException("Todas las coordenadas de los límites de la arena deben ser números finitos.");
        }
        if (minX > maxX) {
            throw new IllegalArgumentException("minX (" + minX + ") no puede ser mayor que maxX (" + maxX + ")");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY (" + minY + ") no puede ser mayor que maxY (" + maxY + ")");
        }
        if (minZ > maxZ) {
            throw new IllegalArgumentException("minZ (" + minZ + ") no puede ser mayor que maxZ (" + maxZ + ")");
        }
    }

    /**
     * Construye un {@link ArenaBounds} a partir de dos vectores mínimos y máximos.
     *
     * @param min vector mínimo
     * @param max vector máximo
     * @return nueva instancia de ArenaBounds
     */
    public static ArenaBounds of(Vector3d min, Vector3d max) {
        Objects.requireNonNull(min, "El vector min no puede ser nulo");
        Objects.requireNonNull(max, "El vector max no puede ser nulo");
        return new ArenaBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /**
     * Retorna los límites estándar predeterminados para una arena del End.
     *
     * @return límites por defecto
     */
    public static ArenaBounds defaults() {
        return new ArenaBounds(
                DEFAULT_MIN_XZ, DEFAULT_MIN_Y, DEFAULT_MIN_XZ,
                DEFAULT_MAX_XZ, DEFAULT_MAX_Y, DEFAULT_MAX_XZ
        );
    }

    /**
     * Comprueba si las coordenadas especificadas se encuentran dentro de los límites de la arena (inclusivo).
     *
     * @param x coordenada X
     * @param y coordenada Y
     * @param z coordenada Z
     * @return true si la posición está dentro del volumen de la arena
     */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Comprueba si la {@link Location} dada está dentro de los límites espaciales de la arena.
     *
     * @param location ubicación Bukkit
     * @return true si location no es nula y sus coordenadas están dentro de los límites
     */
    public boolean contains(Location location) {
        if (location == null) {
            return false;
        }
        return contains(location.getX(), location.getY(), location.getZ());
    }

    /**
     * Comprueba si el {@link Vector3d} dado está dentro de los límites espaciales de la arena.
     *
     * @param vector vector de dominio
     * @return true si el vector no es nulo y está dentro de los límites
     */
    public boolean contains(Vector3d vector) {
        if (vector == null) {
            return false;
        }
        return contains(vector.x(), vector.y(), vector.z());
    }

    /**
     * Retorna el ancho en el eje X.
     */
    public double widthX() {
        return maxX - minX;
    }

    /**
     * Retorna la altura en el eje Y.
     */
    public double heightY() {
        return maxY - minY;
    }

    /**
     * Retorna la profundidad en el eje Z.
     */
    public double depthZ() {
        return maxZ - minZ;
    }

    /**
     * Retorna el volumen total del bounding box.
     */
    public double volume() {
        return widthX() * heightY() * depthZ();
    }
}
