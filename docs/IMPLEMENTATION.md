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
| **3.6** | **Arena, Reglas y Límites** | Límites geométricos de arena, reglas anti-cheese, separación podium/centro, snapshot inmutable y correcciones R1. | `COMPLETE` |
| **3.7** | **Muerte, Victoria y BattleResult** | Transición terminal a COMPLETED, consolidación de BattleResult con CombatSnapshot final, Slayer TOP_DAMAGE, evento de victoria. | `COMPLETE` |
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

---

## 5. Estado de la Fase 3.6 (Arena & Rules) — `COMPLETE` (Auditoría R1 Aplicada)

- **Modelos de Dominio de Arena (`maurxp.betterdragon.arena`):**
  - `Vector3d`: Coordenadas finitas $x, y, z$ puras independientes de Bukkit.
  - `ArenaBounds`: Bounding box axis-aligned con invariante $\min \le \max$, consultas $O(1)$ `contains(x,y,z)` y `contains(Location)`.
  - `ArenaRuleSet`: Reglas de arena tipadas e inmutables (`waterAllowed`, `boundaryEnabled`, `antiTunnelEnabled`).
  - `ArenaDefinition`: Representación canónica de arena que valida que centro y podio se ubiquen dentro de los límites.
  - `ArenaRuleEvaluator`: Evaluador desacoplado de reglas de arena.
- **Contexto Espacial y Selectores (`maurxp.betterdragon.ability`):**
  - `ArenaBattleSpatialContext`: Resuelve `ARENA_CENTER` y `PODIUM_CENTER` directamente desde la `ArenaDefinition` congelada, eliminando todo fallback hardcoded `(0, 100, 0)`.
  - `TargetSelector.ALL_IN_ARENA`: Filtra jugadores con los límites reales `ArenaBounds` del contexto espacial mediante `spatialContext.isInArena(player.getLocation())`.
- **Configuración y Snapshots (`maurxp.betterdragon.config`):**
  - `arenas.yml`: Archivo de configuración oficial con arena `default` en `world_the_end`.
  - `ArenaConfigurationLoader`: Carga tipada estricta y validación de límites, coordenadas y reglas.
  - `ArenaConfigurationSnapshot`: Snapshot inmutable global de arenas.
  - `BattleConfigurationSnapshot`: Congela la `ArenaDefinition` específica de la batalla; la sesión es inmune a recargas `/bd reload`.
  - `ConfigurationService`: Recarga atómica fail-safe coordinada de `config.yml` y `arenas.yml`.
- **Integración con Ciclo de Vida (`maurxp.betterdragon.battle`):**
  - `BattleSession`: Almacena el `ArenaBattleSpatialContext` inmutable y expone `getArena()`, `getArenaId()`, `getSpatialContext()`.
  - `BattleManager`: Valida disponibilidad de la arena y resolución de mundo antes de iniciar batallas.
- **Correcciones Quirúrgicas de Auditoría R1 (Fase 3.6-R1):**
  - *Eliminación de Fallbacks Espaciales Hardcoded:* Se eliminó definitivamente `DefaultBattleSpatialContext` y sus coordenadas provisionales `(0, 100, 0)` y `(0, 65, 0)`. Se eliminó `DEFAULT_ARENA_RADIUS = 150.0` de `TargetSelector`. `LocationResolver` y `TargetSelector` requieren un `BattleSpatialContext` no nulo.
  - *Sincronización Estricta de Arena ID:* `BattleManager.startBattle()` resuelve prioritariamente la `ArenaDefinition` asociada al mundo y congela el snapshot directamente con `arena.id()`, garantizando consistencia absoluta entre `session.getArenaId()` y `session.getArena().id()`.
  - *Fuente Canónica Única de Agua:* Representación canónica en `rules: water_allowed: false`. Se eliminó la duplicación y se detectan/rechazan contradicciones de configuración con `water_denial`.
  - *Cero Defaults de Gameplay Inventados:* `ArenaConfigurationLoader` requiere explícitamente la sección `rules` y sus campos sin asumir valores de gameplay arbitrarios en código.
  - *Evaluación Multidimensional en `isInArena`:* `ArenaBattleSpatialContext.isInArena(Location)` valida estrictamente la igualdad del nombre del mundo (`location.getWorld().getName() == arena.worldName()`) previo a la comprobación de límites ortogonales en `bounds`.
- **Pruebas Unitarias:**
  - 36 pruebas de Arena & Rules (178 pruebas unitarias/integración en total en el proyecto, 0 fallos, 0 errores).
- **Validación Runtime (Paper 26.1.2-74):**
  - Suite de integración de arenas ejecutada con comando `bd-test-arena`: 8/8 checks verificados con apagado limpio exitoso (`exit code 0`).
  - Suite de regresión de fases y habilidades ejecutada con comando `bd-test-phases`: 10/10 checks verificados con apagado limpio exitoso (`exit code 0`).

---

## 6. Estado de la Fase 3.7 y 3.7-R1 (Death / Victory) — `COMPLETE`

- **Modelos y Eventos de Victoria (`maurxp.betterdragon.battle` y `maurxp.betterdragon.battle.event`):**
  - `BetterDragonVictoryEvent`: Evento Bukkit público e informativo (no cancelable) emitido exclusivamente tras alcanzar el estado final `COMPLETED`.
  - `VictoryEventDispatcher`: Interfaz funcional que desacopla el despacho de eventos de Bukkit para pruebas unitarias puras.
  - `BattleResult`: Porta `CombatSnapshot` inmutable (con lista completa de `ParticipantSnapshot`, secuencias y daño acumulado) y constructores `completed(...)` que aseguran inmutabilidad y thread-safety sin retener objetos Bukkit mutables (`Player`, `Entity`, `World`).
- **Correcciones Quirúrgicas de Auditoría R1 (Fase 3.7-R1):**
  - *Encapsulación Estricta de Runtime:* Se removió `BattleSession session` y el método `getSession()` de `BetterDragonVictoryEvent`. El evento expone exclusivamente datos inmutables y seguros de dominio (`BattleId`, `BattleResult`, `worldName`), evitando la filtración de runtime mutable hacia la API pública.
  - *Criterio Estricto del Slayer (2 Criterios):* Se eliminó el tercer criterio de desempate por UUID lexicográfico en `CombatRuntime.TOP_DAMAGE_COMPARATOR`. El comparator evalúa única y exclusivamente:
    1. Mayor daño acumulado (`totalDamage DESC`).
    2. Menor secuencia de impacto inicial (`firstHitSequence ASC`).
  - *Control Soberano sobre Recompensas Vanilla:*
    - Al morir un dragón administrado, se suprimen explícitamente los drops vanilla (`deathEvent.getDrops().clear()`) y la experiencia (`deathEvent.setDroppedExp(0)`) para que no interfieran con el futuro sistema de Rewards de BetterDragon.
    - Dragones vanilla (no administrados) permanecen 100% inalterados: ni su experiencia ni sus drops son suprimidos, y no disparan lógica de BetterDragon.
  - *Independencia de Dragon Egg y Primera Victoria:*
    - BetterDragon no consulta `DragonBattle` ni `EnderDragonFight` (`hasBeenPreviouslyKilled()`, `dragonKilled`).
    - En esta fase no se spawnea manualmente ningún Dragon Egg ni se altera el estado del mundo; el ciclo de vida del huevo y el concepto de primera victoria quedan reservados al sistema propio de BetterDragon en fases posteriores (Rewards, Persistence, Portal).
- **Pruebas Unitarias:**
  - `BattleVictoryTest`: 20 pruebas unitarias exhaustivas que cubren detección de PDC, no-ops de dragones no administrados, progresión de estados, idempotencia de muerte, Slayer `TOP_DAMAGE` con 2 criterios sin desempate por UUID, encapsulación del evento de victoria, supresión de XP/drops en dragones administrados, no intervención en dragones vanilla, independencia de `DragonBattle` y preservación de participantes offline.
  - **Total de pruebas unitarias:** 198 pruebas ejecutadas en Maven, 0 fallos, 0 errores, 0 omitidos.
- **Validación Runtime (Paper 26.1.2-74):**
  - Suite de integración de victoria ejecutada con script headless `run_victory_suite.py` invocando `bd-test-victory`:
    - Spawn de BetterDragon y verificación de PDC (Check 1 & 2).
    - Registro de daño multidimensional con 3 participantes (Check 3).
    - Muerte física real mediante daño letal / `EntityDeathEvent`.
    - Supresión de 12,000 XP y drops vanilla.
    - Emisión exitosa de `BetterDragonVictoryEvent` encapsulado (Check 5).
    - Transición final a `BattleSession = COMPLETED` (Check 6).
    - Consistencia y estructura de `BattleResult` y `CombatSnapshot` (Check 7).
    - Verificación del Slayer `TOP_DAMAGE` (PlayerTwo_Slayer con 250.0 daño) (Check 8).
    - Verificación de idempotencia ante segunda invocación (Check 9).
    - Salida limpia del servidor con exit code 0.
  - Suites de regresión completas ejecutadas exitosamente:
    - `run_phases_suite.py`: 10/10 checks pasados (exit code 0).
    - `run_arena_suite.py`: 8/8 checks pasados (exit code 0).
