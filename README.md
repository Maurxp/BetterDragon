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
- **Fase 3.8–3.8-R2:** Recompensas y Claims (Elegibilidad por daño real, Redistribución Proporcional, Redondeo Determinista, Identidad por `rewardId`, Idempotencia Canónica `battleId:participantId:rewardId`, Validación Estricta de Amount y Material Paper nativo, Safe Defaults neutrales y Claims en memoria) `[DONE]`
- **Fase 3.9:** Persistencia / Claims Durables (SQLite Durable Storage, Single-Writer Async Worker, Schema Versioning v1, Restart Recovery, Crash Consistency Limitation) `[DONE]`
- **Próxima Fase:** Fase 3.10 — Sistema de Leaderboard `[TODO]`

---

## Documentación Oficial
- 📖 [Especificación de Comportamiento](docs/BEHAVIOR_SPEC.md)
- 🏛️ [Arquitectura del Sistema](docs/ARCHITECTURE.md)
- 🗺️ [Plan de Implementación Vivo](docs/IMPLEMENTATION.md)
- 🔬 [Referencias de Investigación](docs/RESEARCH.md)
