# BetterDragon

**BetterDragon** es un plugin para servidores Paper desarrollado por **maurxp** para gestionar el ciclo de vida, combate y recompensas del Ender Dragon de forma autónoma, configurable y de alto rendimiento.

---

## Entorno de Ejecución
- **Minecraft:** Java Edition 26.1.2
- **Servidor:** Paper (build `paper-26.1.2-74`)
- **Java:** 25 (OpenJDK Temurin 25.0.4.1+1-LTS)
- **Arquitectura:** Paper-only, sin soporte Folia ni compatibilidad con versiones antiguas. Dominio y gameplay al 0% NMS (NMS confinado exclusivamente al adaptador de infraestructura para la supresión de la BossBar vanilla).

---

## Estado Actual del Proyecto
- **Fase 3.0–3.0-R1:** Bootstrap y Estructura Productiva `[DONE]`
- **Fase 3.1:** Modelos de Dominio e Invariantes de Estado `[DONE]`
- **Fase 3.2:** Sistema de Configuración y Snapshots Inmutables `[DONE]`
- **Fase 3.3–3.3-R1:** Dragon Lifecycle, Firma PDC y Recuperación de Chunks `[DONE]`
- **Fase 3.4:** Combat Runtime (HitSequence Monotónico, Damage Tracking, TOP_DAMAGE) `[DONE]`
- **Fase 3.5–3.5-R1:** Combat Phases & Abilities (Fases Ordenadas, Monotonicidad, AbilityEngine) `[DONE]`
- **Fase 3.6–3.6-R1:** Arena, Reglas y Límites (Geometría AABB, arenas.yml, Snapshot Inmutable) `[DONE]`
- **Fase 3.7–3.7-R1:** Muerte, Victoria y BattleResult (Idempotencia, Supresión XP/Drops, Encapsulación Evento) `[DONE]`
- **Fase 3.8:** Reward Distribution Engine (Reparto Proporcional, Elegibilidad de Daño, Idempotencia Canónica, Anti-Duplicación) `[DONE]`
- **Fase 3.9–3.9-R2:** Persistencia / Claims Durables (SQLite Durable Storage, Single-Writer Async Worker, Schema Versioning v1, Restart Recovery, Protección Terminal CLAIMED) `[DONE]`
- **Fase 3.10:** Leaderboard Persistente (SQLite schema v2, migración v1 → v2, historial de batallas y participación, estadísticas acumuladas por UUID, Top Damage, Top Slayers, Top Participations con desempate determinista, persistencia async) `[DONE]`
- **Fase 3.11–3.11-R1:** Commands / Admin UX (Application Layer compartida preparada para futuras GUIs, `/betterdragon` y `/bd`, permisos granulares, console safety, subcomandos help, leaderboard, stats, status, start, abort, reload, arena, claim, tab completion, 314 tests) `[DONE]`
- **Fase 3.12:** Gameplay Research & Design (Investigación de mecánicas nativas, análisis de proyectos de referencia y patrones de incursión) `[DONE]`
- **Fase 3.12-R1:** Consolidación, Auditoría y Cierre de Gameplay Research/Design (Auditoría de evidencias, taxonomía estricta, separación de Combat/Flight Phase, banco de experimentos EXP-001 a EXP-008 y especificación maestra) `[DONE]`
- **Fase 3.13:** Catálogo de Dragones, Atributos Nativos y Escalado por Jugadores (Catálogo inmutable `DragonCatalog`, soporte de múltiples perfiles en `config.yml` bajo la sección `dragons:`, atributos nativos `MAX_HEALTH`, `MOVEMENT_SPEED`, `FOLLOW_RANGE` [con `ATTACK_DAMAGE` no disponible/null en Paper 26.1.2-74], exclusión estricta de `Attribute.SCALE` por bug MC-267372, escalado determinista Battle-Start Scaling CAND-01 con capping configurable, spawn y podio derivados matemáticamente de `ArenaDefinition`, snapshot inmutable con `EffectiveDragonStats`, persistencia PDC completa con `betterdragon:definition_id` y `schema_version = 1`, comando `/bd start [mundo] [arena] [perfil]`, `/bd status` con perfil activo) `[DONE]`
- **Fase 3.13-R1:** Consolidación y Hardening de Perfiles, Atributos y Scaling (Semántica inequívoca Opción A para `scaling.enabled` y `mode`, eliminación de silenciamiento `Throwable` en `DragonSpawner`, restauración completa de cobertura de invariantes en `DragonDefinitionTest`, validación estricta de `definition_id` y `schema_version = 1` en PDC y `bd-test-lifecycle`, corrección documental de fuentes de configuración reales) `[DONE]`
- **Fase 3.15–3.15-R2:** Advanced Combat Abilities & Sensory Telegraphs (Bombardeo aéreo de TNT durante vuelo/circling, onda expansiva en aterrizaje al podio, contrataques reactivos con cooldown individual por atacante, invocación de esbirros con firma PDC de 4 claves y sweep determinista, telegrafiado sensorial previo a impactos sobre el hilo principal Bukkit, protección selectiva de bloques contra explosiones BetterDragon, snapshot isolation absoluto ante `/bd reload`, refactorización semántica permanente, purga total de test hooks en producción [-1115 líneas de test residue en BetterDragonPlugin], 447 tests unitarios e integrados [0 fallos, 0 errores], empaquetado limpio sin código de pruebas, validación EXP-010: 10/10 checks PASS) `[DONE]`
- **Próxima Fase:** Fase 3.16 — Arena Enclosure & Anti-Exploit Enforcement `[TODO]`

---

## Documentación Técnica y de Diseño
- 📖 [Especificación Maestra de Jugabilidad y Diseño de Encuentro](docs/GAMEPLAY_DESIGN.md)
- 🏛️ [Arquitectura del Sistema](docs/ARCHITECTURE.md)
- 📜 [Especificación de Comportamiento y Reglas](docs/BEHAVIOR_SPEC.md)
- 🔬 [Repositorio de Investigación y Banco de Experimentos](research/README.md)
