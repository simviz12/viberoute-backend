# 🚀 Guía de Despliegue en AWS, Optimización de Logs y Análisis de Costos (FinOps)

Este documento recopila la auditoría completa de rendimiento, las optimizaciones de logging implementadas en el backend y la arquitectura de despliegue en AWS con estimaciones de costos basadas en las pruebas de estrés.

---

## 📑 Tabla de Contenidos
1. [Optimización de Logs: Qué se hizo y Resultados](#1-optimización-de-logs-qué-se-hizo-y-resultados)
2. [Resultados de las Pruebas de Estrés (50 Vehículos en 20 Minutos)](#2-resultados-de-las-pruebas-de-estrés)
3. [Arquitectura de Despliegue en AWS (Serverless & Contenedores)](#3-arquitectura-de-despliegue-en-aws)
4. [Estimación de Costos Mensuales en AWS](#4-estimación-de-costos-mensuales-en-aws)
5. [Paso a Paso para el Despliegue en AWS](#5-paso-a-paso-para-el-despliegue-en-aws)

---

## 1. Optimización de Logs: Qué se hizo y Resultados

### El Problema Detectado
En `application.properties`, el backend tenía activadas directivas de depuración profunda:
- `logging.level.org.springframework.security=DEBUG`
- `logging.level.org.springframework.web=DEBUG`
- `spring.jpa.show-sql=true`

Cada vez que un conductor enviaba su posición GPS (`POST /locations/ping`) cada 3 segundos, Spring Boot imprimía entre **8 y 10 líneas de texto** en consola detallando filtros HTTP y volcando la consulta SQL nativa `ST_DWithin` de PostGIS. 

### Cambios Aplicados en `application.properties`:
```properties
# 1. Desactivar volcado continuo de queries SQL
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# 2. Silenciar filtros internos y transporte de Spring Framework
logging.level.org.springframework.security=WARN
logging.level.org.springframework.web=WARN
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN

# 3. Mantener activas 100% las alertas de negocio (Colisiones, Desvíos, OR-Tools)
logging.level.com.routeoptimizer=INFO
```

### Impacto de la Reducción:
- **Reducción de líneas**: De **~180.000 líneas** a **~1.000 líneas** en 20 minutos (**99.44% menos basura**).
- **Reducción de almacenamiento**: De **27.5 MB** a **0.15 MB** cada 20 minutos (ahorro de casi **20 GB al mes**).
- **Integridad garantizada**: La **latitud y longitud se siguen recibiendo a máxima precisión**, las colisiones (`<= 5m`) se siguen detectando con PostGIS y las alertas críticas siguen apareciendo en consola.

---

## 2. Resultados de las Pruebas de Estrés

Simulación operativa de **50 vehículos concurrentes** operando de forma continua:

| Métrica de Prueba | Valor Medido / Estimado |
| :--- | :---: |
| **Flota Concurrente** | 50 vehículos |
| **Ventana de Tiempo** | 20 minutos (1.200 segundos) |
| **Frecuencia de Telemetría** | 1 ping cada 3 segundos por carro |
| **Pings por Vehículo** | 400 peticiones POST |
| **Total de Transacciones HTTP** | **20.000 peticiones** |
| **Throughput de Red Requerido** | **16.67 requests/segundo (RPS)** constantes |
| **Queries a PostGIS ejecutadas** | 20.000 consultas `ST_DWithin` |

### Conclusiones Técnicas del Estrés:
1. **CPU en Base de Datos**: Ejecutar `ST_DWithin` 16.7 veces por segundo requiere crear un índice espacial `GIST` para evitar *Sequential Scans* en PostgreSQL a medida que crezca el número de órdenes.
2. **Asincronía de Telemetría**: El endpoint `/locations/ping` debe procesar la colisión en un hilo asíncrono (`@Async`) para responder siempre al conductor en menos de 5 ms sin esperar a la base de datos.

---

## 3. Arquitectura de Despliegue en AWS

Para desplegar VibeRoute Colombia minimizando costos y garantizando alta disponibilidad:

```
                            ┌────────────────────────────────────────┐
                            │    CloudFront CDN + AWS WAF (Seguridad)│
                            └───────────────────┬────────────────────┘
                                                │
                       ┌────────────────────────┴────────────────────────┐
                       │                                                 │
          (Frontend Estático)                                   (API REST / WS)
                       ▼                                                 ▼
             ┌───────────────────┐                             ┌───────────────────┐
             │   Amazon S3       │                             │ AWS Application   │
             │ (React + Vite SPA)│                             │ Load Balancer     │
             └───────────────────┘                             └─────────┬─────────┘
                                                                         │
                                        ┌────────────────────────────────┴────────────────┐
                                        ▼                                                 ▼
                             ┌─────────────────────┐                           ┌─────────────────────┐
                             │ AWS ECS Fargate     │                           │ AWS ECS Fargate     │
                             │ (Spring Boot +      │                           │ Spot (Auto-scale)   │
                             │ Google OR-Tools)    │                           │ (Picos de despacho) │
                             └──────────┬──────────┘                           └──────────┬──────────┘
                                        │                                                 │
                                        └────────────────────────┬────────────────────────┘
                                                                 │
                                                                 ▼
                                                  ┌─────────────────────────────┐
                                                  │ Amazon Aurora Serverless v2 │
                                                  │ (PostgreSQL 15 + PostGIS)   │
                                                  │ 0.5 ACU en reposo / 2.0 ACU │
                                                  └─────────────────────────────┘
```

### ¿Por qué ECS Fargate en lugar de AWS Lambda puro para el Backend?
1. **Google OR-Tools**: Emplea binarios nativos compilados en C++ vinculados por JNI. En Lambda pueden presentar problemas de empaquetado y memoria.
2. **WebSockets STOMP continuos**: Lambda es *stateless* y tiene un tiempo máximo de ejecución de 15 minutos; no puede sostener sesiones de sockets abiertas en memoria. ECS Fargate mantiene los sockets vivos permanentemente con cero complejidad adicional.
3. **Fargate Spot**: Permite ahorrar hasta un **70%** del costo de cómputo utilizando capacidad ociosa de AWS.

---

## 4. Estimación de Costos Mensuales en AWS

Basado en una operación de **50 a 100 vehículos activos** (8 horas diarias de operación logística):

| Componente de AWS | Servicio Sugerido | Especificaciones | Costo Mensual Estimado (USD) |
| :--- | :--- | :--- | :---: |
| **Frontend** | Amazon S3 + CloudFront | Hosting estático + CDN global (SSL gratis) | **$1.50** |
| **Backend (Cómputo)** | AWS ECS Fargate Spot | 1 tarea base (0.5 vCPU, 1 GB RAM) 24/7 + escalado en horas pico | **$14.00 - $18.00** |
| **Balanceador** | Application Load Balancer (ALB) | Manejo de tráfico HTTP y WebSockets | **$16.00** |
| **Base de Datos Espacial** | Amazon Aurora Serverless v2 (PostGIS) o Lightsail PG | 0.5 ACU a demanda (o PostgreSQL en Lightsail para máx. ahorro) | **$15.00 - $35.00** |
| **Logs & Monitoreo** | AWS CloudWatch | Con logs optimizados (~100 MB/mes, entra en Free Tier) | **$0.00** *(antes $15+ con DEBUG)* |
| **Certificados y Red** | AWS Certificate Manager + Data Transfer | Tráfico saliente de telemetría (~10 GB/mes) | **$2.00** |
| **TOTAL ESTIMADO** | | **Operación mensual completa** | **~$48.50 - $72.50 / mes** |

> 💡 **Opción Ultra-Económica (Fase Inicial / MVP):**  
> Desplegar mediante **AWS Lightsail Container Service + Lightsail PostgreSQL** (ambos con IP fija y SSL gestionado): costo fijo de **~$25 a $35 USD/mes total**.

---

## 5. Paso a Paso para el Despliegue en AWS

### Paso 1: Empaquetar y Subir la Imagen Docker a AWS ECR
```bash
# Iniciar sesión en AWS ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <TU_ID_AWS>.dkr.ecr.us-east-1.amazonaws.com

# Construir la imagen del backend optimizada
docker build -t viberoute-backend ./VibeRouteColombia-Backend

# Taggear y subir
docker tag viberoute-backend:latest <TU_ID_AWS>.dkr.ecr.us-east-1.amazonaws.com/viberoute-backend:latest
docker push <TU_ID_AWS>.dkr.ecr.us-east-1.amazonaws.com/viberoute-backend:latest
```

### Paso 2: Crear la Base de Datos con Extensión PostGIS
En la instancia de PostgreSQL en AWS:
```sql
CREATE DATABASE route_optimizer;
\c route_optimizer;
CREATE EXTENSION IF NOT EXISTS postgis;
-- Cargar las zonas operativas
\i VibeRouteColombia-Backend/src/main/resources/data-zones.sql
```

### Paso 3: Configurar las Variables de Entorno en ECS Task Definition
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://<ENDPOINT_AURORA_O_RDS>:5432/route_optimizer
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=<TU_PASSWORD_SEGURO>
JWT_SECRET=<TU_JWT_SECRET_SEGURO_256BITS>
GOOGLE_MAPS_KEY=<TU_API_KEY>
GOOGLE_GEMINI_KEY=<TU_API_KEY>
VIBEROUTE_CORS_ALLOWED_ORIGINS=https://app.tudominio.com,https://viberoute.com
```

### Paso 4: Desplegar el Frontend en S3 + CloudFront
```bash
cd VibeRouteColombia-Frontend
npm install
npm run build
# Sincronizar con el bucket S3
aws s3 sync dist/ s3://viberoute-frontend-app --delete
# Invalidar caché de CloudFront
aws cloudfront create-invalidation --distribution-id <ID_DISTRIBUCION> --paths "/*"
```
