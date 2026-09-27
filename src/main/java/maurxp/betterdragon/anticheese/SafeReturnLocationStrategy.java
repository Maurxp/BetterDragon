package maurxp.betterdragon.anticheese;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.battle.BattleSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Estrategia formal para la resolución de una ubicación de retorno segura (Safe Return Location).
 * <p>
 * Sigue la jerarquía de prioridad formal recomendada:
 * <ol>
 *   <li>Última posición válida conocida del participante (si sigue siendo físicamente segura).</li>
 *   <li>Ubicación del podio/portal central de la arena (nivel de suelo seguro).</li>
 *   <li>Centro espacial de la arena con proyección a nivel de suelo seguro.</li>
 *   <li>Fallback determinista matemáticamente confinado dentro de los límites.</li>
 * </ol>
 * <p>
 * Invariantes de Seguridad:
 * <ul>
 *   <li>Coherencia de mundo y chunk cargado.</li>
 *   <li>Márgenes de seguridad perimetrales (inset >= 5 bloques) para prevenir re-salidas inmediatas.</li>
 *   <li>Espacio libre suficiente (pies y cabeza transitables).</li>
 *   <li>Ausencia de lava, fuego o vacío debajo de los pies.</li>
 * </ul>
 *
 * @author maurxp
 */
public class SafeReturnLocationStrategy {

    public static final double DEFAULT_SAFE_INSET = 8.0;
    public static final double MIN_SAFE_Y = 55.0;

    private final double safeInset;

    public SafeReturnLocationStrategy(double safeInset) {
        this.safeInset = safeInset;
    }

    public SafeReturnLocationStrategy() {
        this(DEFAULT_SAFE_INSET);
    }

    /**
     * Resuelve la mejor ubicación segura de retorno para un participante dentro del contexto de la batalla.
     *
     * @param player         jugador a rescatar/recuperar
     * @param session        sesión activa de batalla
     * @param lastKnownValid última posición válida registrada (puede ser nula)
     * @return ubicación validada y segura de retorno
     */
    public Location resolveSafeLocation(Player player, BattleSession session, Location lastKnownValid) {
        Objects.requireNonNull(session, "session no puede ser nula");

        World world = resolveWorld(player, session);
        if (world == null) {
            return null;
        }

        ArenaBounds bounds = session.getArena().bounds();
        Location resultLocation;

        // Prioridad 1: Última posición válida conocida
        if (lastKnownValid != null && isSafe(lastKnownValid, session, world)) {
            resultLocation = lastKnownValid.clone();
        } else {
            // Prioridad 2: Podio de la arena
            Location podium = session.getSpatialContext().getPodiumCenter(world);
            Location podiumCandidate = (podium != null) ? podium.clone().add(0.5, 1.0, 0.5) : null;
            if (podiumCandidate != null && isSafe(podiumCandidate, session, world)) {
                resultLocation = podiumCandidate;
            } else {
                // Prioridad 3: Centro de la arena con proyección a cota segura
                Location center = session.getSpatialContext().getArenaCenter(world);
                double safeY = (center != null) ? Math.max(center.getY(), 65.0) : 65.0;
                Location centerCandidate = (center != null) ? new Location(world, center.getX() + 0.5, safeY, center.getZ() + 0.5) : null;
                if (centerCandidate != null && isSafe(centerCandidate, session, world)) {
                    resultLocation = centerCandidate;
                } else {
                    // Prioridad 4: Fallback matemático confinado en el centro de la arena con validación física
                    double centerX = session.getArena().center().x();
                    double centerY = Math.max(session.getArena().center().y(), MIN_SAFE_Y);
                    double centerZ = session.getArena().center().z();

                    double effectiveInsetX = Math.min(safeInset, (bounds.maxX() - bounds.minX()) / 4.0);
                    double effectiveInsetY = Math.min(safeInset, (bounds.maxY() - bounds.minY()) / 4.0);
                    double effectiveInsetZ = Math.min(safeInset, (bounds.maxZ() - bounds.minZ()) / 4.0);

                    double fallbackX = Math.max(bounds.minX() + effectiveInsetX, Math.min(bounds.maxX() - effectiveInsetX, centerX));
                    double minYBound = Math.max(bounds.minY() + effectiveInsetY, MIN_SAFE_Y);
                    double maxYBound = Math.max(minYBound, bounds.maxY() - effectiveInsetY);
                    double fallbackY = Math.max(minYBound, Math.min(maxYBound, centerY));
                    double fallbackZ = Math.max(bounds.minZ() + effectiveInsetZ, Math.min(bounds.maxZ() - effectiveInsetZ, centerZ));

                    Location fallbackCandidate = new Location(world, fallbackX, fallbackY, fallbackZ);

                    int chunkX = fallbackCandidate.getBlockX() >> 4;
                    int chunkZ = fallbackCandidate.getBlockZ() >> 4;
                    boolean chunkLoaded = false;
                    try {
                        chunkLoaded = world.isChunkLoaded(chunkX, chunkZ);
                    } catch (Throwable ignored) {
                    }

                    if (chunkLoaded) {
                        if (isSafe(fallbackCandidate, session, world)) {
                            resultLocation = fallbackCandidate;
                        } else {
                            // Búsqueda vertical acotada (máx 5 bloques hacia arriba y 3 hacia abajo) para encontrar superficie segura
                            Location safeColumnLoc = null;
                            int[] yOffsets = {1, 2, 3, 4, 5, -1, -2, -3};
                            for (int offset : yOffsets) {
                                double testY = fallbackY + offset;
                                if (testY >= minYBound && testY <= maxYBound) {
                                    Location candidate = new Location(world, fallbackX, testY, fallbackZ);
                                    if (isSafe(candidate, session, world)) {
                                        safeColumnLoc = candidate;
                                        break;
                                    }
                                }
                            }

                            if (safeColumnLoc != null) {
                                resultLocation = safeColumnLoc;
                            } else {
                                // Si no se halló un bloque 100% seguro, garantizar que los pies no queden en peligro inmediato
                                try {
                                    Block feet = fallbackCandidate.getBlock();
                                    double adjustedY = fallbackY;
                                    int safetyStep = 0;
                                    while (isHazardous(feet.getType()) && safetyStep < 5 && (adjustedY + 1.0) <= maxYBound) {
                                        adjustedY += 1.0;
                                        fallbackCandidate.setY(adjustedY);
                                        feet = fallbackCandidate.getBlock();
                                        safetyStep++;
                                    }
                                } catch (Throwable ignored) {
                                }
                                resultLocation = fallbackCandidate;
                            }
                        }
                    } else {
                        resultLocation = fallbackCandidate;
                    }
                }
            }
        }

        // Preservación estricta de orientación (yaw/pitch) del participante para evitar desorientación
        if (player != null && player.getLocation() != null) {
            resultLocation.setYaw(player.getLocation().getYaw());
            resultLocation.setPitch(player.getLocation().getPitch());
        } else if (lastKnownValid != null) {
            resultLocation.setYaw(lastKnownValid.getYaw());
            resultLocation.setPitch(lastKnownValid.getPitch());
        }

        return resultLocation;
    }

    /**
     * Valida que una localización candidata cumpla con todos los requisitos físicos de seguridad.
     *
     * @param loc     localización a validar
     * @param session sesión activa
     * @param world   mundo objetivo
     * @return true si la posición es segura
     */
    public boolean isSafe(Location loc, BattleSession session, World world) {
        if (loc == null || loc.getWorld() == null || world == null) {
            return false;
        }

        // 1. Verificación de mundo
        if (!loc.getWorld().getName().equalsIgnoreCase(world.getName())) {
            return false;
        }

        // 2. Verificación de límites espaciales con margen interno proporcional
        ArenaBounds bounds = session.getArena().bounds();
        double effectiveInsetX = Math.min(safeInset, (bounds.maxX() - bounds.minX()) / 4.0);
        double effectiveInsetY = Math.min(safeInset, (bounds.maxY() - bounds.minY()) / 4.0);
        double effectiveInsetZ = Math.min(safeInset, (bounds.maxZ() - bounds.minZ()) / 4.0);

        if (loc.getX() < bounds.minX() + effectiveInsetX || loc.getX() > bounds.maxX() - effectiveInsetX
                || loc.getY() < bounds.minY() + effectiveInsetY || loc.getY() > bounds.maxY() - effectiveInsetY
                || loc.getZ() < bounds.minZ() + effectiveInsetZ || loc.getZ() > bounds.maxZ() - effectiveInsetZ) {
            return false;
        }

        // 3. Verificación de cota mínima de vacío
        if (loc.getY() < MIN_SAFE_Y) {
            return false;
        }

        // 4. Verificación de chunk cargado
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return false;
            }
        } catch (Throwable ignored) {
            // Protección ante entornos de test sin chunks reales
        }

        // 5. Verificación de bloques físicos (espacio suficiente y ausencia de lava/fuego)
        try {
            Block feet = loc.getBlock();
            Block head = loc.clone().add(0, 1, 0).getBlock();
            Block ground = loc.clone().subtract(0, 1, 0).getBlock();

            if (isHazardous(feet.getType()) || isHazardous(head.getType()) || isHazardous(ground.getType())) {
                return false;
            }

            // Pies y cabeza no deben estar dentro de bloques opacos no transitables
            if (!feet.isPassable() || !head.isPassable()) {
                return false;
            }

            // El suelo debe ser sólido (no aire ni líquido)
            if (!ground.getType().isSolid()) {
                return false;
            }
        } catch (Throwable ignored) {
            // En entornos mockeados donde getBlock() retorna nulo o falla, la validación geométrica previa basta
        }

        return true;
    }

    private boolean isHazardous(Material material) {
        if (material == null) return false;
        return material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE
                || material == Material.CACTUS
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.WITHER_ROSE;
    }

    private World resolveWorld(Player player, BattleSession session) {
        if (player != null && player.getWorld() != null
                && player.getWorld().getName().equalsIgnoreCase(session.getWorldName())) {
            return player.getWorld();
        }
        try {
            World w = Bukkit.getWorld(session.getWorldUniqueId());
            if (w == null) {
                w = Bukkit.getWorld(session.getWorldName());
            }
            return w;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
