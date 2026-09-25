# Registro Formal de Experimentos Técnicos de BetterDragon (Experiment Registry)

**Módulo:** BetterDragon Technical Validation Framework  
**Fase:** 3.12-R1 (Consolidación y Auditoría Pre-Commit)  
**Autor:** maurxp (@author maurxp)  
**Plataforma de Prueba:** Minecraft 26.1.2 | Paper `paper-26.1.2-74` | Java 25 (Temurin 25.0.4.1+1-LTS)  
**Estado:** `BANCO DE PRUEBAS DOCUMENTAL — PROTOCOLOS PENDIENTES DE EJECUCIÓN`

---

## 1. Estructura Estándar de un Experimento

Cada experimento técnico en BetterDragon sigue esta ficha estandarizada:

```text
EXP-XXX
Title:              Título descriptivo de la prueba
Hypothesis:         Hipótesis técnica o de jugabilidad a contrastar
Environment:        Versión exacta de Paper, build y entorno de JVM
Variables:          Factores de control y variables independientes
Procedure:          Pasos secuenciales y reproducibles de ejecución
Expected Result:    Resultado previsto según la hipótesis de trabajo
Observed Result:    Resultado empírico real (o PENDING si no se ha ejecutado)
Conclusion:         Deducción validada (o PENDING si no se ha ejecutado)
Confidence:         Nivel de certeza de la conclusión (PENDING implica HYPOTHESIS / MEDIUM o LOW)
Follow-up:          Acción de diseño o implementación derivada
```

---

## 2. Banco de Experimentos Técnicos y de Jugabilidad

### EXP-001: Respuesta de `EnderDragon` ante `Attribute.MOVEMENT_SPEED`
- **Hypothesis:** Modificar el valor base de `Attribute.MOVEMENT_SPEED` en Paper 26.1.2 altera de forma perceptible la aceleración y velocidad de crucero del dragón sin causar desincronización de sus hitboxes multipart.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:** Valores base de `MOVEMENT_SPEED` entre 0.6 (vanilla) y 1.2.
- **Procedure:** 
  1. Spawnear un dragón etiquetado con PDC en el End.
  2. Modificar el valor base mediante `dragon.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(valor)`.
  3. Medir el tiempo de traslación entre puntos nodales de vuelo en `CIRCLING`.
  4. Verificar colisiones de las alas contra un jugador en modo Survival.
- **Expected Result:** Aceleración suave perceptible; las sub-entidades `EnderDragonPart` acompañan la traslación sin desfases dentro del rango probado.
- **Observed Result:** `PENDING (Programado para Fase 3.13)`
- **Conclusion:** `PENDING`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Si se confirma aceleración estable, exponer el atributo como opción en `dragons.yml`.

---

### EXP-002: Alcance de `Attribute.FOLLOW_RANGE` y Detección en Arenas Masivas
- **Hypothesis:** Aumentar `Attribute.FOLLOW_RANGE` hasta 256.0 permite que la IA nativa del dragón mantenga aggro y detecte jugadores en arenas de más de 200 bloques de radio sin sobrecargar el hilo principal.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:** Radio de arena (128m vs 256m) y valor de `FOLLOW_RANGE` (128.0 vs 256.0).
- **Procedure:**
  1. Ubicar a un combatiente a 180 bloques del podio en una arena configurada en `arenas.yml`.
  2. Ajustar `FOLLOW_RANGE` a 128.0 vs 256.0.
  3. Comprobar si la IA selecciona al jugador para embestidas (`CHARGE_PLAYER`) o disparos de aliento (`STRAFING`).
  4. Monitorear los tiempos de tick del servidor (MSPT).
- **Expected Result:** En 128.0 el jugador es ignorado por estar fuera de rango; en 256.0 la IA lo detecta y lo ataca sin degradación apreciable de MSPT.
- **Observed Result:** `PENDING (Programado para Fase 3.13)`
- **Conclusion:** `PENDING`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Establecer un límite superior seguro en la configuración para salvaguardar el rendimiento.

---

### EXP-003: Sincronización y Transición Forzada de Fases de Vuelo
- **Hypothesis:** Invocar `dragon.setPhase(Phase.LAND_ON_PORTAL)` o `dragon.setPhase(Phase.CIRCLING)` desde el hilo principal fuerza la transición de vuelo de forma inmediata y dispara limpiamente `EnderDragonChangePhaseEvent`.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:** Fases de origen y fases objetivo en `EnderDragon.Phase`.
- **Procedure:**
  1. Durante la fase de vuelo circular, ejecutar una llamada programada a `dragon.setPhase(Phase.LAND_ON_PORTAL)`.
  2. Capturar y registrar el evento `EnderDragonChangePhaseEvent`.
  3. Observar la trayectoria de vuelo hacia el podio.
- **Expected Result:** El dragón interrumpe el patrón circular e inicia de inmediato la aproximación al podio sin comportamientos erráticos.
- **Observed Result:** `PENDING (Programado para Fase 3.14)`
- **Conclusion:** `PENDING`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Evaluar si cancelar `EnderDragonChangePhaseEvent` mediante `event.setCancelled(true)` es seguro o si puede generar ciclos infinitos en el motor de Paper.

---

### EXP-004: Identificación y Mitigación de Fuentes de Explosión (`ExplosionPolicy`)
- **Hypothesis:** En Paper 26.1.2, es posible discriminar con precisión el origen de una explosión (cama en el End, ancla de reaparición, TNT, o cristal de End legítimo) para atenuar selectivamente las explosiones abusivas sin interferir con la mecánica natural de los cristales.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:** Fuentes de daño explosivo (`BLOCK_EXPLOSION`, `ENTITY_EXPLOSION`, `DamageSource`), tipo de bloque causante y valor de mitigación (`TUNING_CANDIDATE: multiplier = 0.05`).
- **Procedure:**
  1. Inspeccionar cómo Paper 26.1.2 expone la causa de la explosión en `EntityDamageEvent` y `EntityDamageByBlockEvent` (por ejemplo, mediante `DamageSource#getDirectEntity()` o el bloque de origen).
  2. Verificar si `BLOCK_EXPLOSION` permite distinguir por sí sola entre una cama y un ancla de reaparición.
  3. Aplicar un factor de daño configurable y medir el impacto resultante sobre la vida del dragón.
  4. Detonar un cristal de End mientras cura al dragón y confirmar que el daño de rebote vanilla no sea mitigado.
- **Expected Result:** Se determina la API precisa para clasificar la causa de la explosión y se atenúa selectivamente el daño de camas y anclas, preservando el daño de cristales.
- **Observed Result:** `PENDING (Programado para Fase 3.16)`
- **Conclusion:** `PENDING`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Diseñar la interfaz `ExplosionPolicy` en `arenas.yml` basándose en los resultados de identificación.

---

### EXP-005: Confinamiento Espacial de Jugadores y Restricción de Perímetro
- **Hypothesis:** Monitorear periódicamente las coordenadas de los participantes dentro de `ArenaBounds` permite detectar incursiones fuera del perímetro y aplicar medidas de retorno (*BoundaryPolicy*) sin provocar falsos positivos por empuje o desincronización de movimiento.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:** Cadencia de escaneo (cada 10 vs 20 ticks), acciones correctivas (`WARN`, `PUSH`, `TELEPORT`, `DAMAGE`) y tolerancia en bordes.
- **Procedure:**
  1. Un combatiente sale de la AABB de la arena durante el estado `ACTIVE`.
  2. La tarea de escaneo detecta que `!bounds.contains(playerLocation)`.
  3. Ejecutar la política asignada y medir la respuesta del cliente.
- **Expected Result:** El jugador recibe una advertencia visual/acústica clara y es reorientado o teletransportado hacia `arena.center()` sin atascos de colisión.
- **Observed Result:** `PENDING (Programado para Fase 3.16)`
- **Conclusion:** `PENDING`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Evaluar si `PUSH` o `TELEPORT` resulta más agradable y fluido para los jugadores.

---

### EXP-006: Legibilidad y Latencia del Telegrafiado Sensorial
- **Hypothesis:** Determinar experimentalmente el intervalo temporal óptimo para avisos telegrafiados según la severidad del ataque, de modo que los jugadores con latencia de red moderada (50–200 ms) dispongan de tiempo suficiente para reaccionar y esquivar.
- **Environment:** Paper 26.1.2-74, cliente conectado con latencia simulada.
- **Variables:**
  - Severidad: `MINOR` (propuesto `0.8s`), `MODERATE` (`1.5s`), `MAJOR` (`2.0s`), `LETHAL` (`3.0s`).
  - Latencia de red: 50 ms vs 150 ms vs 250 ms.
  - Radio del área telegrafiada: 3m vs 6m vs 10m.
  - Velocidad del jugador: caminando vs sprint normal vs sprint con poción de velocidad.
- **Procedure:**
  1. Proyectar indicador de partículas en el suelo y sonido de carga.
  2. El jugador intenta escapar del radio al percibir la señal.
  3. Registrar la tasa de éxito de escape en cada combinación de variables.
- **Expected Result:** Identificar las ventanas temporales mínimas que permitan un juego justo y basado en habilidad sin que los ataques se vuelvan triviales de esquivar.
- **Observed Result:** `PENDING (Programado para Fase 3.15)`
- **Conclusion:** `PENDING (No se asume ningún valor como óptimo previo a la prueba)`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Calibrar los valores definitivos de `TUNING_CANDIDATE` en `abilities.yml`.

---

### EXP-007: Identificación y Limpieza Determinista de Minions con PDC
- **Hypothesis:** Marcar a todas las entidades invocadas con `betterdragon:managed = true`, `betterdragon:battle_id = <uuid>` y `betterdragon:minion = true` permite que una rutina de barrido selectivo elimine el 100% de los esbirros al finalizar o abortar la batalla sin afectar a mobs pacíficos ni entidades ajenas en la arena.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:** 20 esbirros invocados (Endermites, Phantoms) entremezclados con Endermen nativos de la isla.
- **Procedure:**
  1. Invocar 20 entidades con PDC firmado durante el estado `ACTIVE`.
  2. Forzar la terminación de la batalla vía `/bd abort`.
  3. Ejecutar la rutina de limpieza inspeccionando entidades en el chunk o radio de la arena.
- **Expected Result:** Los 20 esbirros de la batalla son removidos inmediatamente (`entity.remove()`); ningún Enderman ni mob nativo sufre alteraciones.
- **Observed Result:** `PENDING (Programado para Fase 3.15)`
- **Conclusion:** `PENDING`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Integrar la rutina de sweep en `BattleSession#terminate()` y `BattleSession#abort()`.

---

### EXP-008: Comparativa de Modelos de Escalado de Salud: Battle-Start vs Live Dynamic
- **Hypothesis:** Comparar empíricamente dos enfoques de escalado de salud por jugador para determinar cuál proporciona la mejor experiencia de combate, coherencia visual y estabilidad operativa en un servidor survival.
- **Environment:** Paper 26.1.2-74, Java 25.
- **Variables:**
  - **Modelo A (Battle-Start Scaling):** La vida máxima se calcula de forma definitiva en la transición `PREPARING -> ACTIVE` basándose en los jugadores presentes en la arena.
  - **Modelo B (Live Dynamic Scaling):** La vida máxima se recalcula en tiempo real cada vez que un jugador entra o sale del perímetro de la arena durante el combate.
- **Métricas de Evaluación:**
  - Duración total del encuentro.
  - Coherencia visual de la BossBar (¿se percibe como curación injusta al aumentar la vida máxima?).
  - Impacto de jugadores que mueren, se desconectan o reingresan a mitad de la batalla.
  - Complejidad y estabilidad de la máquina de estados de fases.
- **Procedure:**
  1. Ejecutar combates con 2 jugadores iniciales, simulando la entrada de 3 jugadores adicionales a mitad de la pelea bajo ambos modelos.
  2. Registrar la retroalimentación de los participantes y el comportamiento de las fases.
- **Expected Result:** Medir objetivamente las ventajas y desventajas de ambos modelos para tomar una decisión informada.
- **Observed Result:** `PENDING (Programado para Fase 3.13)`
- **Conclusion:** `PENDING (Ambos modelos permanecen como hipótesis; ninguno se declara ganador antes del playtesting)`
- **Confidence:** `HYPOTHESIS / MEDIUM`
- **Follow-up:** Diseñar el modelo de escalado en la Fase 3.13 con flexibilidad para soportar o evaluar ambas variantes.

---

### EXP-009: Validación Runtime de Presentación, BossBar Propia, Atributos de EnderDragon y Soft Enrage (11/11 Checks PASS)
- **Title:** Validación empírica en servidor Paper 26.1.2 de la BossBar propia, supresión vanilla, ciclo de vida de presentación, disponibilidad real de atributos y comportamiento de Soft Enrage.
- **Objective:** Evaluar en un servidor Paper 26.1.2 real bajo matriz formal de evidencia:
  1. La supresión de la BossBar vanilla mediante el controlador de plataforma (`VERIFIED BY RUNTIME SMOKE TEST`).
  2. La inicialización de la BossBar propia de BetterDragon, sincronización de espectadores, cálculo de progreso acotado [0.0, 1.0] y actualización de título con semántica explícita de `{enrage}` (`VERIFIED BY RUNTIME SMOKE TEST`).
  3. Los atributos de `EnderDragon` en la API de Paper: distinguir presencia/valor de comportamiento real verificado (`OBSERVED` vs `NOT INDEPENDENTLY VERIFIED` vs `NOT SUPPORTED / NULL`).
  4. La invocación del método `setPodium` clasificada honestamente como smoke test de invocación API (`VERIFIED BY RUNTIME SMOKE TEST`).
  5. El modificador transversal Soft Enrage: activación por umbral `healthRatio <= threshold` (0.20), estricta monotonicidad (`false -> true`), no mutación de definiciones, y aceleración de cooldowns (`VERIFIED BY RUNTIME SMOKE TEST` y `VERIFIED BY TEST`).
  6. La semántica de descarga diferida (`DEFERRED_PENDING_CHUNK_LOAD`) y limpieza al abortar (`VERIFIED BY RUNTIME SMOKE TEST`).
- **Environment:**
  - Minecraft Java 26.1.2
  - Paper `paper-26.1.2-74`
  - Java 25 (OpenJDK Temurin 25.0.4.1+1-LTS)
  - Pure Paper runtime (sin ProtocolLib, sin Folia, sin plugins externos).
- **Variables:**
  - Salud del dragón (200.0 HP base, daño gradual a 170.0 [85%], 140.0 [70%], 36.0 [18%], curación a 200.0 [100%]).
  - Umbral de fase 2 (`healthRatioThreshold = 0.75`).
  - Umbral de Soft Enrage (`threshold = 0.20`, candidato de tuning: `TUNING_CANDIDATE`).
  - Multiplicador de cooldown de habilidades (`cooldownMultiplier = 0.75`, candidato de tuning: `TUNING_CANDIDATE`).
  - Estado de carga del chunk (Cargado -> Descargado diferido -> Reanudado).
- **Procedure:**
  1. Compilar el plugin (`mvn clean package -DskipTests`) y desplegar en servidor de pruebas Paper 26.1.2.
  2. Iniciar el servidor mediante script de validación externa (ubicado en `../validation/test_presentation_integration.py`).
  3. Ejecutar rutina de verificación de presentación (hook temporal de auditoría retirado tras validación).
  4. Inspeccionar atributos de `EnderDragon` y registrar presencia, valores leídos y ausencias.
  5. Aplicar daño escalonado para observar el progreso de la BossBar y transición de fase.
  6. Cruzar el umbral de Enrage (18% <= 20%) y verificar título de BossBar y aceleración de cooldown.
  7. Curar al dragón al 100% para verificar la monotonicidad estricta de Enrage.
  8. Forzar descarga de chunk a `DEFERRED_PENDING_CHUNK_LOAD` y posterior reanudación.
  9. Abortar la batalla y verificar la limpieza de la BossBar y espectadores.
  10. Apagar el servidor y comprobar la ausencia de excepciones o fugas.
- **Evidence Matrix & Observed Results:**
  - **Check 1 — Batalla Activa:** `VERIFIED BY RUNTIME SMOKE TEST`. Sesión iniciada en estado `ACTIVE` con dragón generado.
  - **Check 2 — Supresión Vanilla BossBar:** `VERIFIED BY RUNTIME SMOKE TEST`. Controlador `VanillaBossBarController` activo en mundo. Nota: Valida integración en servidor; no constituye prueba exhaustiva de paquetes a nivel cliente.
  - **Check 3 — BetterDragon BossBar Propia:** `VERIFIED BY RUNTIME SMOKE TEST`. Inicializada con título `Ender Dragon §7• §fphase_1`, color `PURPLE`, estilo `SOLID`, progreso `1.0`.
  - **Check 4 — Atributos de EnderDragon en Paper 26.1.2-74:**
    - `Attribute.MAX_HEALTH`: `OBSERVED`. Presente con valor base `200.0`.
    - `Attribute.MOVEMENT_SPEED`: `OBSERVED` (presencia y valor `0.7` leídos; comportamiento físico locomotor no verificado de forma independiente: `NOT INDEPENDENTLY VERIFIED`).
    - `Attribute.FOLLOW_RANGE`: `OBSERVED` (presencia y valor `17.77` leídos; comportamiento de IA de detección no verificado de forma independiente: `NOT INDEPENDENTLY VERIFIED`).
    - `Attribute.ATTACK_DAMAGE`: `NOT SUPPORTED / NULL`. Retorna `null` desde `dragon.getAttribute(Attribute.ATTACK_DAMAGE)`. No está soportado nativamente en `EnderDragon` en Paper 26.1.2.
  - **Check 5 — dragon.setPodium():** `VERIFIED BY RUNTIME SMOKE TEST`. Invocación exitosa sin excepción (`API invocation smoke test`). Limitación: la postcondición interna no es legible vía Bukkit API (no existe getter público).
  - **Check 6 — Progreso de Salud:** `VERIFIED BY RUNTIME SMOKE TEST` y `VERIFIED BY TEST`. Progreso actualizado a 85.00% y acotado estrictamente a `[0.0, 1.0]`.
  - **Check 7 — Transición de Fase y Feedback:** `VERIFIED BY RUNTIME SMOKE TEST`. Al caer al 70%, `PhaseRuntime` transicionó a `phase_2`, BossBar actualizó título a `Ender Dragon §7• §fphase_2` y se emitió audio (`ENTITY_ENDER_DRAGON_GROWL`). No se afirma ni utiliza `sendTitle` en pantalla.
  - **Check 8 — Soft Enrage y Cooldown Scaling:** `VERIFIED BY RUNTIME SMOKE TEST` y `VERIFIED BY TEST`. Al 18% (<= 20%), Enrage se activó (`true`), el título reflejó `[ENRAGE]`, y el cooldown efectivo se escaló de 200 ticks a 150 ticks (`Math.round(200 * 0.75)`).
  - **Check 9 — Monotonicidad Estricta:** `VERIFIED BY RUNTIME SMOKE TEST` y `VERIFIED BY TEST`. Al curar al 100%, Enrage permaneció activo (`true`), confirmando irreversibilidad.
  - **Check 10 — Chunk Unload Diferido:** `VERIFIED BY RUNTIME SMOKE TEST`. `deferPendingChunkLoad()` ocultó la BossBar sin tratarlo como muerte; `resumeFromChunkLoad()` restauró el combate y la visibilidad.
  - **Check 11 — Abort & Viewer Zero-Leak Cleanup:** `VERIFIED BY RUNTIME SMOKE TEST` y `VERIFIED BY TEST`. Invocación de `abortBattle` dejó 0 espectadores en BossBar. (En servidor headless sin clientes reales, pre-abort es 0; el tracking y remoción de múltiples espectadores reales está exhaustivamente demostrado en pruebas unitarias deterministas `DragonBossBarTest`).
- **Conclusion:**
  La presentación visual y el Soft Enrage operan de forma coherente y determinista. Se confirma empíricamente que `Attribute.ATTACK_DAMAGE` no existe en `EnderDragon`, por lo que el daño debe ser gestionado mediante interceptores de eventos o habilidades.
- **Confidence:** `VERIFIED BY RUNTIME SMOKE TEST / HIGH`
- **Limitations:**
  Entorno de prueba de servidor headless sin jugadores de red reales conectados físicamente. Valores de `threshold: 0.20` y `cooldown-multiplier: 0.75` permanecen como `TUNING_CANDIDATE` sujetos a playtesting futuro.
