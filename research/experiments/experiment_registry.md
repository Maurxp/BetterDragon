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
