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
- **BetterDragon Combat Phase (`PhaseDefinition`):** Representa el estado narrativo y de progresión del encuentro (ej. FASE_1 al 100%, FASE_2 al 75%, ENRAGE al 20%). Gobierna los pools de habilidades activas, multiplicadores de daño y presentación del encuentro.
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
   Marcado síncrono del dragón en el spawn con `betterdragon:managed = true` y `betterdragon:battle_id = <uuid>`.  
   *Nota de Auditoría:* Las claves `betterdragon:definition_id` y `betterdragon:schema_version` están actualmente reservadas para fases futuras y **no** se escriben todavía en el PDC físico del spawn.
3. **Tracking Determinista de Daño y `TOP_DAMAGE` (`CombatRuntime`):**  
   Acumulación monotónica del daño por jugador con desempate determinista por orden de primer impacto.
4. **Reparto Proporcional de Botín y Recompensas (`RewardAllocationEngine`):**  
   División de pools de recompensa basada en el porcentaje de daño aportado por cada participante elegible, sin modelo *winner-takes-all*.
5. **Persistencia Durable y Buzón de Reclamo (`SQLiteClaimStorage`):**  
   Almacenamiento transaccional asíncrono en SQLite (esquema v2) con comando interactivo de reclamo `/bd claim`.
6. **Supresión NMS Aislada de BossBar Vanilla:**  
   Ocultamiento automático de la barra nativa para sustituirla por la BossBar estilizada administrada por el plugin.

---

## D. Mecánicas Candidatas en Diseño (Candidate Mechanics)

Propuestas de diseño maduras derivadas de la investigación, planificadas para fases posteriores:

### 1. Control de Atributos Nativos (Target: Fase 3.13)
- `Attribute.MAX_HEALTH`: Salud base personalizable mediante configuración.
- `Attribute.MOVEMENT_SPEED`: Ajuste fino de aceleración de vuelo.
- `Attribute.FOLLOW_RANGE`: Ampliación del rango de detección en arenas de gran tamaño.

### 2. Escalado de Salud por Jugador al Inicio de Batalla (*Battle-Start Scaling*) (Target: Fase 3.13)
Ajuste de la salud máxima del dragón al momento de spawnear según los participantes presentes:
$$\text{SaludEfectiva} = \text{SaludBase} \times \min\left(\text{Cap}, 1.0 + (N_{\text{activos}} - 1) \times \alpha\right)$$
Donde $\alpha$ y $\text{Cap}$ son candidatos iniciales de calibración (`TUNING_CANDIDATE`).

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
- `config.yml`: Parámetros globales, portal, logging, catálogo de habilidades, sección `dragons` con un único dragón activo (por defecto `"default"`) y sección de recompensas.
- `arenas.yml`: Catálogo de arenas (`bounds`, `center`, `podium`, `rules`).

*Limitación de Código Auditada:* En `ConfigurationLoader.java` (líneas 265-267), el cargador resuelve un único `DragonDefinition` para la sesión (la clave `"default"` o la primera que encuentre). No existe aún un mapa en memoria `Map<String, DragonDefinition>` seleccionable dinámicamente por arena o comando.

### Estructura de Configuración Objetivo (Target Configuration)
La evolución modular proyectada segmentará la configuración en archivos limpios con responsabilidades separadas:
```text
plugins/BetterDragon/
├── config.yml           # Ajustes globales, almacenamiento SQLite y depuración
├── dragons.yml          # Catálogo completo de variantes de dragón y atributos
├── phases.yml           # Definición declarativa de secuencias de fases de combate
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

---

## M. Alcance Recomendado para la Fase 3.13 (Implementation Scope)

La Fase 3.13 representará el primer paso de implementación de este diseño. Su alcance específico se limitará a:
1. **Catálogo de Múltiples Dragones:** Evolución de `ConfigurationLoader` para cargar un mapa tipado `Map<String, DragonDefinition>` desde `dragons.yml`, permitiendo seleccionar variantes por arena o comando.
2. **Control de Atributos Nativos del Dragón:** Aplicación formal de `Attribute.MAX_HEALTH`, `Attribute.MOVEMENT_SPEED` y `Attribute.FOLLOW_RANGE` durante el spawn en `DragonSpawner`.
3. **Escalado de Salud al Inicio de Batalla (*Battle-Start Scaling*):** Implementación de la fórmula de escalado basada en los participantes presentes en la arena durante el cambio de estado `PREPARING -> ACTIVE`.
4. **Vinculación Espacial Arena → Dragón:** Conexión estricta de las coordenadas de spawn y podio (`dragon.setPodium()`) derivadas directamente de `ArenaDefinition`.
5. **Ampliación de Identidad PDC:** Escritura formal de `betterdragon:definition_id` durante el spawn de la entidad.
