# BetterDragon

**BetterDragon** es un plugin enterprise para servidores Paper diseñado por **maurxp** para gestionar el ciclo de vida y combate del Ender Dragon de forma independiente y de alto rendimiento.

---

## Target Técnico
- **Minecraft:** 26.1.2
- **Plataforma:** Paper 26.1.2-74
- **Java:** 25 (LTS)
- **Core:** 0% NMS (NMS aislado exclusivamente en `platform.bossbar`)

---

## Estado Actual del Proyecto
- **Fase 3.0–3.0-R1:** Bootstrap y Estructura Productiva `[DONE]`
- **Fase 3.1:** Modelos de Dominio e Invariantes de Estado `[DONE]`
- **Fase 3.2:** Sistema de Configuración y Snapshots Inmutables `[DONE]`
- **Fase 3.3–3.3-R1:** Dragon Lifecycle, Firma PDC y Recuperación de Chunks `[DONE]`
- **Fase 3.4:** Combat Runtime (HitSequence Monotónico, Damage Tracking, TOP_DAMAGE) `[DONE]`
- **Fase 3.5–3.5-R1:** Combat Phases & Abilities (Fases Ordenadas, Monotonicidad, AbilityEngine) `[DONE]`
- **Fase 3.6–3.6-R1:** Arena, Reglas y Límites (Geometría AABB, arenas.yml, Snapshot Inmutable, 0% NMS) `[DONE]`
- **Fase 3.7–3.7-R1:** Muerte, Victoria y BattleResult (Idempotencia, Supresión XP/Drops, Encapsulación Evento) `[DONE]`
- **Fase 3.9–3.9-R2:** Persistencia / Claims Durables (SQLite Durable Storage, Single-Writer Async Worker, Schema Versioning v1, Restart Recovery, Crash Consistency Limitation, Protección Terminal CLAIMED) `[DONE]`
- **Fase 3.10:** Leaderboard Persistente (SQLite schema v2, migración no destructiva v1 → v2, historial de batallas y participación, estadísticas acumuladas por UUID, Top Damage, Top Slayers, Top Participations con desempate determinista, transacciones atómicas, idempotencia estricta, integración con BattleResult, persistencia async sin bloqueo del hilo principal) `[DONE]`
- **Fase 3.11–3.11-R1:** Commands / Admin UX (Application Layer compartida preparada para futuras GUIs, `/betterdragon` y `/bd`, permisos granulares, console safety, subcomandos help, leaderboard, stats, status, start, abort, reload, arena, claim, tab completion, 314 tests) `[DONE]`
- **Próxima Fase:** Fase 3.12 — Hardening Final y Cierre `[TODO]`

---

## Documentación Oficial
- 📖 [Especificación de Comportamiento](docs/BEHAVIOR_SPEC.md)
- 🏛️ [Arquitectura del Sistema](docs/ARCHITECTURE.md)
- 🗺️ [Plan de Implementación Vivo](docs/IMPLEMENTATION.md)
- 🔬 [Referencias de Investigación](docs/RESEARCH.md)
