# Research: Vanilla 26.1.2 Ender Dragon Mechanics

**Documento:** Análisis técnico exhaustivo de mecánicas nativas, atributos, hitboxes y ciclo de vida de Vanilla Ender Dragon  
**Plataforma Objetivo:** Minecraft 26.1.2 | Paper `paper-26.1.2-74` | Java 25 (OpenJDK Temurin 25.0.4.1+1-LTS)  
**Autor:** maurxp (@author maurxp)  
**Clasificación de Evidencia:** `DOCUMENTED` / `SOURCE_CODE` / `OBSERVED`  
**Nivel de Confianza:** `HIGH`  

---

## 1. Objetivo de la Investigación

Analizar en profundidad la entidad `EnderDragon` de Minecraft Vanilla en Paper 26.1.2:
- Atributos base nativos y su mutabilidad vía Paper API.
- Sub-entidades de colisión (`EnderDragonPart`).
- Máquina de estados interna de vuelo (`EnderDragon.Phase`).
- Interacción con cristales de End (`EnderCrystal`).
- Vulnerabilidades críticas de diseño (bed bombing, anclas de respawn, abuso de agua).
- Separación conceptual estricta entre fases de vuelo vanilla y fases de combate de BetterDragon.

---

## 2. Fuentes Primarias y Evidencia Consultada

1. **Paper API Javadocs (26.1.2-74):**
   - `org.bukkit.entity.EnderDragon`: Métodos `getPhase()`, `setPhase(Phase)`, `getPodium()`, `setPodium(Location)`, `getDragonBattle()`.
   - `org.bukkit.entity.EnderDragonPart`: Sub-clase de `ComplexEntityPart`, interfaz `Damageable`.
   - `org.bukkit.entity.EnderCrystal`: Métodos `getBeamTarget()`, `setBeamTarget(Location)`, `isShowingBottom()`.
   - `org.bukkit.event.entity.EnderDragonChangePhaseEvent`: Evento síncrono que expone `getCurrentPhase()`, `getNewPhase()`, `setNewPhase(Phase)`, `isCancelled()`.
2. **Mojang Source Mappings / Decompilación (26.1.2):**
   - `net.minecraft.world.entity.boss.enderdragon.EnderDragon`: Comportamiento de hitboxes hijos, cálculo de daño y vectores nodales de vuelo.
   - `net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance`: Controladores de cada sub-estado de IA.
3. **Mojira Issue Tracker:**
   - `MC-267372`: *"Ender Dragon parts and hitbox do not scale when using generic.scale attribute"*.
4. **Minecraft Wiki Oficial:**
   - Multiplicadores de daño por parte corporal, regeneración por cristales y ratios de daño por aliento.

---

## 3. Atributos Nativos y Limitaciones del Motor

### 3.1 Atributos Registrados en Vanilla
En Minecraft 26.1.2, `EnderDragon` registra los siguientes atributos estándar administrables mediante `LivingEntity#getAttribute(Attribute)`:

| Atributo | Valor Vanilla | Mutabilidad en Paper 26.1.2 | Efecto en el Dragón |
| :--- | :---: | :---: | :--- |
| `Attribute.MAX_HEALTH` | 200.0 HP | Totalmente mutable vía `setBaseValue(double)` | Determina la barra de salud máxima del mob. Al modificarse, debe sincronizarse con `setHealth()`. |
| `Attribute.MOVEMENT_SPEED` | 0.6 – 0.7 | Mutable vía `setBaseValue(double)` | Influye en la aceleración lineal durante embestidas (`CHARGE_PLAYER`) y aproximación, pero la velocidad en órbita depende fuertemente de la navegación de waypoints internos. |
| `Attribute.FOLLOW_RANGE` | 128.0 | Mutable vía `setBaseValue(double)` | Radio en bloques para la detección y selección de objetivos por parte de la IA nativa. |
| `Attribute.KNOCKBACK_RESISTANCE` | 1.0 | Fijo (1.0 = 100% inmune) | El dragón no sufre empuje por ataques de armas cuerpo a cuerpo estándar. |

### 3.2 La Problemática de `Attribute.SCALE` (Bug MC-267372)
- **Clasificación de Evidencia:** `DOCUMENTED` / `OBSERVED` (Referencia: `Mojira MC-267372`).
- **Comportamiento Actual:** Aunque Minecraft 1.20.5+ introdujo `Attribute.SCALE` (`generic.scale`), el Ender Dragon presenta un fallo estructural en el motor: las sub-partes (`EnderDragonPart`) no recalculan sus coordenadas relativas ni sus cajas AABB cuando la escala cambia. El modelo visual puede sufrir desincronización o permanecer idéntico mientras las colisiones se corrompen.
- **Posición Formal de BetterDragon:**  
  > *BetterDragon no soporta actualmente el escalado del Ender Dragon mediante `Attribute.SCALE` debido a las limitaciones conocidas del motor de Minecraft (bug MC-267372); esta decisión podrá reevaluarse si el comportamiento del motor cambia en futuras versiones.*
- **Estrategia Alternativa:** Diferenciación estética y sensorial de variantes de dragón mediante efectos de partículas (`Particle.DUST`, `Particle.SOUL_FIRE_FLAME`), sonidos personalizados, títulos y mecánicas de ataque exclusivas.

---

## 4. Anatomía de Hitbox Multipart y Mitigación de Daño

El dragón es una entidad compuesta (`ComplexLivingEntity`) articulada en múltiples sub-entidades físicas (`EnderDragonPart`):

```text
               [head] (x4 daño)
                 |
               [neck]
                 |
      [wing1]--[body]--[wing2]
                 |
               [tail1]
                 |
               [tail2]
                 |
               [tail3]
```

- **Cabeza (`head`):** Absorbe un multiplicador nativo de **4.0x** de daño en Vanilla ante flechas o golpes directos.
- **Cuerpo y Cuello (`body`, `neck`):** Absorben daño normal (1.0x).
- **Alas y Cola (`wing1`, `wing2`, `tail*`):** No reciben daño de flechas en ciertas trayectorias y causan daño por colisión cinética a los jugadores.

### 4.1 La Vulnerabilidad Crítica del Bed Bombing y Respawn Anchors
- **Clasificación:** `OBSERVED` / `DOCUMENTED`.
- **Mecánica del Exploit:** En la dimensión del End, colocar una cama o un ancla de reaparición cargada y hacer clic derecho produce una explosión inmediata con potencia destructiva. Cuando el dragón aterriza en el podio (`LAND_ON_PORTAL` / `BREATH_ATTACK`), su cabeza queda expuesta a nivel de suelo; 3 o 4 explosiones de cama consecutivas infligen más de 200 HP de daño instantáneo, aniquilando al jefe en menos de 10 segundos.
- **Enfoque de BetterDragon (Propuesta Arquitectónica):**  
  Modularizar la mitigación en una política de arena (`ExplosionPolicy` en `ArenaRuleSet`), interceptando el daño hacia el dragón cuando la causa provenga de bloques explosivos no autorizados (`rules.bed_bombing_damage_multiplier: 0.05` como `TUNING_CANDIDATE`).

---

## 5. Principio Central: Vanilla Flight Phase vs BetterDragon Combat Phase

Es mandatorio establecer una distinción arquitectónica estricta entre estos dos conceptos:

```text
┌──────────────────────────────────────────────────────────────┐
│             BETTERDRAGON COMBAT PHASE (Dominio)              │
│  Representa el estado narrativo y de progresión del jefe     │
│  (ej. FASE_1: 100%-75%, FASE_2: 75%-50%, ENRAGE: <20%)       │
└──────────────────────────────┬───────────────────────────────┘
                               │ orquesta / solicita / permite
                               ▼
┌──────────────────────────────────────────────────────────────┐
│              VANILLA FLIGHT PHASE (Motor Paper)              │
│  Representa la sub-rutina de locomoción e IA del dragón      │
│  (CIRCLING, STRAFING, CHARGE_PLAYER, LAND_ON_PORTAL, etc.)   │
└──────────────────────────────────────────────────────────────┘
```

1. **Vanilla Flight Phase (`EnderDragon.Phase`):**  
   Son los 11 estados internos de la IA de locomoción provistos por el juego (`CIRCLING`, `STRAFING`, `FLY_TO_PORTAL`, `LAND_ON_PORTAL`, `LEAVE_PORTAL`, `BREATH_ATTACK`, `SEARCH_FOR_BREATH_ATTACK_TARGET`, `ROAR_BEFORE_ATTACK`, `CHARGE_PLAYER`, `DYING`, `HOVER`).
2. **BetterDragon Combat Phase (`PhaseDefinition`):**  
   Es el estado del encuentro gestionado por `BattleSession` y `PhaseRuntime`, determinado por el porcentaje de vida restante o eventos de combate.
3. **Interacción:**  
   La fase de combate de BetterDragon no reemplaza la IA interna de Bukkit, sino que:
   - **Escucha:** Detecta transiciones de vuelo mediante `EnderDragonChangePhaseEvent` para disparar habilidades sincronizadas (ej. bombardeo aéreo al entrar en `CIRCLING`, onda sísmica al entrar en `LAND_ON_PORTAL`).
   - **Solicita / Fuerza:** Puede forzar un cambio de comportamiento de vuelo llamando a `EnderDragon#setPhase(Phase)` para dirigir el ritmo del combate.

---

## 6. Cristales de End (`EnderCrystal`)

- **Regeneración Vanilla:** El cristal conectado emite un haz de partículas al dragón y le restituye **1.0 HP por tick** (20 HP por segundo). Mientras recibe este rayo de manera continua, el dragón ignora gran parte del daño convencional.
- **Daño de Rebote:** Si un cristal es destruido mientras mantiene un rayo activo hacia el dragón, este recibe **10.0 HP** de daño de retroceso instantáneo.
- **Interacción con Fases (Hipótesis de Diseño):**  
  Los cristales pueden permanecer pasivos o indestructibles durante ciertas fases preliminares y activarse como condición obligatoria de avance en fases de inmunidad (*Vulnerability Windows*).

---

## 7. Control de Posición Central y Desacoplamiento del Podio

- **Vanilla Hardcode:** En el End vanilla, la IA del dragón asume que el podio de bedrock se encuentra centrado en `(0, 65, 0)` y que la órbita de vuelo gira en torno a `(0, y, 0)`.
- **Paper API Capability:** Paper expone el método `EnderDragon#setPodium(Location)`.
- **Diseño de BetterDragon:**  
  La definición de la arena (`ArenaDefinition`) contiene `center` (centro del volumen de combate) y `podium` (coordenada del portal o suelo central). BetterDragon debe configurar explícitamente `dragon.setPodium(arena.podium().toLocation(world))` al inicializar el encuentro para que las fases de aterrizaje no intenten forzarse siempre hacia el origen del mundo.
