# BetterDragon — Plan de Implementación Vivo (Implementation)

**Plugin:** BetterDragon  
**Autor:** maurxp  
**Estado:** [ACTIVO / ROADMAP INCREMENTAL]  

---

## 1. Principio de Desarrollo

El desarrollo avanza exclusivamente por subfases incrementales. Cada subfase produce código compilable en Java 25, probado en Paper 26.1.2-74 y documentado antes de pasar a la siguiente.

---

## 2. Hoja de Ruta Incremental (Roadmap)

| Fase / Subfase | Componente | Descripción | Estado |
| :---: | :--- | :--- | :---: |
| **3.0** | **Bootstrap Base** | Maven, Java 25, Paper API 26.1.2-74, BetterDragonPlugin, VanillaBossBarController (NMS aislado). | `DONE` |
| **3.0-R1** | **Corrección Bootstrap & Docs** | Simplificación radical a `BetterDragon/docs/`, corrección `api-version: '26.1.2'`, auditoría de POM. | `DONE` |
| **3.1** | **Modelos de Dominio e Invariantes** | `BattleId`, `BattleState`, `DragonIdentity`, `BattleResult`, `BattleSession`, `BattleSessionManager`, `BetterDragonKeys`. | `DONE` |
| **3.2** | **Configuración y Snapshots** | Deserialización tipada de `config.yml`, validación de integridad y congelamiento por batalla. | `DONE` |
| **3.3** | **Dragon Lifecycle e Identidad** | Paper API spawning, firma PDC (`managed`, `battle_id`), DragonIdentity, BattleManager, EntityDeathEvent (0 XP vanilla), semántica de chunks y separación de DragonBattle. | `DONE` |
| **3.3-R1** | **Corrección Quirúrgica Lifecycle** | Eliminación de claves PDC innecesarias (`definition_id`, `schema_version`), resolución estricta de entidad en `DEFERRED_PENDING_CHUNK_LOAD` y restauración de estado lógico previo. | `DONE` |
| **3.4** | **Combat Runtime** | Registro de participantes, tracking de daño en hilo principal, hitSequence monotónico, historicalName vs lastKnownName, TOP_DAMAGE con desempate determinista, evento BetterDragonDamageEvent, snapshots inmutables. | `DONE` |
| **3.5** | **Motor de Habilidades y Fases** | Fases ordenadas, progresión monotónica por ratio de salud, AbilityEngine, TargetSelector, LocationResolver, efectos de combate (0% NMS), snapshots tipados, correcciones R1. | `COMPLETE` |
| **3.6** | **Arena, Reglas y Límites** | Límites geométricos de arena, reglas anti-cheese y confinamiento espacial. | `TODO` |
| **3.7** | **Muerte, Victoria y BattleResult** | Transición terminal a COMPLETED, consolidación de BattleResult con CombatSnapshot final. | `TODO` |
| **3.8** | **Recompensas y Claims** | Cálculo de botín, redistribución proporcional de no elegibles y buzón de claims en SQLite. | `TODO` |
| **3.9** | **Persistencia SQLite** | Single-Writer Async Worker, migración de esquema y almacenamiento no bloqueante. | `TODO` |
| **3.10**| **Sistema de Leaderboard** | Agregación de estadísticas históricas (Top Slayers, Mayor Daño, Total Batallas) con caché en memoria. | `TODO` |
| **3.11**| **Framework de Comandos & GUI** | Implementación de `/betterdragon` y `/bd` (`spawn`, `cancel`, `status`, `reload`, `top`, `claim`). | `TODO` |
| **3.12**| **Hardening Final y Cierre** | Pruebas de estrés, auditoría final de rendimiento y release candidate. | `TODO` |

---

## 3. Estado de la Fase 3.4 (Combat Runtime) — `DONE`

- **Clases del Núcleo de Combate (`maurxp.betterdragon.combat`):**
  - `CombatRuntime`: Motor de tracking de combate confinado al hilo principal, encapsulado por `BattleSession`.
  - `ParticipantCombatState`: Contenedor mutable interno ($O(N)$ participantes) con preservación inmutable de `historicalName` y actualización de `lastKnownName`.
  - `ParticipantSnapshot`: DTO/Record inmutable para consumo público seguro.
  - `CombatSnapshot`: Instantánea inmutable global de la batalla.
  - `DamageEventDispatcher`: Interfaz funcional para despacho desacoplado de eventos.
  - `BetterDragonDamageEvent`: Evento cancelable de Bukkit emitido previo a la confirmación de daño.
  - `DragonCombatListener`: Listener de Bukkit (`EntityDamageByEntityEvent`, HIGH, `ignoreCancelled = true`) con resolución de subpartes (`ComplexEntityPart`/`EnderDragonPart`) y proyectiles.
- **Integración con `BattleSession`:**
  - Instancia dedicada `CombatRuntime` por cada sesión (cero singletons).
  - Compuerta de estado: daño aceptado exclusivamente en estado `ACTIVE`.
- **Pruebas Unitarias:**
  - `CombatRuntimeTest`: 28 pruebas unitarias exhaustivas cubriendo todos los casos de borde (daño inválido, secuencias monotónicas, desempates deterministas, aislamiento, inmutabilidad).
  - `DragonCombatListenerTest`: 7 pruebas unitarias probando resolución de entidades y proyectiles.
  - **Total de pruebas unitarias:** 90 tests ejecutados, 0 fallos, 0 errores.
- **Validación Runtime (Paper 26.1.2-74):**
  - Suite de integración automatizada ejecutada con comando `bd-test-combat`.
  - 13/13 checks físicos verificados en el servidor headless, concluyendo con apagado limpio exitoso (`exit code 0`).

---

## 4. Estado de la Fase 3.5 (Combat Phases & Abilities) — `COMPLETE` (Auditoría R1 Aplicada)

- **Modelos y Runtime de Fases (`maurxp.betterdragon.phase`):**
  - `PhaseDefinition`: Record inmutable con orden, umbral de salud relativo `healthRatioThreshold` y catálogo de identificadores de habilidades.
  - `PhaseRuntime`: Gestor encapsulado en `BattleSession` que mantiene la progresión monotónica unidireccional y el salto determinista de umbrales.
  - `BetterDragonPhaseChangeEvent`: Evento Bukkit informativo y no cancelable.
  - `PhaseChangeEventDispatcher`: Interfaz funcional para despacho desacoplado testeable.
- **Motor de Habilidades (`maurxp.betterdragon.ability`):**
  - `AbilityDefinition`: Definición declarativa tipada inmutable con trigger, cooldown en ticks lógicos, selector, origen y tipo de efecto.
  - `AbilityTrigger`: `ON_PHASE_ENTER`, `PERIODIC`.
  - `TargetSelectorType` y `TargetSelector`: Selección determinista de jugadores válidos en arena (`ALL_IN_ARENA`, `RANDOM_PLAYER`, `RANDOM_SUBSET`, `NEAREST_PLAYER`, `DAMAGER`, `TRIGGERING_PLAYER`).
  - `EffectOriginType`, `BattleSpatialContext` y `LocationResolver`: Resolución 0% NMS de orígenes espaciales con fallbacks seguros (`DRAGON_HEAD`, `DRAGON_BODY`, `TARGET_FEET`, `PODIUM_CENTER`, `ARENA_CENTER`, `TRIGGER_LOCATION`), separando formalmente el podium de la arena de combate.
  - `AbilityEffect` y Efectos Canónicos: `DamageEffect` (aislado de CombatRuntime), `KnockbackEffect`, `ParticleEffect`, `SoundEffect`.
  - `AbilityCooldownTracker`: Seguimiento estricto en ticks de servidor, con reset al cambiar de fase.
  - `AbilityExecutionContext`: Contexto inmutable y efímero suministrado a los efectos.
  - `AbilityEngine`: Coordinador resiliente con resolución espacial `Origin -> Target`, validación estricta de triggers de habilidad y captura segura de `Exception` (sin silenciar errores críticos de la JVM).
- **Configuración y Snapshots (`maurxp.betterdragon.config`):**
  - `DragonDefinition`: Modelo inmutable con validación de monotonicidad de thresholds e integridad referencial de habilidades.
  - `BattleConfigurationSnapshot`: Congela `DragonDefinition` aislando la batalla de recargas YAML.
  - `ConfigurationLoader`: Deserialización tipada estricta y fail-safe de secciones `dragons` y `abilities`, admitiendo catálogos vacíos sin imponer moveset inventado.
  - `config.yml`: Configuración base limpia sin ataques inventados (`abilities: {}`, fases con `abilities: []`).
- **Pruebas Unitarias:**
  - 52 pruebas unitarias de Fases y Habilidades (142 pruebas unitarias en total en el proyecto, 0 fallos, 0 errores).
- **Validación Runtime (Paper 26.1.2-74):**
  - Suite de integración automatizada ejecutada con comando `bd-test-phases`.
  - 10/10 checks de runtime verificados en el servidor headless, concluyendo con apagado limpio exitoso (`exit code 0`).
