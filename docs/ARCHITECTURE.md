# BetterDragon — Arquitectura del Sistema (Architecture)

**Plugin:** BetterDragon  
**Autor:** maurxp  
**Estado:** [VIVO / CONSOLIDADO]  
**Versión:** 1.0.0  

---

## 1. Topología Modular y Separación Core / Platform

```
+-----------------------------------------------------------------+
|                       CORE (0% NMS)                             |
|                                                                 |
|   [BetterDragonPlugin]                                          |
|            │                                                    |
|            ├──► [BattleManager] ──► [BattleSession] (Mutable)   |
|            │                              │                     |
|            │                              ▼                     |
|            │                      [CombatRuntime]               |
|            │                      (Damage, Phases, Abilities)   |
|            │                              │                     |
|            │                              ▼                     |
|            │                      [BattleResult] (Immutable)    |
|            │                              │                     |
|            │                      ┌───────┴───────┐             |
|            │                      ▼               ▼             |
|            │              [RewardManager]   [Leaderboard]       |
|            │                      │                             |
|            │                      ▼                             |
|            │                [ClaimManager]                      |
|            │                                                    |
+------------│----------------------------------------------------+
             │ (Platform Interface: VanillaBossBarController)
             ▼
+-----------------------------------------------------------------+
|                    PLATFORM ADAPTER (NMS)                       |
|   maurxp.betterdragon.platform.bossbar                          |
|                                                                 |
|   ├── VanillaBossBarController (Pure Bukkit Interface)          |
|   ├── NmsVanillaBossBarController (Paper 26.1.2-74 internals)   |
|   ├── NoneVanillaBossBarController (Fallback seguro)            |
|   └── VanillaBossBarControllerFactory (Detección de entorno)    |
+-----------------------------------------------------------------+
```

### Reglas de Desacoplamiento:
1. **Core Libre de NMS:** Ninguna clase de combate, modelos, persistencia, recompensas o comandos puede importar paquetes `net.minecraft.*` o `org.bukkit.craftbukkit.*`.
2. **Platform Layer Aislada:** Las dependencias de bajo nivel quedan encapsuladas detrás de interfaces limpias de Bukkit (`VanillaBossBarController`).

---

## 2. Modelo de Concurrencia y Datos

| Dominio | Hilo Responsable | Componentes | Garantías |
| :--- | :--- | :--- | :--- |
| **Gameplay & Combate** | **Main Thread** | `BattleSession`, `CombatRuntime`, `DamageTracker`, Bukkit Events | Cero bloqueos de sincronización; ejecución atómica en los ticks de Paper. |
| **Persistencia & I/O** | **Async Worker** | `PersistenceService`, `SQLite Storage` (`betterdragon.db`) | Single-writer secuencial; cero lag de I/O en los 20 TPS del servidor. |

### Invariantes de Estado:
- **`BattleSession` (Estado Runtime Mutable):** Vive exclusivamente en el hilo principal durante una batalla activa. Captura un *snapshot* inmutable de la configuración al iniciar; las recargas con `/bd reload` no perturban la sesión en curso.
- **`BattleResult` (Resultado Inmutable):** Objeto DTO/Record inmutable construido al morir el dragón (`COMPLETED`) o ser cancelada (`ABORTED`). Thread-safe por diseño, se despacha de forma segura al worker asíncrono de persistencia.
- **`DragonIdentity` (Identidad Inmutable):** Firma inmutable del dragón (`UUID entityId`, `BattleId`, `String definitionId`, `int schemaVersion`).
- **`BetterDragonKeys` (PDC Centralizado):** Centraliza `betterdragon:managed`, `betterdragon:battle_id`, `betterdragon:definition_id`, `betterdragon:schema_version`.
- **`BattleSessionManager`:** Administra sesiones en memoria en el hilo principal. Garantiza la invariante de contexto: **máximo una sesión activa por mundo**.

### Invariantes de Recuperación (Recovery):
Al reiniciar el servidor o descargar chunks:
1. `Entity#isValid() == false` **no** significa que la entidad murió; solo indica que su chunk se descargó.
2. Si el chunk está descargado: la batalla transiciona a `DEFERRED_PENDING_CHUNK_LOAD`.
3. Si el chunk está cargado y la entidad no existe en disco ni en memoria: la batalla se considera `ABORTED` por pérdida de entidad.
4. Dragones huérfanos con PDC sin `BattleSession` activa son purgados para restablecer la consistencia.

---

## 3. Sistema de Configuración, Validación y Snapshots

```
config.yml ──► [ConfigurationLoader] ──► [BetterDragonConfig] (Global Inmutable)
                      │                         │
           (Validación Estricta)                ▼
                      │               [BattleConfigurationSnapshot] (Congelado)
                      ▼                         │
           ConfigValidationException            ▼
             (Fail-Safe / Rechazo)       [BattleSession] (Aislada de recargas)
```

### Invariantes de Configuración:
1. **Tipado Estricto (0% FileConfiguration en Dominio):** Bukkit `FileConfiguration` se confina exclusivamente al `ConfigurationLoader`. El resto del plugin interactúa con modelos tipados inmutables (`BetterDragonConfig`).
2. **Snapshot Inmutable por Batalla (`BattleConfigurationSnapshot`):** Cada `BattleSession` recibe y congela una instantánea al crearse. Una recarga posterior de configuración jamás altera los parámetros de batallas activas en curso.
3. **Recarga Atómica Fail-Safe:** Si un archivo YAML editado contiene errores sintácticos, tipos incompatibles o valores fuera de dominio, la recarga se rechaza por completo y la configuración global previa se mantiene activa e intacta en memoria.
4. **Semántica de `portal.enabled`:**
   - `false`: BetterDragon NO crea, modifica, restaura ni administra el portal central de salida.
   - `true`: BetterDragon administra el portal central tras la victoria.

---

## 4. Decisiones Críticas de Plataforma y Maven

### 4.1 Declaración de `api-version` en `paper-plugin.yml`
- **Decisión:** Declarar formalmente `api-version: '26.1.2'`.
- **Justificación Técnica:**
  - En Paper 26.1.2-74, `apiVersioning.json` define `currentApiVersion: "26.1.2"`.
  - El deserializador `PaperPluginMeta` resuelve la versión mediante `ApiVersion.getOrCreateVersion(str)` y requiere que coincida con el formato `major.minor.patch` o `major.minor`.
  - Si se declara `'1.21'`, Paper activa las capas de compatibilidad retroactiva (`org.bukkit.craftbukkit.legacy.*`, Commodore bytecode rerouting).
  - Declarar `'26.1.2'` marca a BetterDragon como un plugin nativo de la plataforma 26.1.2 sin activar shims legacy.

### 4.2 Dependencias de Compilación en `pom.xml`
- **`paper-api` (`26.1.2.build.74-stable`):** Dependencia provista principal para todo el Core del plugin.
- **`paper-server` (`26.1.2.build.74-stable` en scope `provided`):**
  - Requerida **únicamente** para compilar la clase `NmsVanillaBossBarController` (`ServerLevel`, `EnderDragonFight`, `CraftWorld`).
  - Al estar en scope `provided`, **no** se empaqueta en el JAR del plugin (el JAR final pesa ~24 KB).
- **`com.mojang:datafixerupper` (scope `provided`):** Resuelve referencias de tipo en bytecode requeridas por `javac` al analizar las clases del servidor en Java 25.

---

## 5. Ciclo de Vida del Dragón e Identidad Persistente (Phase 3.3)

### 5.1 Soberanía de Entidad e Independencia de DragonBattle
BetterDragon administra su propio `EnderDragon` de forma autónoma y desacoplada del sistema vanilla:
- **Cero Dependencia de `DragonBattle` / `EnderDragonFight`:** No se consulta `dragon.getDragonBattle()`, no se leen flags vanilla (`dragonKilled`, `dragonUUID`), ni se invocan métodos como `setDragonKilled()` o `resetCrystals()`.
- **Relación Unidireccional:**
  ```
  BattleSession
       │
       ▼
  DragonIdentity (Inmutable: entityUUID, battleId, definitionId, schemaVersion)
       │
       ▼
  PersistentDataContainer (PDC: betterdragon:managed=true, betterdragon:battle_id)
       │
       ▼
  EnderDragon (Entidad física Paper API)
  ```
- **Spawning Limpio con Paper API:** Invocado vía `world.spawn(location, EnderDragon.class, consumer -> { ... })` con consumidor síncrono que establece la fase inicial (`Phase.CIRCLING`) y etiqueta el PDC antes de entregar la entidad al mundo.
- **Validación Estricta de Dimensión:** Solo se permite la creación de batallas y dragones en mundos con ambiente `World.Environment.THE_END`. Cualquier intento fuera del End es rechazado de inmediato.

### 5.2 Identidad Persistente (PDC)
Centralizada en `DragonPdcHandler` y `BetterDragonKeys`:
- `betterdragon:managed`: Booleano `true` (byte `1`). Si está ausente o es falso, la entidad es tratada como dragón ajeno y jamás es procesada.
- `betterdragon:battle_id`: UUID persistente en formato canónico String (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).
- `betterdragon:definition_id`: Identificador tipado del perfil de dragón instanciado (`STRING`).
- `betterdragon:schema_version`: Versión del esquema de persistencia PDC (`INTEGER = 1`).
- *Nota histórica (Fase 3.3-R1 vs 3.13 / 3.13-R1):* En la fase 3.3-R1 las claves `definition_id` y `schema_version` se reservaron temporalmente; a partir de la Fase 3.13 / 3.13-R1, las 4 claves se escriben síncronamente al spawnear y se validan en el ciclo de vida y en `DragonPdcHandler.validateDragonForSession()`.

### 5.3 Coordinación del Ciclo de Vida (`BattleManager`)
- **Secuencia Estricta PREPARING -> ACTIVE:**
  1. Validación de dimensión `THE_END` y no duplicidad en el mundo (`hasActiveSession`).
  2. Generación de `BattleId`, resolución de perfil y congelamiento de snapshot (`BattleConfigurationSnapshot`).
  3. Creación y registro de `BattleSession` en estado `PREPARING`.
  4. Spawn físico de la entidad con marcado PDC atómico de las 4 claves (`managed`, `battle_id`, `definition_id`, `schema_version`).
  5. Extracción y verificación física del PDC desde la entidad generada.
  6. Asociación de `DragonIdentity` y transición a `ACTIVE`.
- **Limpieza Idempotente ante Fallos:** Si el spawn falla o la verificación PDC no pasa, se elimina la entidad parcial, la sesión se marca como `ABORTED` (`BattleAbortReason.SPAWN_FAILED`) y se remueve de `BattleSessionManager`.

### 5.4 Detección de Muerte Física (`EntityDeathEvent`)
- Interceptada por `DragonLifecycleListener` en hilo principal.
- Valida que la entidad sea `EnderDragon` con PDC válido de BetterDragon asociado a una sesión activa.
- **Supresión de Drops y XP Vanilla:** Ejecuta `event.setDroppedExp(0)` (cancelando los 12,000 XP vanilla masivos) y `event.getDrops().clear()` para prevenir duplicación con el sistema propio de recompensas.
- Transiciona la sesión de `ACTIVE` a `DYING`.

### 5.5 Semántica de Descarga de Chunks y Recuperación Diferida
- **Descarga de Chunk (`EntityRemoveEvent.Cause.UNLOAD`):**
  - No marca la batalla como abortada.
  - Guarda el estado lógico previo (`stateBeforeChunkDeferral`: `ACTIVE` o `DYING`).
  - La sesión pasa a `DEFERRED_PENDING_CHUNK_LOAD`, preservando `BattleId` y `DragonIdentity`.
- **Carga de Chunk y Resolución Estricta (`resolveDeferredDragon`):**
  - `DEFERRED_PENDING_CHUNK_LOAD` representa indisponibilidad temporal del chunk; la carga del chunk **no** reactiva automáticamente la batalla.
  - Al cargarse entidades (`EntitiesLoadEvent`), el listener delega en `BattleManager.resolveDeferredDragon(...)`.
  - Se busca y valida exhaustivamente la entidad (`validateDragonForSession`): UUID esperado, tipo `EnderDragon`, `managed == true`, `battle_id` coincidente.
  - Si la entidad es válida, se restaura su estado lógico previo (`ACTIVE` -> `ACTIVE`, `DYING` -> `DYING`).
  - Si la entidad no se encuentra o su identidad está corrupta, la sesión se aborta con `BattleAbortReason.ENTITY_MISSING`.
- **Pérdida Inesperada de Entidad (`Cause.DISCARD`, `Cause.DESPAWN`, `Cause.OUT_OF_WORLD`):**
  - Si el chunk está cargado y la entidad es destruida inesperadamente fuera de muerte natural, la sesión transiciona a `ABORTED` con motivo `BattleAbortReason.ENTITY_MISSING`.

### 5.6 BossBar Vanilla como Infraestructura Interna Obligatoria
- La supresión de la BossBar vanilla mediante el adapter NMS aislado `VanillaBossBarController` es un componente arquitectónico interno mandatorio.
- La opción pública `bossbar.suppress-vanilla` fue eliminada de `config.yml`, modelos tipados y snapshots.
- `VanillaBossBarController` se inicializa y suprime automáticamente todas las BossBars vanilla del End en segundo plano sin intervención del usuario.

---

## 6. Combat Runtime & Damage Tracking (Phase 3.4)

### 6.1 Principio de Separación: CombatRuntime vs BattleManager
Para evitar convertir `BattleManager` en un God Object, la lógica de combate está estrictamente confinada en `CombatRuntime`:
- **`BattleManager`:** Orquesta el ciclo de vida de la sesión (`startBattle`, `abortBattle`, `resolveDeferredDragon`). No maneja participantes ni daño.
- **`CombatRuntime`:** Instancia encapsulada dentro de cada `BattleSession`. Gobierna participantes, acumulación de daño, contadores monotónicos, nombres históricos y consultas de `TOP_DAMAGE`.

### 6.2 Flujo Canónico de Procesamiento de Daño
Existe una única ruta oficial para el registro de daño:

```
Bukkit EntityDamageByEntityEvent (HIGH, ignoreCancelled = true)
          │
          ▼
DragonCombatListener
          │
          ├──► 1. Resolución de entidad: EnderDragon raíz o subparte (EnderDragonPart / ComplexEntityPart)
          ├──► 2. Validación PDC: DragonPdcHandler.extractIdentity (managed=true, battle_id)
          ├──► 3. Resolución de BattleSession en BattleSessionManager
          ├──► 4. Compuerta de estado: session.getState() == BattleState.ACTIVE
          ├──► 5. Resolución de causante: Player directo o tirador de Projectile (ProjectileSource)
          ├──► 6. Validación de daño: Double.isFinite(damage) && damage > 0.0
          │
          ▼
CombatRuntime.recordDamage(playerId, currentName, damage, currentTick)
          │
          ├──► 7. Cálculo de prospectiveSequence = hitSequence + 1
          ├──► 8. Emisión de BetterDragonDamageEvent (cancelable)
          │       │
          │       ├─► Si es cancelado:
          │       │     └── Aborta de inmediato; hitSequence NO se consume y NO se muta el estado.
          │       │
          │       └─► Si es aceptado:
          │             ├── hitSequence = prospectiveSequence (monotónico)
          │             ├── ParticipantCombatState (crea si nuevo con historicalName, actualiza si existe)
          │             ├── totalDamage += damage
          │             ├── lastKnownName = currentName (historicalName permanece inmutable)
          │             └── lastActivityTick / lastHitSequence actualizados
          │
          ▼
ParticipantSnapshot (Inmutable) / Futuras Fases
```

### 6.3 Modelo de Datos y Confinamiento al Hilo Principal
- **Cero Concurrencia en Combate:** Todo el combate se ejecuta en el hilo principal de Paper. Se emplean colecciones estándar `HashMap`, primitivos `long` y `double`. Cero `AtomicLong`, cero `ConcurrentHashMap`, cero bloques `synchronized` ni locks.
- **Identidad Estricta:** La identidad es `UUID playerId`. El modelo interno no retiene instancias de `Player`, `Entity`, `World` ni `Location`.
- **Estructura Interna O(N participantes):** No se almacenan listas infinitas de impactos individuales; únicamente totales acumulados y secuencias de hit extremas (`firstHitSequence`, `lastHitSequence`).
- **Frontera de Inmutabilidad:** Las consultas externas devuelven colecciones inmutables (`List.copyOf`) o instantáneas DTO inmutables (`ParticipantSnapshot`, `CombatSnapshot`).

### 6.4 Determinación de TOP_DAMAGE y Desempate
El participante con mayor daño acumulado se determina mediante:
1. Mayor `totalDamage` acumulado (`totalDamage DESC`).
2. Desempate determinista: menor `firstHitSequence` (`firstHitSequence ASC`, quien aportó primero al combate).
Sin tercer criterio lexicográfico por UUID. Garantiza un resultado determinista idéntico e independiente del orden de iteración de `HashMap`.

### 6.5 Deuda Técnica de Identidad (Registrada de 3.3-R1)
> 3.3-R1 mantiene `definitionId` y `schemaVersion` fuera del PDC efectivo. `DragonIdentity` todavía contiene defaults heredados de la arquitectura inicial (`"default"`, `1`). En Fase 3.5, `DragonDefinition` se introduce formalmente en la capa de configuración tipada y snapshot inmutable (`BattleConfigurationSnapshot`), asociando fases y habilidades sin escribir aún en el PDC físico.

---

## 7. Combat Phases & Abilities (Phase 3.5)

### 7.1 Topología Modular y Desacoplamiento de Runtime
Para preservar la modularidad y evitar un God Object, el flujo de ejecución de fases y habilidades se estructura en capas unidireccionales estrictamente segregadas:

```
BattleSession
    │
    ├──► CombatRuntime (Participantes, Daño de jugadores, TOP_DAMAGE)
    │
    └──► PhaseRuntime (Monitoreo de vida, Fases ordenadas, Monotonicidad)
           │
           └──► AbilityEngine (Triggers, Cooldowns lógicos)
                  │
                  ├──► TargetSelector (Entidades válidas en arena)
                  ├──► LocationResolver (Orígenes espaciales en mundo)
                  │
                  └──► AbilityEffect (0% NMS: DAMAGE, KNOCKBACK, PARTICLE, SOUND)
```

### Reglas de Desacoplamiento:
1. **`PhaseRuntime` NO registra daño:** El cálculo de daño y la tabla de clasificación pertenecen exclusivamente a `CombatRuntime`.
2. **`AbilityEngine` NO gestiona estado de batalla:** No decide transiciones de ciclo de vida (`DYING`, `COMPLETED`, `ABORTED`), ni interactúa con bases de datos SQLite o inventarios de recompensas.
3. **Aislamiento de Daño Dragon -> Jugador:** El daño producido por las habilidades del dragón a jugadores (`DamageEffect`) se despacha vía `player.damage(amount, dragon)` y **jamás** se registra en `CombatRuntime` (evitando feedback loops).

### 7.2 Progresión Monotónica y Semántica de Thresholds
- **Cálculo Robusto de Health Ratio:**
  $$\text{ratio} = \text{clamp}\left(\frac{\text{currentHealth}}{\text{maxHealth}}, 0.0, 1.0\right)$$
  Protección matemática absoluta contra divisiones por cero (`maxHealth <= 0`), valores negativos, `NaN` y `Infinity`.
- **Invariante de Monotonicidad Estricta:**
  La progresión de fases es unidireccional ($\text{Phase}_1 \to \text{Phase}_2 \to \text{Phase}_3 \to \dots$). Si el dragón es curado por cristales del End y su vida asciende superando umbrales anteriores, la batalla **permanece en la fase alcanzada**.
- **Salto Determinista de Fases:**
  Si un impacto masivo reduce la vida del dragón de 100% a 20%, el sistema avanza deterministamente en un solo tick hasta la fase correspondiente más avanzada sin transiciones erráticas ni bucles.

### 7.3 Motor de Habilidades y Cooldowns Lógicos
- **Ticks Lógicos:** Los cooldowns se evalúan en ticks del servidor (`nextAvailableTick = currentTick + cooldownTicks`), eliminando vulnerabilidades ante desfases de hora del sistema.
- **Aislamiento por Sesión:** Cada batalla posee su propio `AbilityCooldownTracker`; dos batallas concurrentes mantienen sus recargas completamente independientes.
- **Política de Cooldown en Cambio de Fase:**
  Al transicionar de fase:
  1. Se descartan las recargas pendientes de la fase anterior (`reset()`).
  2. Las habilidades de la nueva fase quedan disponibles inmediatamente según su configuración.
  3. Las habilidades `ON_PHASE_ENTER` se ejecutan exactamente una vez tras el cambio de fase.

### 7.4 Contexto Inmutable, Selectores y Resolutores
- **`AbilityExecutionContext` (Inmutable y Efímero):** Captura el estado congelado en el tick de ejecución (`battleId`, `worldName`, `dragon`, `trigger`, `resolvedTargets`, `resolvedOrigin`, `executionTick`, `ability`, `phase`). Se descarta al concluir el tick.
- **`TargetSelector` (Entidades, no Bloques):**
  - `ALL_IN_ARENA`: Jugadores en el mundo dentro del volumen ortogonal tridimensional real de la arena (`ArenaBounds`) y en el mismo mundo (`world + bounds`), en modo supervivencia o aventura. Sin radios ni esferas arbitrarias.
  - `RANDOM_PLAYER`: Jugador válido mediante RNG encapsulado y determinista.
  - `RANDOM_SUBSET`: Hasta N jugadores sin duplicados (o todos si hay menos de N).
  - `NEAREST_PLAYER`: Jugador más cercano al origen con desempate determinista por `UUID.toString()`.
  - `DAMAGER`: Jugador con `TOP_DAMAGE` consultado de `CombatRuntime`.
  - `TRIGGERING_PLAYER`: Jugador causante del evento disparador.
- **`LocationResolver` y `BattleSpatialContext` (0% NMS):**
  Resuelve coordenadas espaciales precisas para `DRAGON_HEAD`, `DRAGON_BODY`, `TARGET_FEET`, `PODIUM_CENTER`, `ARENA_CENTER` y `TRIGGER_LOCATION` con fallbacks seguros que garantizan cero `NullPointerException`.
  - `BattleSpatialContext`: Abstracción que separa semánticamente la ubicación del podium de salida (`PODIUM_CENTER`, nivel pedestal de bedrock) del centro de la arena de combate (`ARENA_CENTER`, altitud de combate), formalizado en la Fase 3.6 y 3.6-R1.
- **Orden de Resolución Coherente (Origin -> Target):**
  El origen se resuelve previo a la selección de objetivos (salvo `TARGET_FEET`), suministrando una referencia espacial exacta para `NEAREST_PLAYER` (ej. calculando distancia contra la cabeza del dragón para `DRAGON_HEAD`).
- **Seguridad de Ejecución y Validación de Triggers:**
  - El motor valida que el disparador invocado coincida estrictamente con `ability.trigger()` configurado, rechazando discrepancias de forma temprana.
  - La ejecución de efectos captura exclusivamente `Exception`, permitiendo que errores graves de la JVM (`Error`, `OutOfMemoryError`) se propaguen adecuadamente.

---

## 8. Arena & Rules (Phase 3.6)

### 8.1 Modelo de Dominio de Arena (0% Bukkit en Modelos)
El concepto de arena se materializa como un modelo de dominio fuertemente tipado e inmutable (`maurxp.betterdragon.arena`):
- **`Vector3d`:** Record inmutable de coordenadas tridimensionales finitas $(x, y, z)$ con conversión segura `toLocation(World)`.
- **`ArenaBounds`:** Bounding box axis-aligned (AABB) con invariante estricto $\min \le \max$ en cada eje. Provee consultas $O(1)$ deterministas: `contains(double x, y, z)` y `contains(Location)`.
- **`ArenaRuleSet`:** Record inmutable que encapsula reglas específicas de arena:
  - `waterAllowed`: Regla canónica de permiso o denegación de agua (`water_allowed: false`). Posee una única fuente de verdad, rechazando configuraciones conflictivas (`water_allowed` y `water_denial` duplicados o inconsistentes).
  - `boundaryEnabled`: Regla de confinamiento perimetral explícitamente requerida.
  - `antiTunnelEnabled`: Regla de mitigación anti-túnel explícitamente requerida.
  - *Cero defaults de gameplay inventados:* `ArenaConfigurationLoader` no asume mecánicas arbitrarias ante campos omitidos; la configuración de reglas debe ser explícita.
- **`ArenaDefinition`:** Definición canónica completa (`id`, `worldName`, `center`, `podium`, `bounds`, `rules`). Valida en su constructor compacto que los puntos de interés (`center` y `podium`) se ubiquen estrictamente dentro de los límites de la arena.
- **`ArenaRuleEvaluator`:** Evaluador desacoplado y de alta cohesión que consulta la validez de posiciones y acciones frente a las reglas configuradas.

### 8.2 Separación Estricta: Centro de Arena vs Podio
Se eliminaron definitivamente todos los fallbacks ficticios (`DefaultBattleSpatialContext`, `(0, 100, 0)`, `(0, 65, 0)`):
- **`ARENA_CENTER`:** Centro espacial y lógico aéreo del combate (ej. $Y=100.0$), utilizado por habilidades aéreas del dragón y proyectiles de área.
- **`PODIUM_CENTER`:** Centro físico del pedestal de bedrock a nivel del suelo (ej. $Y=65.0$), utilizado para efectos terrestres o mecánicas de portal futuro.
- **Independencia Absoluta:** Ambos puntos se configuran de forma desacoplada en `arenas.yml`; no existe alias ni delegación cruzada (`getArenaCenter()` $\neq$ `getPodiumCenter()`).

### 8.3 Integración Espacial, Dimensiones y Selectores de Objetivos
- **`ArenaBattleSpatialContext`:** Implementación inmutable de `BattleSpatialContext` asociada a la arena real congelada en la sesión. Requiere obligatoriamente un contexto espacial no nulo en `LocationResolver` y `TargetSelector`.
- **Evaluación Espacial Multidimensional (`isInArena`):** `ArenaBattleSpatialContext.isInArena(Location)` evalúa como unidad indivisible `world + bounds`: si el mundo de la coordenada no coincide de forma estricta (insensible a mayúsculas) con `arena.worldName()`, retorna `false` de inmediato sin evaluar las coordenadas numéricas.
- **`TargetSelector.ALL_IN_ARENA`:** Sustituye de forma radical cualquier radio esférico arbitrario (eliminando `DEFAULT_ARENA_RADIUS = 150.0`) por la comprobación exacta mediante `spatialContext.isInArena(player.getLocation())`, asegurando coherencia dimensional y geométrica con arenas pequeñas, grandes, asimétricas o multichunk.

### 8.4 Sincronización de Arena ID, Loader y Snapshot Inmutable de Arenas
```
arenas.yml ──► [ArenaConfigurationLoader] ──► [ArenaConfigurationSnapshot] (Global Inmutable)
                       │                                     │
            (Validación Estricta)                            ▼
                       │                        [BattleConfigurationSnapshot] (Congelado)
                       ▼                                     │
            ConfigValidationException                        ▼
              (Fail-Safe / Rechazo)            [BattleSession] (Aislada de recargas)
```
- **Sincronización Estricta de Identidad:** `BattleManager.startBattle()` resuelve prioritariamente la `ArenaDefinition` física asociada al mundo (`resolveArenaForWorld`), y genera el `BattleConfigurationSnapshot` congelando directamente `arena.id()`. Esto erradica inconsistencias donde una arena secundaria (ej. `arena_pvp`) retenía un identificador `"default"`.
- **`ArenaConfigurationLoader`:** Lee `arenas.yml`, valida sintaxis, números finitos, dimensiones coherentes e inclusiones geométricas, reportando errores detallados de forma acumulativa.
- **Fail-Safe Atómico en Recarga (`/bd reload`):** Si `arenas.yml` presenta errores de validación, la recarga se aborta, la configuración de arenas previa se mantiene intacta en memoria y las sesiones de batalla activas conservan su `ArenaDefinition` inmutable original.
- **Disponibilidad de Mundo en Runtime:** La arena distingue entre configuración válida en disco y disponibilidad del mundo en el servidor Paper. Si el mundo no está cargado o no existe, `BattleManager` rechaza iniciar la batalla tempranamente de forma segura.

### 8.5 Confinamiento de Responsabilidades y Cero NMS
- La capa de Arena no implementa recompensas, claims, portales, inventarios, destrucción global de bloques ni trampas de teletransporte.
- 0% NMS: Todas las consultas espaciales operan sobre primitivos matemáticos puros y la API pública de Bukkit/Paper.

---

## 9. Muerte, Victoria y BattleResult (Fase 3.7 & 3.7-R1)

### 9.1 Fuente Exclusiva de Verdad de la Victoria
- La **única fuente válida** para decretar la victoria de una batalla BetterDragon es el evento de Bukkit `EntityDeathEvent` sobre el `EnderDragon` físico administrado (`betterdragon:managed=true`, `betterdragon:battle_id`).
- **Independencia Total de DragonBattle:** BetterDragon ignora por completo el ciclo de vida, métodos o estados internos de `DragonBattle` / `EnderDragonFight` (`dragonKilled`, `getEnderDragon()`, `hasBeenPreviouslyKilled()`). La fuente de verdad reside en la `BattleSession` y en la identidad PDC persistente.
- **Control Soberano sobre Recompensas Vanilla:**
  - Al confirmarse la defunción legítima de un dragón administrado, BetterDragon suprime los drops vanilla (`deathEvent.getDrops().clear()`) y la experiencia (`deathEvent.setDroppedExp(0)`), evitando que las recompensas vanilla interfieran con el futuro sistema de Rewards de BetterDragon.
  - **Dragones Vanilla Intactos:** Dragones no administrados (sin PDC) no sufren alteración alguna; conservan intactos sus drops y experiencia vanilla, y no disparan eventos ni transiciones de BetterDragon.
- **Dragon Egg y Primera Victoria Independientes:**
  - El Dragon Egg y la determinación de "primera victoria" no dependen de `DragonBattle` ni de su estado vanilla (`hasBeenPreviouslyKilled()`).
  - En esta fase NO se genera ni manipula ningún bloque ni ítem de Dragon Egg. El ciclo de vida completo del Egg queda explícitamente reservado al sistema propio de BetterDragon en fases posteriores.

### 9.2 Distinción Crítica: Muerte Real vs Entidad Desaparecida
- **Muerte Real (`EntityDeathEvent`):** Representa la muerte natural del dragón por combate legítimo. Dispara la secuencia de victoria `ACTIVE -> DYING -> COMPLETED` y emite `BetterDragonVictoryEvent`.
- **Entidad Desaparecida (`EntityRemoveEvent` / Fallo de Recuperación):** Si durante un reinicio o recarga de chunks la entidad no se encuentra o su identidad PDC no coincide, la sesión transiciona a `ABORTED` (`BattleAbortReason.ENTITY_MISSING`). **Bajo ninguna circunstancia la ausencia de una entidad se interpreta como victoria.**
- Si el chunk está descargado temporalmente, la batalla se mantiene en `DEFERRED_PENDING_CHUNK_LOAD` y no finaliza prematuramente.

### 9.3 Idempotencia y Compuertas de Estado
- `BattleManager.handleDragonDeath` implementa compuertas estrictas:
  - Si la sesión ya se encuentra en `COMPLETED`, devuelve el `BattleResult` inmutable previamente cacheado sin redisparar eventos ni mutar contadores.
  - Si la sesión está en `ABORTED`, `IDLE` o `PREPARING`, se descarta de forma segura como no-op.
  - Si la sesión está en `DEFERRED_PENDING_CHUNK_LOAD`, valida el dragón y reanuda la sesión antes de procesar la defunción.
- La transición transcurre por los estados formales:
  ```text
  ACTIVE
    ↓
  EntityDeathEvent (Cancela 12,000 XP vanilla y drops)
    ↓
  DYING (Cierre definitivo de registro de daño en CombatRuntime)
    ↓
  Captura de CombatSnapshot inmutable + Determinación de Slayer (TOP_DAMAGE)
    ↓
  COMPLETED (Construcción de BattleResult + Despacho de BetterDragonVictoryEvent)
  ```

### 9.4 Determinación de Slayer (`TOP_DAMAGE`) y Desempate
- La política de diseño inmutable del proyecto es `Slayer = TOP_DAMAGE`.
- El Slayer se extrae del snapshot inmutable de `CombatRuntime` mediante dos únicos criterios deterministas:
  1. Participante con mayor daño total acumulado (`totalDamage DESC`).
  2. Si existe empate en daño, desempata exclusivamente el menor `firstHitSequence` (`firstHitSequence ASC`, quien asestó el primer golpe registrado).
  3. No se utiliza ningún tercer criterio (se eliminó definitivamente el desempate por UUID lexicográfico).
- Los participantes desconectados u offline conservan intacta su candidatura como Slayer con sus nombres históricos (`historicalName` y `lastKnownName`).

### 9.5 Inmutabilidad de `BattleResult` y Frontera de Dominio
- `BattleResult` es un `record` inmutable y serializable:
  - Almacena `CombatSnapshot` (lista inmutable de `ParticipantSnapshot`, secuencias, daño total acumulado).
  - **Cero referencias vivas:** No almacena instancias de `Player`, `Entity`, `World` ni colecciones mutables de Bukkit.
  - Diseñado para ser consumido de forma thread-safe en fases posteriores por Recompensas (3.8), Persistencia SQLite (3.9) y Leaderboard (3.10).

### 9.6 Evento Público de Dominio: `BetterDragonVictoryEvent`
- Evento Bukkit público en `maurxp.betterdragon.battle.event`, no cancelable e informativo.
- Publicado tras alcanzar el estado final `COMPLETED`, exponiendo únicamente tipos de dominio seguros e inmutables (`BattleId`, `BattleResult`, `worldName`).
- **Encapsulación Estricta de Runtime:** `BetterDragonVictoryEvent` NO expone `BattleSession` ni ningún runtime mutable interno (`CombatRuntime`, `PhaseRuntime`, `AbilityEngine`). Los consumidores externos acceden a los datos de la batalla exclusivamente a través de `BattleResult`.
- Desacoplado mediante la interfaz funcional `VictoryEventDispatcher` para habilitar pruebas unitarias puras sin necesidad de un servidor Bukkit mockeado.

### 9.7 Transición de Fase
- **Fase 3.7 y 3.7-R1 Completadas:** El ciclo terminal de defunción, supresión vanilla, resolución de Slayer y emisión desacoplada de `BetterDragonVictoryEvent` quedó consolidado y blindado contra regresiones.

---

## 10. Recompensas, Asignación y Buzón de Reclamos (Fase 3.8 / 3.8-R1 / 3.8-R2)

### 10.1 Cadena Conceptual de Recompensas
El subsistema de recompensas de BetterDragon desacopla estrictamente el ciclo de combate de la adjudicación y la entrega de botín:
```text
Combat (CombatRuntime)
  ↓
CombatSnapshot (Inmutable)
  ↓
BattleResult (Inmutable)
  ↓
Victory (BetterDragonVictoryEvent)
  ↓
Reward Eligibility (Cálculo puro de umbral de daño real)
  ↓
Reward Allocation (Redistribución proporcional y redondeo determinista con rewardId)
  ↓
Reward Delivery (Entrega física en inventario con gestión de leftovers)
  ↓
Claim Storage (Buzón de reclamos en memoria PENDING / CLAIMED / FAILED_RETRYABLE)
```

### 10.2 Modelo de Dominio Desacoplado (0% Bukkit)
- **Aislamiento Total de Plataforma:** Las clases en `maurxp.betterdragon.reward.model` no importan `ItemStack`, `Player`, `Entity` ni `World`.
- **`RewardItem`:** Representación canónica inmutable del ítem de recompensa (`String material`, `int amount`, `displayName`, `lore`).
- **`RewardAllocation`:** Cuota individual inmutable calculada para un participante (`BattleId`, `UUID participantId`, `String participantName`, `RewardSource source`, `String rewardId`, `RewardItem item`, `double participationPercent`, `Instant allocatedAt`).
- **`RewardAllocationPlan`:** Plan de asignación global de la batalla (`BattleId`, lista de `RewardAllocation`, listas de participantes elegibles/no elegibles, daño total y daño elegible).
- **`RewardClaim`:** Registro inmutable del reclamo de un participante, con tracking atómico de `originalAmount`, `deliveredAmount`, `ClaimStatus` (`PENDING`, `CLAIMED`, `FAILED_RETRYABLE`), marcas temporales y motivo de fallo opcional.

### 10.3 Reglas de Elegibilidad, Identidad y Defaults Seguros
1. **Identidad Estricta de Recompensas (`rewardId`):**
   - Toda definición de ítem (`RewardItemDefinition`) requiere obligatoriamente un `id` alfanumérico único (`^[a-zA-Z0-9_-]+$`).
   - El `ConfigurationLoader` valida la unicidad global de `id` entre el pool de participación y las recompensas de slayer, rechazando de forma fail-fast cualquier duplicado.
2. **Validación Estricta de Cantidad (`amount` — Fase 3.8-R2):**
   - `amount` representa una cantidad entera positiva exacta mayor a 0.
   - Se rechaza de forma fail-fast cualquier valor no entero o no positivo: números decimales/fraccionarios (`1.7`, `1.5`, `2.5`), cero (`0`), negativos (`-1`), no finitos (`NaN`, `Infinity`) y desbordamientos (`> Integer.MAX_VALUE`).
   - Valores numéricos que representan exactamente un entero positivo (por ejemplo `2.0` entregado como `Double` por parsers YAML) son aceptados con precisión matemática sin truncamiento ni redondeo silencioso.
   - El mensaje de error de configuración identifica con precisión la ruta, el identificador `rewardId` y el campo `'amount'`.
3. **Validación Nativa de Material (Paper API — Fase 3.8-R2):**
   - Validación estricta contra la API real de Paper/Bukkit mediante `Material.matchMaterial(material)`.
   - Se eliminaron por completo fallbacks heurísticos basados en expresiones regulares o listas de palabras prohibidas (`isValidMaterialFallback`), garantizando que nombres plausibles pero inexistentes (`FOO_BAR`, `FAKE_MATERIAL`, `INVALID_MATERIAL`) sean rechazados en la carga de configuración.
   - Se rechazan explícitamente materiales de tipo aire (`AIR`, `CAVE_AIR`, `VOID_AIR`).
4. **Defaults Neutros y No Inventados:**
   - La configuración por defecto del plugin (`RewardConfigurationSnapshot.defaults()` y `config.yml`) inicia con `rewards.enabled: false`, `min_participation_percent: 0.0` y listas de ítems vacías.
   - Cero asunciones de gameplay inventado (sin diamantes, netherite ni esmeraldas forzadas en código de producción).
5. **Elegibilidad por Daño Real:**
   $$\text{participationPercent} = \frac{\text{participant.totalDamage}}{\text{totalBattleDamage}} \times 100$$
   Un participante es elegible si y solo si $\text{participationPercent} \ge \text{min\_participation\_percent}$ y $\text{participant.totalDamage} > 0$.
   - Si $\text{totalBattleDamage} == 0$ o ningún participante alcanza el umbral, el sistema retorna de forma segura y determinista un `RewardAllocationPlan.empty(battleId)`.
6. **Redistribución Proporcional de No Elegibles:**
   - La cuota de los participantes no elegibles nunca se pierde silenciosamente; se redistribuye entre los participantes elegibles proporcionalmente a sus participaciones relativas:
     $$\text{relativeShare} = \frac{\text{participant.totalDamage}}{\text{eligibleDamage}} \times \text{totalPoolUnits}$$

### 10.4 Redondeo Determinista de Unidades Enteras
1. **Unidades Base:** Cada participante elegible recibe $\lfloor \text{relativeShare} \rfloor$.
2. **Cálculo de Remanente:** $\text{remainderUnits} = \text{totalPoolUnits} - \sum \text{baseUnits}$.
3. **Distribución del Remanente:** Si $\text{remainderUnits} > 0$, las unidades sobrantes se asignan de a una unidad (1 ítem) a los participantes ordenados por:
   - Mayor fracción decimal residual (`fraction DESC`).
   - Empate en fracción: menor `firstHitSequence` (`firstHitSequence ASC`, quien asestó el primer impacto en combate).
   - Estabilidad absoluta: `participantId` lexicográfico (`UUID ASC`).

### 10.5 Recompensa de Slayer (`TOP_DAMAGE`)
- La identidad del Slayer proviene exclusivamente de `BattleResult.slayerUniqueId()`, resuelto al morir el dragón con los 2 criterios inmutables (`totalDamage DESC` y `firstHitSequence ASC`).
- Si `slayer_reward.enabled = true`, se adjudica el paquete de ítems configurado. Si `requires_eligibility = true`, el Slayer solo lo recibe si superó el porcentaje mínimo de participación general.

### 10.6 Separación de Allocation vs Delivery y Protección de Reclamos
- **Allocation Engine (`RewardAllocationEngine`):** Componente funcional puro que recibe `BattleResult` y `RewardConfigurationSnapshot` y genera `RewardAllocationPlan`. Cero efectos colaterales de inventario o Bukkit.
- **Delivery Service (`RewardDeliveryService`):** Orquestador de entrega física asistido por `PlayerInventoryAdapter`:
  - **Jugador Online:** Intenta entrega con `inventory.addItem()`.
  - **Jugador Offline:** El reclamo se almacena intacto en `ClaimStorage` con estado `PENDING` (`deliveredAmount = 0`). Cero ítems perdidos.
  - **Inventario Saturado / Entrega Parcial:** Se computan los *leftovers* exactos; las unidades que cupieron actualizan `deliveredAmount`, y el remanente permanece como `PENDING`. Ningún ítem se descarta ni se arroja descontroladamente al suelo.
  - **Reconexión / Reintento:** Al reconectarse el jugador o liberar ranuras, `retryPendingForPlayer` completa la entrega de los ítems pendientes y transiciona el reclamo a `CLAIMED`.
  - **Resiliencia ante Fallos:** Si ocurre una excepción inesperada durante la manipulación de inventario, el reclamo pasa a `FAILED_RETRYABLE` preservando el estado para reintento.

### 10.7 Idempotencia Canónica y Prevención de Colisiones
- **Fórmula Canónica de Idempotencia:**
  $$\text{idempotencyKey} = \text{battleId} + ":" + \text{participantId} + ":" + \text{rewardId}$$
- **Prevención de Colisiones y Saneamiento Global:**
  - En la auditoría 3.8-R1 y saneamiento 3.8-R2 se consolidó el uso exclusivo de `rewardId` como discriminador canónico en la clave de deduplicación. Toda documentación y Javadoc obsoleto que hacía referencia a `source:material` fue erradicado.
  - Si dos definiciones de recompensa configuran el mismo material (por ejemplo, `pool_diamonds: DIAMOND x5` y `slayer_bonus: DIAMOND x10`), ambas poseen `rewardId` distintos, produciendo claves de idempotencia distintas y permitiendo la coexistencia y entrega independiente de ambos reclamos sin colisión.
- Si una recompensa ya se encuentra en estado `CLAIMED` en `ClaimStorage`, cualquier invocación repetida de entrega es un no-op inmediato que no duplica ítems físicos.
- El doble procesamiento de un `BattleResult` (por doble listener o retry) es seguro e inocuo.

### 10.8 Límites de Almacenamiento en Memoria (Fase 3.8)
- **`InMemoryClaimStorage`:**
  - Almacenamiento concurrente seguro (`ConcurrentHashMap`) limitado al ciclo de vida del proceso de la JVM.
  - **Límites Documentados:** Los reclamos resguardados en memoria **NO** son persistentes ante reinicios del servidor, detenciones o recargas del plugin (`crash/restart`).
  - Desde la Fase 3.9, se conserva exclusivamente como infraestructura de pruebas unitarias aisladas.

---

## 11. Persistencia Durable SQLite y Buzón de Reclamos (Fase 3.9)

### 11.1 Almacenamiento Durable y Desacoplamiento de Persistencia
A partir de la Fase 3.9, BetterDragon implementa almacenamiento duradero basado en SQLite embebido (`plugins/BetterDragon/data/betterdragon.db`):
- **Abstracción `ClaimStorage`:** La interfaz de almacenamiento fue evolucionada a un contrato totalmente asíncrono basado en `CompletableFuture<T>`. Ninguna consulta o mutación de persistencia expone conexiones JDBC al dominio ni bloquea el hilo que la invoca.
- **`SQLiteClaimStorage`:** Implementación productiva respaldada por SQLite. Mapea modelos de dominio inmutables (`RewardClaim`) a columnas relacionales normalizadas sin recurrir a serialización Java de objetos ni almacenar objetos de Bukkit (`Player`, `Inventory`, `ItemStack`).
- **`InMemoryClaimStorage`:** Se mantiene intacto para tests unitarios rápidos y deterministas. **Regla de integridad:** El plugin en producción **nunca** realiza un fallback silencioso a `InMemoryClaimStorage` si SQLite falla; la inicialización falla de forma explícita y fail-safe para evitar pérdida silenciosa de recompensas.

### 11.2 Topología de Hilos y Single-Writer Dedicado
SQLite opera bajo un modelo de concurrencia estrictamente controlado mediante un ejecutor de persistencia dedicado (*Single-Writer Async Worker*):
```
Hilo Principal Bukkit (Main Thread)               Persistence Worker Thread (Async)
───────────────────────────────────               ─────────────────────────────────
• Eventos Bukkit (Victory / Join)
• Player / Inventory / Item delivery
• Decisiones de gameplay
        │
        │ [1. deliverPlan / retryPending]
        ▼
RewardDeliveryService ────────────────────────► [2. Query claims / idempotencyKey]
                                                          │
                                                          ▼
                                                  DatabaseManager / SQLite
                                                  (Single-Writer Executor)
                                                          │
                                                          ▼
[4. Entrega física en inventario] ◄─────────── [3. CompletableFuture callback]
(vía MainThreadDispatcher)
        │
        ▼
[5. Confirmación de entrega] ─────────────────► [6. Update estado / remainingAmount]
                                                          │
                                                          ▼
                                                  COMMIT a SQLite
```
- **Cero JDBC en Main Thread:** Ninguna instrucción `DriverManager`, `PreparedStatement` ni `ResultSet` se ejecuta en el hilo principal del servidor.
- **Cero Objetos Bukkit en Async:** Instancias de `Player`, `Inventory`, `World` o `Entity` jamás se envían a los hilos de persistencia. Solo se intercambian tipos primitivos, `UUID`, cadenas y DTOs inmutables de dominio.
- **`MainThreadDispatcher`:** Interfaz funcional (`Consumer<Runnable>`) inyectada en `RewardDeliveryService` que permite planificar las entregas físicas en el hilo de Bukkit (`runTask(plugin, runnable)`), facilitando al mismo tiempo la ejecución directa inline (`Runnable::run`) en pruebas unitarias deterministas.

### 11.3 Configuración de Conexión y Pragmas SQLite
`DatabaseManager` gestiona el ciclo de vida de la conexión JDBC SQLite bajo parámetros seguros:
- `PRAGMA foreign_keys = ON;`: Garantiza integridad referencial.
- `PRAGMA busy_timeout = 5000;`: Evita bloqueos inmediatos por contención de I/O en disco durante escrituras concurrentes de checkpoint.
- Conexión persistente única poseída exclusivamente por `DatabaseManager`. Ninguna otra clase tiene acceso directo a la `Connection`.

### 11.4 Esquema Relacional y Versionado (`SchemaInitializer`)
El esquema inicial v1 se define e inicializa de forma completamente idempotente:
- **Tabla de Metadatos (`bd_schema_metadata`):**
  - Estructura clave-valor (`key TEXT PRIMARY KEY`, `value TEXT NOT NULL`).
  - Almacena `schema_version = 1`.
  - **Rechazo Fail-Safe:** Si al arrancar se detecta `schema_version > CURRENT_SCHEMA_VERSION` (por ejemplo, una base de datos proveniente de una versión más reciente del plugin), el sistema arroja `IllegalStateException` y detiene el subsistema de persistencia inmediatamente sin intentar downgrades destructivos ni sobrescribir datos.
- **Tabla Principal (`bd_reward_claims`):**
  - Columnas: `claim_id` (UUID string PK), `idempotency_key` (TEXT UNIQUE NOT NULL), `battle_id` (UUID string NOT NULL), `participant_uuid` (UUID string NOT NULL), `player_name` (TEXT NOT NULL), `source` (TEXT NOT NULL, ej. PARTICIPATION, SLAYER), `material` (TEXT NOT NULL, nombre canónico de Paper), `display_name` (TEXT), `lore` (TEXT multilinea), `original_amount` (INTEGER NOT NULL), `delivered_amount` (INTEGER NOT NULL), `remaining_amount` (INTEGER NOT NULL), `status` (TEXT NOT NULL: PENDING, CLAIMED, FAILED_RETRYABLE), `created_at` (INTEGER epoch ms NOT NULL), `claimed_at` (INTEGER epoch ms), `failure_reason` (TEXT), `updated_at` (INTEGER epoch ms NOT NULL).
- **Índices de Rendimiento:**
  - `idx_reward_claims_status` sobre `status`.
  - `idx_reward_claims_participant` sobre `participant_uuid`.
  - `idx_reward_claims_battle` sobre `battle_id`.

### 11.5 Idempotencia Durable, Separación Create/Update y Estado Terminal CLAIMED
La clave canónica de deduplicación:
$$\text{idempotencyKey} = \text{battleId} + ":" + \text{participantId} + ":" + \text{rewardId}$$
está resguardada por la restricción `UNIQUE(idempotency_key)` a nivel de base de datos.

La mutación y ciclo de vida de los reclamos separa estrictamente la creación de la actualización:
1. **Creación Idempotente (`createIfAbsent`):**
   ```sql
   INSERT INTO bd_reward_claims (...) VALUES (...)
   ON CONFLICT(idempotency_key) DO NOTHING;
   SELECT * FROM bd_reward_claims WHERE idempotency_key = ?;
   ```
   Si el reclamo ya existía, no es modificado y se retorna la entidad persistida preexistente con su estado actual.
2. **Actualización Condicional (`updateExisting`):**
   ```sql
   UPDATE bd_reward_claims SET
       player_name = ?, delivered_amount = ?, remaining_amount = ?,
       status = ?, claimed_at = ?, failure_reason = ?, updated_at = ?
   WHERE idempotency_key = ?
     AND status != 'CLAIMED';
   ```
   **`CLAIMED` como Estado Terminal Definitivo:** Una vez que un reclamo alcanza el estado persistente `CLAIMED`, ninguna actualización posterior (incluso proveniente de un objeto obsoleto con estado `CLAIMED`) puede modificarlo. Cualquier intento es rechazado retornando `false` y sin alterar ninguna columna.
3. **Deduplicación Concurrente en Memoria:** `inFlightDeliveries` (`ConcurrentHashMap`) coalesce entregas concurrentes sobre la misma clave en runtime, evitando doble entrega física.

### 11.6 Recuperación tras Reinicio (Restart Recovery)
Al reiniciar el servidor:
1. La base de datos SQLite se conecta y valida el esquema.
2. Los reclamos con estado `PENDING` y `FAILED_RETRYABLE` se conservan intactos con su `remaining_amount`.
3. Los reclamos en estado `CLAIMED` no se reactivan como pendientes, pero permanecen almacenados para garantizar idempotencia histórica y auditoría.
4. Al ingresar un jugador (`PlayerJoinEvent`), `DragonRewardListener` invoca `rewardService.retryPendingClaims(player.getUniqueId())` de forma no bloqueante:
   - Consulta los claims pendientes en el worker asíncrono.
   - Si existen, despacha la entrega física al hilo principal mediante `MainThreadDispatcher`.
   - Tras depositar los ítems en el inventario de Bukkit, persiste asíncronamente el nuevo estado (`CLAIMED` o remanente actualizado).

### 11.7 Limitación Explícita de Consistencia ante Caídas (Crash Consistency Limitation)
> [!IMPORTANT]
> **Garantía Real vs. Atomicidad Transaccional de Minecraft:**
> BetterDragon **NO** puede garantizar atomicidad hardware "exactly-once" coordinada entre el archivo SQLite en disco y el inventario del jugador almacenado en los archivos de región/NBT de Minecraft (`world/playerdata/*.dat`).
> Ambos representan subsistemas de persistencia independientes sin soporte de protocolo de commit en dos fases (*Two-Phase Commit / 2PC*).
>
> Existe una ventana de vulnerabilidad teórica inherente a la arquitectura de Minecraft:
> 1. Un ítem es depositado físicamente en la memoria del inventario del jugador en el hilo principal de Bukkit.
> 2. El servidor experimenta una caída catastrófica (ej. `kill -9`, apagón eléctrico del host o crash de la JVM) en el microsegundo exacto antes de que el worker asíncrono logre persistir el estado `CLAIMED` en SQLite.
> 3. Al reiniciar, el claim en SQLite continuará figurando como `PENDING`. Si el guardado de chunks/jugadores de Minecraft logró escribir el inventario a disco antes del corte, el jugador podría recibir nuevamente el remanente en el siguiente inicio.
>
> **Mitigación Adoptada:**
> - BetterDragon implementa la política *at-least-once con deduplicación optimista*: **jamás** se marca un claim como `CLAIMED` en SQLite antes de haber confirmado la entrega efectiva en el hilo principal.
> - Se prefiere el riesgo acotado de un reintento en un crash catastrófico antes que arriesgar la pérdida silenciosa de ítems legítimos de los jugadores.

### 11.8 Apagado Limpio (Graceful Shutdown)
Durante `onDisable()`:
1. Se rechazan nuevos trabajos en `DatabaseManager`.
2. El ejecutor dedicado `persistenceExecutor` ejecuta las tareas en cola y se cierra con `shutdown()` y espera controlada (`awaitTermination(5, SECONDS)`).
3. La conexión JDBC `connection.close()` se cierra formalmente, forzando la sincronización de cualquier journal pendiente.

---

## 12. Subsistema de Leaderboard Persistente (Fase 3.10)

### 12.1 Flujo y Desacoplamiento de Datos
El leaderboard consume exclusivamente el resultado formal inmutable producido al completarse una batalla:
```
BetterDragonVictoryEvent (Bukkit Event / MONITOR)
       │
       ▼
   BattleResult (Immutable DTO)
       │
       ▼
DragonLeaderboardListener (Bukkit Listener)
       │
       ▼
LeaderboardService (Application Service)
       │
       ▼ (Transformación a records inmutables)
SQLiteLeaderboardStorage (JDBC / Single-Writer Async)
       │
       ▼ (BEGIN TRANSACTION ... COMMIT)
    SQLite (betterdragon.db)
```

### 12.2 Separación Estricta de Persistencia y Convivencia de Esquema
- **Convivencia:** Leaderboard y Rewards conviven en la misma base de datos relacional (`betterdragon.db`) y comparten el mismo ejecutor monohilo asíncrono (`DatabaseManager`).
- **Aislamiento Funcional:** Las tablas de leaderboard (`bd_leaderboard_battles`, `bd_leaderboard_participation`, `bd_leaderboard_players`) son completamente independientes de `bd_reward_claims`. Ninguna operación de leaderboard altera ni consulta las claves o estados de recompensas.

### 12.3 Esquema Relacional v2 y Migración
- **`bd_schema_metadata`:** Versión de esquema actualizada a `schema_version = 2`.
- **Instalación Nueva:** Inicializa directamente todas las tablas de rewards y leaderboard en versión 2.
- **Migración No Destructiva (v1 → v2):** Detecta versión 1, crea las tablas e índices de leaderboard preservando intactos los registros de `bd_reward_claims`, y asciende la versión a 2.
- **Rechazo Fail-Safe:** Si se detecta `schema_version > 2`, el inicio se detiene arrojando `IllegalStateException`.

### 12.4 Tablas del Leaderboard
1. **Historial de Batallas (`bd_leaderboard_battles`):**
   - `battle_id TEXT PRIMARY KEY`: Identificador único de la batalla.
   - `completed_at INTEGER NOT NULL`: Timestamp de conclusión en epoch milliseconds.
   - `world_name TEXT NOT NULL`: Nombre del mundo de combate.
   - `slayer_uuid TEXT`: UUID del Slayer o NULL.
   - Índice: `idx_leaderboard_battles_completed` sobre `completed_at DESC`.

2. **Historial de Participación (`bd_leaderboard_participation`):**
   - `battle_id TEXT NOT NULL`, `player_uuid TEXT NOT NULL`: Clave primaria compuesta `PRIMARY KEY (battle_id, player_uuid)`.
   - `historical_name TEXT NOT NULL`: Nombre capturado específicamente en dicha batalla (inmutable retroactivamente).
   - `damage REAL NOT NULL`: Daño exacto infligido.
   - `first_hit_sequence INTEGER NOT NULL`: Secuencia monotónica de impacto.
   - `was_slayer INTEGER NOT NULL`: 1 si fue proclamado Slayer (TOP_DAMAGE), 0 en caso contrario.
   - `participated_at INTEGER NOT NULL`: Epoch ms de la participación.
   - Índices: `idx_leaderboard_part_player` sobre `player_uuid`, `idx_leaderboard_part_battle` sobre `battle_id`.

3. **Estadísticas Acumuladas por Jugador (`bd_leaderboard_players`):**
   - `player_uuid TEXT PRIMARY KEY`: Identidad única y permanente del jugador.
   - `last_known_name TEXT NOT NULL`: Nombre más reciente conocido del jugador.
   - `battles_participated INTEGER NOT NULL`: Cantidad total de batallas únicas participadas.
   - `total_damage REAL NOT NULL`: Daño acumulado a lo largo de todas las batallas.
   - `highest_damage REAL NOT NULL`: Daño máximo registrado en una sola batalla.
   - `slayer_count INTEGER NOT NULL`: Cantidad total de victorias como Slayer (`was_slayer = true`).
   - `first_participation_at INTEGER NOT NULL`, `last_participation_at INTEGER NOT NULL`: Tiempos de primera y última batalla.
   - Índices deterministas:
     - `idx_leaderboard_players_damage` sobre `(total_damage DESC, player_uuid ASC)`
     - `idx_leaderboard_players_slayer` sobre `(slayer_count DESC, player_uuid ASC)`
     - `idx_leaderboard_players_battles` sobre `(battles_participated DESC, player_uuid ASC)`

### 12.5 Idempotencia y Transacciones Atómicas
- Cada registro de victoria se realiza dentro de una transacción SQLite única (`BEGIN TRANSACTION ... COMMIT` / `ROLLBACK`).
- La inserción de la batalla utiliza `INSERT INTO bd_leaderboard_battles (...) VALUES (...) ON CONFLICT(battle_id) DO NOTHING;`. Si no se insertan filas (`executeUpdate() == 0`), la transacción se revierte inmediatamente sin tocar participaciones ni agregados.
- Re-procesar la misma batalla múltiples veces produce exactamente el mismo estado que procesarla una sola vez.
- La actualización de acumulados en `bd_leaderboard_players` utiliza `INSERT ... ON CONFLICT(player_uuid) DO UPDATE` empleando funciones nativas de SQLite (`MAX(highest_damage, excluded.highest_damage)`, `MIN(first_participation_at, excluded.first_participation_at)`, `+ excluded.total_damage`, `+ 1`).
- `average_damage` no se persiste en base de datos; se calcula dinámicamente en memoria (`totalDamage / battlesParticipated`).

### 12.6 Consultas y Determinismo de Rankings
- Las consultas `getTopDamage(limit)`, `getTopSlayers(limit)` y `getTopParticipations(limit)` resuelven empates aplicando siempre `player_uuid ASC` como criterio de desempate determinista.
- Todas las consultas validan `limit > 0` y aplican un límite máximo de seguridad interna (`MAX_RANKING_LIMIT = 1000`).

### 12.7 Limitaciones de Consistencia
- No existe un protocolo de dos fases (2PC) entre eventos de Bukkit y SQLite. La garantía formal es: *una vez confirmada la transacción en SQLite, ningún reintento o re-procesamiento del mismo `battle_id` duplicará contadores o estadísticas acumuladas*.

---

## 13. Application Layer, Interfaz de Comandos y UX Administrativo (Fase 3.11)

### 13.1 Principio Arquitectónico Fundamental: Desacoplamiento de Interfaz
BetterDragon 3.11 establece una frontera formal entre las capas de entrada (CLI y futuras interfaces gráficas) y el núcleo de negocio mediante una **Capa de Aplicación (Application Layer)**:

```
                    BetterDragon
                         │
             ┌───────────┴───────────┐
             │                       │
        Commands CLI            FUTURE GUI
             │                       │
             └───────────┬───────────┘
                         ▼
                 APPLICATION LAYER
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
       Battles        Rewards       Leaderboard
                         │
                         ▼
                      DOMAIN
                         │
                         ▼
                    Persistence
```

### 13.2 Reglas de Desacoplamiento para Futuras GUIs:
1. **Cero Lógica de Negocio en Comandos:** Los comandos no modifican estados de combate, no validan reglas de negocio ni manipulan la base de datos directamente.
2. **Cero Dependencia de Bukkit UI en Application Layer:** Los servicios de aplicación (`maurxp.betterdragon.application.*`) no dependen de `Inventory`, `ItemStack`, `Component` complejos ni clases de comandos. Retornan modelos y vistas tipadas puras (`BattleStatusView`, `ArenaSummaryView`, `ArenaDetailView`, `ReloadResult`, `BattleOperationResult`, `LeaderboardPlayerStats`).
3. **Reutilización Idéntica:** Tanto la CLI de comandos como las futuras GUIs consumen exactamente los mismos Application Services y los mismos chequeos de permisos (`PermissionChecker`).
4. **Prohibición de Puentes Falsos:** Las futuras GUIs jamás invocarán comandos sintéticos (ej. `player.performCommand("bd start")`).

### 13.3 Servicios de la Capa de Aplicación (`maurxp.betterdragon.application`):
- **`permission`:**
  - `CommandPermission`: Enum tipado con nodos canónicos (`betterdragon.use`, `betterdragon.leaderboard`, `betterdragon.stats`, `betterdragon.stats.others`, `betterdragon.claim`, `betterdragon.admin.*`).
  - `PermissionChecker`: Contrato desacoplado para validar permisos por `CommandSender` o `UUID`.
  - `BukkitPermissionChecker`: Implementación nativa sobre Paper API que respeta herencia de nodos (`betterdragon.admin` otorga subpermisos admin).
- **`leaderboard`:**
  - `LeaderboardApplicationService`: Expone consultas seguras de rankings (`getTopDamage`, `getTopSlayers`, `getTopParticipations`) y estadísticas (`getPlayerStats`, `getPlayerStatsByQuery`) acotadas con límites sanitizados y ejecución 100% asíncrona no bloqueante.
- **`battle`:**
  - `BattleAdminService`: Coordina operaciones administrativas sobre batallas activas (`startBattle`, `abortBattle`, `getStatus`, `getAllStatuses`).
  - `BattleStatusView`: DTO inmutable de lectura que calcula métricas seguras (porcentaje de salud, duración, damager líder) sin exponer mutabilidad de `BattleSession`.
  - `BattleOperationResult`: Resultado tipado inmutable de operaciones administrativas (`success`, `message`).
- **`arena`:**
  - `ArenaQueryService`: Expone consultas inmutables de arenas cargadas en memoria (`listArenas`, `getArenaDetail`) consumiendo `ConfigurationService`.
  - `ArenaSummaryView` y `ArenaDetailView`: Vistas puras desacopladas de Bukkit con reglas activas y vectores espaciales.
- **`admin`:**
  - `AdminApplicationService`: Gestiona la recarga fail-safe y atómica de configuraciones y arenas (`reloadConfiguration`), retornando `ReloadResult` con milisegundos transcurridos.
- **`reward`:**
  - `RewardApplicationService`: Facilita la consulta de buzones de reclamo pendientes (`getPendingClaims`) y la entrega explícita (`claimPendingRewards`) delegando en `RewardService` y `ClaimStorage`.

### 13.4 Framework de Comandos (`maurxp.betterdragon.command`):
- **Comando Raíz y Alias:** `/betterdragon` (canónico) y `/bd` (alias oficial) registrados directamente en el `CommandMap` de Bukkit/Paper durante `onEnable()`.
- **`CommandRegistry`:** Registro centralizado, despachador y enrutador por nombre canónico y alias.
- **`CommandContext`:** Contexto inmutable de ejecución (`sender`, `label`, `args`, `isPlayer`, `asPlayer`, helpers de mensajes).
- **`AllowedSender` (Console Safety):** Define explícitamente si un comando admite `PLAYER_ONLY`, `CONSOLE_ONLY` o `BOTH`. La consola jamás sufre un `ClassCastException`.
- **`CommandMessages`:** Centralización visual de prefijos, separadores, cabeceras y códigos de color Minecraft sin librerías externas.
- **Tab Completion Dinámico:** Autocompletado sensible al contexto, filtrado estrictamente por los permisos de capacidad del emisor y sin realizar bloqueos de I/O ni consultas pesadas a SQLite.

### 13.5 Subcomandos Implementados en Fase 3.11:
1. `/bd help [subcomando]` (`aliases: ayuda, ?`): Ayuda general y detallada con permisos y uso.
2. `/bd leaderboard [damage|slayers|battles] [límite]` (`aliases: top, lb`): Consulta interactiva no bloqueante de rankings SQLite.
3. `/bd stats [jugador]` (`aliases: perfil, estadisticas`): Consulta de perfil propio o de terceros (con permiso `betterdragon.stats.others`).
4. `/bd status [mundo]` (`aliases: estado`): Estado en vivo de la batalla activa (fase, salud, líder de daño).
5. `/bd start [mundo] [arena] [definición]` (`aliases: spawn, iniciar`): Inicio administrativo seguro con validación de entorno `THE_END`.
6. `/bd abort [mundo|battleId]` (`aliases: cancel, cancelar, stop`): Cancelación administrativa formal con limpieza de entidades y sesión.
7. `/bd reload` (`aliases: recargar`): Recarga atómica fail-safe de `config.yml` y `arenas.yml`.
8. `/bd arena <list|info> [id]` (`aliases: arenas`): Inspección de arenas cargadas y reglas geométricas.
9. `/bd claim` (`aliases: reclamar, recompensas`): Reclamo manual de ítems pendientes en el buzón de recompensas.

### 13.6 Operaciones Excluidas y Fuera de Alcance:
- **GUIs de Inventario:** Excluidas en 3.11; la arquitectura queda 100% lista para su implementación sin tocar lógica de negocio.
- **Edición en Caliente de Arenas:** `/bd arena create/delete/edit` no fue improvisado para evitar corromper `arenas.yml` sin un motor de serialización dedicado.
- **DragonBattle / EndDragonFight:** Cero integración con el sistema vanilla; BetterDragon mantiene soberanía absoluta.
- **NMS:** Cero NMS adicional en comandos ni en la capa de aplicación.

---

## 14. Catálogo de Dragones, Atributos Nativos y Escalado por Jugadores (Fases 3.13 / 3.13-R1)

### 14.1 Componentes del Dominio:
- **`DragonCatalog` (`config`):** Catálogo inmutable de perfiles de dragón (`DragonDefinition`) cargados desde `config.yml` (sección `dragons:`). Soporta múltiples perfiles, normalización insensible a mayúsculas, y resolución segura de perfiles default vs explícitos.
- **`DragonAttributes` (`config`):** Encapsula los atributos de Paper API:
  - `Attribute.MAX_HEALTH` (Base obligatoria, por defecto 200.0 HP; disponible/observado).
  - `Attribute.MOVEMENT_SPEED` (Opcional; disponible/observado; comportamiento físico locomotor no afirmado sin prueba).
  - `Attribute.FOLLOW_RANGE` (Opcional; disponible/observado; comportamiento IA no afirmado sin prueba).
  - `Attribute.ATTACK_DAMAGE` (**No disponible nativamente** en la entidad `EnderDragon` en Paper 26.1.2-74; retorna `null`).
  - *Exclusión:* `Attribute.SCALE` rechazado formalmente debido a que las partes multipart (`EnderDragonPart`) no escalan en el motor vanilla (MC-267372).
- **`DragonScalingDefinition` & `ScalingMode` (`config`):** Modelo declarativo de escalado. En 3.13-R1 se consolida la **Semántica Opción A**:
  - `enabled: true` sin `mode` infiere automáticamente `ScalingMode.LINEAR`.
  - `enabled: false` sin `mode` infiere `ScalingMode.NONE`.
  - Se rechaza formalmente la configuración inconsistente `enabled: true` con `mode: NONE`.
  - Constantes de tuning (`health-per-player: 0.25`, `max-multiplier: 3.0`) catalogadas como `TUNING_CANDIDATE` (valores de ingeniería provisionales sujetos a calibración en pruebas de juego).
- **`DragonScalingCalculator` (`config`):** Calculador puro, determinista y sin efectos colaterales que aplica la fórmula:
  $$\text{SaludEfectiva} = \text{SaludBase} \times \min\left(\text{Cap}, \max\left(1.0, 1.0 + (N_{\text{activos}} - 1) \times \alpha\right)\right)$$
  Donde $N_{\text{activos}}$ es el recuento síncrono de jugadores vivos en `SURVIVAL`/`ADVENTURE` dentro de los límites espaciales de la arena al iniciar la batalla.
- **`EffectiveDragonStats` (`config`):** Snapshot inmutable que congela las estadísticas calculadas para la batalla en curso, impidiendo que recargas de configuración (`/bd reload`) o fluctuaciones de jugadores en combate alteren la salud o los atributos de una batalla activa.

### 14.2 Firma Persistente PDC (Contrato Formal 3.13-R1):
Todo dragón generado por BetterDragon porta síncronamente 4 claves en su `PersistentDataContainer`:
1. `betterdragon:managed` (`BOOLEAN`, true).
2. `betterdragon:battle_id` (`STRING`, UUID de la sesión).
3. `betterdragon:definition_id` (`STRING`, ID normalizado del perfil).
4. `betterdragon:schema_version` (`INTEGER`, versión soportada = 1).

**Validación de Sesión:** `DragonPdcHandler.validateDragonForSession()` comprueba tanto el UUID de entidad y el `battle_id` como la compatibilidad estricta de `definition_id` frente al snapshot de la batalla, impidiendo que entidades con perfiles distintos usurpen la sesión activa. Versiones de esquema no soportadas (`schema_version > 1` o `< 1`) son rechazadas.

### 14.3 Invocación Segura y Spawner (`DragonSpawner`):
- Eliminación total de coordenadas mágicas históricas (`0.5, 128.0, 0.5`).
- Derivación estricta desde `ArenaDefinition`: `arena.center().toLocation(world)` y podio `dragon.setPodium(arena.podium().toLocation(world))`.
- Cero silenciamiento de excepciones: eliminación de `catch (Throwable ignored)`. Toda advertencia de atributos o podio es registrada con `Level.WARNING` para auditoría y trazabilidad operacional.

---

## 15. Encounter Presentation y Soft Enrage (Fase 3.14 / 3.14-R1)

### 15.1 Arquitectura de Presentación (BossBar Propia):
- **Soberanía y Separación Estricta:** La supresión de la BossBar vanilla original permanece confinada a la capa de plataforma (`platform.bossbar` con NMS aislado). La BossBar propia de BetterDragon opera con **0% NMS** utilizando directamente la API pública de Bukkit (`org.bukkit.boss.BossBar` vía `Bukkit.createBossBar`).
- **Controlador por Sesión (`DragonBossBar`):** Cada `BattleSession` posee su propia instancia de `DragonBossBar`. Cero gestores globales o singletons de interfaz de usuario.
- **Cálculo Robusto de Progreso:** La relación de salud se computa mediante `DragonBossBar.calculateProgress(currentHealth, maxHealth)`:
  - Garantiza estrictamente un valor normalizado dentro del intervalo $[0.0, 1.0]$.
  - Filtra y neutraliza `Double.NaN`, `Double.isInfinite`, valores de salud negativos y denominadores $\le 0.0$ retornando `0.0`.
  - La salud de la entidad física real y el runtime de combate continúan siendo la única fuente de verdad (sin vida paralela artificial).
- **Semántica Explícita de Placeholder `{enrage}` (Fase 3.14-R1):**
  - Si el título configurado contiene `{enrage}`, se reemplaza por `&c[ENRAGE]` cuando la batalla está en enrage, o por cadena vacía `""` cuando no lo está.
  - Si el título NO contiene `{enrage}`, **nunca** se anexa automáticamente ninguna etiqueta de enrage.
- **Gestión Determinista de Espectadores:**
  - Sincronización en hilo principal (`syncBossBarViewers()`) evaluando a los jugadores presentes dentro de los límites espaciales de la arena (`spatialContext.isInArena(player.getLocation())`).
  - `DragonPresentationListener` asegura remoción inmediata de espectadores ante eventos de `PlayerQuitEvent`, `PlayerDeathEvent` y resincronización limpia en `PlayerTeleportEvent` y `PlayerRespawnEvent`, eliminando cualquier riesgo de barras fantasma o referencias colgantes.
- **Eliminación de `catch (Throwable ignored)` (Hardening 3.14-R1):**
  - Todas las operaciones de creación de BossBar, audio, gestión de espectadores y cleanup utilizan excepciones tipadas (`Exception`) con logging contextual (`Level.WARNING` / `Level.FINE`), sin silenciar errores graves.
- **Ciclo de Vida Integrado:**
  - En estado `ACTIVE`: barra visible, progreso y título sincronizados.
  - En estados terminales `DYING`, `COMPLETED` y `ABORTED`: invocación síncrona de `DragonBossBar.cleanup()` que ejecuta `removeAll()`, oculta la barra y purga el conjunto interno de espectadores.
  - En estado `DEFERRED_PENDING_CHUNK_LOAD`: la barra se oculta temporalmente sin expulsar el estado de sesión ni tratar la descarga de chunk como muerte del dragón.
  - En `onDisable()` del plugin: limpieza masiva de todas las BossBars activas.

### 15.2 Feedback Sensorial de Transición de Fases:
- **Evento de Dominio Desacoplado:** Reutilización directa de `BetterDragonPhaseChangeEvent` emitido por `PhaseRuntime` al detectar avance monotónico de fase.
- **Audio Real y BossBar:** `DragonPresentationListener` actualiza el título visible en la BossBar y reproduce `Sound.ENTITY_ENDER_DRAGON_GROWL` (volumen 1.0, pitch 1.0) a todos los espectadores de la batalla. **No se utiliza ni afirma `sendTitle()` en pantalla.**

### 15.3 Modificador Transversal Soft Enrage:
- **Desacoplamiento Conceptual:** Enrage **NO** es una fase de combate (`CombatPhase`); las fases definen la secuencia de progresión (ej. Phase 1 a Phase 4), mientras que Enrage es un estado modificador transversal que puede coexistir con cualquier fase (incluyendo Phase 4 + Enrage).
- **Activación Determinista y Monotónica:**
  - Se activa cuando $\text{healthRatio} \le \text{threshold}$ (por defecto $0.20$, catalogado como `TUNING_CANDIDATE`).
  - Monotonicidad estricta ($false \to true$ irreversible): una vez activado, jamás regresa a $false$ durante la misma batalla, incluso si el dragón regenera su salud al 100% mediante cristales de End.
  - Activación única: reproduce sonido temático (`Sound.ENTITY_ENDER_DRAGON_GROWL`, volumen 1.2, pitch 0.8) exactamente una vez.
- **Aceleración Efectiva de Cooldowns de Habilidades:**
  - En la Fase 3.14, el único efecto funcional de Soft Enrage consiste en modificar el cooldown efectivo de habilidades ejecutadas por `AbilityEngine`:
    $$\text{EffectiveCooldown} = \max(1, \text{round}(\text{BaseCooldown} \times \text{cooldownMultiplier}))$$
  - Interpretación de `cooldownMultiplier`: `< 1.0` (cooldown más corto), `= 1.0` (sin cambio), `> 1.0` (cooldown más largo).
  - `cooldownMultiplier` por defecto es $0.75$ (`TUNING_CANDIDATE`).
  - La definición inmutable original de la habilidad (`AbilityDefinition.cooldownTicks()`) permanece intacta sin mutaciones.
- **Aislamiento en Snapshot ante Reload:**
  - `DragonBossBarDefinition` y `DragonEnrageDefinition` están encapsulados en `DragonDefinition` y congelados en `BattleConfigurationSnapshot`.
  - La ejecución de `/betterdragon reload` no afecta a ninguna batalla activa (conservan su título, color, estilo, umbral y multiplicador originales). Las nuevas batallas sí adoptan la configuración recargada.

### 15.4 Validación Runtime (EXP-009 — 11/11 Checks PASS):
- Validado empíricamente sobre Paper 26.1.2-74 (Java 25) durante la auditoría de integración EXP-009 (11 checks verificados exitosamente con exit code 0).
- Clasificación de evidencias de atributos nativos en `EnderDragon`:
  - `Attribute.MAX_HEALTH`: Disponible y configurable (`200.0` HP base observado).
  - `Attribute.MOVEMENT_SPEED`: Disponible/observado (`0.7` base; comportamiento locomotor no afirmado sin pruebas físicas independientes: `NOT INDEPENDENTLY VERIFIED`).
  - `Attribute.FOLLOW_RANGE`: Disponible/observado (`17.77` base; comportamiento IA no afirmado sin pruebas físicas independientes: `NOT INDEPENDENTLY VERIFIED`).
  - `Attribute.ATTACK_DAMAGE`: **No disponible / null** nativamente en la entidad `EnderDragon` en Paper 26.1.2-74 (`dragon.getAttribute(Attribute.ATTACK_DAMAGE) == null`).
  - `dragon.setPodium(Location)`: API invocation smoke test ejecutado con éxito (postcondición interna no expuesta en Bukkit API: `VERIFIED BY RUNTIME SMOKE TEST`).

---

## 16. Advanced Combat Abilities & Sensory Telegraphs (Fase 3.15)

### 16.1 Telegrafiado Sensorial Declarativo e Inmutable:
- **Modelo de Dominio (`TelegraphDefinition`):**
  - Objeto inmutable (record) desacoplado del efecto físico de combate.
  - Atributos esenciales: `durationTicks` (duración previa), `particle` (`Particle`), `particleCount`, `particleRadius`, `sound` (`String` con resolución a `Sound` vía `OldEnum.valueOf` con fallback seguro), `soundVolume`, `soundPitch`.
  - Ventanas de aviso estandarizadas como `TUNING_CANDIDATE`:
    - `TICKS_MINOR = 16L` (impactos leves, ~0.8s)
    - `TICKS_MODERATE = 30L` (impactos moderados, 1.5s)
    - `TICKS_MAJOR = 40L` (impactos mayores / bombardeo, 2.0s)
    - `TICKS_LETHAL = 60L` (impactos letales, 3.0s)
- **Separación Temporal Telegrafiado vs. Efecto Físico:**
  - El telegrafiado sensorial se dispara en el tick 0 de invocación de la habilidad, emitiendo partículas y sonido en el hilo principal de Bukkit/Paper.
  - El efecto físico real (daño, knockback, proyectiles) **NO** ocurre inmediatamente; se programa mediante `DelayedTaskScheduler` para ejecutarse exactamente tras los `durationTicks` especificados.
  - Hilo principal garantizado: **0% operaciones asíncronas** manipulando mundos, entidades o jugadores.

### 16.2 Triggers No Proliferantes (Anti-Sobrearquitectura):
- En lugar de crear enums específicos por cada fase (`CIRCLING`, `LAND_ON_PORTAL`, `TAKEOFF`, etc.), se formalizan triggers genéricos:
  1. `ON_FLIGHT_PHASE`: Se dispara ante transiciones de fase de vuelo vanilla (`EnderDragonChangePhaseEvent` vía `DragonFlightListener`), filtrando la fase requerida mediante propiedades de la habilidad.
  2. `ON_DAMAGE`: Se dispara ante daño físico recibido por el dragón (`DragonCombatListener`), permitiendo contrataques condicionales según el tipo de daño (`damage-types`).

### 16.3 Efectos Declarativos Implementados:
- **Bombardeo Aéreo (`CarpetBombEffect` / `CARPET_BOMB` / CAND-03):**
  - Genera racimos de entidades `TNTPrimed` alrededor del dragón con fuse ticks configurable (default 80 ticks = 4s).
  - Firma cada entidad TNT con PDC (`betterdragon:managed`, `betterdragon:battle_id`, `betterdragon:explosive`).
- **Onda Expansiva de Aterrizaje (`ShockwaveEffect` / `SHOCKWAVE` / CAND-04):**
  - Se ejecuta en transiciones de aterrizaje (`LAND_ON_PORTAL` o perching).
  - Calcula vectores radiales tridimensionales de empuje $(\vec{v} = \text{normalize}(\Delta x, \Delta z) \times k_{h} + k_{v}\hat{j})$ y daño configurable hacia jugadores dentro del perímetro de la arena. Presentación auditiva y visual de sonic boom.
- **Invocación de Esbirros (`SummonEffect` / `SUMMON` / CAND-06):**
  - Spawnea entidades de apoyo con firma completa de 4 claves PDC (`managed`, `battle_id`, `minion`, `minion_type`).
  - Tipo de criatura configurable con fallback dinámico (default `ENDERMITE`, clasificado como `TUNING_CANDIDATE`).

### 16.4 Contrataques Reactivos con Cooldown por Atacante (`ON_DAMAGE` / CAND-05):
- `AbilityCooldownTracker.setAttackerCooldown(abilityId, attackerUuid, tick, cooldownTicks)`:
  - Mantiene tracking de cooldowns individualizado por cada atacante.
  - Si el atacante A golpea al dragón, entra en cooldown (default 100 ticks = 5s, `TUNING_CANDIDATE`); si el atacante B golpea inmediatamente después, el contrataque es elegible de forma independiente sin ser bloqueado por el estado de A.

### 16.5 Protección de Terreno Determinista (`DragonExplosionListener`):
- Intercepta `EntityExplodeEvent` con prioridad NORMAL.
- Comprueba si la entidad portadora de la explosión tiene el PDC `betterdragon:explosive = true` y `betterdragon:managed = true`.
- Si es BetterDragon:
  - Invoca `event.blockList().clear()`, neutralizando al 100% la destrucción de bloques de la isla del End.
  - Conserva íntegro el daño a jugadores y knockback físico.
- Si es una explosión externa/vanilla:
  - No realiza ninguna acción; el evento y su `blockList()` se preservan idénticos.
  - Cero flags booleanas globales en el servidor.

### 16.6 Identificación y Limpieza Determinista de Esbirros (PDC):
- Esquema de 4 claves PDC en minions:
  - `betterdragon:managed = true` (BOOLEAN)
  - `betterdragon:battle_id = <uuid>` (STRING)
  - `betterdragon:minion = true` (BOOLEAN)
  - `betterdragon:minion_type = <id>` (STRING)
- Limpieza determinista (`BattleSession.cleanSessionMinions()`):
  - Invocada en estados terminales (`abort()` y `complete()`).
  - Escanea y remueve exclusivamente las entidades cuyo PDC coincida con el `battleId` de la sesión.
  - Cero eliminación de mobs naturales o minions pertenecientes a otras batallas.

### 16.7 Ciclo de Vida y Resiliencia:
- `BattleSession.registerPendingTask(CancellableTask)`:
  - Toda tarea diferida (telegraph o delay de efecto) queda registrada en la sesión.
  - Al abortar o terminar la sesión, `cancelPendingTasks()` cancela inmediatamente todas las tareas pendientes, impidiendo la aparición de efectos o explosiones huérfanas tras el fin de la batalla.
- Preservación íntegra de `DEFERRED_PENDING_CHUNK_LOAD`: la descarga de chunk no interrumpe la sesión ni altera los estados de enrage o habilidades.

### 16.8 Aislamiento de Snapshot (Snapshot Isolation):
- Todas las habilidades, telegrafiado, cooldowns y parámetros provienen de `BattleConfigurationSnapshot`.
- La ejecución de `/betterdragon reload` congela el combate activo; las modificaciones en YAML solo afectan batallas futuras.

### 16.9 Cero NMS:
- Todo el subsistema de combate, listeners, telegrafiado y explosiones opera con **0% NMS** sobre la API de Paper 26.1.2-74 y Java 25.
