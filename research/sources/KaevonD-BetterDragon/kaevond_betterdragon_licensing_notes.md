# BetterDragon — Notas de Licenciamiento y Análisis de Propiedad Intelectual

**Documento:** Notas de Análisis de Licencia y Referencias Técnicas  
**Proyecto:** BetterDragon  
**Workspace:** `SMP-BetterDragon/research/`  
**Autor:** maurxp (@author maurxp)  
**Fecha:** 2026-09-19  

---

## 1. ESTADO DE LICENCIA DEL REPOSITORIO DE REFERENCIA

* **Repositorio:** `https://github.com/KaevonD/BetterDragon`
* **Commit:** `d05ad7fc752864785d61c85c3c05a5640c66440e` (11 de Enero de 2022)
* **Archivo de Licencia:** **Inexistente.** El repositorio no contiene `LICENSE`, `LICENSE.md`, `COPYING` ni ninguna mención a licencias de código abierto como MIT, Apache 2.0, GPL o BSD.
* **Encabezados en Código:** Ninguno de los archivos Java (`HarderBosses.java`, `MyListener.java`, `SuperDragon.java`) contiene avisos de licencia de autor.

---

## 2. MARCO LEGAL APLICABLE

Conforme a la legislación internacional de derechos de autor (incluido el Convenio de Berna) y los Términos de Servicio de GitHub:
1. **Derechos de Autor por Defecto:** Cuando un desarrollador publica un proyecto público en GitHub sin una licencia explícita, conserva todos los derechos sobre su código fuente (**"All Rights Reserved"**).
2. **Uso Permitido:** Los Términos de Servicio de GitHub otorgan a otros usuarios el derecho a ver y clonar (hacer *fork* o descarga) del repositorio a través de la plataforma para fines de visualización y análisis personal.
3. **Uso Prohibido:** Queda estrictamente prohibido copiar código fuente, incorporar fragmentos de código, redistribuir archivos binarios derivados o reclamar autoría sobre el material sin consentimiento expreso del titular.

---

## 3. PRINCIPIO DE DISEÑO DE CUARTO LIMPIO (*CLEAN-ROOM DESIGN*)

Con el fin de salvaguardar la independencia arquitectónica y la higiene del repositorio, **BetterDragon** adopta una metodología de diseño de cuarto limpio (*clean-room design*) y análisis conceptual:

* **Separación entre concepto e implementación:** El análisis de proyectos de terceros se limita a estudiar mecánicas abstractas de juego y dinámicas de combate (por ejemplo: lluvia de dinamita telegrafiada o restricción perimetral), sin copiar ni adaptar código fuente ajeno.
* **Aislamiento Técnico:**
  - Cero líneas de código o archivos Java copiados de `KaevonD/BetterDragon`.
  - Cero paquetes o nombres de clases clonados (`me.drysu.*` queda completamente descartado).
  - Cero dependencias externas propietarias (se descarta `guardianbeam` que usa NMS).
  - Toda la arquitectura, diseño modular, persistencia en SQLite, algoritmos matemáticos de daño (`TOP_DAMAGE`) y listeners de BetterDragon son concebidos e implementados de forma independiente por el autor **maurxp** para la API pública de Paper 26.1.2.

---

## 4. POLÍTICA DE NO REDISTRIBUCIÓN Y ALCANCE DOCUMENTAL

BetterDragon no redistribuye el código fuente ni los binarios de terceros utilizados durante el proceso de investigación. Cualquier código fuente, archivo JAR o repositorio Git externo consultado preliminarmente fue retirado deliberadamente del workspace público.

El repositorio conserva únicamente notas de investigación originales, análisis conceptuales y referencias bibliográficas. No se asume permiso alguno para la reutilización o redistribución de código de terceros sin una licencia aplicable o autorización expresa correspondiente del autor original.

Esta documentación tiene fines estrictamente técnicos, analíticos y de registro de diseño de software; no constituye ni debe interpretarse como asesoría jurídica.
