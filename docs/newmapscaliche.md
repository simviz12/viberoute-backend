# newmapscaliche — Cambios Backend (rama `mapa`)

**Proyecto:** VibeRoute Colombia  
**Ámbito:** Backend Spring Boot (`com.routeoptimizer`)  
**Rama Git:** `mapa`  
**Fecha de referencia:** Mayo 2026  
**Relación:** Parche coordinado con hotfix de mapa móvil y seguridad del panel **DRIVER** en el frontend (`frontend_new`).

---

## 1. Resumen ejecutivo

Este documento describe los cambios aplicados al backend para soportar:

1. **Mapa y navegación del conductor** (datos geoespaciales correctos, pedidos acotados por repartidor).
2. **Endurecimiento de seguridad** (CORS, WebSockets, contraseñas, exposición de datos).
3. **Desarrollo local con Vite** (`localhost:5173`) junto a Docker (`:8080`, `:3000`).

**No es un rediseño arquitectónico.** La estructura por capas (Controller → Service → Repository) se mantiene. El impacto se estima en **medio-bajo** (≈ 4–5/10): cambios focalizados, reversibles y alineados con principio de mínimo privilegio.

---

## 2. Objetivos del parche

| Objetivo | Descripción |
|----------|-------------|
| Geolocalización fiable | Corregir consulta PostGIS para alertas de proximidad. |
| Aislamiento DRIVER | El conductor solo consulta sus pedidos vía API dedicada. |
| Seguridad operativa | BCrypt uniforme, WebSocket autenticado, CORS centralizado. |
| DevEx | Permitir frontend Vite en puerto 5173 sin errores CORS. |
| Estabilidad en despliegue | Evitar reseteo de contraseña del super admin en cada arranque. |

---

## 3. Archivos modificados o nuevos

### 3.1 Archivos nuevos

| Archivo | Responsabilidad |
|---------|-----------------|
| `src/main/java/com/routeoptimizer/security/WebSocketAuthChannelInterceptor.java` | Valida JWT en el frame STOMP `CONNECT` antes de permitir suscripciones. |

### 3.2 Archivos modificados

| Archivo | Tipo de cambio |
|---------|----------------|
| `config/SecurityConfig.java` | CORS ampliado (5173), comentario WebSocket, eliminación de `.cors()` duplicado. |
| `config/WebSocketConfig.java` | Registro del interceptor inbound; orígenes WebSocket alineados con CORS HTTP. |
| `config/UserSeeder.java` | Ya no sobrescribe `password_hash` del admin existente en cada startup. |
| `controller/OrderController.java` | Nuevo `GET /orders/mine`; `GET /orders` prohibido para rol `DRIVER`. |
| `controller/DriverController.java` | Eliminado `GET /drivers/debug-all`; removido `@CrossOrigin("*")`. |
| `controller/AuthController.java` | Removido `@CrossOrigin("*")`. |
| `controller/BatchController.java` | Removido `@CrossOrigin("*")`. |
| `controller/SystemManagementController.java` | Removido `@CrossOrigin("*")`. |
| `controller/RouteController.java` | Removido `@CrossOrigin("*")`. |
| `controller/ReportController.java` | Removido `@CrossOrigin("*")`. |
| `controller/LocationController.java` | Removido `@CrossOrigin("*")`. |
| `controller/AnalyticsController.java` | Removido `@CrossOrigin("*")`. |
| `controller/AIController.java` | Removido `@CrossOrigin("*")`. |
| `repository/OrderRepository.java` | Fix PostGIS `findNearbyOrders`; nuevo `findByDriverId`. |
| `service/OrderService.java` | Método `findByDriverId(Long driverId)`. |
| `service/DriverService.java` | `PasswordEncoder` obligatorio al crear conductores. |

---

## 4. Cambios detallados

### 4.1 PostGIS — `OrderRepository.findNearbyOrders`

**Problema:** La query nativa usaba columnas `o.lat` / `o.lng`, inexistentes. La entidad `Order` usa `@Embedded Coordinate location` → columnas JPA: `location_lat`, `location_lng`.

**Solución:** Query corregida con `location_lng`, `location_lat` y filtro `IS NOT NULL`.

**Impacto:** `ProximityAlertService` y alertas WebSocket de colisión usan coordenadas reales.

---

### 4.2 API de pedidos por rol — `OrderController`

#### Nuevo endpoint

```
GET /api/v1/orders/mine
GET /orders/mine
```

| Requisito | Detalle |
|-----------|---------|
| Autenticación | JWT Bearer obligatorio |
| Rol | Solo `DRIVER` |
| Respuesta | `200` + lista `OrderResponseDTO` del conductor autenticado |
| Lógica | `orderService.findByDriverId(user.getId())` — pedidos cuyo `batch` pertenece al `driver_id` |

#### Cambio en listado global

```
GET /api/v1/orders
```

| Rol | Comportamiento |
|-----|----------------|
| `DRIVER` | **403 Forbidden** (cuerpo vacío) |
| `LOGISTICS` | Lista filtrada por `assignedCity` del usuario |
| `ADMIN` / `SUPER_ADMIN` | Lista completa o por query `?city=` |

**Contrato para frontend:** El panel conductor debe usar **`/orders/mine`**, no `/orders`.

---

### 4.3 Repositorio — `findByDriverId`

```java
@Query("SELECT o FROM Order o JOIN Batch b ON o.batchId = b.id WHERE b.driver.id = :driverId")
List<Order> findByDriverId(@Param("driverId") Long driverId);
```

Pedidos vinculados a lotes (`batches`) asignados al repartidor. Si el conductor no tiene lotes, la lista será vacía (comportamiento esperado).

---

### 4.4 Contraseñas de conductores — `DriverService`

**Antes:** `setPasswordHash(dto.getPassword())` en texto plano o `"defaultHash"`.

**Ahora:**

- Inyección de `PasswordEncoder` (BCrypt).
- Contraseña obligatoria; `IllegalArgumentException` si viene vacía.
- Hash: `passwordEncoder.encode(rawPassword)`.

Alineado con `AuthService.register` para rol `DRIVER`.

---

### 4.5 WebSockets — autenticación STOMP

| Componente | Comportamiento |
|------------|----------------|
| Handshake HTTP `/ws-alertas/**` | Sigue en `permitAll()` (SockJS) |
| Frame STOMP `CONNECT` | `WebSocketAuthChannelInterceptor` exige header `Authorization: Bearer <JWT>` |
| Token inválido / ausente | `AccessDeniedException` — conexión rechazada |

**Configuración en `WebSocketConfig`:**

- `configureClientInboundChannel` registra el interceptor.
- Orígenes permitidos: `localhost:3000`, `127.0.0.1:3000`, `localhost:5173`, `127.0.0.1:5173`.

---

### 4.6 CORS — `SecurityConfig`

Orígenes HTTP permitidos (centralizado, sin `@CrossOrigin` por controlador):

- `http://localhost:3000` — frontend Docker (nginx)
- `http://127.0.0.1:3000`
- `http://localhost:5173` — Vite dev server
- `http://127.0.0.1:5173`

Métodos: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.  
Headers: `Authorization`, `Content-Type`, etc.  
`allowCredentials: true`.

**Nota empresarial:** En producción conviene externalizar orígenes a variables de entorno (`CORS_ALLOWED_ORIGINS`).

---

### 4.7 UserSeeder — cuenta maestra

**Antes:** En cada arranque se re-hasheaba y guardaba la contraseña del admin desde `ADMIN_PASSWORD`.

**Ahora:**

- Si el usuario existe: actualiza rol, nombre y ciudad; **no modifica** `password_hash`.
- Si no existe: crea super admin con contraseña del `.env`.
- Se eliminó log del email en consola.

---

### 4.8 Endpoints y superficie de ataque

| Cambio | Motivo |
|--------|--------|
| Eliminado `GET /drivers/debug-all` | Fuga de datos de flota en producción |
| Removido `@CrossOrigin(origins = "*")` en todos los controladores | CORS único y coherente en `SecurityConfig` |

---

## 5. Matriz de permisos (pedidos y mapa)

| Endpoint | SUPER_ADMIN | ADMIN | LOGISTICS | DRIVER |
|----------|:-----------:|:-----:|:---------:|:------:|
| `GET /orders` | ✅ | ✅ | ✅ (ciudad) | ❌ 403 |
| `GET /orders/mine` | ❌ 403 | ❌ 403 | ❌ 403 | ✅ |
| `PATCH /orders/{id}/status` | ✅ | ✅ | ✅ | ✅ |
| `GET /batches/**` | ✅ | ✅ | ✅ | ✅ |
| `POST /batches/{id}/optimize` | ✅ | ✅ | ✅ | ✅* |
| WebSocket CONNECT | ✅ con JWT | ✅ | ✅ | ✅ |

\*Según asignación de lote y reglas de negocio existentes.

---

## 6. Integración con el frontend (rama `mapa`)

| Pantalla | Endpoint backend esperado |
|----------|---------------------------|
| `DriverDashboardPage` — carga inicial | `GET /orders/mine` |
| `OrdersManagementModule` (modo conductor) | `GET /orders/mine` |
| `MapsNavigationModule` | `POST /batches/{id}/optimize`, store `backupOrders` sincronizado al entregar |
| Login (Vite) | `POST /auth/login` desde origen `http://localhost:5173` |

---

## 7. Despliegue y Docker

### Variables de entorno relevantes (`.env` raíz)

```env
ADMIN_EMAIL=admin@viberoute.com
ADMIN_PASSWORD=admin123
JWT_SECRET=<base64-256-bit>
GOOGLE_MAPS_KEY=<key>
SPRING_DATASOURCE_URL=jdbc:postgresql://viberoute-db:5432/route_optimizer
```

### Reconstruir backend tras merge

```bash
cd VibeRouteColombia
docker compose up -d --build backend
```

### Desarrollo híbrido (recomendado para probar `newmapscaliche`)

```bash
# Terminal 1 — infra + API
docker compose up -d db backend

# Terminal 2 — frontend con últimos cambios
cd frontend_new && npm run dev
# → http://localhost:5173
```

---

## 8. Pruebas manuales sugeridas

### 8.1 CORS y login

```http
OPTIONS http://localhost:8080/api/v1/auth/login
Origin: http://localhost:5173
```
Esperado: respuesta CORS sin rechazo.

### 8.2 Conductor — pedidos propios

1. Login como `DRIVER` → obtener JWT.
2. `GET /api/v1/orders/mine` con `Authorization: Bearer <token>` → **200**.
3. `GET /api/v1/orders` con el mismo token → **403**.

### 8.3 PostGIS (si hay datos en `orders`)

Verificar que `ProximityAlertService` no lanza error SQL al reportar ubicación del conductor.

### 8.4 WebSocket

Conectar a `/ws-alertas` con SockJS; en `CONNECT` enviar `Authorization: Bearer <token>`. Sin token → conexión denegada.

---

## 9. Riesgos conocidos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Frontend antiguo llama `GET /orders` como DRIVER | Actualizar a `/orders/mine` (hecho en rama `mapa` front). |
| Imagen Docker sin rebuild | `docker compose build backend` tras merge. |
| Conductor sin pedidos en BD | Asignar lote + pedidos desde panel ADMIN/LOGISTICS. |
| CORS hardcodeado | Roadmap: `application.yml` + perfiles `dev`/`prod`. |
| JWT en WebSocket sin revocación | Comportamiento previo al parche; sin cambio de política de expiración. |

---

## 10. Lo que NO cambió

- Motor de optimización (OR-Tools) y lógica de lotes.
- Modelo de estados de pedidos (State pattern).
- Endpoints de autenticación (`/auth/login`, `/auth/register`) salvo CORS.
- Esquema general de JWT y BCrypt en `AuthService`.
- Rutas legacy duplicadas (`/api/v1/*` y `/*` sin prefijo) — deuda preexistente.

---

## 11. Roadmap recomendado (nivel empresarial)

1. Externalizar orígenes CORS a configuración por entorno.
2. Documentar API en OpenAPI 3 (`/orders/mine` en spec).
3. Tests de integración: `MockMvc` — DRIVER 403 en `/orders`, 200 en `/orders/mine`.
4. Testcontainers + PostGIS para `findNearbyOrders`.
5. Unificar rutas solo bajo `/api/v1` en versión mayor.

---

## 12. Referencias internas

- `docs/GUIA_REMEDIACION_SEGURIDAD.md` — remediaciones previas OWASP.
- `docs/CONTEXTO_TECNICO_MAYO_2026.md` — arquitectura general y roles.
- Frontend (mismo release): hotfix mapa en `MapsNavigationModule.tsx`, rama `mapa`.

---

## 13. Glosario

| Término | Significado en este parche |
|---------|----------------------------|
| **newmapscaliche** | Nombre interno del release backend+mapa conductor (caliche = parche/local, no producto separado). |
| **mapa** | Rama Git donde se integraron estos cambios. |
| **mine** | Endpoint REST “mis pedidos” para el repartidor autenticado. |

---

*Documento generado para trazabilidad del release **newmapscaliche**. Mantener actualizado al mergear `mapa` → `develop` o `main`.*
