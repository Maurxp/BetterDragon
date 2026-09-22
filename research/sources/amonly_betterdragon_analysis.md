# Research: BetterDragon de Amonly (shanruto) — Gameplay & UX Analysis

**Documento:** Análisis de diseño de combate, contrataques reactivos y limitaciones del plugin comercial BetterDragon  
**Fuente Principal:** Recurso SpigotMC `81439` (*BetterDragon: Counter Attacks & Abilities* by amonly / shanruto), notas de lanzamiento públicas e hilos de discusión técnica  
**Autor del Análisis:** maurxp (@author maurxp)  
**Clasificación de Evidencia:** `DOCUMENTED` / `INFERRED`  
**Nivel de Confianza:** `MEDIUM` (Basado en documentación pública exhaustiva, registros de configuración y changelogs verificados; código fuente propietario no disponible)  

---

## 1. Propósito del Análisis

Evaluar las innovaciones y limitaciones de la implementación comercial "BetterDragon" desarrollada por **amonly** (identificado en la comunidad como **shanruto**). Este plugin fue uno de los primeros en SpigotMC en introducir explícitamente el concepto de **contrataques y habilidades reactivas** para el Ender Dragon.

---

## 2. Inventario de Mecánicas Analizadas

### 2.1 Contrataques Reactivos (*Counter-Attacks*)
- **Clasificación de Evidencia:** `DOCUMENTED` (Notas de versión del recurso SpigotMC 81439).
- **Mecánica Observada:** A diferencia del dragón vanilla que solo ataca cuando su IA cíclica decide descender o disparar aliento, el dragón de Amonly reacciona al recibir daño directo:
  - Al recibir impactos de flechas o golpes cuerpo a cuerpo, existe una probabilidad porcentual de desencadenar una respuesta inmediata: rayos sobre el atacante, ráfagas de fuego, empuje o invocación de esbirros menores.
- **Evaluación de Gameplay:**  
  - *Impacto Positivo:* Rompe la pasividad del combate a distancia (jugadores apostados en torres disparando impunemente) y fuerza el movimiento constante tras cada impacto.
  - *Riesgo Identificado (Inferido):* Si no se impone un cooldown interno estricto por jugador, un ataque rápido con arco encantado o ballesta multidireccional puede generar una tormenta descontrolada de partículas y rayos, provocando picos de lag o muertes instantáneas injustas.
- **Estado en BetterDragon:** **`CANDIDATE`**. Se propone como trigger `ON_DAMAGE_TAKEN` en el `AbilityEngine` futuro, pero con limitación de frecuencia obligatoria (`TUNING_CANDIDATE: cooldown_seconds = 5.0`).

### 2.2 Modos de Operación: Default vs Entity Mode
- **Clasificación de Evidencia:** `DOCUMENTED` (Manual de configuración de Amonly).
- **Mecánica:**
  - *Default Mode:* Integra al dragón con la estructura nativa de `DragonBattle` en el End vanilla (reaparición con 4 cristales, regeneración de pilares).
  - *Entity Mode:* Instancia al dragón como una entidad jefa autónoma (*standalone boss*) sin tocar estructuras vanilla ni regenerar portales.
- **Evaluación de Gameplay:** Confirmó que la comunidad de administradores necesita desacoplar al jefe de la mecánica rígida del End vanilla para poder emplazar combates en mapas de aventuras o dimensiones personalizadas.
- **Estado en BetterDragon:** BetterDragon ya superó conceptualmente esta dicotomía en Fase 3.3/3.6: **todas** las batallas son autónomas mediante `BattleSession` y se adaptan a cualquier arena delimitada en `arenas.yml`.

### 2.3 Estructura de Recompensas por Roles
- **Clasificación de Evidencia:** `DOCUMENTED`.
- **Mecánica:** Divide el botín en tres grupos: *Killer* (último golpe), *Top Damager* (mayor daño acumulado) y *Participants* (daño mínimo cumplido).
- **Estado en BetterDragon:** **Consolidado en Fase 3.8/3.9**. BetterDragon implementó un modelo matemáticamente superior y más equitativo: el `RewardAllocationEngine` distribuye cuotas proporcionales basadas en daño real con persistencia en buzón durable SQLite para evitar pérdidas.

---

## 3. Limitaciones Técnicas Documentadas

1. **Rumbo Fijado a `(0, 0)`:**  
   *Evidencia:* `DOCUMENTED`. En arenas situadas lejos del origen del mundo, la IA del dragón presenta fallas al intentar perchear hacia `(0, 0)`.
2. **Dependencia Frágil de Chunks Cargados:**  
   *Evidencia:* `DOCUMENTED`. El plugin requería forzar la carga permanente de los chunks centrales para evitar que la entidad desapareciera o perdiera su IA.
3. **Exigencia de `scan_for_legacy_ender_dragon: false`:**  
   *Evidencia:* `DOCUMENTED` (Guía de instalación de Amonly en PaperMC).  
   Para evitar que Paper elimine al dragón en modo Entity, exigía desactivar una protección nativa de Paper. Esto indica que no utilizaba `PersistentDataContainer` (PDC) para identificar inequívocamente a su entidad. BetterDragon resolvió esto de forma nativa en Fase 3.3 mediante `DragonPdcHandler`.

---

## 4. Clasificación de Certeza Técnica

- **Confirmado por Documentación Pública:** Existencia de contrataques por daño, división Default/Entity mode, categorías de recompensa Killer/Top/Damager.
- **Inferido Técnicamente:** Ausencia de sistema declarativo de fases por umbral de salud; la IA de Amonly opera por temporizadores o eventos aleatorios sin progresión dramática.
- **Desconocido:** Implementación exacta de algoritmos de targeting y gestión de memoria interna en el código fuente (no accesible legalmente).
