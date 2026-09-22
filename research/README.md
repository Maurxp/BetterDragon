# BetterDragon Research: Repositorio de Investigación y Evidencia Técnica

**Módulo:** BetterDragon Research System  
**Fase:** 3.12-R1 (Consolidación y Auditoría Pre-Commit)  
**Autor:** maurxp (@author maurxp)  
**Plataforma Objetivo:** Minecraft 26.1.2 | Paper `paper-26.1.2-74` | Java 25 (Temurin 25.0.4.1+1-LTS)  
**Estado:** `CONSOLIDADO Y AUDITADO`  

---

## 1. Misión y Metodología Clean-Room

El directorio `research/` alberga la investigación técnica, evaluación de jugabilidad, análisis de patrones de combate y benchmarking de proyectos de referencia para **BetterDragon**.

### Declaración de Principios y Buenas Prácticas:
1. **Diseño de Cuarto Limpio (*Clean-Room Design*):**  
   Los proyectos externos se analizan exclusivamente para comprender necesidades de la comunidad, mecánicas de juego, balance y limitaciones de experiencia de usuario (UX).  
   **Regla Absoluta:** Queda prohibido copiar código fuente, clases propietarias, nombres de paquetes o assets de terceros. Todo el código de BetterDragon se diseña y escribe de forma independiente.
2. **No Redistribución de Materiales Ajenos:**  
   El repositorio de BetterDragon no redistribuye código fuente original ni binarios JAR de terceros como parte del producto. Las fuentes externas se utilizan estrictamente como referencias bibliográficas de investigación y se atribuyen mediante enlaces y documentación pública.
3. **Aviso Legal:**  
   Esta documentación tiene fines puramente técnicos, analíticos y de diseño de software; no constituye asesoría jurídica.
4. **Trazabilidad y Verificabilidad:**  
   Ninguna afirmación técnica se presenta como un hecho consumado sin una referencia concreta a documentación primaria, javadocs oficiales, código fuente auditable o experimentos reproducibles.

---

## 2. Taxonomía de Evidencia Obligatoria

Toda afirmación, dato o mecánica documentada se clasifica bajo esta taxonomía estricta:

| Etiqueta | Definición | Requisito de Verificación |
| :--- | :--- | :--- |
| `DOCUMENTED` | Comportamiento documentado formalmente en una fuente oficial o confiable. | Cita a Javadocs de Paper, documentación de Mojang, Minecraft Wiki o issue trackers. |
| `SOURCE_CODE` | Comportamiento verificado mediante lectura directa de código en el proyecto. | Ruta de archivo, clase y líneas exactas en `src/main/java/` propio. |
| `DECOMPILED` | Comportamiento comprobado mediante descompilación técnica previa de binarios. | Nombre del binario analizado y método relevante documentado bibliográficamente. |
| `OBSERVED` | Comportamiento presenciado empíricamente en pruebas activas. | Procedimiento de prueba y resultado observable documentado. |
| `INFERRED` | Conclusión lógica razonable derivada de evidencias indirectas. | Deducción explícita; no se presenta como hecho comprobado. |
| `PROPOSAL` | Idea o modelo conceptual propuesto para BetterDragon. | Justificación de diseño de juego y arquitectura; no implementado aún. |
| `HYPOTHESIS` | Suposición técnica que requiere validación experimental. | Protocolo de prueba asociado en el registro de experimentos. |
| `TUNING_CANDIDATE` | Valor numérico inicial de balance sujeto a calibración en playtests. | Justificación del rango y plan de ajuste empírico. |
| `UNKNOWN` | Información que no ha podido determinarse con certeza. | Declaración explícita de incertidumbre; sin especulaciones. |

---

## 3. Niveles de Confianza

- **`HIGH`:** Respaldado por código fuente propio accesible, javadocs de Paper 26.1.2 o experimentos reproducibles directos.
- **`MEDIUM`:** Respaldado por documentación pública general, notas de versión o análisis bibliográfico externo.
- **`LOW`:** Respaldado únicamente por comentarios de la comunidad o deducciones teóricas pendientes de prueba.

---

## 4. Estructura del Directorio de Investigación

```text
research/
├── README.md                           # Índice general, taxonomía y metodología (este archivo)
├── sources/                            # Fuentes primarias y análisis monográficos propios
│   ├── vanilla_26.1.2_dragon_mechanics.md   # Mecánicas, hitboxes y limitaciones del dragón vanilla
│   ├── dragonslayer_gameplay_analysis.md    # Auditoría de combate y limitaciones de DragonSlayer
│   ├── amonly_betterdragon_analysis.md      # Análisis del plugin BetterDragon de amonly / shanruto
│   ├── external_boss_design_patterns.md     # Patrones de incursión (raids) y diseño de jefes MMO
│   ├── useful_links.txt                     # Enlaces a recursos oficiales y documentación pública
│   └── KaevonD-BetterDragon/                # Análisis documental propio sobre el trabajo de KaevonD
│       ├── kaevond_betterdragon_mechanics_audit.md
│       ├── kaevond_betterdragon_configuration_ideas.md
│       └── kaevond_betterdragon_licensing_notes.md
├── extracted/                          # Fragmentos de referencia y tablas técnicas
│   └── code_references.md                  # Citas bibliográficas estables y referencias a src/ propio
├── comparisons/                        # Análisis comparativo entre proyectos
│   └── feature_matrix.md                   # Matriz de características con referencias y estatus
├── decisions/                          # Registros formales de decisiones de diseño
│   └── gameplay_decisions_record.md        # Decisiones clasificadas (PRINCIPLE, DECISION, CANDIDATE, etc.)
└── experiments/                        # Registro de experimentos técnicos controlados
    └── experiment_registry.md              # Banco de pruebas EXP-001 a EXP-008
```

---

## 5. Resumen de Hallazgos Críticos Auditados

1. **Bug Mojira `MC-267372`:**  
   El Ender Dragon en Minecraft 26.1.2 ignora el atributo `generic.scale` (`Attribute.SCALE`). Sus cajas de colisión (`EnderDragonPart`) no escalan. BetterDragon no soporta actualmente el redimensionamiento del dragón mediante este atributo.
2. **Separación Conceptual de Fases:**  
   Las fases internas de vuelo de Vanilla (`EnderDragon.Phase`: `CIRCLING`, `STRAFING`, etc.) no equivalen a las fases de combate de BetterDragon (`PhaseDefinition`: progresión por umbral de salud). La fase de combate gobierna el encuentro y orquesta las fases de vuelo vanilla sin acoplamiento rígido.
3. **Aislamiento Estricto de NMS:**  
   El dominio y gameplay de BetterDragon operan al **0% NMS**. NMS está confinado exclusivamente al adaptador de infraestructura para la supresión de la BossBar nativa (`platform.bossbar`).
4. **Anti-Cheese como Necesidad Primaria:**  
   El combate del Ender Dragon vanilla es vulnerable a la colocación masiva de camas en el podio y al uso de cubos de agua. BetterDragon modulariza estas soluciones conceptualmente en `ExplosionPolicy` y `WaterPolicy` dentro de `ArenaRuleSet`.
