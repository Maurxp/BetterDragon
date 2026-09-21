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
| **3.8–3.8-R2** | **Recompensas y Claims** | Hardening final: validación estricta de amount (enteros positivos exactos), Material nativo Paper API (sin heurísticos), Javadocs de idempotencia canónica, 245 tests unitarios. | `COMPLETE` |
| **3.9** | **Persistencia SQLite** | Single-Writer Async Worker, almacenamiento durable de claims en SQLite (`betterdragon.db`), versionado v1, restart recovery, no-blocking async, 260 tests. | `COMPLETE` |
| **3.10**| **Sistema de Leaderboard** | Agregación de estadísticas históricas (Top Slayers, Mayor Daño, Total Batallas) con caché en memoria. | `TODO (Siguiente Fase)` |
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

---

## 7. Estado de la Fase 3.8, 3.8-R1 & 3.8-R2 (Rewards — Hardening Final & Cierre) — `COMPLETE`

- **Modelos de Dominio Puros (`maurxp.betterdragon.reward.model`):**
  - `RewardItem`: Representación inmutable 0% Bukkit del ítem de botín (`material`, `amount`, `displayName`, `lore`).
  - `RewardSource`: Enumeración tipada (`PARTICIPATION`, `SLAYER`).
  - `RewardAllocation`: Cuota individual inmutable calculada para un participante de la batalla, enriquecida con `rewardId`.
  - `RewardAllocationPlan`: Plan global inmutable de asignaciones con conteo de participantes y daño total.
  - `ClaimStatus`: Estados de ciclo de vida (`PENDING`, `CLAIMED`, `FAILED_RETRYABLE`).
  - `RewardClaim`: Registro inmutable de reclamo con tracking atómico de `originalAmount`, `deliveredAmount`, marcas temporales y motivo de fallo.
- **Identidad de Recompensa y Prevención de Colisiones (Auditoría 3.8-R1 / 3.8-R2):**
  - `RewardItemDefinition`: Campo obligatorio `id` validado por regex `^[a-zA-Z0-9_-]+$`.
  - `ConfigurationLoader`: Detección fail-fast de duplicados de `id` entre pool y slayer rewards.
  - `RewardAllocation.idempotencyKey()`: Fórmula canónica actualizada a `battleId:participantId:rewardId`, garantizando que definiciones distintas con igual material no colisionen y produzcan reclamos independientes.
  - **Saneamiento de Javadocs (3.8-R2):** Erradicación completa de referencias obsoletas que documentaban la clave como `battleId:playerId:source:material`.
- **Endurecimiento de Validación de Cantidad (`amount` — Fase 3.8-R2):**
  - `ConfigurationLoader.parseExactPositiveAmount`: Exige enteros positivos exactos mayores a 0 (`Integer`, `Long`, `Short`, `Byte`, `Double`, `Float`, `Number`).
  - Rechaza sin truncamiento ni redondeo silencioso valores fraccionarios/decimales (`1.7`, `1.5`, `2.5`), cero (`0`), negativos (`-1`), no finitos (`NaN`, `Infinity`) y overflow (`> Integer.MAX_VALUE`).
  - Acepta representaciones numéricas que equivalen exactamente a enteros positivos (como `2.0` entregado como Double por YAML).
  - Los mensajes de excepción identifican de manera unívoca la ruta, el identificador `rewardId` y el campo `'amount'`.
- **Validación Nativa de Material Paper API (Fase 3.8-R2):**
  - Validación directa mediante `Material.matchMaterial(material)` de Paper/Bukkit.
  - Eliminación total de heurísticos regex/palabras prohibidas (`isValidMaterialFallback`), impidiendo que nombres inexistentes con formato plausible (`FOO_BAR`, `FAKE_MATERIAL`, `INVALID_MATERIAL`) sean considerados válidos.
  - Rechazo explícito de tipos de aire (`AIR`, `CAVE_AIR`, `VOID_AIR`) mediante comparación de enums desacoplada de la inicialización de registros legacy de Paper en entornos de test.
- **Defaults Técnicos Neutros:**
  - Eliminación de defaults inventados de gameplay (ni diamantes ni netherite en código de producción).
  - `RewardConfigurationSnapshot.defaults()`: `enabled = false`, `min_participation_percent = 0.0`, listas vacías.
  - `config.yml`: `rewards.enabled: false`, `min_participation_percent: 0.0`, con ejemplos comentados como documentación sintáctica.
- **Motor de Asignación Funcional Puro (`maurxp.betterdragon.reward.allocation`):**
  - `RewardAllocationEngine`: Componente 100% desacoplado de Bukkit que calcula elegibilidad ($\ge \text{min\_participation\_percent}$ y daño $> 0$), redistribución proporcional de remanentes de no elegibles y redondeo determinista de unidades enteras con desempate por `firstHitSequence ASC`.
- **Capa de Entrega y Buzón de Reclamos (`maurxp.betterdragon.reward.delivery` y `maurxp.betterdragon.reward.claim`):**
  - `PlayerInventoryAdapter`: Interfaz para desacoplar la entrega física del motor de dominio.
  - `BukkitPlayerInventoryAdapter`: Implementación de Paper con `inventory.addItem()` y cómputo de *leftovers*.
  - `RewardDeliveryService`: Orquestador de entregas que gestiona estados online/offline, saturación de inventario y reintentos.
  - `ClaimStorage`: Abstracción de almacenamiento para resguardo de recompensas no entregadas.
  - `InMemoryClaimStorage`: Implementación concurrente en memoria para la Fase 3.8/3.8-R1. Límites formalmente documentados: el almacenamiento es volátil durante el ciclo de vida del proceso de la JVM (no durable ante caídas o reinicios).
- **Servicios y Eventos Públicos (`maurxp.betterdragon.reward.service` y `maurxp.betterdragon.reward.event`):**
  - `BetterDragonRewardEvent`: Evento informativo de Bukkit que expone el plan y los resultados de entrega inmutables.
  - `RewardEventDispatcher`: Despachador funcional para pruebas desacopladas.
  - `RewardService`: Coordinador central tras la victoria con soporte para `processVictory` y `retryPendingClaims`.
  - `DragonRewardListener`: Listener de Bukkit (`BetterDragonVictoryEvent`, `PlayerJoinEvent`, MONITOR) para entrega automática y reintentos en reconexión.
- **Pruebas Unitarias de Recompensas (47 pruebas exhaustivas):**
  - `RewardEligibilityAndAllocationTest` (13 pruebas): casos límite de 1 jugador, múltiples, todos, ninguno, daño 0, min 0%, min 100%, valores extremos, empates, Slayer con desempate y compuertas de elegibilidad.
  - `RewardDeliveryAndClaimTest` (6 pruebas): online, offline, inventario lleno, entrega parcial, reintento tras liberar espacio y captura de fallos transitorios.
  - `RewardIdempotencyTest` (4 pruebas): estabilidad de claves canónicas, prevención de colisiones para igual material con distinto `rewardId`, no duplicación ante doble entrega y doble llamada a `processVictory`.
  - `RewardConfigurationLoaderTest` (21 pruebas): parseo de YAML, valores por defecto neutrales, rechazo de IDs duplicados o faltantes, rechazo de decimales (`1.7`, `1.5`, `2.5`), aceptación de enteros dobles (`2.0`), rechazo de no positivos y desbordamiento, rechazo de materiales inexistentes (`FOO_BAR`, `FAKE_MATERIAL`, `INVALID_MATERIAL`), rechazo de aire, aceptación de materiales reales (`NETHERITE_SWORD`, `GOLDEN_APPLE`, `ENDER_PEARL`).
  - `RewardIntegrationTest` (3 pruebas): flujo completo desde evento de victoria hasta resguardo en claims y reconexión.
  - **Total de pruebas unitarias del proyecto:** 245 pruebas ejecutadas en Maven, 0 fallos, 0 errores, 0 omitidos.

---

## 8. Estado de la Fase 3.9 (Persistencia / Claims Durables SQLite) — `COMPLETE`

- **Infraestructura de Base de Datos y Dependencias (`maurxp.betterdragon.persistence`):**
  - Driver `org.xerial:sqlite-jdbc:3.44.1.0` incluido en `pom.xml` e integrado en el JAR final mediante `maven-shade-plugin:3.6.0`, excluyendo binarios de Paper API y firmas de seguridad.
  - `DatabaseManager`: Administrador centralizado de la conexión SQLite ubicado en `plugins/BetterDragon/data/betterdragon.db` (`JavaPlugin#getDataFolder()`). Configura `PRAGMA foreign_keys = ON;` y `PRAGMA busy_timeout = 5000;`.
  - **Single-Writer Async Worker:** Ejecutor monohilo dedicado `persistenceExecutor` ("BetterDragon-Persistence") que serializa todas las escrituras y lecturas de persistencia fuera del hilo principal de Bukkit, garantizando cero bloqueos a los 20 TPS del servidor.
  - `SchemaInitializer`: Inicializador DDL idempotente. Crea la tabla de metadatos `bd_schema_metadata` (`schema_version = 1`) y la tabla `bd_reward_claims` con índices optimizados por `status`, `participant_uuid` y `battle_id`.
  - **Rechazo Fail-Safe:** Rechazo inmediato con `IllegalStateException` ante bases de datos con `schema_version > 1`, previniendo corrupción accidental por downgrades.
- **Evolución Asíncrona de Claims (`maurxp.betterdragon.reward.claim`):**
  - `ClaimStorage`: Interfaz evolucionada a un contrato no bloqueante basado en `CompletableFuture<T>`.
  - `SQLiteClaimStorage`: Implementación relacional en SQLite. Persiste claims mapeados a columnas primitivas, garantizando idempotencia estricta mediante `INSERT INTO bd_reward_claims ... ON CONFLICT(idempotency_key) DO UPDATE SET ...`.
  - Validación de integridad de material contra `Material.matchMaterial(...)` al deserializar filas de SQLite, evitando caídas ante cambios de versiones de Minecraft y previniendo falsos positivos de entrega completa.
  - `InMemoryClaimStorage`: Actualizado a `CompletableFuture.completedFuture(...)`, preservado exclusivamente como arnés de pruebas unitarias.
- **Desacoplamiento de Entrega y Reconexión (`maurxp.betterdragon.reward.delivery` y `service`):**
  - `MainThreadDispatcher`: Abstracción funcional para delegar la entrega física de ítems en el hilo principal de Bukkit sin mezclar dependencias en los servicios asíncronos.
  - `RewardDeliveryService`: Entrega no bloqueante retornando `CompletableFuture<DeliveryBatchResult>`, y reintentos vía `CompletableFuture<Integer>`.
  - `DragonRewardListener`: Al recibir `PlayerJoinEvent`, consulta de forma asíncrona los claims pendientes del jugador y orquesta su entrega en el hilo principal sin bloquear el inicio de sesión.
  - `BetterDragonPlugin`: Inicialización en `onEnable()` de `DatabaseManager`, validación de esquema, y cierre ordenado de conexiones y ejecutores en `onDisable()`.
- **Pruebas de Persistencia SQLite (15 pruebas exhaustivas en `SQLiteClaimStorageTest`):**
  - Creación de esquema y metadatos de versión (v1).
  - Verificación de tablas e índices requeridos (`idx_reward_claims_status`, `idx_reward_claims_participant`, `idx_reward_claims_battle`).
  - Inserción y recuperación de claims con todos sus campos primitivos e inmutables.
  - Idempotencia estricta ante doble inserción de la misma clave canónica `idempotency_key`.
  - Persistencia y coexistencia de estados `PENDING`, `CLAIMED` y `FAILED_RETRYABLE`.
  - Entrega parcial y persistencia exacta del remanente `remaining_amount`.
  - **Prueba de Recovery Real:** Ciclo completo de escritura -> cierre de base de datos -> reapertura de nueva instancia -> verificación de supervivencia de `PENDING` y `FAILED_RETRYABLE`, y exclusión de `CLAIMED` en consultas pendientes.
  - Aislamiento estricto por UUID de jugador, Battle ID y Reward ID.
  - Manejo seguro de materiales inválidos o desconocidos (prevención de `CLAIMED` silencioso).
  - Escrituras concurrentes masivas sobre la misma clave sin duplicación de registros.
  - Integración completa con `RewardDeliveryService` sobre SQLite real.
  - Rechazo fail-safe de versiones futuras de esquema (`version 99`).
  - Captura controlada de excepciones al operar sobre almacenamiento cerrado.
  - **Total de pruebas del proyecto:** 260 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidos.

---

## 9. Próxima Fase: Fase 3.10 — Sistema de Leaderboard `[PENDIENTE]`

- **Objetivo Arquitectónico:** Implementar agregación de estadísticas históricas de batallas (Top Slayers, Mayor Daño, Total Batallas Ganadas) respaldadas en SQLite con caché en memoria para consultas eficientes de alta velocidad.
