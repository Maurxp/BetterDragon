# BetterDragon Master Gameplay Design Specification

**Documento:** Especificación Maestra de Jugabilidad, Mecánicas de Encuentro y Modelo de Experiencia  
**Fase:** 3.12-R1 (Consolidación y Auditoría Pre-Commit)  
**Autor:** maurxp (@author maurxp)  
**Plataforma Objetivo:** Minecraft 26.1.2 | Paper `paper-26.1.2-74` | Java 25 (Temurin 25.0.4.1+1-LTS)  
**Estado:** `CONSOLIDADO Y AUDITADO — BASE DE CONOCIMIENTO TÉCNICA PARA FASE 3.13+`

---

## A. Visión del Proyecto (Vision)

BetterDragon transforma la batalla monótona y fácilmente explotable del Ender Dragon vanilla en un **encuentro de jefe de incursión (*Raid Boss*) cinemático, profundo, altamente configurable y justo para comunidades multijugador**.

El objetivo no es acumular código por elegancia abstracta, sino responder a una directriz cardinal:
> *"¿Qué experiencias de combate contra el Ender Dragon queremos que BetterDragon sea capaz de crear?"*

BetterDragon busca ofrecer:
1. Batallas que aumenten en dramatismo y ritmo a través de **fases de combate progresivas**.
2. Ataques telegrafiados que premien la **habilidad y los reflejos del jugador**, reduciendo muertes inesperadas y mejorando la legibilidad de los ataques de alto impacto.
3. Encuentros que permitan **calibrar la experiencia para distintos tamaños de grupo**, facilitando el balance tanto para un jugador en solitario como para grupos numerosos.
4. Protección de la integridad del combate mediante **políticas modulares anti-cheese** (evaluación de mitigación de camas y gestión de agua).
5. Justicia distributiva en recompensas (consolidada en Fase 3.8/3.9) que valore el esfuerzo real y minimice el riesgo de pérdida de botín por desconexión o inventario lleno.

---

## B. Principios Rectores de Diseño (Principles)

### 1. Metodología de Diseño de Cuarto Limpio (*Clean-Room*)
La investigación de proyectos existentes (DragonSlayer, BetterDragon de KaevonD, BetterDragon de amonly) se utiliza exclusivamente para comprender necesidades de la comunidad, patrones de combate y limitaciones de diseño. Queda estrictamente prohibida la copia de código fuente, clases propietarias o esquemas de terceros. Todo el código de BetterDragon se escribe de forma independiente sobre las APIs públicas de Paper 26.1.2.

### 2. Aislamiento Estricto de NMS
> **Regla Arquitectónica:** *No existe NMS en el dominio, gameplay o núcleo de BetterDragon. El uso de NMS permanece estrictamente aislado y confinado en el adaptador de infraestructura que controla la supresión de la BossBar vanilla (`platform.bossbar`).*  
Toda la lógica de entidades, partículas, sonidos, atributos y proyectiles se construye sobre la API pública de Paper 26.1.2.

### 3. Separación Conceptual: Combat Phase vs Flight Phase
Se establece como axioma fundamental la distinción entre dos máquinas de estados independientes:
- **BetterDragon Combat Phase (`PhaseDefinition`):** Representa el estado narrativo y de progresión del encuentro (ej. FASE_1 al 100%, FASE_2 al 75%, FASE_3 al 50%, FASE_4 al 25%). Gobierna los pools de habilidades activas y la progresión secuencial del encuentro.
  - *Distinción Transversal:* Soft Enrage **no** es una fase de combate; es un modificador transversal que coexiste con la fase activa (ej. FASE_4 + Enrage).
- **Vanilla Flight Phase (`EnderDragon.Phase`):** Representa la locomoción e IA física interna de la entidad provista por Paper (`CIRCLING`, `STRAFING`, `LAND_ON_PORTAL`, `CHARGE_PLAYER`, etc.).
- **Interacción:** La fase de combate de BetterDragon orquesta, solicita o reacciona ante las fases de vuelo de Paper mediante `EnderDragon#setPhase(Phase)` y `EnderDragonChangePhaseEvent`, pero **nunca se tratan como el mismo estado**.

### 4. Telegrafiado Universal de Ataques de Alto Impacto
Todo ataque de alto impacto o potencialmente letal debe ser advertido sensorialmente (partículas visuales en suelo/aire, sonidos de carga o advertencias en pantalla) con antelación suficiente antes de aplicar daño físico, otorgando una ventana de escape basada en habilidad.

### 5. Respeto al Modelo Multipart Nativo
BetterDragon respeta la física de sub-entidades (`EnderDragonPart`), reconociendo el multiplicador de daño en la cabeza ($4\times$) como un incentivo legítimo para el disparo de precisión.

---

## C. Mecánicas Confirmadas en el Runtime (Confirmed Mechanics)

Las siguientes capacidades ya existen y han sido validadas en el código de producción hasta la Fase 3.11-R1:

1. **Gestión Autónoma del Ciclo de Vida (`BattleSession`):**  
   Desacoplamiento total de `DragonBattle` vanilla. La batalla posee su propia máquina de estados formal (`INITIALIZING`, `PREPARING`, `ACTIVE`, `VICTORY`, `TERMINATING`, `ABORTED`, `DEFERRED_PENDING_CHUNK_LOAD`).
2. **Identidad Persistente en PDC (`DragonPdcHandler`):**  
   Marcado síncrono del dragón en el spawn con `betterdragon:managed = true`, `betterdragon:battle_id = <uuid>`, `betterdragon:definition_id = <id>` y `betterdragon:schema_version = 1`. (Consolidado en Fase 3.13).
3. **Tracking Determinista de Daño y `TOP_DAMAGE` (`CombatRuntime`):**  
   Acumulación monotónica del daño por jugador con desempate determinista por orden de primer impacto.
4. **Reparto Proporcional de Botín y Recompensas (`RewardAllocationEngine`):**  
   División de pools de recompensa basada en el porcentaje de daño aportado por cada participante elegible, sin modelo *winner-takes-all*.
5. **Persistencia Durable y Buzón de Reclamo (`SQLiteClaimStorage`):**  
   Almacenamiento transaccional asíncrono en SQLite (esquema v2) con comando interactivo de reclamo `/bd claim`.
6. **Supresión NMS Aislada de BossBar Vanilla:**  
   Ocultamiento automático de la barra nativa para sustituirla por la BossBar estilizada administrada por el plugin.
7. **Catálogo de Dragones y Atributos Nativos (`DragonCatalog`, `DragonAttributes`):**
   Soporte completo para múltiples definiciones de dragones en `config.yml` (sección `dragons:`) congeladas en `BattleConfigurationSnapshot`. Control nativo Paper de `MAX_HEALTH`, `MOVEMENT_SPEED`, `FOLLOW_RANGE` (con `ATTACK_DAMAGE` no disponible/null en `EnderDragon` en Paper 26.1.2-74), con exclusión de `Attribute.SCALE` por bug MC-267372. (Implementado en Fase 3.13, consolidado en 3.13-R1).
8. **Escalado Determinista de Salud al Inicio de Batalla (*Battle-Start Scaling* CAND-01):**
   Cálculo inmutable de `EffectiveDragonStats` en el snapshot de inicio de batalla según la cantidad de jugadores presentes en la arena geométrica. Fórmula lineal con capping y suelo unitario, inmune a reconexiones o muertes durante el combate. (Implementado en Fase 3.13, consolidado en 3.13-R1).

---

## D. Mecánicas Candidatas en Diseño (Candidate Mechanics)

Propuestas de diseño maduras derivadas de la investigación, planificadas para fases posteriores:

### 1. Control de Atributos Nativos — `[IMPLEMENTADO EN FASE 3.13]`
- `Attribute.MAX_HEALTH`: Salud base personalizable mediante configuración (disponible/observado, 200.0 HP base).
- `Attribute.MOVEMENT_SPEED`: Ajuste fino de velocidad de vuelo (disponible/observado; comportamiento físico locomotor no afirmado sin pruebas dedicadas).
- `Attribute.FOLLOW_RANGE`: Ampliación del rango de detección en arenas de gran tamaño (disponible/observado; comportamiento IA no afirmado sin pruebas dedicadas).
- `Attribute.ATTACK_DAMAGE`: **No disponible / null** nativamente en `EnderDragon` en Paper 26.1.2-74.
- *Nota:* `Attribute.SCALE` rechazado formalmente por bug de motor MC-267372.

### 2. Escalado de Salud por Jugador al Inicio de Batalla (*Battle-Start Scaling*) — `[IMPLEMENTADO EN FASE 3.13]`
Ajuste de la salud máxima del dragón al momento de spawnear según los participantes presentes:
$$\text{SaludEfectiva} = \text{SaludBase} \times \min\left(\text{Cap}, \max\left(1.0, 1.0 + (N_{\text{activos}} - 1) \times \alpha\right)\right)$$
Donde $\alpha$ (`health-per-player`) y $\text{Cap}$ (`max-multiplier`) son parametrizables por definición de dragón.

### 3. Bombardeo Aéreo de TNT con Terreno Inmune (Target: Fase 3.15)
Genera proyectiles explosivos telegrafiados mientras el dragón vuela en `CIRCLING`, forzando movimiento activo en tierra mientras el daño a bloques se cancela totalmente vía flag de arena.

### 4. Onda Expansiva al Aterrizar (*Perch Shockwave*) (Target: Fase 3.15)
Genera un anillo concéntrico de partículas expansivas al tocar el podio (`LAND_ON_PORTAL`), infligiendo empuje vertical y daño calibrado para dispersar a los jugadores en el podio.

### 5. Contrataques Reactivos por Daño (*Counter-Attacks*) (Target: Fase 3.15)
Añade una probabilidad porcentual de contraataque cuando el dragón recibe daño a distancia, con cooldown interno estricto por atacante para evitar saturación de efectos.

### 6. Invocación de Esbirros con Limpieza Garantizada (*Minions*) (Target: Fase 3.15)
Invocación de oleadas menores marcadas con PDC propio:
- `betterdragon:managed = true`
- `betterdragon:battle_id = <uuid>`
- `betterdragon:minion = true`
- `betterdragon:minion_type = <id>`  
Garantiza que un sweep al terminar o abortar la batalla elimine los esbirros invocados sin afectar a mobs pacíficos de la isla.

### 7. Políticas Modulares Anti-Cheese (Target: Fase 3.16)
- **`ExplosionPolicy`:** Atenuación selectiva del daño recibido por camas y anclas de respawn (`BLOCK_EXPLOSION`) sin alterar explosiones legítimas de cristales de End (`ENTITY_EXPLOSION`).
- **`WaterPolicy`:** Gestión del agua colocada en la arena mediante cuatro modos conceptuales:
  - `ALLOW`: Comportamiento estándar vanilla.
  - `DENY_PLACEMENT`: Cancela la colocación de cubos de agua en la arena.
  - `EVAPORATE`: El agua colocada se evapora con partículas tras un breve retardo.
  - `PUNISH`: El dragón castiga a los jugadores que acampan en agua con proyectiles o descargas.
- **`BoundaryPolicy` (*Void Tether*):** Confinamiento espacial dentro de `ArenaBounds`. Estrategias conceptuales a evaluar en pruebas: `ALLOW`, `WARN`, `PUSH`, `TELEPORT`, `DAMAGE`, sin fijar una única implementación definitiva antes del playtesting.

### 8. Furia Progresiva (*Soft Enrage*) (Target: Fase 3.14)
Aceleración de cooldowns e incremento moderado de agresividad cuando la salud cae por debajo de un umbral crítico de cierre.

---

## E. Hipótesis Pendientes de Validación Experimental (Hypotheses)

1. **`HYP-01` — Escalado Dinámico en Vivo vs Escalado al Inicio:**  
   Comparativa neutral entre recalcular la salud en vivo al cruzar el perímetro vs fijarla al inicio de la batalla (`EXP-008`).
2. **`HYP-02` — Ventanas de Aturdimiento por Destrucción de Cristal:**  
   Evaluar si interrumpir el haz de curación de un cristal destruyéndolo en canalización activa puede aturdir al dragón temporalmente sin desestabilizar la locomoción de Paper (`EXP-003` / Post-3.15).
3. **`HYP-03` — Límite Temporal Absoluto (*Hard Enrage*):**  
   Evaluar la viabilidad de un temporizador global de aniquilación, propuesto como opción desactivada por defecto.

---

## F. Candidatos de Calibración Numérica (Tuning Candidates)

Ningún valor numérico se asume como una verdad absoluta ni definitiva. Todos los valores propuestos representan puntos de partida iniciales sujetos a playtesting:

| Parámetro | Valor Propuesto Inicial | Clasificación | Propósito de Calibración | Método de Validación |
| :--- | :---: | :---: | :--- | :--- |
| **Telegraph `MINOR`** | `0.8 s` | `TUNING_CANDIDATE` | Aviso breve para desventajas leves o daño menor. | Medición de esquiva a corta distancia. |
| **Telegraph `MODERATE`**| `1.5 s` | `TUNING_CANDIDATE` | Aviso visible para ataques medios y empujes. | Medición de sprint de escape. |
| **Telegraph `MAJOR`** | `2.0 s` | `TUNING_CANDIDATE` | Círculo visible en suelo y sonido para ataques severos. | Prueba de tolerancia a latencia (`EXP-006`). |
| **Telegraph `LETHAL`** | `3.0 s` | `TUNING_CANDIDATE` | Alerta máxima en pantalla para mecánicas letales. | Playtest con jugadores de diversos niveles. |
| **Scaling Factor ($\alpha$)** | `0.25` | `TUNING_CANDIDATE` | +25% de vida base por cada jugador adicional en arena. | Curvas de duración de combate (`EXP-008`). |
| **Scaling Cap** | `3.0x` | `TUNING_CANDIDATE` | Multiplicador máximo de salud permitido por escalado. | Prevención de fatiga en grupos masivos. |
| **Bed Damage Multiplier** | `0.05` | `TUNING_CANDIDATE` | Reduce el daño recibido por camas en un 95%. | Prueba de daño comparativo (`EXP-004`). |
| **Soft Enrage Threshold** | `0.20` | `TUNING_CANDIDATE` | Umbral del 20% de salud para activar fase de furia. | Medición de ritmo de combate al cierre. |
| **Counterattack Cooldown**| `5.0 s` | `TUNING_CANDIDATE` | Límite de 1 contraataque cada 5s por agresor. | Pruebas de disparo continuo con arco. |

---

## G. Mecánicas Descartadas y Justificación (Rejected Mechanics)

1. **Escalado de Modelo mediante `Attribute.SCALE` (`generic.scale`):**  
   *Hecho Técnico:* Bug `MC-267372`. Las hitboxes multipart del Ender Dragon no escalan en Minecraft 26.1.2. BetterDragon **no soporta actualmente** el redimensionamiento del dragón mediante este atributo.  
   *Opción de Diseño:* Las variantes de dragón pueden diferenciarse mediante atributos numéricos, habilidades, efectos visuales de partículas, sonidos personalizados y patrones de comportamiento.
2. **Atribución de Victoria por Último Golpe (*Last-Hit*):**  
   *Motivo:* Fomenta el robo de muertes y la inactividad durante la batalla. Superado en Fase 3.7 mediante `TOP_DAMAGE`.
3. **Uso de ProtocolLib para Estatuas y NPCs:**  
   *Motivo:* Introduce dependencia externa frágil que viola la portabilidad nativa.
4. **Reconstrucción Forzada y Destructiva de Portales:**  
   *Motivo:* Rompe mapas y arenas personalizadas. Se respeta `portal.enabled: false`.
5. **Coordenadas Centrales Hardcodeadas a `(0, 0)`:**  
   *Motivo:* Incompatible con arenas en cualquier coordenada del mundo. BetterDragon soporta coordenadas arbitrarias mediante `ArenaDefinition`.
6. **Subordinación al Ciclo de Vida de `DragonBattle` Vanilla:**  
   *Motivo:* Fuente constante de bugs de desincronización y corrupción de chunks.

---

## H. Sistemas Futuros (Future Systems)

- **FUT-01 — Battle Designer GUI (Fase 3.19):**  
  Editor visual en juego mediante menús de inventario interactivos para diseñar dragones, fases y habilidades.
- **FUT-02 — Integración de Placeholders vía PAPI (Fase 3.20):**  
  Exposición de estadísticas acumuladas del leaderboard (`top_damage`, `slayer_count`, etc.) para scoreboards externos.

---

## I. Filosofía y Estructura de Configuración (Configuration Philosophy)

### Estado Actual de los Archivos de Configuración (Fase 3.11-R1)
Actualmente, el plugin opera con:
- `config.yml`: Parámetros globales, portal, logging, catálogo de habilidades, sección `dragons:` con el catálogo de variantes de dragón y sección de recompensas.
- `arenas.yml`: Catálogo de arenas (`bounds`, `center`, `podium`, `rules`).

*[Estado histórico 3.12-R1 | Resuelto en Fase 3.13 y consolidado en 3.13-R1]:* En la Fase 3.12, `ConfigurationLoader.java` resolvía únicamente un dragón (la clave `"default"` o la primera que encontrara). A partir de la Fase 3.13, se implementó el catálogo inmutable `DragonCatalog`, soportando múltiples `DragonDefinition` en `config.yml` (sección `dragons:`) indexadas por ID y seleccionables por comando o arena.

### Estructura de Configuración Objetivo (Target Configuration)
La evolución modular proyectada segmentará la configuración en archivos limpios con responsabilidades separadas (la división modular a múltiples archivos se mantiene como visión arquitectónica futura):
```text
plugins/BetterDragon/
├── config.yml           # Ajustes globales, almacenamiento SQLite y depuración (actualmente incluye dragons:)
├── dragons.yml          # [FUTURE] Propuesta de catálogo independiente de variantes de dragón y atributos
├── phases.yml           # [FUTURE] Definición declarativa de secuencias de fases de combate
├── abilities.yml        # Catálogo tipado de habilidades, proyectiles y telegrafiado
├── arenas.yml           # Geometría de arenas y políticas modulares anti-cheese
└── rewards.yml          # Pools de recompensas por rangos de contribución
```

---

## J. Arquetipos Conceptuales de Experiencia (Experience Profiles)

Los perfiles se conciben como **arquetipos conceptuales de experiencia** pendientes de implementación y calibración numérica:

1. **Classic+ (Arquetipo Conceptual):** La experiencia vanilla estilizada. Salud moderadamente aumentada, mitigación de abuso de camas, telegrafiado visual limpio y aliento persistente.
2. **Hardcore (Arquetipo Conceptual):** Mayor exigencia táctica, proyectiles rápidos, mitigación de agua activa y penalizaciones por permanencia en zonas estáticas.
3. **Raid Boss (Arquetipo Conceptual):** Diseñado para grupos coordinados. Escalado de salud activo, fases de invocación de esbirros y ventanas tácticas de daño.
4. **Chaos (Arquetipo Conceptual):** Combate impredecible con lluvia de proyectiles, inversiones gravitacionales y efectos ambientales dinámicos.
5. **Endgame (Arquetipo Conceptual):** Dificultad orientada a servidores con equipamiento avanzado y armaduras personalizadas, soft-enrage acelerado y múltiples fases de esbirros de soporte.

---

## K. Arquitectura Conceptual del Battle Designer (GUI)

El Battle Designer (Fase 3.19) se define bajo una regla arquitectónica inquebrantable:
```text
┌──────────────────────────────────────────────────────────────┐
│                    BATTLE DESIGNER (GUI)                     │
│                Vistas de Inventario y Menús                  │
└──────────────────────────────┬───────────────────────────────┘
                               │ delega acciones de usuario
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                  APPLICATION LAYER (Servicio)                │
│             Validación, mutaciones y snapshots               │
└──────────────────────────────┬───────────────────────────────┘
                               │ serializa / actualiza
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                  DOMAIN / CONFIGURATION STORAGE              │
│       Modelos inmutables en memoria y archivos YAML          │
└──────────────────────────────────────────────────────────────┘
```
**Regla:** La GUI es una simple vista interactiva; **nunca** interactúa directamente con el runtime de combate ni ejecuta lógica de juego.

---

## L. Plan de Validación y Banco de Experimentos

Toda mecánica candidata debe ser validada mediante los experimentos técnicos formalizados en [research/experiments/experiment_registry.md](file:///c:/Users/amaur/Desktop/SMP-Plugins/SMP-BetterDragon/BetterDragon/research/experiments/experiment_registry.md):
- `EXP-001`: Respuesta de `EnderDragon` ante `Attribute.MOVEMENT_SPEED`.
- `EXP-002`: Detección de objetivos y `Attribute.FOLLOW_RANGE`.
- `EXP-003`: Sincronización y forzado de fases vía `EnderDragonChangePhaseEvent`.
- `EXP-004`: Intercepción y mitigación de daño por camas y anclas (`ExplosionPolicy`).
- `EXP-005`: Confinamiento espacial y retorno perimetral a la arena (`Void Tether`).
- `EXP-006`: Legibilidad y tolerancias de latencia en telegrafiado visual.
- `EXP-007`: Limpieza determinista de esbirros con PDC tras victoria o aborto.
- `EXP-008`: Comparativa de modelos de escalado (Battle-Start vs Live Dynamic).
- `EXP-009`: Validación Runtime de BossBar Propia, Atributos de EnderDragon y Soft Enrage (11/11 Checks PASS) (`[VERIFIED BY RUNTIME SMOKE TEST / HIGH]`).

---

## M. Alcance de la Fase 3.13 y Consolidación 3.13-R1 (Implementation Scope) — `[IMPLEMENTADO]`

La Fase 3.13 y su consolidación 3.13-R1 implementaron:
1. **Catálogo de Múltiples Dragones:** Carga de un catálogo inmutable `DragonCatalog` desde la sección `dragons:` de `config.yml`, permitiendo seleccionar variantes por comando (`/bd start [mundo] [arena] [perfil]`) y congelándolas en snapshots inmutables.
2. **Control de Atributos Nativos del Dragón:** Aplicación formal de `Attribute.MAX_HEALTH`, `Attribute.MOVEMENT_SPEED` y `Attribute.FOLLOW_RANGE` durante el spawn en `DragonSpawner` (`Attribute.ATTACK_DAMAGE` no disponible/null en Paper 26.1.2-74).
3. **Escalado de Salud al Inicio de Batalla (*Battle-Start Scaling*):** Implementación de la fórmula de escalado determinista basada en los participantes presentes en la arena durante el cambio de estado `PREPARING -> ACTIVE`.
4. **Vinculación Espacial Arena → Dragón:** Conexión estricta de las coordenadas de spawn y podio (`dragon.setPodium()`) derivadas directamente de `ArenaDefinition`.
5. **Ampliación de Identidad PDC:** Escritura y validación formal de `betterdragon:definition_id` y `betterdragon:schema_version = 1` durante el spawn de la entidad.

---

## N. Alcance de la Fase 3.14 / 3.14-R1 (Encounter Presentation & Soft Enrage) — `[IMPLEMENTADO]`

La Fase 3.14 y su consolidación 3.14-R1 implementaron la presentación inmersiva y el escalado de intensidad de combate sin sobrearquitectura:
1. **BossBar Propia e Independiente:**
   - Presentación autónoma en Paper API (`org.bukkit.boss.BossBar`) preservando la supresión de la BossBar vanilla original.
   - Cálculo de progreso estrictamente acotado a `[0.0, 1.0]` inmune a `NaN`, `Infinity`, salud negativa y denominadores nulos.
   - Formateo configurable con placeholders `{dragon_name}`, `{phase}` y `{enrage}` explícito:
     - Si el título contiene `{enrage}`: activo -> `&c[ENRAGE]`, inactivo -> `""`.
     - Si el título NO contiene `{enrage}`: **no se añade automáticamente** ninguna etiqueta.
   - Sincronización en tiempo real de espectadores en arena con remoción determinista en desconexión, muerte y teletransporte.
   - Eliminación de `catch (Throwable ignored)` sustituido por logging contextual tipado.
2. **Feedback Sensorial de Transición de Fases:**
   - Reutilización de `BetterDragonPhaseChangeEvent` con notificación y emisión de audio real (`Sound.ENTITY_ENDER_DRAGON_GROWL`, volumen 1.0, pitch 1.0) a espectadores.
   - No se utiliza ni afirma `sendTitle()` en pantalla.
3. **Mecánica de Soft Enrage Transversal:**
   - Separación formal de las fases de combate: Enrage actúa como modificador transversal global aplicable en cualquier fase.
   - Activación monotónica irreversible (`false -> true`) al cruzar el umbral (`healthRatio <= threshold`, candidato `0.20`).
   - Aceleración efectiva de recarga de habilidades compatibles mediante `cooldownMultiplier` (candidato `0.75`; `< 1.0` acelera, `= 1.0` sin cambio, `> 1.0` retarda), preservando las definiciones inmutables de las habilidades.
   - Emisión de audio de furia (`Sound.ENTITY_ENDER_DRAGON_GROWL`, volumen 1.2, pitch 0.8).
4. **Aislamiento en Snapshot e Inmutabilidad:**
   - Congelamiento de perfiles de BossBar y Enrage en `BattleConfigurationSnapshot`. Los reloads globales (`/bd reload`) no alteran las batallas en curso.
5. **Validación Runtime Real (EXP-009 — 11/11 Checks PASS):**
   - Comprobaciones empíricas ejecutadas en Paper 26.1.2-74 (Java 25) con apagado limpio.
   - Matriz de evidencia rigurosa:
     - `Attribute.MAX_HEALTH`: 200.0 (OBSERVED).
     - `Attribute.MOVEMENT_SPEED`: 0.7 (OBSERVED; comportamiento locomotor no afirmado sin prueba física).
     - `Attribute.FOLLOW_RANGE`: 17.77 (OBSERVED; comportamiento IA no afirmado sin prueba física).
     - `Attribute.ATTACK_DAMAGE`: NOT SUPPORTED / NULL (`dragon.getAttribute(Attribute.ATTACK_DAMAGE) == null`).
     - `dragon.setPodium`: VERIFIED BY RUNTIME SMOKE TEST (API invocation smoke test; postcondición interna no expuesta).
     - Supresión vanilla: VERIFIED BY RUNTIME SMOKE TEST (controlador activo en mundo).
