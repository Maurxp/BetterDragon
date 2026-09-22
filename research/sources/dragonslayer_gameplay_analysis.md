# Research: DragonSlayer (Jeppa) Gameplay & UX Analysis

**Documento:** Auditoría técnica de diseño de combate, flujo de reaparición y limitaciones de DragonSlayer  
**Versión Binaria Auditada:** DragonSlayer `0.15.6_Jeppa` (`DragonSlayer-0.15.6_Jeppa.jar`)  
**Autor del Análisis:** maurxp (@author maurxp)  
**Herramientas de Análisis:** Descompilación técnica CFR 0.152, inspección de `plugin.yml`, `config.yml` y fuentes públicas  
**Clasificación de Evidencia:** `DECOMPILED` / `DOCUMENTED` / `OBSERVED`  
**Nivel de Confianza:** `HIGH` (para comportamiento de código descompilado) / `MEDIUM` (para impacto en comunidades)  

---

## 1. Propósito de la Investigación

Evaluar DragonSlayer —el plugin histórico más utilizado en servidores Spigot/Bukkit para la gestión del Ender Dragon— bajo el prisma del diseño de experiencia de juego (*gameplay design*), equilibrio de recompensas y arquitectura de software. El objetivo es identificar qué mecánicas aportaron valor real a los servidores y qué fallas estructurales causaron frustración en la comunidad, evitando repetirlas en BetterDragon.

---

## 2. Inventario de Mecánicas Analizadas

### 2.1 Salud Aumentada Estática ("Bullet Sponge")
- **Evidencia Técnica:** `DECOMPILED` (Clase `DragonSlayer.class`, método de spawn).
- **Mecánica:** Modifica el atributo `Attribute.MAX_HEALTH` al instanciar el dragón basándose en el parámetro de configuración `dragonhealth` (ej. 1000, 2000, 5000 HP).
- **Evaluación de Gameplay:**  
  - *Ventaja:* Impide que un dragón con 200 HP vanilla muera en 30 segundos frente a jugadores con equipamiento de nivel alto.
  - *Falla Crítica de Diseño:* **Esponja de Daño Monótona.** El dragón ejecuta exactamente las mismas rutinas de IA vanilla desde el primer hasta el último punto de vida. La batalla no aumenta en dramatismo ni introduce mecánicas nuevas; simplemente alarga artificialmente el tiempo de lanzamiento de flechas, aburriendo a los jugadores.

### 2.2 Reaparición Programada y Temporizadores
- **Evidencia Técnica:** `DECOMPILED` (Clase `TimerManager.class`, lectura de `TimerList.yml`).
- **Mecánica:** Mantiene un planificador (*scheduler*) asíncrono que controla un temporizador de cuenta regresiva (`respawndelay`, en minutos) por mundo, o dispara reapariciones en horarios fijos del reloj del servidor.
- **Evaluación de Gameplay:**  
  - *Ventaja:* Fomenta eventos comunitarios regulares donde los clanes se organizan para coincidir en el servidor a la hora de la batalla.
  - *Relevancia para BetterDragon:* Candidato para el ciclo de vida de administración en fases futuras (`CANDIDATE`).

### 2.3 Atribución de "Slayer" y Estatuas con ProtocolLib
- **Evidencia Técnica:** `DECOMPILED` (Clase `DragonSlayerListener.class`, método `onEntityDeath`).
- **Mecánica:** Registra al jugador que propinó el *último golpe* (`last-hit`) como el "Dragon Slayer". Otorga un prefijo en chat, ejecuta comandos configurables y utiliza la API de ProtocolLib para enviar paquetes de entidades ficticias (`PacketContainer`) que renderizan una estatua con la skin del jugador en el centro del End.
- **Evaluación de Gameplay:**  
  - *Falla Crítica:* La atribución por último golpe es sumamente tóxica en servidores survival multijugador; incentiva a los jugadores a esconderse y no aportar daño hasta que el dragón se encuentre con 5% de vida para robar la muerte (*kill stealing*).
  - *Problema Técnico de las Estatuas:* La dependencia obligatoria de ProtocolLib introduce fragilidad severa ante cada actualización de versión de Minecraft/Paper.
  - *Postura de BetterDragon:* **RECHAZADO el modelo de último golpe y el uso de ProtocolLib.** En BetterDragon, el título y reconocimiento se otorgan mediante `TOP_DAMAGE` inmutable (consolidado en Fase 3.7/3.10) y la presentación se maneja exclusivamente con Adventure MiniMessage nativo.

### 2.4 Control y Reconstrucción Forzada del Portal de Salida
- **Evidencia Técnica:** `DECOMPILED` (Métodos `createPortal`, `placePortalBlock`).
- **Mecánica:** Para impedir que Minecraft regenere de forma descontrolada el portal vanilla, DragonSlayer intercepta la muerte e intenta colocar manualmente bloques de `BEDROCK` y `END_PORTAL` en coordenadas predefinidas.
- **Evaluación de Gameplay:**  
  - *Falla:* Frecuentes reportes de portales duplicados, bloques de portal rotos en arenas personalizadas desplazadas y corrupción estética de estructuras del End.
  - *Postura de BetterDragon:* Respeto estricto del aislamiento de estructuras. Se mantiene `portal.enabled: false` por defecto para no manipular bloques destructivamente en mapas ajenos.

---

## 3. Matriz Comparativa DragonSlayer vs BetterDragon

| Característica DragonSlayer | Evidencia | Evaluación de Gameplay | Estado en BetterDragon | Justificación |
| :--- | :---: | :--- | :---: | :--- |
| **Salud Aumentada Fija** | `DECOMPILED` | Prolonga el combate pero genera monotonía si no hay fases. | **MODIFY (`CANDIDATE`)** | Permitir salud personalizada, pero acoplada a progresión de fases y escalado por jugadores. |
| **Temporizador de Respawn** | `DECOMPILED` | Fomenta citas y eventos de comunidad en el servidor. | **PROPOSAL (`FUTURE`)** | Se evaluará como tarea de lifecycle independiente en fases futuras. |
| **Atribución por Último Golpe** | `DECOMPILED` | Fomenta el robo de muertes y la inactividad durante la pelea. | **REJECT** | BetterDragon consolidó en 3.7 el cálculo inmutable por mayor daño total (`TOP_DAMAGE`). |
| **Estatuas vía ProtocolLib** | `DECOMPILED` | Dependencia externa pesada y propensa a romperse en cada update. | **REJECT** | Cero dependencias externas. Reconocimiento mediante Chat Adventure, Títulos y BossBar. |
| **Reconstrucción de Portales** | `DECOMPILED` | Rompe la geometría de mundos con arenas personalizadas. | **REJECT** | Aislamiento funcional. Respeto incondicional a `portal.enabled: false`. |
| **Recompensas Winner-Takes-All** | `DOCUMENTED` | Desmotiva a jugadores secundarios con menor equipamiento. | **REJECT** | BetterDragon implementó en 3.8/3.9 el reparto proporcional con buzón durable en SQLite. |
