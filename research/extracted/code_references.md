# Catálogo de Referencias de Código y Citas Técnicas (Extracted Code References)

**Módulo:** BetterDragon Evidence & Audit Layer  
**Fase:** 3.12-R1 (Consolidación y Auditoría Pre-Commit)  
**Autor:** maurxp (@author maurxp)  
**Clasificación de Evidencia:** `SOURCE_CODE` (BetterDragon propio) / `DOCUMENTED` y referencias bibliográficas externas (proyectos de terceros)  

---

## 1. Referencias Bibliográficas Externas: KaevonD / BetterDragon

- **Proyecto Original:** KaevonD/BetterDragon (denominado internamente `HarderBosses`)
- **Autor Original:** KaevonD (@drysu)
- **Repositorio Público de Referencia:** https://github.com/KaevonD/BetterDragon (Commit `d05ad7f`, 11 de enero de 2022)
- **Licencia:** Sin licencia explícita declarada (Derechos de autor reservados por defecto).
- **Propósito en BetterDragon:** Análisis de mecánicas conceptuales (cuarto limpio / *clean-room*). No se incluye ni distribuye código fuente original en este repositorio.

### 1.1 Sincronización con Fases de Vuelo de la IA Vanilla
* **Referencia Externa:** `KaevonD BetterDragon — MyListener, sincronización de ataques con EnderDragonChangePhaseEvent`.
* **Concepto Observado:** El listener de eventos de KaevonD intercepta transiciones de fase de vuelo vanilla (`Phase.CIRCLING`, `Phase.LAND_ON_PORTAL`, `Phase.STRAFING`) para disparar habilidades específicas sin inyectar rutas de vuelo por NMS.
* **Adaptación en BetterDragon:** En BetterDragon, las fases de combate (`PhaseDefinition`) y las fases de vuelo (`EnderDragon.Phase`) están formalmente desacopladas. El runtime de combate puede solicitar fases de vuelo o escuchar transiciones de Paper API limpiamente.

### 1.2 Mecánica de Denegación de Zonas de Agua
* **Referencia Externa:** `KaevonD BetterDragon — MyListener, detección de presencia en agua en PlayerMoveEvent`.
* **Concepto Observado:** Verifica si la ubicación del jugador contiene `Material.WATER` en el End y aplica daño directo.
* **Adaptación en BetterDragon:** En lugar de daño arbitrario en `PlayerMoveEvent`, BetterDragon propone una política modular `WaterPolicy` en `arenas.yml` (con opciones de permitir, cancelar colocación, evaporar o castigar).

### 1.3 Contragolpe Eléctrico ante Daño Directo
* **Referencia Externa:** `KaevonD BetterDragon — MyListener, invocación de rayo en EntityDamageByEntityEvent`.
* **Concepto Observado:** Aplica una probabilidad ($1/6 \approx 16.7\%$) de invocar un rayo sobre el atacante cuando el dragón es dañado.
* **Adaptación en BetterDragon:** Se evalúa como trigger `ON_DAMAGE_TAKEN` con cooldown individual por jugador para evitar saturación de efectos.

---

## 2. Referencias Bibliográficas Externas: DragonSlayer

- **Proyecto Original:** DragonSlayer
- **Autor Original:** Jeppa
- **Fuente Pública Oficial:** https://www.spigotmc.org/resources/dragonslayer.36250/
- **Propósito en BetterDragon:** Análisis de diseño de encuentros históricos, temporizadores y limitaciones del modelo de salud estática.

### 2.1 Temporizadores Asíncronos de Reaparición
* **Referencia Externa:** `DragonSlayer — TimerManager, temporizador de cuenta regresiva para reaparición programada`.
* **Concepto Observado:** Un planificador asíncrono que verifica periódicamente si el tiempo actual ha alcanzado el umbral fijado para spawnear un nuevo dragón.
* **Adaptación en BetterDragon:** Candidato futuro para un scheduler de reaparición integrado con `BattleSession`.

### 2.2 Reconstrucción de Portales y Desalineación
* **Referencia Externa:** `DragonSlayer — createPortal / placePortalBlock, colocación directa de bloques de bedrock y portal`.
* **Concepto Observado:** DragonSlayer manipula directamente bloques del mundo para forzar la geometría del portal central.
* **Decisión en BetterDragon:** Rechazado. BetterDragon respeta `portal.enabled: false` y el aislamiento de estructuras administradas para no corromper arenas en coordenadas personalizadas.

---

## 3. Referencias del Código Fuente Propio de BetterDragon

Fuente interna verificable: `src/main/java/maurxp/betterdragon/`

### 3.1 Claves PDC Persistidas vs Reservadas
* **Archivo:** [battle/DragonPdcHandler.java](file:///c:/Users/amaur/Desktop/SMP-Plugins/SMP-BetterDragon/BetterDragon/src/main/java/maurxp/betterdragon/battle/DragonPdcHandler.java)
* **Líneas:** 43–47
* **Comportamiento Verificado:**
  ```java
  PersistentDataContainer pdc = dragon.getPersistentDataContainer();
  pdc.set(BetterDragonKeys.MANAGED, PersistentDataType.BOOLEAN, true);
  pdc.set(BetterDragonKeys.BATTLE_ID, PersistentDataType.STRING, identity.battleId().asString());
  // Phase 3.3-R1: definition_id y schema_version se reservan para fases futuras y NO se escriben en el spawn.
  ```
* **Estado Actual:** Solo `managed` y `battle_id` son escritos físicamente en el PDC en la versión actual; `definition_id` y `schema_version` están reservados para la Fase 3.13+.

### 3.2 Registro de Participantes Confinado al Daño Válido
* **Archivo:** [combat/CombatRuntime.java](file:///c:/Users/amaur/Desktop/SMP-Plugins/SMP-BetterDragon/BetterDragon/src/main/java/maurxp/betterdragon/combat/CombatRuntime.java)
* **Líneas:** 95–105
* **Comportamiento Verificado:**
  Un participante se registra y acumula daño exclusivamente cuando inflige daño válido $> 0$ durante `BattleState.ACTIVE`. No existe aún registro de combatientes pasivos o que no hayan golpeado al dragón.

### 3.3 Selección de Definición Única en ConfigurationLoader
* **Archivo:** [config/ConfigurationLoader.java](file:///c:/Users/amaur/Desktop/SMP-Plugins/SMP-BetterDragon/BetterDragon/src/main/java/maurxp/betterdragon/config/ConfigurationLoader.java)
* **Líneas:** 265–267
* **Comportamiento Verificado:**
  ```java
  String targetDragonKey = dragonsSec.contains("default")
          ? "default"
          : dragonsSec.getKeys(false).stream().findFirst().orElse(null);
  ```
* **Estado Actual:** El cargador resuelve un único `DragonDefinition` para la sesión. El soporte para un catálogo completo `Map<String, DragonDefinition>` es objetivo de la Fase 3.13.

### 3.4 Coordenadas de Spawn por Defecto en DragonSpawner
* **Archivo:** [battle/DragonSpawner.java](file:///c:/Users/amaur/Desktop/SMP-Plugins/SMP-BetterDragon/BetterDragon/src/main/java/maurxp/betterdragon/battle/DragonSpawner.java)
* **Líneas:** 27–29 y 50
* **Comportamiento Verificado:**
  Si no se pasa una `Location` explícita, `DragonSpawner` recurre a constantes estáticas (`0.5, 128.0, 0.5`). La derivación formal de spawn y podio desde `ArenaDefinition` está planificada para la Fase 3.13.
