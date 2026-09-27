package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.arena.ArenaBounds;
import org.bukkit.Location;

import java.util.Objects;

/**
 * Política formal para la evaluación y control perimetral de los límites de la arena de combate.
 * <p>
 * Principios:
 * <ul>
 *   <li><b>Geometría Unificada:</b> Reutiliza directamente el Axis-Aligned Bounding Box ({@link ArenaBounds})
 *       sin inventar geometrías paralelas ni volúmenes inconsistentes.</li>
 *   <li><b>Zona de Amortiguación (Warning Buffer):</b> Detecta proactivamente cuando un participante se aproxima
 *       al perímetro antes de que se produzca una brecha definitiva.</li>
 *   <li><b>Determinismo Espacial:</b> Provee operaciones matemáticas $O(1)$ de consulta de zonas, distancias
 *       y proyección segura hacia el interior.</li>
 * </ul>
 *
 * @author maurxp
 */
public class BoundaryPolicy {

    public static final double DEFAULT_WARNING_BUFFER = 5.0;

    private final double warningBuffer;

    public BoundaryPolicy(double warningBuffer) {
        if (!Double.isFinite(warningBuffer) || warningBuffer < 0.0) {
            throw new IllegalArgumentException("El warningBuffer debe ser un número positivo y finito: " + warningBuffer);
        }
        this.warningBuffer = warningBuffer;
    }

    public BoundaryPolicy() {
        this(DEFAULT_WARNING_BUFFER);
    }

    /**
     * Retorna el margen de amortiguación en bloques para la zona de advertencia.
     */
    public double getWarningBuffer() {
        return warningBuffer;
    }

    /**
     * Evalúa en qué zona espacial perimetral se ubica una localización respecto a los límites de la arena.
     *
     * @param location ubicación a evaluar
     * @param bounds   límites de la arena
     * @return {@link BoundaryZone} calculada
     */
    public BoundaryZone evaluateZone(Location location, ArenaBounds bounds) {
        if (location == null || bounds == null) {
            return BoundaryZone.OUTSIDE;
        }

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        if (!bounds.contains(x, y, z)) {
            return BoundaryZone.OUTSIDE;
        }

        boolean nearX = (x - bounds.minX() < warningBuffer) || (bounds.maxX() - x < warningBuffer);
        boolean nearY = (y - bounds.minY() < warningBuffer) || (bounds.maxY() - y < warningBuffer);
        boolean nearZ = (z - bounds.minZ() < warningBuffer) || (bounds.maxZ() - z < warningBuffer);

        if (nearX || nearY || nearZ) {
            return BoundaryZone.NEAR_BOUNDARY;
        }

        return BoundaryZone.INSIDE;
    }

    /**
     * Evalúa la transición de movimiento de un participante entre una posición origen y destino.
     *
     * @param from   ubicación previa
     * @param to     ubicación destino
     * @param bounds límites de la arena
     * @return {@link BoundaryTransition} representativa
     */
    public BoundaryTransition evaluateTransition(Location from, Location to, ArenaBounds bounds) {
        BoundaryZone fromZone = evaluateZone(from, bounds);
        BoundaryZone toZone = evaluateZone(to, bounds);

        if (fromZone == BoundaryZone.OUTSIDE) {
            return toZone == BoundaryZone.OUTSIDE
                    ? BoundaryTransition.OUTSIDE_TO_OUTSIDE
                    : BoundaryTransition.OUTSIDE_TO_INSIDE;
        }

        if (toZone == BoundaryZone.OUTSIDE) {
            return fromZone == BoundaryZone.NEAR_BOUNDARY
                    ? BoundaryTransition.NEAR_BOUNDARY_TO_OUTSIDE
                    : BoundaryTransition.INSIDE_TO_OUTSIDE;
        }

        if (toZone == BoundaryZone.NEAR_BOUNDARY) {
            return BoundaryTransition.INSIDE_TO_NEAR_BOUNDARY;
        }

        return BoundaryTransition.INSIDE_TO_INSIDE;
    }

    /**
     * Comprueba si una posición representa una brecha perimetral (fuera de la arena).
     *
     * @param location ubicación a evaluar
     * @param bounds   límites de la arena
     * @return true si la ubicación está fuera de los límites
     */
    public boolean isBreach(Location location, ArenaBounds bounds) {
        return evaluateZone(location, bounds) == BoundaryZone.OUTSIDE;
    }

    /**
     * Calcula la distancia euclídea mínima desde una ubicación hacia los planos perimetrales de la arena.
     *
     * @param location ubicación
     * @param bounds   límites de la arena
     * @return distancia mínima a un plano de los límites (negativa si está fuera)
     */
    public double distanceToNearestBoundary(Location location, ArenaBounds bounds) {
        if (location == null || bounds == null) {
            return -1.0;
        }

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        double distMinX = x - bounds.minX();
        double distMaxX = bounds.maxX() - x;
        double distMinY = y - bounds.minY();
        double distMaxY = bounds.maxY() - y;
        double distMinZ = z - bounds.minZ();
        double distMaxZ = bounds.maxZ() - z;

        return Math.min(Math.min(distMinX, distMaxX), Math.min(Math.min(distMinY, distMaxY), Math.min(distMinZ, distMaxZ)));
    }

    /**
     * Proyecta y restringe una ubicación dentro de los límites de la arena con un margen de seguridad interno.
     *
     * @param location ubicación a proyectar
     * @param bounds   límites de la arena
     * @param inset    distancia hacia el interior a garantizar
     * @return nueva Location confinada estrictamente en el interior
     */
    public Location clampInside(Location location, ArenaBounds bounds, double inset) {
        Objects.requireNonNull(location, "location no puede ser nula");
        Objects.requireNonNull(bounds, "bounds no puede ser nulo");

        double effectiveInsetX = Math.min(inset, (bounds.maxX() - bounds.minX()) / 4.0);
        double effectiveInsetY = Math.min(inset, (bounds.maxY() - bounds.minY()) / 4.0);
        double effectiveInsetZ = Math.min(inset, (bounds.maxZ() - bounds.minZ()) / 4.0);

        double clampedX = Math.max(bounds.minX() + effectiveInsetX, Math.min(bounds.maxX() - effectiveInsetX, location.getX()));
        double clampedY = Math.max(bounds.minY() + effectiveInsetY, Math.min(bounds.maxY() - effectiveInsetY, location.getY()));
        double clampedZ = Math.max(bounds.minZ() + effectiveInsetZ, Math.min(bounds.maxZ() - effectiveInsetZ, location.getZ()));

        return new Location(location.getWorld(), clampedX, clampedY, clampedZ, location.getYaw(), location.getPitch());
    }
}
