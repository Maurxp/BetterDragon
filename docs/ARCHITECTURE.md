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
- *Nota de diseño (Fase 3.3-R1):* Las claves `betterdragon:definition_id` y `betterdragon:schema_version` se reservan para fases posteriores y **no** forman parte de la firma mínima escrita durante el spawn. `DragonPdcHandler.extractIdentity` aplica defaults transparentes (`"default"`, `1`) cuando están ausentes.

### 5.3 Coordinación del Ciclo de Vida (`BattleManager`)
- **Secuencia Estricta PREPARING -> ACTIVE:**
  1. Validación de dimensión `THE_END` y no duplicidad en el mundo (`hasActiveSession`).
  2. Generación de `BattleId` y congelamiento de snapshot (`BattleConfigurationSnapshot`).
  3. Creación y registro de `BattleSession` en estado `PREPARING`.
  4. Spawn físico de la entidad con marcado PDC atómico (solo `managed` y `battle_id`).
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

### 10.8 Límites de Almacenamiento en Memoria y Frontera con Fase 3.9
- **`InMemoryClaimStorage` (Fase 3.8 / 3.8-R1):**
  - Almacenamiento concurrente seguro (`ConcurrentHashMap`) limitado al ciclo de vida del proceso de la JVM.
  - **Límites Documentados:** Los reclamos resguardados en memoria **NO** son persistentes ante reinicios del servidor, detenciones o recargas del plugin (`crash/restart`).
- **Frontera Estricta con Fase 3.9:**
  - La persistencia duradera en base de datos relacional SQLite (`betterdragon.db`), la recuperación de claims pendientes al encender el servidor y los comandos de usuario final (`/bd claim`) forman parte exclusiva de la **Fase 3.9**. La interfaz `ClaimStorage` garantiza que este paso se dará sin tocar el motor de recompensas.
