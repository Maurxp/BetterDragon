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
- **Leaderboard:** Incluido en el MVP, consultable mediante comandos y respaldado por SQLite.
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
   ▼ (Resolución futura en 3.4+: Slayer, loot, BattleResult inmutable)
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
  - `ALL_IN_ARENA`: Todos los jugadores conectados en el mundo dentro del radio de la arena (150 bloques), con vida > 0 y en supervivencia o aventura.
  - `RANDOM_PLAYER`: Un jugador válido seleccionado con RNG encapsulado.
  - `RANDOM_SUBSET`: Hasta N jugadores sin duplicados (o todos si la cantidad disponible es menor a N).
  - `NEAREST_PLAYER`: Jugador más cercano a la coordenada de origen previamente resuelta (`resolvedOrigin`, ej. `DRAGON_HEAD`) con desempate determinista por UUID.
  - `DAMAGER`: Jugador con mayor daño acumulado (`TOP_DAMAGE`) consultado de `CombatRuntime`.
  - `TRIGGERING_PLAYER`: Jugador asociado al trigger (si existe).
- **Orígenes de Efecto (0% NMS):**
  - `DRAGON_HEAD`: Proyección frontal de la cabeza del dragón vía Bukkit API pública.
  - `DRAGON_BODY`: Ubicación corporal de la entidad dragón.
  - `TARGET_FEET`: Coordenadas a los pies del objetivo primario (con fallback seguro al dragón).
  - `PODIUM_CENTER`: Ubicación central del pedestal/portal de bedrock en el End (Y=65.0) provista por `BattleSpatialContext`.
  - `ARENA_CENTER`: Centro geométrico aéreo de la arena de combate (Y=100.0) provisto por `BattleSpatialContext`.
  - `TRIGGER_LOCATION`: Coordenada del causante del trigger (con fallback seguro al dragón).
- **Efectos Canónicos de Habilidades:**
  - `DAMAGE`: Inflige daño directo al jugador vía `target.damage(amount, dragon)`. Aislado al 100% de `CombatRuntime` (no alimenta daño de jugadores hacia dragón).
  - `KNOCKBACK`: Aplica impulsos vectoriales físicos moderados y acotados (`setVelocity`) sin NMS.
  - `PARTICLE`: Genera partículas en el origen resuelto mediante `World#spawnParticle`.
  - `SOUND`: Reproduce efectos de audio espacial en el origen resuelto mediante `World#playSound`.

---

## 5. Recompensas, Claims y Persistencia

- **Recompensas:**
  - Recompensa exclusiva para el Slayer (`TOP_DAMAGE`).
  - Botín escalonado por porcentaje de contribución.
- **Buzón de Claims:** Si el inventario de un participante está lleno o el jugador está desconectado, los ítems se guardan en SQLite y se retiran con `/bd claim`. `[PENDIENTE]`
- **Persistencia SQLite:** Almacenamiento no bloqueante mediante un worker asíncrono de escritor único (*Single-Writer Async Worker*). `[CONGELADO]`
- **Recuperación tras Reinicio:** Dragones con PDC detectados durante el arranque sin batalla activa en memoria son removidos de forma limpia para evitar entidades huérfanas. `[CONGELADO]`
