# Huecko Backend — Plataforma Inteligente de Coordinación de Horarios y Planes de Grupo
[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Relational%20DB-blue.svg)](https://www.postgresql.org/)
[![MongoDB](https://img.shields.io/badge/MongoDB-NoSQL%20Events-brightgreen.svg)](https://www.mongodb.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Hybrid%20Persistence-purple.svg)]()
**Huecko Backend** es una API REST empresarial diseñada para resolver el problema recurrente de coordinar agendas y organizar eventos en grupos. La plataforma combina ingestión de horarios híbrida (manual + OCR), cruce inteligente de disponibilidad mediante heatmaps y algoritmos de coincidencia por umbrales, votación consensuada de planes, seguimiento de puntualidad en tiempo real y resolución asistida de imprevistos según criticidad de roles.
---
## 📌 Tabla de Contenidos
1. [Visión General del Proyecto](#-visión-general-del-proyecto)
2. [Arquitectura e Híbrido de Persistencia](#-arquitectura-e-híbrido-de-persistencia)
3. [Módulos e Historias de Usuario (HU)](#-módulos-e-historias-de-usuario-hu)
4. [Requerimientos Funcionales (RF)](#-requerimientos-funcionales-rf)
5. [Estructura del Proyecto](#-estructura-del-proyecto)
6. [Instalación y Configuración](#-instalación-y-configuración)
---
## 🎯 Visión General del Proyecto
Organizar actividades grupales (reuniones de estudio, salidas, proyectos) suele verse frustrado por incompatibilidad de horarios, respuestas tardías y cancelaciones de último momento. 
**Huecko** automatiza y simplifica este proceso:
* **Ingreso Híbrido:** Permite registrar disponibilidad semanal, eventos puntuales e importar horarios universitarios/laborales mediante OCR.
* **Algoritmo de Cruce Flexible:** Calcula intersecciones de tiempo libre ajustando umbrales de quórum (ej. 70%–100% de disponibilidad).
