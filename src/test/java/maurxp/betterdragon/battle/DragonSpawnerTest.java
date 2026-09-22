package maurxp.betterdragon.battle;

import maurxp.betterdragon.arena.ArenaBounds;
import maurxp.betterdragon.arena.ArenaDefinition;
import maurxp.betterdragon.arena.ArenaRuleSet;
import maurxp.betterdragon.arena.Vector3d;
import maurxp.betterdragon.battle.model.BattleId;
import maurxp.betterdragon.config.EffectiveDragonStats;
import maurxp.betterdragon.util.BetterDragonKeys;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import java.util.function.Consumer;
import org.bukkit.entity.EnderDragon;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de DragonSpawner con Arena y Atributos (Fase 3.13)")
class DragonSpawnerTest {

    @Test
    @DisplayName("spawnDragon con ArenaDefinition deriva ubicación de arena.center() y podio de arena.podium()")
    void testSpawnWithArenaDefinitionDerivesLocationAndPodium() {
        UUID worldUid = UUID.randomUUID();
        AtomicReference<Location> capturedSpawnLocation = new AtomicReference<>();
        AtomicReference<Location> capturedPodium = new AtomicReference<>();
        Map<Attribute, Double> setAttributes = new HashMap<>();
        AtomicReference<Double> setHealth = new AtomicReference<>();
        FakePdc pdc = new FakePdc();

        Vector3d center = new Vector3d(10.0, 80.0, 10.0);
        Vector3d podium = new Vector3d(0.0, 65.0, 0.0);
        ArenaBounds bounds = new ArenaBounds(-50.0, 0.0, -50.0, 50.0, 150.0, 50.0);
        ArenaDefinition arena = new ArenaDefinition("custom_arena", "world_the_end", center, podium, bounds, ArenaRuleSet.defaults());

        World world = createFakeEndWorld("world_the_end", worldUid, (loc, clazz, consumer) -> {
            capturedSpawnLocation.set(loc);
            EnderDragon dragon = createFakeDragon(UUID.randomUUID(), pdc, capturedPodium, setAttributes, setHealth);
            consumer.accept(dragon);
            return dragon;
        });

        EffectiveDragonStats effectiveStats = new EffectiveDragonStats(
                "nightmare",
                750.0,
                3,
                1.5,
                Optional.of(0.35),
                Optional.of(80.0),
                Optional.of(18.0)
        );

        BattleId battleId = BattleId.random();
        DragonSpawner spawner = new DragonSpawner();

        EnderDragon result = spawner.spawnDragon(world, arena, effectiveStats, battleId, null);

        assertNotNull(result);

        // 1. Verificación de ubicación de spawn derivada de arena.center()
        assertNotNull(capturedSpawnLocation.get());
        assertEquals(10.0, capturedSpawnLocation.get().getX());
        assertEquals(80.0, capturedSpawnLocation.get().getY());
        assertEquals(10.0, capturedSpawnLocation.get().getZ());

        // 2. Verificación de podio configurado desde arena.podium()
        assertNotNull(capturedPodium.get());
        assertEquals(0.0, capturedPodium.get().getX());
        assertEquals(65.0, capturedPodium.get().getY());
        assertEquals(0.0, capturedPodium.get().getZ());

        // 3. Verificación de salud aplicada según estadísticas efectivas
        assertEquals(750.0, setHealth.get());

        // 4. Verificación de PDC completo
        assertTrue(pdc.has(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN));
        assertEquals(battleId.asString(), pdc.get(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING));
        assertEquals("nightmare", pdc.get(BetterDragonKeys.DEFINITION_ID, PersistentDataType.STRING));
    }

    @Test
    @DisplayName("spawnDragon con ubicación override respeta el override en vez de arena.center()")
    void testSpawnWithLocationOverride() {
        AtomicReference<Location> capturedSpawnLocation = new AtomicReference<>();
        FakePdc pdc = new FakePdc();

        ArenaDefinition arena = ArenaDefinition.defaults();
        World world = createFakeEndWorld("world_the_end", UUID.randomUUID(), (loc, clazz, consumer) -> {
            capturedSpawnLocation.set(loc);
            EnderDragon dragon = createFakeDragon(UUID.randomUUID(), pdc, new AtomicReference<>(), new HashMap<>(), new AtomicReference<>());
            consumer.accept(dragon);
            return dragon;
        });

        EffectiveDragonStats stats = EffectiveDragonStats.from(maurxp.betterdragon.config.DragonDefinition.defaults(), 1);
        Location customLoc = new Location(world, 100.0, 90.0, 100.0);

        DragonSpawner spawner = new DragonSpawner();
        spawner.spawnDragon(world, arena, stats, BattleId.random(), customLoc);

        assertEquals(100.0, capturedSpawnLocation.get().getX());
        assertEquals(90.0, capturedSpawnLocation.get().getY());
        assertEquals(100.0, capturedSpawnLocation.get().getZ());
    }

    @Test
    @DisplayName("spawnDragon sin ubicación ni arena falla (eliminación definitiva de coordenadas mágicas)")
    void testSpawnWithoutLocationThrowsException() {
        World world = createFakeEndWorld("world_the_end", UUID.randomUUID(), (loc, clazz, consumer) -> null);
        DragonSpawner spawner = new DragonSpawner();

        assertThrows(NullPointerException.class, () ->
                spawner.spawnDragon(world, (Location) null, BattleId.random(), "default"));
    }

    @Test
    @DisplayName("spawnDragon rechaza mundos que no sean THE_END")
    void testSpawnRejectsNonEndWorld() {
        World netherWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEnvironment")) return World.Environment.NETHER;
                    if (method.getName().equals("getName")) return "world_nether";
                    return null;
                }
        );

        DragonSpawner spawner = new DragonSpawner();
        assertThrows(IllegalArgumentException.class, () ->
                spawner.spawnDragon(netherWorld, new Location(netherWorld, 0, 80, 0), BattleId.random(), "default"));
    }

    private World createFakeEndWorld(String name, UUID uid, SpawnFunction spawnFunc) {
        Chunk fakeChunk = (Chunk) Proxy.newProxyInstance(
                Chunk.class.getClassLoader(),
                new Class<?>[]{Chunk.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isLoaded")) return true;
                    if (method.getName().equals("load")) return true;
                    return null;
                }
        );

        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getUID")) return uid;
                    if (method.getName().equals("getEnvironment")) return World.Environment.THE_END;
                    if (method.getName().equals("getChunkAt") || method.getName().equals("getChunk")) return fakeChunk;
                    if (method.getName().equals("spawn") && args.length == 3) {
                        return spawnFunc.spawn((Location) args[0], (Class<?>) args[1], (Consumer<EnderDragon>) args[2]);
                    }
                    return null;
                }
        );
    }

    @FunctionalInterface
    interface SpawnFunction {
        EnderDragon spawn(Location location, Class<?> clazz, Consumer<EnderDragon> consumer);
    }

    private EnderDragon createFakeDragon(
            UUID uuid,
            FakePdc pdc,
            AtomicReference<Location> podiumRef,
            Map<Attribute, Double> attributes,
            AtomicReference<Double> healthRef
    ) {
        return (EnderDragon) Proxy.newProxyInstance(
                EnderDragon.class.getClassLoader(),
                new Class<?>[]{EnderDragon.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("getUniqueId")) return uuid;
                    if (mName.equals("getPersistentDataContainer")) return pdc;
                    if (mName.equals("isValid")) return true;
                    if (mName.equals("setPodium")) {
                        podiumRef.set((Location) args[0]);
                        return null;
                    }
                    if (mName.equals("setPhase")) return null;
                    if (mName.equals("setHealth")) {
                        healthRef.set((Double) args[0]);
                        return null;
                    }
                    if (mName.equals("getAttribute")) {
                        Attribute attr = (Attribute) args[0];
                        return createFakeAttributeInstance(attr, attributes);
                    }
                    return null;
                }
        );
    }

    private AttributeInstance createFakeAttributeInstance(Attribute attr, Map<Attribute, Double> attributes) {
        return (AttributeInstance) Proxy.newProxyInstance(
                AttributeInstance.class.getClassLoader(),
                new Class<?>[]{AttributeInstance.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setBaseValue")) {
                        attributes.put(attr, (Double) args[0]);
                        return null;
                    }
                    if (method.getName().equals("getValue") || method.getName().equals("getBaseValue")) {
                        return attributes.getOrDefault(attr, 200.0);
                    }
                    return null;
                }
        );
    }

    private static class FakePdc implements PersistentDataContainer {
        private final Map<NamespacedKey, Object> map = new HashMap<>();

        @Override
        public <T, Z> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
            map.put(key, value);
        }

        @Override
        public <T, Z> boolean has(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return map.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @Nullable Z get(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
            return (Z) map.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T, Z> @NotNull Z getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z defaultValue) {
            return (Z) map.getOrDefault(key, defaultValue);
        }

        @Override
        public @NotNull Set<NamespacedKey> getKeys() {
            return map.keySet();
        }

        @Override
        public void remove(@NotNull NamespacedKey key) {
            map.remove(key);
        }

        @Override
        public int getSize() {
            return map.size();
        }

        @Override
        public boolean has(@NotNull NamespacedKey key) {
            return map.containsKey(key);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void copyTo(@NotNull PersistentDataContainer target, boolean replace) {
        }

        @Override
        public @NotNull PersistentDataAdapterContext getAdapterContext() {
            return null;
        }

        @Override
        public byte @NotNull [] serializeToBytes() {
            return new byte[0];
        }

        @Override
        public void readFromBytes(byte @NotNull [] bytes, boolean clear) {
        }
    }
}
