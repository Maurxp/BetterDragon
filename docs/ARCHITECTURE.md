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
1. Mayor `totalDamage` acumulado.
2. Desempate primario determinista: menor `firstHitSequence` (quien aportó primero al combate).
3. Desempate secundario definitivo: orden lexicográfico del `UUID.toString()`.
Garantiza un resultado determinista idéntico e independiente del orden de iteración de `HashMap`.

### 6.5 Deuda Técnica de Identidad (Registrada de 3.3-R1)
> 3.3-R1 mantiene `definitionId` y `schemaVersion` fuera del PDC efectivo. `DragonIdentity` todavía contiene defaults heredados de la arquitectura inicial (`"default"`, `1`). Esta deuda se resolverá cuando `DragonDefinition` y `schemaVersion` sean conceptos reales del dominio en fases posteriores. Combat Runtime depende exclusivamente de `BattleId`, `UUID`, sesión y datos de participación.



