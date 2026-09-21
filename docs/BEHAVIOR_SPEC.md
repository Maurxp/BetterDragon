# BetterDragon — Especificación de Comportamiento (Behavior Spec)

**Plugin:** BetterDragon  
**Autor:** maurxp  
**Estado:** [VIVO / CONSOLIDADO]  
**Versión:** 1.0.0  

---

## 1. Propósito y Alcance MVP

BetterDragon es un plugin para Paper que toma la **soberanía exclusiva del ciclo de vida y combate del Ender Dragon** en dimensiones del End. Sustituye la lógica vanilla de `EnderDragonFight` por batallas controladas, atribución justa de daño, recompensas proporcionales y persistencia relacional.

### Decisiones Congeladas del MVP `[CONGELADO]`
- **Slayer:** Otorgado estrictamente al participante con mayor daño acumulado (`TOP_DAMAGE`). No se premia el último golpe (*last hit*).
- **Redistribución Proporcional:** Las porciones de botín de participantes no elegibles se redistribuyen proporcionalmente entre los participantes elegibles según su daño relativo.
- **Respawn tras Downtime:** El sistema entra en `ARMED_WAITING_PLAYER` y espera a que un jugador ingrese a la dimensión del End antes de invocar al dragón.
- **Portal Central (`portal.enabled`):** Si `portal.enabled: false`, BetterDragon **no crea, no modifica, no restaura y no administra** el portal de salida de bedrock vanilla.
- **BossBar Vanilla:** Neutralizada de forma determinista mediante el adaptador NMS aislado (`VanillaBossBarController`). El método `restoreVanillaBossBar()` fue eliminado permanentemente.
- **Leaderboard:** Incluido en el MVP y respaldado por SQLite. La interfaz de consulta mediante comandos fue implementada en la Fase 3.11.
- **Estatuas / NPCs:** Fuera del alcance del MVP.
- **Comandos:** `/betterdragon` (canónico) y `/bd` (alias oficial).
- **Core 0% NMS:** Sin dependencias de internals de Minecraft fuera de `platform.bossbar`.

---

## 2. Ciclo de Vida de una Batalla (Battle Lifecycle)

La batalla opera como una Máquina de Estados Finitos (FSM) confinada al hilo principal:

```
[IDLE]
   │
   ▼ (startBattle / Invocación)
[PREPARING] ── (Snapshot congelado, spawn Paper API, firmado PDC atómico)
   │
   ├─► [ABORTED] (Fallo de spawn o verificación PDC fallida: SPAWN_FAILED)
   │
   ▼ (Validación exitosa de entidad física y PDC)
[ACTIVE] ◄──────┐ (resolveDeferredDragon: entidad válida, restaura ACTIVE)
   │            │
   ├─► [DEFERRED_PENDING_CHUNK_LOAD] (Descarga de chunk / Cause.UNLOAD)
   │            │
   │            └─► [DYING] (resolveDeferredDragon: dragón en resolución, restaura DYING)
   │
   ├─► [ABORTED] (Pérdida inesperada de entidad o dragón no resuelto: ENTITY_MISSING)
   │
    ▼ (Muerte física real / EntityDeathEvent: 0 XP vanilla, drops suprimidos)
[DYING]
    │
    ▼ (Consolidación de victoria en 3.7: Slayer TOP_DAMAGE, BattleResult, BetterDragonVictoryEvent)
[COMPLETED]
```

### Reglas de Estado y Transición (Fase 3.3-R1):
- **PREPARING:** La sesión existe y está registrada en memoria (`BattleSessionManager`), pero no pasa a `ACTIVE` hasta que la entidad física ha sido creada en el mundo y su PDC (`betterdragon:managed=true`, `betterdragon:battle_id`) ha sido extraído y verificado.
- **ACTIVE:** El dragón está físicamente presente en el End y el combate está en curso.
- **DEFERRED_PENDING_CHUNK_LOAD:** Si el chunk del dragón es descargado (`EntityRemoveEvent.Cause.UNLOAD`), la batalla se pausa en este estado preservando el estado previo (`stateBeforeChunkDeferral`), `BattleId` y `DragonIdentity`. Al recargarse entidades (`EntitiesLoadEvent`), se ejecuta `resolveDeferredDragon`:
  - Si la entidad física legítima es encontrada y validada (`validateDragonForSession`), se restaura el estado previo (`ACTIVE` o `DYING`).
  - Si la entidad no se encuentra o su identidad PDC no coincide, la sesión aborta por `BattleAbortReason.ENTITY_MISSING`.
- **DYING:** Al morir la entidad físicamente (`EntityDeathEvent`), se cancelan los 12,000 XP vanilla (`event.setDroppedExp(0)`), se limpian los drops vanilla y la sesión transiciona a `DYING`.
- **ABORTED:** Si ocurre un fallo irrecuperable (`SPAWN_FAILED`, `ENTITY_MISSING`, `INVALID_WORLD`, `DUPLICATE_BATTLE`), la sesión se aborta con su motivo tipado (`BattleAbortReason`) y se limpia idempotentemente.

---

## 3. Combate, Daño y Participación (Fase 3.4: Combat Runtime)

- **Identidad Persistente (PDC):** Todo dragón de BetterDragon porta una etiqueta Bukkit PDC (`betterdragon:battle_id` y `betterdragon:managed = true`).
- **Criterio de Participación:** Un jugador se convierte en participante exclusivamente al infligir un impacto de daño legítimo y válido contra el dragón en estado `ACTIVE`. No se crean participantes por entrar al End, estar cerca o mirar al dragón.
- **Identidad de Dominio:** La identidad del participante es estrictamente su `UUID`. Nunca se retienen instancias vivas de `Player` o `Entity` en el modelo de dominio.
- **Preservación Histórica de Nombres:**
  - `historicalName`: Captura el nombre del jugador en el momento de su primer impacto válido. Es **estrictamente inmutable**.
  - `lastKnownName`: Nombre más recientemente conocido del jugador; se actualiza con cada impacto posterior válido.
- **Damage Tracking y Acumulación:**
  - Registrado en el hilo principal (`Main-Thread Confined`) con precisión estándar `double`.
  - Rechazo estricto de daño `<= 0`, `NaN`, `Infinity` o valores no finitos, sin mutar contadores ni participantes.
- **Secuencia Monotónica de Impactos (`hitSequence`):**
  - Contador primitivo `long hitSequence` que inicia en 0 y avanza secuencialmente ($1, 2, 3, \dots$) con cada daño confirmado.
  - No emplea `AtomicLong` ni locks al estar confinado al hilo principal.
  - Si un daño es rechazado o el evento es cancelado, la secuencia **no se consume**.
- **Determinación de Slayer (`TOP_DAMAGE`) y Desempate:**
  - Determinado por el participante con mayor `totalDamage` acumulado.
  - **Desempate Determinista:** Si dos jugadores tienen exactamente el mismo daño acumulado, el desempate se resuelve por menor `firstHitSequence` (quien aportó primero al combate). Si persistiera empate, desempata el orden lexicográfico del UUID.
- **Compuertas de Estado (`BattleState Gates`):**
  - El daño solo se acepta en estado `ACTIVE`.
  - Se rechaza en `IDLE`, `PREPARING`, `DYING`, `COMPLETED`, `ABORTED` y `DEFERRED_PENDING_CHUNK_LOAD`.
  - Al transicionar a `DYING` (muerte física), el registro de daño se cierra permanentemente.
- **Persistencia en Memoria y Participantes Offline:**
  - La desconexión, muerte o salida de un jugador del mundo no elimina su registro ni su daño acumulado.
- **Evento Cancelable `BetterDragonDamageEvent`:**
  - Emitido antes de acumular el daño en el runtime.
  - Si un listener externo cancela el evento, no se consume `hitSequence`, no se acumula daño y no se altera el participante.
- **Subpartes y Proyectiles:**
  - Soporte transparente para impactos dirigidos a `EnderDragon` y a subpartes complejas (`EnderDragonPart`/`ComplexEntityPart`), resolviendo la entidad raíz.
  - Soporte de atacantes directos (`Player`) y proyectiles lanzados por jugadores (`ProjectileSource`).
- **Fases y Habilidades:** Consolidadas en Fase 3.5. `[CONSOLIDADAS]`

---

## 4. Fases de Combate y Habilidades (Fase 3.5)

- **Modelo de Fases Ordenadas:**
  - Cada dragón posee una secuencia ordenada de fases (`PhaseDefinition`) con umbrales decrecientes de ratio de salud (`healthRatioThreshold`).
  - La fase inicial se evalúa al spawnear/activar la batalla según la salud inicial del dragón.
- **Invariante de Monotonicidad Estricta:**
  - La progresión avanza exclusivamente hacia adelante ($\text{Fase } 1 \to \text{Fase } 2 \to \text{Fase } 3$).
  - Si el dragón se cura (por ejemplo, mediante cristales del End) y su salud supera un umbral previo, la batalla **NO** retrocede.
  - Si un golpe masivo reduce la vida del dragón saltando múltiples umbrales (ej. 100% a 20%), el sistema transiciona deterministamente hasta la fase final que corresponda.
- **Evento de Cambio de Fase (`BetterDragonPhaseChangeEvent`):**
  - Despachado informativamente al inicializar la primera fase (`previousPhase = null`) y en cada transición posterior.
  - Es **estrictamente no cancelable** para prevenir corrupción o bifurcación de la máquina de estados.
- **Triggers de Habilidades:**
  - `ON_PHASE_ENTER`: Se ejecuta exactamente una vez al entrar a una fase (la fase inicial cuenta como entrada).
  - `PERIODIC`: Se evalúa en el hilo principal durante el tick central de la sesión, sujeto a recargas en ticks lógicos.
  - **Validación de Trigger:** Si se invoca una habilidad con un trigger distinto al declarado, la ejecución es rechazada de forma temprana y segura.
- **Política de Cooldowns:**
  - Medidos en ticks del servidor (`long`).
  - Cada sesión de batalla mantiene su propio estado de cooldown sin singletons.
  - Al cambiar de fase, las recargas de la fase previa se descartan (`reset()`) para que las habilidades de la nueva fase estén disponibles de inmediato.
- **Selectores de Objetivos (Entidades):**
  - `ALL_IN_ARENA`: Jugadores en el mundo de la arena dentro del volumen tridimensional ortogonal real (`ArenaBounds`), evaluados mediante `spatialContext.isInArena(player.getLocation())` sin aproximaciones esféricas, radios inventados ni coordenadas mágicas.
  - `RANDOM_PLAYER`: Un jugador aleatorio elegible.
  - `RANDOM_SUBSET`: Subconjunto aleatorio de hasta N jugadores elegibles.
  - `NEAREST_PLAYER`: Jugador elegible más cercano al origen resuelto (desempate determinista por UUID).
  - `DAMAGER`: Jugador con mayor daño acumulado (`TOP_DAMAGE`) consultado de `CombatRuntime`.
  - `TRIGGERING_PLAYER`: Jugador asociado al trigger (si existe).
- **Orígenes de Efecto (0% NMS):**
  - `DRAGON_HEAD`: Proyección frontal de la cabeza del dragón vía Bukkit API pública.
  - `DRAGON_BODY`: Ubicación corporal de la entidad dragón.
  - `TARGET_FEET`: Coordenadas a los pies del objetivo primario (con fallback seguro al dragón).
  - `PODIUM_CENTER`: Ubicación real del pedestal central de bedrock en el End provista por la `ArenaDefinition` congelada en `BattleSpatialContext`.
  - `ARENA_CENTER`: Centro geométrico aéreo real de la arena provisto por la `ArenaDefinition` congelada en `BattleSpatialContext`.
  - `TRIGGER_LOCATION`: Coordenada del causante del trigger (con fallback seguro al dragón).
- **Efectos Canónicos de Habilidades:**
  - `DAMAGE`: Inflige daño directo al jugador vía `target.damage(amount, dragon)`. Aislado al 100% de `CombatRuntime` (no alimenta daño de jugadores hacia dragón).
  - `KNOCKBACK`: Aplica impulsos vectoriales físicos moderados y acotados (`setVelocity`) sin NMS.
  - `PARTICLE`: Genera partículas en el origen resuelto mediante `World#spawnParticle`.
  - `SOUND`: Reproduce efectos de audio espacial en el origen resuelto mediante `World#playSound`.

---

## 5. Arenas, Geometría y Reglas de Combate (Fase 3.6 & 3.6-R1)

- **Configuración y Gestión de Arenas (`arenas.yml`):**
  - Cada arena posee un identificador estable (`id`), nombre del mundo asociado (`world`), centro aéreo (`center`), podio (`podium`), límites (`bounds`) y reglas (`rules`).
  - La arena física resuelta para el mundo determina directamente el `arenaId` congelado en el `BattleConfigurationSnapshot` de la `BattleSession` (sincronización estricta de identidad).
- **Separación Rigurosa entre Centro de Arena y Podio:**
  - `ARENA_CENTER`: Punto tridimensional de combate en el aire donde operan las habilidades aéreas del dragón.
  - `PODIUM_CENTER`: Coordenada terrestre exacta del portal/pedestal central de bedrock.
  - Se prohíben las equivalencias o fallbacks mutuos entre ambos conceptos, y se eliminaron definitivamente los fallbacks duros como `(0, 100, 0)` o `(0, 65, 0)`.
- **Límites de Arena (`ArenaBounds`) y Evaluación Multidimensional:**
  - Representados mediante un volumen ortogonal alineado con los ejes (Axis-Aligned Bounding Box, AABB).
  - Invariante geométrica estricta: $\min \le \max$ para $X, Y, Z$.
  - Método determinista `contains(Location)` y `contains(double x, y, z)` utilizado activamente por `TargetSelector.ALL_IN_ARENA`.
  - `isInArena(Location)` valida como una unidad indivisible `world + bounds`: si la entidad está en otro mundo diferente al configurado en la arena, se devuelve `false` incluso si las coordenadas numéricas coinciden.
- **Reglas Canónicas de Arena (`ArenaRuleSet` y `ArenaRuleEvaluator`):**
  - `waterAllowed` (Water Denial): Define si la colocación o presencia de agua está permitida en la arena durante el combate (`water_allowed: false` canónico). Se rechaza cualquier ambigüedad o contradicción con `water_denial`.
  - `boundaryEnabled`: Define si la detección perimetral de la arena está activa.
  - `antiTunnelEnabled`: Define si los controles de mitigación anti-túnel están habilitados.
  - *Cero defaults arbitrarios:* La configuración en YAML debe ser explícita para todas las reglas; el loader rechaza omisiones sin asumir gameplay arbitrario.
  - Las reglas quedan completamente aisladas de `AbilityEngine`, `CombatRuntime` y `RewardManager`.
- **Aislamiento de Sesiones e Inmutabilidad ante Recargas:**
  - Toda `BattleSession` congela la `ArenaDefinition` de su batalla al iniciar.
  - La ejecución de `/bd reload` con un archivo `arenas.yml` inválido es rechazada atómicamente preservando la configuración anterior.
  - Si una nueva configuración válida de arenas es cargada, las batallas en curso continúan inalteradas con su snapshot previo; únicamente las batallas nuevas usarán la nueva configuración.

## 6. Muerte y Victoria (Fase 3.7 & 3.7-R1: Death / Victory) `[COMPLETO / CONSOLIDADO]`

- **Disparador Exclusivo de Victoria:**
  - La victoria de una batalla BetterDragon requiere imperativamente el evento de Bukkit `EntityDeathEvent` sobre el `EnderDragon` administrado (`betterdragon:managed=true`, `betterdragon:battle_id`).
  - La descarga de chunks, desaparición o reinicio **nunca** constituyen victoria (`ABORTED_ENTITY_MISSING`).
  - **Independencia Absoluta de DragonBattle:** ningún método, evento o flag de `DragonBattle` vanilla (`hasBeenPreviouslyKilled()`, `dragonKilled`, `getEnderDragon()`) interviene como fuente de verdad en el ciclo de vida o victoria de BetterDragon.
- **Transición y Finalización:**
  - Al recibir `EntityDeathEvent` verificado por PDC:
    1. Se suprimen la experiencia vanilla (0 XP) y drops del dragón administrado (`getDrops().clear()`, `setDroppedExp(0)`).
    2. La sesión pasa a `DYING`.
    3. Se extrae `CombatSnapshot` inmutable del `CombatRuntime`.
    4. Se determina el Slayer por `TOP_DAMAGE` mediante dos únicos criterios deterministas: mayor daño total (`totalDamage DESC`) y menor `firstHitSequence ASC` en caso de empate (sin desempate por UUID ni tercer criterio).
    5. La sesión pasa a `COMPLETED`.
    6. Se construye y cachea el `BattleResult` inmutable.
    7. Se despacha el evento público `BetterDragonVictoryEvent` (exponiendo datos inmutables de dominio: `BattleResult`, `BattleId` y `worldName`, sin exponer `BattleSession` ni runtime mutable interno).
- **Comportamiento Vanilla Preservado:**
  - Dragones no administrados (vanilla) que mueran en el End no sufren supresión de XP ni de drops, y no disparan eventos ni transiciones de BetterDragon.
- **Dragon Egg y Primera Victoria Independientes:**
  - El huevo de dragón y el concepto de primera victoria no dependen del estado vanilla `DragonBattle`.
  - En la Fase 3.7-R1 **NO** se genera, manipula ni elimina ningún huevo de dragón en el mundo. El ciclo de vida del huevo y el concepto de primera victoria quedan explícitamente reservados para el sistema propio de BetterDragon en fases posteriores.
- **Idempotencia Estricta:**
  - Cualquier llamada posterior con el mismo dragón o sesión ya `COMPLETED` es un no-op seguro que retorna el mismo `BattleResult` inmutable sin disparar eventos duplicados.
- **Participantes Offline:**
  - Jugadores desconectados u offline conservan su candidatura a Slayer y sus nombres históricos sin requerir conexión a Bukkit.

---

## 7. Recompensas, Claims y Persistencia

- **Recompensas Propias de BetterDragon (Fase 3.8 / 3.8-R1 / 3.8-R2) `[COMPLETADA]`:**
  - El sistema de recompensas se activa exclusivamente tras la victoria formal (`COMPLETED`) a través de `BetterDragonVictoryEvent`.
  - **Defaults Técnicos Seguros:** `rewards.enabled: false`, `min_participation_percent: 0.0`, y pools de ítems vacíos por defecto. Sin ítems de gameplay arbitrarios en código ni en configuración estándar.
  - **Identidad de Recompensa Obligatoria (`rewardId`):** Cada ítem configurado requiere un `id` alfanumérico único (`^[a-zA-Z0-9_-]+$`). El cargador de configuración rechaza colisiones entre pool y slayer rewards fail-fast.
  - **Validación Estricta de Cantidad (`amount` — 3.8-R2):** `amount` debe ser un entero positivo exacto mayor a 0. Se rechazan valores decimales/fraccionarios (`1.7`, `1.5`, `2.5`), no positivos (`0`, `-1`), no finitos (`NaN`, `Infinity`) y overflow (`> Integer.MAX_VALUE`). Se aceptan valores numéricos que representan exactamente un entero positivo (como `2.0` entregado por parsers YAML) sin truncamiento ni redondeo silencioso. Los mensajes de error identifican con claridad la ruta, `rewardId` y `'amount'`.
  - **Validación Nativa de Material (Paper API — 3.8-R2):** El material se valida directamente contra la API oficial mediante `Material.matchMaterial(material)`. Se eliminaron por completo fallbacks heurísticos regex/blacklist (`isValidMaterialFallback`), rechazando materiales inexistentes de formato plausible (`FOO_BAR`, `FAKE_MATERIAL`, `INVALID_MATERIAL`) y tipos de aire (`AIR`, `CAVE_AIR`, `VOID_AIR`).
  - **Elegibilidad Invariante:** Un jugador es elegible si su daño acumulado real en `CombatSnapshot` alcanza o supera `min_participation_percent` del daño total y `participant.totalDamage > 0`. Participantes con daño menor o igual a cero jamás son elegibles, incluso si `min_participation_percent = 0.0`.
  - **Redistribución Proporcional:** Las porciones que habrían correspondido a participantes no elegibles se redistribuyen íntegramente y de manera proporcional entre los participantes elegibles según su daño relativo.
  - **Redondeo Determinista:** Las cuotas fraccionarias se truncan a enteros base y las unidades de remanente se asignan de a una por orden de mayor residuo decimal, desempatando por `firstHitSequence ASC` y UUID lexicográfico.
  - **Slayer (`TOP_DAMAGE`):** Recompensa adicional configurable adjudicada al Slayer resuelto en `BattleResult`, evaluando la compuerta `requires_eligibility`.
  - **Buzón de Reclamos y Cero Pérdidas:** Si el jugador está desconectado o su inventario saturado, las unidades no entregadas quedan resguardadas en `ClaimStorage` en estado `PENDING`. Al reconectarse o liberar ranuras, se entregan automáticamente.
  - **Idempotencia Estricta y Prevención de Colisiones:** Deduplicación determinista por clave canónica `battleId:participantId:rewardId`. Distintas definiciones con el mismo material no colisionan, permitiendo la coexistencia de reclamos independientes. Reintentos, doble procesamiento y reconexiones nunca duplican ítems físicos. Toda documentación y Javadoc fue saneado para erradicar referencias obsoletas a `source:material`.
- **Persistencia SQLite y Buzón de Reclamos Durables (Fase 3.9) `[COMPLETADA]`:**
  - **Almacenamiento Durable Embebido:** Migración del almacenamiento de claims a base de datos relacional SQLite (`plugins/BetterDragon/data/betterdragon.db`).
  - **Driver Empaquetado:** Dependencia `org.xerial:sqlite-jdbc:3.44.1.0` sombreada (*shaded*) en el JAR final con binarios nativos para todas las plataformas sin asumir provisión externa de Paper.
  - **Topología No Bloqueante (Single-Writer Async):** Toda interacción JDBC (`SELECT`, `INSERT`, `UPDATE`, checkpoint) se ejecuta fuera del hilo principal en un ejecutor dedicado secuencial (`PersistenceExecutor`). Cero llamadas de base de datos bloquean los 20 TPS de Bukkit.
  - **Esquema Relacional Inicial y Versionado (v1):** Creación e inicialización DDL idempotente con tabla de metadatos `bd_schema_metadata` registrando `schema_version = 1`.
  - **Rechazo Fail-Safe de Versiones Futuras:** Si la base de datos registra una versión mayor a la soportada por el plugin (`schema_version > 1`), el subsistema arroja `IllegalStateException` y se detiene inmediatamente sin realizar modificaciones destructivas.
  - **Idempotencia Relacional Estricta:** Restricción `UNIQUE(idempotency_key)` sobre `battleId:participantId:rewardId` coordinada mediante `createIfAbsent` con `INSERT INTO bd_reward_claims ... ON CONFLICT(idempotency_key) DO NOTHING` y recuperación del registro persistido, complementado por `updateExisting` separado con cláusula `WHERE idempotency_key = ? AND status != 'CLAIMED'` que impide mutar claims cuyo estado terminal ya sea `CLAIMED`.
  - **Ciclo de Vida Durable de Reclamos:**
    - `PENDING`: Sobrevive reinicios del servidor. Se crea cuando un jugador está desconectado o con inventario saturado, registrando `remaining_amount`.
    - `CLAIMED`: Recompensa físicamente entregada en su totalidad (`remaining_amount = 0`). No vuelve a entregarse tras reiniciar, pero permanece almacenada para auditoría e idempotencia.
    - `FAILED_RETRYABLE`: Preserva el reclamo con su motivo de error ante fallos temporales de entrega, disponible para reintento.
  - **Recuperación tras Reinicio y Reconexión:**
    - Los claims pendientes no se entregan a jugadores offline en el arranque.
    - Al ingresar un jugador (`PlayerJoinEvent`), `DragonRewardListener` solicita asíncronamente sus reclamos pendientes y transfiere la entrega física al hilo principal mediante `MainThreadDispatcher`, actualizando el estado resultante en SQLite.
  - **Limitación Documentada de Consistencia ante Caídas:** BetterDragon no afirma falsas garantías transaccionales "exactly-once" coordinadas entre SQLite y el guardado de inventarios NBT de Minecraft. La política es *at-least-once con deduplicación optimista*: nunca se marca un claim como `CLAIMED` antes de confirmar la entrega en memoria en el hilo principal.
  - **Prohibición de Fallback Silencioso:** Si SQLite falla al inicializarse, el plugin no degrada silenciosamente a `InMemoryClaimStorage` fingiendo durabilidad; reporta el error y bloquea el procesamiento persistente para evitar pérdidas silenciosas.
  - **Comandos de Usuario:** El comando de reclamo manual de recompensas pendientes se encuentra implementado mediante `/bd claim` (Fase 3.11). No existe un comando independiente `/bd rewards`.
- **Recuperación tras Reinicio:**
  - Dragones con PDC detectados durante el arranque sin batalla activa en memoria son removidos de forma limpia para evitar entidades huérfanas. `[CONSOLIDADO EN 3.3-R1]`

---

## 8. Leaderboard Persistente e Idempotente (Fase 3.10) `[COMPLETADA]`

- **Consumo Exclusivo de `BattleResult`:**
  - El leaderboard se activa ante `BetterDragonVictoryEvent` consumiendo el `BattleResult` inmutable ya producido.
  - Cero consultas a entidades Bukkit vivas (`Player`, `EnderDragon`), `DragonBattle` o `EndDragonFight`.
- **Identidad Inmutable por UUID:**
  - La clave de identidad del jugador es exclusivamente su `UUID`.
  - Los nombres de jugador nunca son claves primarias ni identificadores lógicos. Un jugador que cambia de nombre sigue siendo el mismo registro.
- **Separación de Nombres (`historicalName` vs `lastKnownName`):**
  - `historical_name`: Almacenado en `bd_leaderboard_participation` para auditar el nombre que portaba el jugador durante esa batalla específica. Es inmutable y no se sobrescribe jamás retroactivamente.
  - `last_known_name`: Almacenado y actualizado en `bd_leaderboard_players` reflejando el nombre más reciente conocido.
- **Respeto Estricto de Slayer (`TOP_DAMAGE`):**
  - El Slayer es exactamente el `slayerUniqueId` resuelto en `BattleResult`.
  - Si el jugador fue Slayer en la batalla (`was_slayer = true`), su contador acumulado `slayer_count` se incrementa en 1; de lo contrario no se incrementa.
- **Transacciones Atómicas e Idempotencia:**
  - Cada batalla se registra dentro de una transacción SQLite única (`BEGIN TRANSACTION` -> `bd_leaderboard_battles` -> `bd_leaderboard_participation` -> `bd_leaderboard_players` -> `COMMIT`).
  - Idempotencia estricta por `ON CONFLICT(battle_id) DO NOTHING`. Si la batalla ya fue procesada, la transacción revierte y no altera ninguna estadística.
  - Re-procesar la misma batalla $N$ veces no incrementa contadores, daño ni participaciones.
- **Agregados Acumulados y `averageDamage` Derivado:**
  - `battles_participated`: Cantidad de participaciones únicas en batallas ganadas.
  - `total_damage`: Suma de daño de todas las batallas.
  - `highest_damage`: $\max(\text{daño de cada batalla})$.
  - `slayer_count`: Victorias como Slayer.
  - `first_participation_at` / `last_participation_at`: Epoch milliseconds de la primera y última participación.
  - `average_damage`: Calculado bajo demanda en memoria (`totalDamage / battlesParticipated`). No se almacena en base de datos.
- **Consultas Deterministas y Límites:**
  - `getTopDamage(limit)`: Ordenado por `total_damage DESC, player_uuid ASC`.
  - `getTopSlayers(limit)`: Ordenado por `slayer_count DESC, player_uuid ASC`.
  - `getTopParticipations(limit)`: Ordenado por `battles_participated DESC, player_uuid ASC`.
  - Desempate determinista siempre por `player_uuid ASC`.
  - Validación de `limit > 0` con límite interno máximo de 1000.
- **Aislamiento de Persistencia:**
  - Convivencia en `betterdragon.db` con `schema_version = 2` y migración no destructiva desde v1.
  - `bd_reward_claims` permanece intacta e independiente.
- **Sin Bloqueo del Hilo Principal:**
  - Todas las operaciones de lectura y escritura se ejecutan de manera asíncrona mediante `CompletableFuture` en el worker de `DatabaseManager`.

---

## 9. Comandos, Permisos y UX Administrativo (Fase 3.11) `[COMPLETADA]`

- **Puntos de Entrada Canónicos:**
  - Comando raíz: `/betterdragon`
  - Alias oficial: `/bd`
  - Registrados atómicamente en el `CommandMap` de Bukkit/Paper durante `onEnable()`.
- **Estructura y Comportamiento de Subcomandos:**
  - `/bd help [subcomando]`: Muestra la lista de subcomandos disponibles según los permisos del emisor o información detallada (descripción, sintaxis, alias, permiso requerido, emisores permitidos) de un subcomando específico.
  - `/bd leaderboard [damage|slayers|battles] [límite]` (alias: `top`, `lb`): Consulta asíncrona no bloqueante de los rankings SQLite. Admite categorías `damage` (daño acumulado y media), `slayers` (victorias como Slayer), y `battles` (participaciones totales). Aplica límite máximo de 20 líneas en chat para evitar spam.
  - `/bd stats [jugador]` (alias: `perfil`, `estadisticas`): Muestra estadísticas detalladas del jugador (UUID, batallas participadas, daño total, daño récord, promedio y victorias como Slayer). Si se omite argumento, consulta el propio emisor. Si se especifica un argumento, requiere el permiso `betterdragon.stats.others`; acepta UUID directo y resuelve jugadores online o presentes en la caché del servidor (`Bukkit.getOfflinePlayerIfCached`). No realiza una resolución arbitraria de nombres históricos directamente desde SQLite.
  - `/bd status [mundo]` (alias: `estado`): Muestra el estado en tiempo real de la batalla activa (fase actual, barra de vida y porcentaje, tiempo transcurrido, participantes y damager líder). Si se ejecuta desde consola sin argumentos, lista el estado de todas las batallas activas en el servidor.
  - `/bd start [mundo] [arena] [definición]` (alias: `spawn`, `iniciar`): Inicia una batalla controlada de forma segura. Valida que el mundo exista, esté cargado y pertenezca al entorno `THE_END`, y que no exista una batalla previa activa en dicho mundo. Si se ejecuta desde consola, el argumento `mundo` es estrictamente obligatorio.
  - `/bd abort [mundo|battleId]` (alias: `cancel`, `cancelar`, `stop`): Cancela formalmente una sesión activa en el mundo o por su UUID de batalla, eliminando la entidad física administrada sin decretar victoria falsa ni entregar botín.
  - `/bd reload` (alias: `recargar`): Ejecuta una recarga atómica y fail-safe de `config.yml` y `arenas.yml`. Si los archivos contienen errores sintácticos o de validación, la recarga se rechaza conservando intacta la configuración activa anterior y preservando las sesiones de batalla en curso.
  - `/bd arena <list|info> [id]` (alias: `arenas`):
    - `list`: Lista todas las arenas cargadas en memoria indicando mundo, arena por defecto y límites AABB.
    - `info <id>`: Detalla centro aéreo de combate, podio terrestre de salida, dimensiones espaciales y reglas geométricas activas (`water_allowed`, `boundary`, `anti_tunnel`).
  - `/bd claim` (alias: `reclamar`, `recompensas`): Comando exclusivo de jugador para solicitar la entrega manual de ítems pendientes en su buzón durable de SQLite tras liberar espacio en su inventario.
- **Modelo Granular de Permisos:**
  - `betterdragon.use`: Permiso base para interactuar con la interfaz de BetterDragon (por defecto: `true` para todos los usuarios).
  - `betterdragon.leaderboard`: Permite consultar el ranking global persistente (por defecto: `true`).
  - `betterdragon.stats`: Permite consultar las estadísticas de combate propias (por defecto: `true`).
  - `betterdragon.stats.others`: Permite inspeccionar las estadísticas de otros jugadores (por defecto: `op`).
  - `betterdragon.claim`: Permite reclamar recompensas pendientes (por defecto: `true`).
  - `betterdragon.admin`: Permiso administrativo comodín que habilita todas las operaciones de gestión (por defecto: `op`).
  - `betterdragon.admin.status`: Permite consultar el estado de batallas activas (por defecto: `op`).
  - `betterdragon.admin.start`: Permite iniciar batallas manualmente (por defecto: `op`).
  - `betterdragon.admin.abort`: Permite abortar o cancelar batallas activas (por defecto: `op`).
  - `betterdragon.admin.reload`: Permite recargar la configuración del plugin (por defecto: `op`).
  - `betterdragon.admin.arena`: Permite inspeccionar la configuración de arenas (por defecto: `op`).
- **Seguridad de Consola (Console Safety):**
  - Todo subcomando declara explícitamente su emisor admitido (`PLAYER_ONLY`, `CONSOLE_ONLY`, `BOTH`).
  - Subcomandos que operan sobre la ubicación del jugador (como `/bd claim`) rechazan la consola con un mensaje claro sin arrojar `ClassCastException`.
  - Subcomandos como `/bd start` y `/bd abort` exigen a la consola especificar explícitamente el nombre del mundo.
- **Asincronía y Rendimiento:**
  - Toda interacción con SQLite originada por comandos (`leaderboard`, `stats`, `claim`) retorna `CompletableFuture` y se despacha fuera del hilo principal.
  - Cero bloqueos con `future.get()` o `future.join()` en el hilo de ticks de Bukkit.
  - Las respuestas se renderizan de forma no invasiva notificando al usuario al completarse la consulta; en particular, la presentación final de `/bd claim` garantiza su retorno al hilo principal mediante `MainThreadDispatcher`.
- **Tab Completion:**
  - Autocompletado contextual y determinista para subcomandos y argumentos (categorías de ranking, IDs de arenas, mundos cargados).
  - Filtra estrictamente las opciones según los permisos reales del emisor.
  - No ejecuta consultas I/O a disco ni a base de datos durante los eventos de autocompletado.
- **Manejo de Errores y Presentación:**
  - Errores de sintaxis y argumentos inválidos devuelven mensajes formateados claros con sugerencias de uso.
  - Excepciones internas imprevistas son registradas en el logger con detalle técnico mientras que al emisor se le informa de manera amigable sin mostrar trazas de error (stack traces) en el chat.
