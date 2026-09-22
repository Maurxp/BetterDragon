# Registro Formal de Decisiones de Diseño de Gameplay (Decisions Record)

**Módulo:** BetterDragon Architecture & Design Governance  
**Fase:** 3.12-R1 (Consolidación, Auditoría y Cierre de Gameplay Research/Design)  
**Autor:** maurxp (@author maurxp)  
**Categorías de Estado:** `PRINCIPLE` | `DECISION` | `CANDIDATE` | `HYPOTHESIS` | `TUNING` | `FUTURE` | `REJECT`  

---

## 1. Principios Rectores de Diseño (Principles)

### PRIN-01: Separación Arquitectónica entre Combat Phase y Flight Phase
* **Status:** `PRINCIPLE`
* **Evidence:** `Paper API: EnderDragon.Phase`, `BetterDragon: PhaseDefinition.java` (`SOURCE_CODE / HIGH`).
* **Rationale:** La fase de combate de BetterDragon (`PhaseDefinition`) representa el estado narrativo y progresión del encuentro (ej. FASE_1, FASE_2, ENRAGE), mientras que la fase de vuelo vanilla (`EnderDragon.Phase`) representa la cinemática e IA interna del mob (`CIRCLING`, `LAND_ON_PORTAL`, etc.). Son dos máquinas de estados independientes que cooperan: la fase de combate solicita, escucha o permite fases de vuelo, pero nunca deben tratarse como equivalentes.
* **Consequences:** Desacopla la lógica de juego del ciclo interno de Paper, permitiendo cambiar ataques sin romper el vuelo del dragón.
* **Validation Needed:** Ninguna adicional; principio estructural formalizado.
* **Implementation Target:** Arquitectura base de Fases (Fases 3.5 y 3.14).

### PRIN-02: Telegrafiado Sensorial de Ataques de Alto Impacto
* **Status:** `PRINCIPLE`
* **Evidence:** `Action RPG & MMORPG Literature`, `EXP-006` (`DOCUMENTED / HIGH`).
* **Rationale:** Los ataques que infligen gran daño o causan desplazamientos masivos sin previo aviso generan frustración y se perciben como muertes injustas. Todo ataque de alto impacto debe proveer advertencias visuales y auditivas antes de su activación física.
* **Consequences:** El combate premia la habilidad de movimiento y la coordinación táctica del jugador.
* **Validation Needed:** Calibrar tiempos de reacción con latencia de red (`EXP-006`).
* **Implementation Target:** Ability Engine (Fase 3.15).

### PRIN-03: Confinamiento Estricto de NMS (0% en Dominio y Gameplay)
* **Status:** `PRINCIPLE`
* **Evidence:** `BetterDragon Architecture Spec` (`SOURCE_CODE / HIGH`).
* **Rationale:** El núcleo de dominio, el combate y las habilidades operan al **0% NMS**, usando exclusivamente las APIs públicas y deterministas de Paper 26.1.2. El uso de NMS queda restringido de forma única y aislada al adaptador de infraestructura para la supresión de la BossBar vanilla (`platform.bossbar`).
* **Consequences:** Cero deuda técnica por actualización de versiones de Minecraft y estabilidad a largo plazo.
* **Validation Needed:** Auditoría de dependencias en cada compilación.
* **Implementation Target:** Todas las fases.

---

## 2. Decisiones Arquitectónicas Firmes (Decisions)

### DEC-01: Control de Atributos Nativos mediante Paper API
* **Status:** `DECISION (CONSOLIDADA / IMPLEMENTADA EN FASE 3.13)`
* **Evidence:** `Paper Javadoc: LivingEntity#getAttribute(Attribute)` (`SOURCE_CODE / HIGH`).
* **Rationale:** Permite calibrar la vida máxima (`Attribute.MAX_HEALTH`), velocidad (`MOVEMENT_SPEED`), rango de seguimiento (`FOLLOW_RANGE`) y daño de ataque (`ATTACK_DAMAGE`) sin recurrir a paquetes internos de servidor.
* **Consequences:** Soporte transparente de perfiles de atributos cargados desde configuración (`DragonAttributes`), aplicados de forma segura en `DragonSpawner`.
* **Validation Needed:** Validado con tests unitarios en `DragonAttributesTest` y `DragonSpawnerTest`.
* **Implementation Target:** Fase 3.13 (Completada).

### DEC-02: Rechazo Formal del Escalado de Modelo vía `Attribute.SCALE`
* **Status:** `REJECT`
* **Evidence:** `Mojira Issue Tracker: MC-267372` (`DOCUMENTED / HIGH`).
* **Rationale:** En Minecraft 26.1.2, el Ender Dragon ignora `generic.scale`; sus hitboxes multipart (`EnderDragonPart`) permanecen estáticas, lo que corrompe el cálculo de colisiones.
* **Consequences:** BetterDragon **no soporta actualmente** el escalado del Ender Dragon mediante `Attribute.SCALE`. Las variantes del dragón se diferencian mediante efectos de partículas, auras, velocidad y habilidades únicas.
* **Validation Needed:** Reevaluar si Mojang o Paper corrigen el bug en versiones futuras.
* **Implementation Target:** N/A (Descartado).

### DEC-03: Catálogo Objetivo de Múltiples Definiciones de Dragón
* **Status:** `DECISION (CONSOLIDADA / IMPLEMENTADA EN FASE 3.13)`
* **Rationale:** Soporta un catálogo inmutable `DragonCatalog` de múltiples definiciones cargadas desde `config.yml` (sección `dragons:`), permitiendo variantes de jefe seleccionables por comando (`/bd start [mundo] [arena] [perfil]`) o asignadas por configuración, con congelación inmutable en `BattleConfigurationSnapshot`. La separación física a un archivo dedicado `dragons.yml` se clasifica como propuesta arquitectónica `FUTURE`.
* **Consequences:** Superado el modelo de perfil único o fallback al primer key YAML. Soporte de validación de identificadores, nombres legibles y aislamiento ante recargas (`/bd reload`).
* **Validation Needed:** Validado con tests unitarios en `DragonCatalogTest`, `DragonConfigurationLoaderTest` y `DragonBattleSnapshotReloadTest`.
* **Implementation Target:** Fase 3.13 / 3.13-R1 (Completada).

### DEC-04: Atribución de Victoria y Reparto Proporcional de Botín
* **Status:** `DECISION (CONSOLIDADA)`
* **Evidence:** `CombatRuntime.java:47-50`, `RewardAllocationEngine.java:55-80` (`SOURCE_CODE / HIGH`).
* **Rationale:** El modelo de último golpe (*last-hit*) fue superado en Fase 3.7 y 3.8 en favor de la atribución por mayor daño acumulado (`TOP_DAMAGE`) y redistribución proporcional con buzón durable en SQLite.
* **Consequences:** Cero pérdidas de ítems y erradicación del *kill stealing*.
* **Validation Needed:** Ya validado con 314 tests automáticos.
* **Implementation Target:** Fases 3.7, 3.8, 3.9, 3.10 (Completadas).

---

## 3. Mecánicas Candidatas en Diseño (Candidates)

### CAND-01: Escalado de Salud por Jugador al Inicio de Batalla (*Battle-Start Scaling*)
* **Status:** `DECISION (IMPLEMENTADA EN FASE 3.13)`
* **Evidence:** Literatura de diseño de incursiones y MMO raid boss design (`DOCUMENTED / MEDIUM`).
* **Rationale:** Ajustar la salud del dragón al momento de spawnear basándose en la cantidad de participantes presentes en la arena geométrica al iniciar la batalla. Evita los problemas de recálculo dinámico en vivo si jugadores mueren o se desconectan.
* **Consequences:** Fórmula implementada en pure-function `DragonScalingCalculator`: $\text{Salud} = \text{Base} \times \min(\text{Cap}, \max(1.0, 1.0 + (N_{\text{activos}} - 1) \times \alpha))$. Parámetros `health-per-player` ($\alpha$) y `max-multiplier` ($\text{Cap}$) configurables por perfil de dragón.
* **Validation Needed:** La implementación técnica de la fórmula fue validada exhaustivamente mediante `DragonScalingCalculatorTest` con casos de borde (0 jugadores, 1 jugador, N jugadores, capping superior y modo NONE). Los valores de tuning por defecto (`health-per-player: 0.25`, `max-multiplier: 3.0`) continúan clasificados como candidatos provisionales (`TUNING_CANDIDATE`) y requieren validación empírica mediante playtesting antes de considerarse balance definitivo.
* **Implementation Target:** Fase 3.13 (Completada).

### CAND-02: Mitigación de Explosiones de Camas y Anclas de Respawn
* **Status:** `CANDIDATE`
* **Evidence:** Mecánica documentada de explosiones de camas/anclas y vulnerabilidad en podio en Vanilla (`DOCUMENTED / HIGH`).
* **Rationale:** Permite erradicar la aniquilación del dragón en 10 segundos en el podio mediante colocación masiva de camas, forzando a los jugadores a combatir las mecánicas reales.
* **Consequences:** Requiere una política modular `ExplosionPolicy` en `ArenaRuleSet` que aplique un multiplicador atenuador a fuentes `BLOCK_EXPLOSION`.
* **Validation Needed:** `EXP-004 (PENDING)` — Verificar discriminación de causas de explosión en Paper API y comprobar que no afecte el daño legítimo de los cristales de End.
* **Implementation Target:** Fase 3.16.

### CAND-03: Bombardeo Aéreo de TNT con Terreno Inmune
* **Status:** `CANDIDATE`
* **Evidence:** Referencia histórica externa: KaevonD BetterDragon GitHub (commit `d05ad7f`: `MyListener.java`) (`SOURCE_CODE (external historical source code) / MEDIUM`; material externo no distribuido en este repositorio).
* **Rationale:** Convierte la fase de vuelo circular (`CIRCLING`), tradicionalmente pasiva, en una fase activa que exige esquivas dinámicas en tierra.
* **Consequences:** Genera proyectiles de dinamita con daño a bloques cancelado en `EntityExplodeEvent`.
* **Validation Needed:** Verificar que el spawn de entidades no genere tirones de rendimiento en el servidor.
* **Implementation Target:** Fase 3.15.

### CAND-04: Onda Expansiva al Aterrizar en el Podio (*Perch Shockwave*)
* **Status:** `CANDIDATE`
* **Evidence:** Referencia histórica externa: KaevonD BetterDragon GitHub (commit `d05ad7f`: `MyListener.java`) (`SOURCE_CODE (external historical source code) / MEDIUM`; material externo no distribuido en este repositorio).
* **Rationale:** Castiga el campeo preventivo en el podio de bedrock cuando el dragón aterriza, emitiendo un anillo expansivo de partículas con empuje vertical y daño calibrado.
* **Consequences:** Evita la trampa de KaevonD (que forzaba la salud a 1 HP) usando daño porcentual o numérico moderado.
* **Validation Needed:** Calibrar radio de expansión y velocidad de propagación.
* **Implementation Target:** Fase 3.15.

### CAND-05: Contrataques Reactivos por Daño Recibido
* **Status:** `CANDIDATE`
* **Evidence:** Referencia histórica externa: KaevonD BetterDragon GitHub (commit `d05ad7f`: `MyListener.java`) (`SOURCE_CODE (external historical source code) / MEDIUM`; material no distribuido) y documentación pública SpigotMC recurso 81439 (`DOCUMENTED / MEDIUM`).
* **Rationale:** Disuade el ataque seguro e ininterrumpido a larga distancia desde torres elevadas.
* **Consequences:** Dispara un proyectil o rayo cosmético con cooldown individual por agresor.
* **Validation Needed:** Playtest de tasa de activación para evitar spam descontrolado.
* **Implementation Target:** Fase 3.15.

### CAND-06: Invocación de Esbirros con Limpieza Garantizada vía PDC
* **Status:** `CANDIDATE`
* **Evidence:** Literatura de diseño de raid bosses (`DOCUMENTED / MEDIUM`) y modelo de persistencia PDC propio en BetterDragon (`SOURCE_CODE / HIGH`).
* **Rationale:** Divide la atención de los jugadores e introduce presión sobre el grupo de combate.
* **Consequences:** Cada mob invocado portará etiquetas PDC (`betterdragon:managed = true`, `betterdragon:battle_id = <uuid>`), permitiendo una limpieza inmediata y exhaustiva al culminar la sesión.
* **Validation Needed:** Validación del sweep de entidades en `EXP-007 (PENDING)`.
* **Implementation Target:** Fase 3.15.

### CAND-07: Confinamiento y Retorno a la Arena (*Void Tether*)
* **Status:** `CANDIDATE`
* **Evidence:** Modelo geométrico de límites de arena en BetterDragon (`ArenaBounds.java`) (`SOURCE_CODE / HIGH`).
* **Rationale:** Evita que los jugadores disparen desde chunks lejanos fuera de la zona de riesgo del jefe.
* **Consequences:** Aplica vector de empuje hacia el centro o teleportación con advertencia sensorial al cruzar el perímetro.
* **Validation Needed:** Calibración de la tolerancia perimetral en `EXP-005 (PENDING)`.
* **Implementation Target:** Fase 3.16.

### CAND-08: Furia en Salud Crítica (*Soft Enrage*)
* **Status:** `CANDIDATE`
* **Evidence:** `Raid Boss Literature` (`DOCUMENTED / HIGH`).
* **Rationale:** Aumenta drásticamente la tensión en el tramo final del combate acelerando la cadencia de habilidades o la velocidad de vuelo.
* **Consequences:** Transición automática al cruzar el umbral crítico final.
* **Validation Needed:** Playtest de balance para evitar muertes en cascada inevitables.
* **Implementation Target:** Fase 3.14.

---

## 4. Hipótesis Técnicas y de Jugabilidad (Hypotheses)

### HYP-01: Escalado Dinámico de Salud en Vivo con Entrada de Nuevos Jugadores
* **Status:** `HYPOTHESIS`
* **Evidence:** `EXP-008` (`PROPOSAL / MEDIUM`).
* **Rationale:** Evaluar si aumentar la vida máxima en pleno combate cuando entran nuevos jugadores resulta más justo o si genera frustración al ver la barra de vida "regenerarse" artificialmente.
* **Validation Needed:** Comparativa A/B contra *Battle-Start Scaling* en `EXP-008`.
* **Implementation Target:** Post-3.13.

### HYP-02: Aturdimiento (*Stun*) al Destruir un Cristal en Canalización
* **Status:** `HYPOTHESIS`
* **Evidence:** `Vanilla EnderCrystal Beam Interaction` (`DOCUMENTED / MEDIUM`).
* **Rationale:** Si un jugador destruye un cristal de End mientras este mantiene un haz de curación activo hacia el dragón, el jefe podría quedar aturdido en tierra durante 4 a 6 segundos, abriendo una ventana táctica de daño cuerpo a cuerpo.
* **Validation Needed:** Verificar si Bukkit permite suspender la IA de vuelo temporalmente sin bugs de gravedad.
* **Implementation Target:** Post-3.15.

---

## 5. Candidatos de Calibración Numérica (Tuning Candidates)

| Parámetro de Balance | Valor Propuesto | Clasificación | Justificación Inicial | Método de Validación |
| :--- | :---: | :---: | :--- | :--- |
| **Telegraph Minor** | `0.8 s` | `TUNING_CANDIDATE` | Daño leve; requiere reacción rápida pero perdonable. | Playtest a corta distancia. |
| **Telegraph Moderate**| `1.5 s` | `TUNING_CANDIDATE` | Daño moderado; tiempo justo para sprintar fuera del radio. | Medición de desplazamiento. |
| **Telegraph Major** | `2.0 s` | `TUNING_CANDIDATE` | Daño severo; debe ser evitable incluso con 150ms de ping. | Prueba de latencia simulada (`EXP-006`). |
| **Telegraph Lethal** | `3.0 s` | `TUNING_CANDIDATE` | Ataque letal; advertencia clara y tiempo para cubrirse o saltar. | Evaluación con grupos diversos. |
| **Scaling Alpha ($\alpha$)**| `0.25` | `TUNING_CANDIDATE` | +25% de vida base por cada jugador adicional en la arena. | Curvas de duración de combate (`EXP-008`). |
| **Scaling Cap** | `3.0x` | `TUNING_CANDIDATE` | Límite superior para evitar que grupos masivos generen esponjas de 10k HP. | Medición de fatiga de combate. |
| **Bed Damage Mult.** | `0.05` | `TUNING_CANDIDATE` | Reduce el daño de camas en 95%, volviendo la estrategia ineficiente. | Prueba de combate con camas (`EXP-004`). |
| **Enrage Threshold** | `0.20` | `TUNING_CANDIDATE` | Se activa cuando la vida cae por debajo del 20%. | Medición de picos de tensión al cierre. |
| **Counterattack Cooldown**| `5.0 s` | `TUNING_CANDIDATE` | Límite de un contragolpe cada 5s por jugador para evitar spam. | Pruebas de fuego rápido con arco. |

---

## 6. Sistemas Futuros (Future)

### FUT-01: Battle Designer (Editor Gráfico de Batallas en GUI)
* **Status:** `FUTURE`
* **Rationale:** Permitir a los administradores diseñar secuencias de fases, habilidades y recompensas mediante menús interactivos en el juego.
* **Arquitectura Obligatoria:** La GUI nunca escribe lógica de juego directamente; debe ser una vista sobre la Application Layer que serializa hacia el modelo de configuración.
* **Implementation Target:** Fase 3.19.

### FUT-02: Exposición de Placeholders mediante PlaceholderAPI (PAPI)
* **Status:** `FUTURE`
* **Rationale:** Exponer estadísticas acumuladas del leaderboard (`top_damager`, `slayer_count`, etc.) para su uso en scoreboards o tablist.
* **Implementation Target:** Fase 3.20.

---

## 7. Conceptos Rechazados (Reject)

| Concepto | Estado | Justificación de Descarte |
| :--- | :---: | :--- |
| **Uso de ProtocolLib para Estatuas/NPCs** | `REJECT` | Dependencia innecesaria que compromete la portabilidad limpia ante nuevas versiones de Paper. |
| **Atribución Exclusiva por Último Golpe** | `REJECT` | Promueve el robo de muertes y la pasividad tóxica en servidores multijugador. |
| **Reconstrucción Destructiva de Portales** | `REJECT` | Corrompe mapas personalizados; BetterDragon respeta `portal.enabled: false`. |
| **Coordenadas Centrales Hardcodeadas a (0, 0)** | `REJECT` | Incompatible con arenas desplazadas en cualquier coordenada del mundo. |
| **Subordinación al Ciclo de Vida de `DragonBattle`** | `REJECT` | Conduce a inestabilidad y bugs de desincronización de chunks. |
