# BetterDragon — Auditoría de Mecánicas del Repositorio KaevonD/BetterDragon

**Documento:** Informe Técnico de Investigación Pre-Fase 3: Auditoría y Catálogo de Mecánicas de Referencia  
**Proyecto Principal:** BetterDragon  
**Workspace Exclusivo:** `SMP-BetterDragon` (`c:\Users\amaur\Desktop\SMP-Plugins\SMP-BetterDragon\`)  
**Directorio de Investigación:** `SMP-BetterDragon/research/KaevonD-BetterDragon/`  
**Autor del Análisis:** maurxp (@author maurxp)  
**Plataforma Objetivo de BetterDragon:** Minecraft 26.1.2 | Paper build `paper-26.1.2-74` | Java 25 (OpenJDK Temurin 25.0.4.1+1-LTS)  
**Repositorio Auditado:** `https://github.com/KaevonD/BetterDragon` (Commit `d05ad7f`, 11 de Enero de 2022)  
**Estado:** [INVESTIGACIÓN PRE-FASE 3 — AISLADA DE PRODUCCIÓN]  
**Fecha de Auditoría:** 2026-09-19  

---

## 1. INTRODUCCIÓN Y REGLAS DE AISLAMIENTO

Este documento constituye la ejecución de una **investigación independiente previa a la Fase 3** de BetterDragon. Su objetivo exclusivo es auditar el código fuente, la lógica de combate y los patrones de diseño del repositorio histórico de KaevonD para descubrir qué mecánicas, ideas de combate y comportamientos interesantes pueden inspirar o enriquecer el diseño modular de nuestro BetterDragon moderno.

### Reglas Inmutables de Trabajo:
1. **Aislamiento Total del Workspace:** El repositorio de referencia se descargó y clonó exclusivamente dentro de `SMP-BetterDragon/research/KaevonD-BetterDragon/`. No se mezcla con el código de producción ni se compila junto a él.
2. **Cero Código de Producción / No Inicio de Fase 3:** Esta investigación es estrictamente analítica y de diseño conceptual. No se implementa código Java en producción, no se estructuran paquetes finales ni se alteran las decisiones congeladas de Fase 2.5.3.
3. **Cero Infracción de Propiedad Intelectual / No Copia:** No se copia código fuente, no se clonan clases ni paquetes, y no se importa código propietario. Las ideas de juego se extraen de forma conceptual (diseño de cuarto limpio o *clean-room design*).
4. **Respeto a la Arquitectura Congelada de BetterDragon:** La arquitectura de BetterDragon (soberanía funcional, desacoplamiento de `DragonBattle`, `BattleSession`, `BattleResult`, persistencia SQLite, `TOP_DAMAGE`, redistribución proporcional, 0% NMS en MVP, `portal.enabled: false` estricto) permanece intacta e inalterable.

---

## 2. PERFIL TÉCNICO DEL REPOSITORIO AUDITADO

| Parámetro | Valor Identificado en el Repositorio de Referencia |
|---|---|
| **URL del Repositorio** | `https://github.com/KaevonD/BetterDragon` |
| **Nombre Interno del Plugin** | `HarderBosses` (especificado en `pom.xml` y `plugin.yml`) |
| **GroupId / ArtifactId** | `me.drysu` : `harderBosses` |
| **Versión Declarada** | `1.0` |
| **Versión de Java** | Java 1.8 (`<java.version>1.8</java.version>`) |
| **Plataforma / API Objetivo** | Spigot API `1.18.1-R0.1-SNAPSHOT` |
| **Sistema de Build** | Maven (`pom.xml` con `maven-compiler-plugin` 3.8.1 y `maven-shade-plugin` 3.2.4) |
| **Ruta Local de Salida Histórica** | `C:\Users\Kaevon\Desktop\spigotServer\plugins\HarderBosses.jar` |
| **Licencia** | **Sin Licencia Declarada (All Rights Reserved por defecto)** |
| **Dependencias en POM** | `org.spigotmc:spigot-api:1.18.1-R0.1-SNAPSHOT` (provided)<br>`me.clip:placeholderapi:2.10.10` (provided, no utilizada en código)<br>`io.github.skytasul:guardianbeam:2.1.0` (compile / shaded) |
| **Estructura de Archivos** | Exactamente 3 clases Java y 1 archivo de recursos: |
| | ├── `HarderBosses.java` (19 líneas) — Clase principal / registro de eventos |
| | ├── `SuperDragon.java` (10 líneas) — Modelo de flag booleano `revive` |
| | ├── `MyListener.java` (330 líneas) — Toda la lógica de combate, tareas y eventos |
| | └── `plugin.yml` (5 líneas) — Metadata mínima (`name`, `version`, `main`, `api-version: 1.18`) |
| **Archivos YAML de Configuración** | **Ninguno.** No existe `config.yml`. Todos los valores están 100% hardcodeados. |
| **Comandos Registrados** | **Ninguno.** No posee comandos ni subcomandos administrativos. |
| **Permisos Registrados** | **Ninguno.** |
| **Persistencia** | **Ninguna.** No usa base de datos ni archivos en disco. Estado volátil en RAM. |

---

## 3. AUDITORÍA EXHAUSTIVA POR CATEGORÍAS (1 A 14)

### 3.1 Combate
* **Hallazgos:**
  - El dragón posee una estructura de combate de dos fases continuas (`secondDragon` booleano). La primera fase es el dragón vanilla estándar; la segunda fase es un "Super Dragón" resucitado con mecánicas aumentadas.
  - La segunda fase altera atributos nativos: duplica la vida a 400.0 HP (`GENERIC_MAX_HEALTH`).
  - No existe tracking de daño por jugador ni lógica de mitigación de daño de camas o explosiones.
  - El dragón vincula sus ataques directamente al ciclo de fases de vuelo vanilla de `EnderDragonChangePhaseEvent`:
    - Al entrar en `Phase.CIRCLING` (orbitando la isla): ejecuta bombardeo aéreo de TNT.
    - Al entrar en `Phase.LAND_ON_PORTAL` (aterrizaje en el podio): desata una onda expansiva de partículas púrpuras.
    - Al entrar en `Phase.STRAFING` (pasada de ataque aéreo): dispara rayos láser y levita jugadores.
* **Evaluación:** La idea de sincronizar habilidades de jefe con las transiciones de vuelo de la IA (`EnderDragonChangePhaseEvent`) es conceptualmente brillante y perfectamente ejecutable en Paper API sin NMS.

### 3.2 Habilidades Especiales
El repositorio presenta 5 habilidades de combate activas bien definidas:
1. **Bombardeo Aéreo de TNT (*Justice Rains from Above*):** Durante el vuelo circular, el dragón engendra 21 bloques de TNT activada (`TNTPrimed`) que caen cada 20 ticks (1 segundo) con fusible de 80 ticks (4 segundos).
2. **Onda Expansiva Radial (*Don't Get Hit*):** Al aterrizar en el podio central, emite 128 rayos de partículas púrpuras en $360^\circ$ que avanzan horizontalmente. Si un rayo impacta a un jugador, reduce su salud a exactamente 2 HP (1 corazón).
3. **Láser de Levitación Masiva (*Sky Beam*):** Al volar en pasada rasante, selecciona a $\lceil N/3 \rceil$ jugadores, les conecta un rayo láser continuo durante 5 segundos y les aplica Levitación VII (elevándolos a gran altura para forzar daño por caída o MLG water bucket).
4. **Rayo Defensivo Anti-Agua (*Water is Cringe*):** Si un jugador pisa agua en el End, el dragón dispara un haz balístico de partículas rojas que lo persigue y le causa 13 de daño directo (~6.5 corazones).
5. **Contragolpe por Rayo Eléctrico (*Lightning Retaliation*):** 1 de cada 6 ataques recibidos por el dragón (flechas o golpes cuerpo a cuerpo) dispara un rayo (`strikeLightning`) directamente sobre las coordenadas del agresor.

### 3.3 Dificultad
* **Hallazgos:** No implementa un sistema dinámico ni configurable de dificultad. La dificultad está rígidamente fijada en código:
  - Fase 1: Dificultad vanilla común.
  - Fase 2: Dificultad "pesadilla" no parametrizable.
* **Evaluación:** El concepto puede generalizarse en BetterDragon mediante perfiles de dificultad (`DifficultyProfile`) que ajusten multiplicadores de daño, cantidad de proyectiles, cadencia y umbrales de activación.

### 3.4 Variantes del Dragón
* **Hallazgos:** En el repositorio solo existe una clase rudimentaria `SuperDragon.java` con un booleano `revive = true`. No existen perfiles, tipos ni variantes.
* **Evaluación:** Refuerza la conveniencia de la arquitectura de BetterDragon basada en `DragonDefinition` inmutables cargadas desde YAML (`dragons.yml`).

### 3.5 Vida y Estadísticas
* **Hallazgos:** Al resucitar el segundo dragón, se programa una tarea demorada (460 ticks tras el spawn) que modifica:
  ```java
  db.getEnderDragon().getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(400.0);
  db.getEnderDragon().setHealth(400.0);
  ```
* **Evaluación:** Modificación directa de atributos estándar de Bukkit/Paper. En BetterDragon esto ya se contempla mediante `DragonDefinition.attributes`, pero la idea de aumentar dinámicamente la vida máxima o regenerarla al conmutar de fase es muy atractiva.

### 3.6 Recompensas
* **Hallazgos:** **Cero recompensas personalizadas.** El plugin depende 100% de los drops nativos de Minecraft vanilla (orbes de XP y huevo vanilla en el portal).
* **Evaluación:** No aporta nada nuevo a nuestro sistema. El modelo de BetterDragon (`SlayerCalculator`, redistribución proporcional, `RewardManager`, `ClaimStorage`) es infinitamente superior y más avanzado.

### 3.7 Participación Multijugador
* **Hallazgos:**
  - El plugin escanea los jugadores presentes en el mundo: `e.getEntity().getWorld().getPlayers()`.
  - En la habilidad de levitación, selecciona un tercio aleatorio de los jugadores (`Math.ceil(playerList.size() / 3.0)`).
  - No existe tracking de daño, ni estadísticas por participante, ni leaderboard.
* **Evaluación:** La selección de subconjuntos de jugadores basada en la población actual de la arena ($\lceil N / k \rceil$) es una fórmula escalable útil para ataques grupales.

### 3.8 Arena / Entorno
* **Hallazgos:**
  1. **Protección de Cristales de End:** Hace invulnerables a los cristales durante la ceremonia de respawn (`canDamageCrystals = false`), cancelando `EntityDamageByEntityEvent`.
  2. **Supresión de Destrucción de Terreno por TNT:** Escucha `EntityExplodeEvent` para `TNTPrimed` y ejecuta `e.blockList().clear()`, permitiendo que la dinamita dañe jugadores sin pulverizar los bloques de la isla de End.
  3. **Trampa de Salida del Portal:** Al entrar un jugador al portal de salida durante la fase 2, el plugin busca los bloques `Material.END_PORTAL` en un radio de $5 \times 5$ y llama a `temp.breakNaturally()`, destruyendo el portal para atrapar a los demás jugadores.
* **Evaluación:**
  - La supresión de daño a bloques en `EntityExplodeEvent` es una excelente práctica.
  - La destrucción destructiva de bloques del portal vanilla es **inaceptable** en BetterDragon (viola el aislamiento de estructuras administradas). Sin embargo, el concepto de *bloqueo o trampa en la arena* puede lograrse cancelando el teletransporte con un vector de rechazo físico (*pushback*) y un aviso de advertencia.

### 3.9 Fases del Boss
* **Hallazgos:** Máquina de estados binaria hardcodeada:
  - `secondDragon == false`: Fase 1 (Combate preliminar).
  - `secondDragon == true`: Fase 2 (Super Dragón activo con todas las habilidades y 400 HP).
  - Transición orquestada en `EntityDeathEvent`: al morir el dragón de fase 1, coloca 4 cristales y llama a `DragonBattle.initiateRespawn()`.
* **Evaluación:** Muy rudimentario, pero valida la atracción que genera en los jugadores una batalla por etapas con una "segunda barra de vida" inesperada.

### 3.10 Eventos y Triggers
* **Triggers identificados en el código de KaevonD:**
  1. **`EntityDeathEvent` (Fase 1 completada):** Inicia temporizador de 500 ticks para respawn ceremonial.
  2. **`EntitySpawnEvent` (Dragón materializado):** Activa atributos de fase 2 tras 460 ticks.
  3. **`EnderDragonChangePhaseEvent`:**
     - A `CIRCLING`: Trigger de bombardeo TNT.
     - A `LAND_ON_PORTAL`: Trigger de anillo de choque púrpura.
     - A `STRAFING`: Trigger de rayos de levitación.
  4. **`EntityDamageByEntityEvent`:** Trigger de rayo punitivo por probabilidad (1/6).
  5. **`PlayerMoveEvent`:** Trigger de rayo anti-agua al detectar `Material.WATER` bajo los pies del jugador.
  6. **`EntityPortalEnterEvent`:** Trigger de trampa al tocar el portal.

### 3.11 Configuración
* **Hallazgos:** Inexistente. Cero archivos YAML.
* **Evaluación:** Esta es la mayor debilidad del repositorio histórico. Oportunidad directa para que BetterDragon abstraiga cada una de estas mecánicas en un esquema YAML declarativo limpio.

### 3.12 Comandos y Administración
* **Hallazgos:** Inexistentes. No hay recarga, no hay spawn manual, no hay inspección de estado.
* **Evaluación:** Reafirma el valor de la arquitectura de comandos `/betterdragon` y `/bd` ya consolidada en nuestra especificación.

### 3.13 Feedback al Jugador
* **Hallazgos:**
  - Diálogos del dragón en el chat global mediante `Bukkit.broadcastMessage`:
    - Uso de colores clásicos: `ChatColor.GOLD + "EnderDragon: " + ChatColor.RED + mensaje`.
    - Mensajes sarcásticos y amenazantes asociados a cada habilidad ("*Justice rains from above!*", "*Don't get hit ;)*", "*Water is cringe*").
  - Efectos visuales de partículas:
    - Redstone con `DustOptions(Color.PURPLE, 40)` para la onda expansiva.
    - Redstone con `DustOptions(Color.RED, 1000)` para el rayo anti-agua.
  - Rayos láser continuos usando la biblioteca `guardianbeam` conectando al dragón con los jugadores.
* **Evaluación:** El feedback mediante diálogos contextuales añade una inmensa personalidad al encuentro. En BetterDragon moderno, esto debe actualizarse hacia **Adventure MiniMessage**, títulos en pantalla (`Title`), barras de acción (`ActionBar`) y efectos de sonido nativos (`dragon.growl`, `wither.spawn`, etc.).

### 3.14 Integraciones
* **Hallazgos:**
  - `me.clip:placeholderapi`: Presente en el POM pero con cero líneas de código utilizándolo.
  - `io.github.skytasul:guardianbeam`: Biblioteca externa que genera entidades falsas de Guardian/Calamar vía reflection para renderizar el haz del guardián.
* **Evaluación:** En Paper 26.1.2 moderno (Java 25), no necesitamos librerías de terceros con reflection ni NMS. Los rayos visuales y avisos pueden implementarse limpiamente con partículas nativas (`Particle.DUST`, `Particle.VIBRATION`, `Particle.END_ROD`, `Particle.SONIC_BOOM`) o proyectiles mágicos sin dependencias externas.

---

## 4. DIFERENCIACIÓN ENTRE MECÁNICA E IMPLEMENTACIÓN

Es crítico no descartar una mecánica atractiva únicamente porque su implementación histórica fue rudimentaria, dependiente de NMS o defectuosa:

| Mecánica (Gameplay) | Implementación Histórica (KaevonD) | Implementación Moderna en BetterDragon (Paper 26.1.2) |
|---|---|---|
| **Segunda Fase / Revival** | Depende de `DragonBattle.initiateRespawn()` y spawn vanilla. Modifica campos estáticos globales. | Gestionado por la máquina de estados de `BattleSession`. Al llegar la salud a 0 en Fase 1, el dragón reproduce sonido de enrage, cambia temporalmente a invulnerable y recarga su barra propia sin tocar `DragonBattle`. |
| **Bombardeo de TNT en Vuelo** | Spawnea 21 `TNTPrimed` en un BukkitRunnable relativo. Modifica `e.blockList().clear()` en un listener global. | Tarea periódica acotada a `BattleSession`. Genera proyectiles o TNT con `originUuid` registrado, desactivando daño a bloques mediante flag de protección de arena. |
| **Onda Expansiva Radial** | 128 BukkitRunnables individuales disparados en un for loop. Fuerza `setHealth(2)` hardcodeado. | Algoritmo determinista en un único scheduler con radio creciente $R(t)$. Aplica daño configurable escalado por armadura o porcentaje de vida con `EntityDamageEvent`. |
| **Láser de Levitación** | Inyección de paquetes NMS mediante librería externa `guardianbeam`. | Partículas direccionales de alta densidad (`Particle.DUST` / `VIBRATION` hacia el jugador) o trazado de rayos con Paper API, aplicando `PotionEffectType.LEVITATION` configurable. |
| **Castigo por Campeo en Agua** | Listener en `PlayerMoveEvent` que chequea `world_the_end` y dispara partículas rojas con daño fijo de 13. | Módulo `AntiCheese` en `BattleSession`. Monitorea jugadores en la arena cada $N$ ticks; si están en agua o zonas seguras prohibidas, activa rayos de aliento o fuego de dragón cancelable. |
| **Contragolpe Eléctrico** | `rand.nextInt(6) == 1` invocando `strikeLightning` crudo. | Habilidad pasiva `LightningThorns` en `DragonDefinition` con probabilidad %, cooldown interno y compatibilidad con `strikeLightningEffect` para daño personalizado. |
| **Diálogos de Combate** | `Bukkit.broadcastMessage` con `ChatColor` hardcodeado en inglés. | Sistema `BossDialogue` en `dragons.yml` usando Adventure MiniMessage con soporte multilingüe, títulos y sonidos espaciales. |

---

## 5. MATRIZ GENERAL DE MECÁNICAS IDENTIFICADAS

A continuación se resume el inventario completo de mecánicas analizadas en el repositorio:

| ID | Nombre de la Mecánica | Comportamiento Principal | Configurable en Repo | Dependencias Repo | ¿Usa NMS / Reflection? | Complejidad | Compatibilidad Paper 26.1.2 | Clasificación BetterDragon |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **MEC-01** | **Revival / Multi-Fase** | Dragón revive con 400 HP y nuevas habilidades tras morir en fase 1. | No (Hardcoded) | `DragonBattle` | No (usa Spigot API) | Media | Alta (vía `BattleSession`) | **A — Candidata MVP (como Fase/HP)** |
| **MEC-02** | **TNT Carpet Bombing** | Lluvia de 21 dinamitas durante la fase de vuelo circular. | No (Hardcoded) | Bukkit API | No | Baja | Alta (100% Paper API) | **A — Candidata MVP (Habilidad)** |
| **MEC-03** | **Perch Shockwave Wave** | Anillo de partículas en $360^\circ$ al aterrizar que deja al jugador en 1 HP. | No (Hardcoded) | Bukkit Particles | No | Media | Alta (100% Paper API) | **A — Candidata MVP (Habilidad)** |
| **MEC-04** | **Laser Levitation Beam** | Haces de guardián que levitan a $1/3$ de los jugadores en fase rasante. | No (Hardcoded) | `guardianbeam` | **Sí** (en la librería) | Media | Alta (reemplazando partículas) | **B — Post-MVP** |
| **MEC-05** | **Anti-Water Strike** | Castiga con rayo de partículas rojas y 13 de daño a quien pise agua en la arena. | No (Hardcoded) | Bukkit API | No | Baja | Alta (100% Paper API) | **A — Candidata MVP (Anti-Cheese)** |
| **MEC-06** | **Lightning Counter-Attack** | 16.7% de probabilidad de lanzar un rayo al jugador que dañe al dragón. | No (Hardcoded) | Bukkit API | No | Baja | Alta (100% Paper API) | **A — Candidata MVP (Pasiva)** |
| **MEC-07** | **Terrain Explosion Protection** | Cancela la destrucción de bloques de terreno por la TNT del dragón. | No (Hardcoded) | Bukkit API | No | Baja | Alta (100% Paper API) | **A — Candidata MVP (Protección)** |
| **MEC-08** | **Crystal Invulnerability** | Bloquea la rotura de cristales de End mientras el dragón está resucitando. | No (Hardcoded) | Bukkit API | No | Baja | Alta (100% Paper API) | **B — Post-MVP** |
| **MEC-09** | **Portal Escape Trap** | Rompe los bloques de portal si un jugador intenta escapar durante la batalla. | No (Hardcoded) | Bukkit API | No | Baja | Nula (Viola arquitectura) | **D — No Adoptar (Rompe bloques)** |
| **MEC-10** | **Boss Chat Taunts** | Mensajes sarcásticos y amenazantes en chat al detonar cada ataque. | No (Hardcoded) | `Bukkit.broadcast` | No | Baja | Alta (vía Adventure) | **A — Candidata MVP (Cues)** |

---

## 6. TOP DE MECÁNICAS QUE MERECE LA PENA ESTUDIAR PARA BETTERDRAGON

De las 10 mecánicas analizadas, se seleccionan las **5 ideas de mayor valor jugable** para enriquecer el combate de BetterDragon:

### 1. [MEC-02] Bombardeo Aéreo de TNT (Air Strike en Fase de Vuelo)
* **Por qué es interesante:**  
  El Ender Dragon vanilla pasa largos periodos volando en círculos lejos del alcance cuerpo a cuerpo sin representar ninguna amenaza real para los jugadores en tierra. El bombardeo obliga a los jugadores a mantenerse en constante movimiento, mirar al cielo y coordinarse para esquivar explosiones.
* **Modernización en BetterDragon:**  
  Configurable en `dragons.yml` bajo la sección `abilities.carpet_bomb`:
  - `enabled: true`
  - `trigger_phase: CIRCLING`
  - `interval_seconds: 1.5`
  - `bomb_count: 10`
  - `fuse_ticks: 60`
  - `prevent_block_damage: true`
  - `sound: entity.tnt.primed`
* **Impacto Arquitectónico:** Nulo en el core. Se ejecuta como un scheduler subordinado al ciclo de vida de `BattleSession`. Cero NMS.

### 2. [MEC-05] Mecánica Anti-Cheese de Agua (Safe-Zone Denial)
* **Por qué es interesante:**  
  En Minecraft, la táctica predominante contra el Ender Dragon y los Endermen es colocar cubos de agua en el suelo para impedir que los Endermen ataquen y anular por completo el daño por caída y empuje. Esta táctica elimina la tensión del combate.
* **Modernización en BetterDragon:**  
  Módulo configurable `anti_cheese.water_punishment`:
  - En lugar de un ataque instantáneo invasivo, si un jugador permanece en agua más de $X$ segundos en la arena, el dragón lanza una bola de aliento ácida (`DragonFireball`) dirigida o electrifica el charco de agua eliminando la fuente.
* **Impacto Arquitectónico:** Totalmente desacoplado mediante eventos `PlayerMoveEvent` filtrados por la bounding box de la arena configurada en `arenas.yml`. Cero NMS.

### 3. [MEC-03] Onda Expansiva de Aterrizaje (Shockwave / Perch Wipe)
* **Por qué es interesante:**  
  En vanilla, la fase de aterrizaje en el podio suele ser la más fácil para los jugadores (se colocan detrás del dragón y lo atacan con camas o espadas con total impunidad). Una onda expansiva que se expande desde el podio obliga a saltar o alejarse temporalmente, creando una dinámica de "esquiva y contraataca".
* **Modernización en BetterDragon:**  
  En lugar de forzar arbitrariamente la vida del jugador a 1 corazón (lo cual es injusto y frustrante), se emite un anillo concéntrico de partículas `Particle.SONIC_BOOM` o `Particle.DUST` que inflige daño configurable y empuje vertical:
  - `wave_speed: 1.2`
  - `max_radius: 24`
  - `damage: 8.0`
  - `knockback: 1.5`
* **Impacto Arquitectónico:** Tarea temporal en el hilo principal de `BattleSession`. Cero NMS.

### 4. [MEC-06] Contragolpe Eléctrico / Espinas Reactivas
* **Por qué es interesante:**  
  Añade riesgo táctico al combate a distancia (francotiradores con arcos desde plataformas lejanas).
* **Modernización en BetterDragon:**  
  Implementar como habilidad pasiva `abilities.retaliation`:
  - Permite configurar una probabilidad (`chance: 0.15`), cooldown interno (`cooldown_seconds: 5`) y tipo de daño (`strikeLightningEffect` para evitar fuego incontrolado).
* **Impacto Arquitectónico:** Listener en `DragonDamageListener` dentro del main thread. Cero NMS.

### 5. [MEC-10] Diálogos y Cues de Combate (Boss Dialogue & Audio Feedback)
* **Por qué es interesante:**  
  Aporta identidad, personalidad y dinamismo. Transforma una entidad muda de Minecraft en un verdadero encuentro de jefe épico.
* **Modernización en BetterDragon:**  
  Integración plena con el ecosistema de Adventure API:
  - Formato MiniMessage en los archivos de configuración:
    `"<gradient:#ff0055:#ffaa00><bold>Dragón:</bold></gradient> <gray>¡La lluvia de fuego caerá sobre ustedes!</gray>"`
  - Proyección simultánea de subtítulo en pantalla (`Title`) o sonido envolvente.
* **Impacto Arquitectónico:** Utiliza exclusivamente Adventure API nativa de Paper. Cero NMS.

---

## 7. ANÁLISIS DE CONFLICTOS CON LA ARQUITECTURA DE FASE 2.5.3

Durante la auditoría se identificaron mecánicas en el repositorio histórico que **entran en conflicto directo** con las decisiones arquitectónicas congeladas de BetterDragon:

### Conflicto 1: Dependencia de `DragonBattle` Vanilla para el Respawn
* **Mecánica Histórica:** KaevonD invoca `world.getEnderDragonBattle().initiateRespawn()` y coloca 4 cristales físicos en el portal para reiniciar el combate vanilla.
* **Conflicto:** BetterDragon tiene **formalmente congelado el desacoplamiento total de `DragonBattle`**. `DragonBattle` no es fuente de verdad. Iniciar la secuencia vanilla reactivaría dragones duplicados, portales no deseados y la BossBar vanilla conflictiva.
* **Postura:** **RECHAZADA LA IMPLEMENTACIÓN HISTÓRICA.** La segunda fase en BetterDragon debe ser orquestada de forma 100% autónoma por `BattleSession` sin tocar `DragonBattle`.

### Conflicto 2: Destrucción de Bloques de Portal (`Material.END_PORTAL`)
* **Mecánica Histórica:** Rompe los bloques del portal de salida (`temp.breakNaturally()`) cuando un jugador ingresa para atrapar a los demás.
* **Conflicto:** Viola el principio inmutable de **protección estricta de estructuras administradas** y la semántica de `portal.enabled: false`. BetterDragon jamás debe destruir destructivamente bloques del mundo de los jugadores ni corromper portales.
* **Postura:** **RECHAZADA LA DESTRUCCIÓN DE BLOQUES.** Si se desea una mecánica de confinamiento en la arena, se debe implementar mediante cancelación del evento de teletransporte (`PlayerTeleportEvent.setCancelled(true)`) o barreras físicas transparentes no destructivas.

### Conflicto 3: Modificación Brutal de Salud del Jugador (`setHealth(2)`)
* **Mecánica Histórica:** La onda expansiva fuerza `((Player) b).setHealth(2)` ignorando armaduras, tótems y efectos de pociones.
* **Conflicto:** Rompe el balance del juego y frustra a los usuarios. BetterDragon debe respetar el sistema de daño estándar de Minecraft (`EntityDamageEvent`), permitiendo que las armaduras, encantamientos y tótems funcionen correctamente.
* **Postura:** **RECHAZADA LA ASIGNACIÓN DIRECTA DE SALUD.** Utilizar daño escalar configurable.

---

## 8. PROPUESTAS CONCEPTUALES DE EXTENSIÓN PARA BETTERDRAGON

A partir de los hallazgos de esta auditoría, se proponen conceptualmente **tres extensiones modulares** que no alteran la arquitectura congelada y que podrán incorporarse en la Fase 3 o en el roadmap Post-MVP:

```
+-------------------------------------------------------------------------+
|                    BETTERDRAGON EXTENSION MODULES                       |
+-------------------------------------------------------------------------+
|                                                                         |
|  1. ABILITY ENGINE (MVP / POST-MVP CONDICIONAL)                         |
|     - Mapeo declarativo de habilidades a fases de vuelo de Paper        |
|     - Triggers: CIRCLING, STRAFING, LAND_ON_PORTAL, DAMAGE_TAKEN        |
|     - Tipos: CARPET_BOMB, SHOCKWAVE, LIGHTNING_THORNS, WATER_DENIAL     |
|                                                                         |
|  2. BOSS CUE & DIALOGUE SYSTEM (MVP LIGERO)                             |
|     - Diálogos MiniMessage disparados por inicio, fase, skill y muerte  |
|     - Audio cues sincronizados con Adventure API                        |
|                                                                         |
|  3. MULTI-PHASE COMBAT RUNTIME (POST-MVP)                               |
|     - Sucesión de etapas en una misma BattleSession (ej: 100% -> 50% -> 0%)|
|     - Transición con enrage visual, recarga de atributos y nuevas skills|
|                                                                         |
+-------------------------------------------------------------------------+
```

### 8.1 Sistema de Habilidades Declarativo (`Ability Engine`)
En lugar de programar habilidades hardcodeadas en listeners monolíticos, BetterDragon puede diseñar una interfaz limpia:
```java
public interface DragonAbility {
    String getId();
    boolean canTrigger(BattleSession session, EnderDragon dragon);
    void execute(BattleSession session, EnderDragon dragon);
}
```
Esto permite que en `dragons.yml` los administradores puedan habilitar o deshabilitar cada ataque de forma modular.

### 8.2 Sistema de Feedback y Diálogos (`BossCue`)
Permite enriquecer la experiencia sensorial del jugador sin complejidad arquitectónica, inyectando cadenas formateadas en Adventure al cambiar de estado de combate.

---

## 9. LICENCIAMIENTO Y ALCANCE DOCUMENTAL

* **Licencia del Repositorio Consultado:** El repositorio `KaevonD/BetterDragon` carece de archivo `LICENSE` o encabezados de copyright formales.
* **Contexto de Referencia:** Al tratarse de un repositorio público sin licencia explícita, se toma como premisa que el autor original conserva los derechos sobre su código fuente, por lo que no se asume permiso alguno de copia o reutilización de código.
* **Independencia y No Redistribución:**
  1. **Desarrollo Independiente:** BetterDragon se concibe e implementa de forma enteramente independiente bajo la autoría de `maurxp` para la API pública de Paper 26.1.2 en Java 25 (cero líneas de código copiadas o adaptadas, cero paquetes o clases clonadas, y cero dependencias externas compartidas).
  2. **Propósito de la Investigación:** El análisis técnico previo se utilizó exclusivamente para comprender comportamiento, mecánicas abstractas de juego y dinámicas de encuentro de interés para la comunidad.
  3. **No Redistribución:** BetterDragon no redistribuye código fuente ni artefactos binarios de terceros. Cualquier código fuente o repositorio externo consultado durante la investigación preliminar fue retirado del workspace público.
  4. **Conservación Exclusiva:** El repositorio público conserva exclusivamente notas originales de investigación, análisis conceptuales y referencias bibliográficas. No se asume autorización para reutilizar código ajeno sin una licencia aplicable o consentimiento expreso.
  5. **Descargo Técnico:** Esta documentación posee carácter meramente técnico y de registro de ingeniería de software; no constituye ni debe interpretarse como asesoría jurídica.

---

## 10. CONCLUSIÓN GENERAL

El repositorio histórico de KaevonD demuestra que con muy pocas líneas de código Java (apenas 330 líneas) es posible transformar un combate aburrido de Ender Dragon en una experiencia memorable mediante **bombardeos aéreos, ondas expansivas en el podio, castigo al campeo y diálogos amenazantes**. 

Sin embargo, su implementación técnica es sumamente precaria: carece de configuración, carece de persistencia, depende de `DragonBattle` vanilla, destruye bloques de portal y usa reflection.

Para nuestro **BetterDragon**, este análisis aporta una visión clara:
- **Descartar completamente la arquitectura y el código de KaevonD.**
- **Aprovechar las excelentes ideas de combate** (bombardeo de TNT en vuelo circular, shockwave en podio, castigo de agua y diálogos) para conceptualizar un futuro **Ability System declarativo** gobernado limpiamente por `BattleSession` y 100% basado en la API pública de Paper 26.1.2 con **0% NMS**.
