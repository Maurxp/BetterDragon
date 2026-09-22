# BetterDragon — Ideas de Configuración y Modelado Declarativo

**Documento:** Propuestas de Modelado de Configuración inspiradas en la Auditoría de KaevonD  
**Proyecto:** BetterDragon  
**Workspace:** `SMP-BetterDragon/research/`  
**Autor:** maurxp (@author maurxp)  
**Fecha:** 2026-09-19  

---

## 1. OBJETIVO

El repositorio histórico de KaevonD contenía excelentes conceptos de combate (bombardeos aéreos, ondas expansivas, denegación de zonas de agua, contragolpes eléctricos y diálogos de jefe), pero todos ellos estaban rígidamente codificados en constantes hardcodeadas en Java.

Este documento formaliza cómo abstraer y modelar esas ideas en esquemas YAML limpios y declarativos para `dragons.yml` en BetterDragon, sin alterar la arquitectura de Fase 2.5.3 y respetando el principio de **0% NMS**.

---

## 2. MODELADO DE HABILIDADES (`abilities`)

```yaml
# dragons.yml — Ejemplo de configuración modular de habilidades
dragons:
  elder_dragon:
    display_name: "<gradient:#ff0055:#ffaa00><bold>Dragón Ancestral</bold></gradient>"
    attributes:
      max_health: 500.0
      attack_damage: 15.0
      damage_multiplier: 1.2
    
    # Sistema de habilidades especiales declarativo
    abilities:
      # Habilidad 1: Bombardeo de TNT durante el vuelo circular
      carpet_bomb:
        enabled: true
        trigger: "PHASE_CIRCLING"
        chance: 1.0
        interval_seconds: 1.5
        bomb_count: 12
        fuse_ticks: 60
        prevent_block_damage: true
        sound: "entity.tnt.primed"
        dialogue:
          - "<red>¡Lluvia de justicia desde las alturas!</red>"
          - "<red>¿Creían que su protección contra explosiones bastaría?</red>"
      
      # Habilidad 2: Onda de choque al aterrizar en el podio
      perch_shockwave:
        enabled: true
        trigger: "PHASE_LAND_ON_PORTAL"
        speed: 1.5
        max_radius: 20.0
        damage: 10.0
        damage_percent_max_health: 0.25 # Quita 25% de la vida máxima en vez de forzar a 1 corazón
        knockback: 1.2
        particle_color: "#aa00ff"
        sound: "entity.warden.sonic_boom"
        dialogue:
          - "<gold>¡Sientan el impacto de mis alas!</gold>"
      
      # Habilidad 3: Haz de levitación sobre jugadores
      gravity_beam:
        enabled: true
        trigger: "PHASE_STRAFING"
        max_targets_ratio: 0.33 # Afecta a un tercio de los combatientes
        duration_seconds: 4
        levitation_amplifier: 4
        beam_particle: "DRAGON_BREATH"
        sound: "entity.evoker.cast_spell"
        dialogue:
          - "<aqua>¡Espero que hayan traído un cubo de agua!</aqua>"
          - "<aqua>¿Cómo se ve la arena desde allá arriba?</aqua>"

      # Habilidad 4: Contragolpe eléctrico reactivo
      lightning_retaliation:
        enabled: true
        trigger: "ON_DAMAGE_TAKEN"
        chance: 0.15
        cooldown_seconds: 4
        strike_effect_only: false # true = solo cosmético, false = daño real de rayo
        dialogue:
          - "<yellow>¡La tormenta me protege!</yellow>"

    # Módulo de mitigación de trampas y tácticas abusivas
    anti_cheese:
      water_punishment:
        enabled: true
        check_interval_ticks: 20
        max_time_in_water_seconds: 3.0
        damage: 12.0
        electrify_water: true
        dialogue:
          - "<blue>¡Salgan de esa agua cobardes!</blue>"
          - "<blue>El agua no los salvará de mi furia.</blue>"
```

---

## 3. MODELADO DE DIÁLOGOS Y CUES AUDITIVOS (`dialogue`)

En BetterDragon, los diálogos no deben enviarse como mensajes de texto plano en inglés. Se estructuran para enriquecer el feedback sensorial con Adventure API:

```yaml
dialogue:
  prefix: "<gradient:#ff0055:#ffaa00><bold>[BetterDragon]</bold></gradient> "
  display_mode: "ACTIONBAR" # Opciones: CHAT, ACTIONBAR, SUBTITLE, CHAT_AND_ACTIONBAR
  voice_sound: "entity.ender_dragon.growl"
  sound_volume: 1.0
  sound_pitch: 0.8
```

---

## 4. CONCLUSIÓN DE CONFIGURABILIDAD

La separación entre la lógica de ejecución del motor (`BattleSession`) y la definición declarativa en YAML permite que cualquier servidor diseñe jefes radicalmente distintos (dragones incendiarios, dragones de tormenta, dragones bombarderos) sin necesidad de tocar código Java ni compilar dependencias ajenas.
