# Research: Patrones de Diseño de Jefes de Incursión (Raid Boss Design Patterns)

**Documento:** Análisis de patrones de combate, legibilidad de ataques, ritmo de encuentro y mitigación de frustración extraídos del diseño de videojuegos  
**Autor:** maurxp (@author maurxp)  
**Clasificación de Evidencia:** `GENERAL DESIGN PATTERN` / `INFERRED` / `PROPOSAL`  
**Nivel de Confianza:** `HIGH` para la existencia de los patrones en la industria; `HYPOTHESIS` para su adaptación numérica en Minecraft 26.1.2  

---

## 1. Misión del Análisis

Examinar las convenciones establecidas en el diseño de videojuegos de acción, MMORPGs y juegos cooperativos para enriquecer el combate del Ender Dragon, superando la pasividad y previsibilidad del encuentro vanilla.

### Principio Epistemológico:
- **Lo que demuestra la literatura de diseño:** Demuestra que la legibilidad de ataques, las fases estructuradas y la justicia distributiva mejoran la retención y satisfacción de los jugadores en encuentros cooperativos.
- **Lo que NO demuestra:** No demuestra que valores numéricos concretos (ej. 2.0 segundos de telegrafiado o 20% de salud para furia) sean universalmente correctos para Minecraft. Todo valor numérico es estrictamente un **`TUNING_CANDIDATE`** que debe calibrarse mediante pruebas de juego en Paper 26.1.2.

---

## 2. Inventario de Patrones de Diseño Auditados

### 2.1 Telegrafiado Sensorial y Legibilidad de Ataques
- **Referencia en la Industria:** Convención estándar de diseño en Action RPGs (FromSoftware: *Dark Souls/Elden Ring*) y MMORPGs (Square Enix: *Final Fantasy XIV*).
- **Qué Demuestra:** Los ataques con ventana de advertencia visual previa permiten al jugador anticipar el peligro y reaccionar mediante habilidad motriz, transformando una muerte súbita frustrante en una oportunidad de aprendizaje.
- **Qué NO Demuestra:** No demuestra que una duración fija funcione para todo tipo de ataque o para cualquier nivel de latencia de red.
- **Adaptación en BetterDragon:** Se adopta como **`PRINCIPLE`** organizando las habilidades por severidad (`MINOR`, `MODERATE`, `MAJOR`, `LETHAL`) con duraciones iniciales propuestas como `TUNING_CANDIDATE` sujetas a validación experimental (`EXP-006`).

### 2.2 Progresión Dramática por Fases de Salud
- **Referencia en la Industria:** Patrón universal en incursiones cooperativas (Blizzard: *World of Warcraft Raid Encounters*).
- **Qué Demuestra:** Dividir una barra de vida extensa en actos dramáticos decrecientes (ej. 100% $\to$ 75% $\to$ 50% $\to$ 25%) mantiene el compromiso cognitivo, evita la sensación de "esponja de balas" estática y permite introducir mecánicas escalonadas.
- **Qué NO Demuestra:** No garantiza por sí solo que la pelea sea divertida si las fases intermedias no aportan variaciones reales de ritmo o posicionamiento.
- **Adaptación en BetterDragon:** Consolidado en el modelo de dominio `PhaseDefinition` y `PhaseSequence` (Fase 3.5).

### 2.3 Furia (*Enrage Mechanics*): Suave vs Estricta
- **Referencia en la Industria:** Convención de incursiones MMO (encuentros de bandas).
- **Qué Demuestra:**
  - *Soft Enrage:* Un aumento moderado de cadencia o agresividad al final del combate eleva la tensión y crea un clímax memorable.
  - *Hard Enrage:* Un temporizador estricto de aniquilación global previene estrategias de desgaste excesivamente pasivas en entornos competitivos.
- **Qué NO Demuestra:** No demuestra que el Hard Enrage sea bien recibido en comunidades survival relajadas o semivanilla, donde puede percibirse como punitivo.
- **Adaptación en BetterDragon:**
  - *Soft Enrage:* Clasificado como **`CANDIDATE`** (con umbral propuesto al 20% como `TUNING_CANDIDATE`).
  - *Hard Enrage:* Clasificado como **`PROPOSAL (OPTIONAL)`**, desactivado por defecto en la configuración.

### 2.4 Control de Esbirros Menores (*Add Control*)
- **Referencia en la Industria:** Encuentros con esbirros de soporte en incursiones de rol cooperativo.
- **Qué Demuestra:** Añadir objetivos secundarios obliga al grupo a dividirse tareas (control de masas vs daño al jefe) y dinamiza el combate.
- **Qué NO Demuestra:** No garantiza que los esbirros no causen saturación o lag si no existe un control estricto de su ciclo de vida y recuento máximo.
- **Adaptación en BetterDragon:** Clasificado como **`CANDIDATE`** bajo la condición estricta de que cada esbirro posea identidad PDC vinculada a la sesión para su limpieza garantizada (`EXP-007`).

### 2.5 Ventanas Tácticas de Vulnerabilidad (*Damage Windows / Stun*)
- **Referencia en la Industria:** Mecánicas de ruptura de partes o aturdimiento en juegos de cacería (Capcom: *Monster Hunter*).
- **Qué Demuestra:** Premiar una acción de contrajuego compleja (ej. cortar un ataque o destruir un cristal mientras canaliza) con un periodo de aturdimiento genera satisfacción en el grupo.
- **Qué NO Demuestra:** Requiere verificar si el motor de Paper permite detener las rutinas de vuelo del dragón sin desajustes cinéticos.
- **Adaptación en BetterDragon:** Clasificado como **`HYPOTHESIS`** para investigación post-3.15.
