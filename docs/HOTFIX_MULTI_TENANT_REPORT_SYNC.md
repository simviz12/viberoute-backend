# Hotfix: Multi-Tenant Security and Report Synchronization

Fecha: 2026-05-29

## Alcance

Este hotfix finaliza la conexión entre seguridad JWT, aislamiento por ciudad para usuarios `LOGISTICS`, generación segura de reportes y sincronización de filtros en frontend.

Se trabajó principalmente sobre controladores Java y componentes React. Durante la verificación aparecieron dos errores de compilación en servicios ya modificados previamente; se corrigieron de forma mínima para dejar el backend compilando.

## Objetivos Cubiertos

- Forzar aislamiento por ciudad para usuarios `LOGISTICS`.
- Evitar acceso cross-city por parámetros manipulados desde frontend.
- Retornar `403 Forbidden` sin filtrar datos cuando un usuario `LOGISTICS` intenta acceder a otra ciudad.
- Sincronizar filtros activos en exportación Excel.
- Enviar ciudad activa al generar PDF desde el centro de despacho.
- Agregar reset de filtros sin recargar la página.
- Confirmar compilación backend y build frontend.

## Archivos Modificados

### Backend - Controladores

- `AnalyticsController.java`
- `DriverController.java`
- `BatchController.java`
- `OrderController.java`
- `ReportController.java`

### Backend - Correcciones de Compilación

- `ReportService.java`
- `SmartDispatchService.java`

Estas correcciones fueron puntuales:

- Se renombró una variable local duplicada `auth` a `reportAuth`.
- Se actualizó `driverService.getFleetStatus()` para pasar el filtro de ciudad requerido.

### Frontend

- `OrdersManagementModule.tsx`
- `LogisticsDispatchCenter.tsx`

## Cambios de Seguridad

### Extracción de Usuario Autenticado

En los controladores se agregó lectura del usuario autenticado desde `SecurityContextHolder`.

El flujo usado es:

```java
var auth = SecurityContextHolder.getContext().getAuthentication();
return auth != null && auth.getPrincipal() instanceof User user ? user : null;
```

Con esto se obtiene:

- Rol del usuario.
- Ciudad asignada.
- Identidad autenticada disponible en el contexto JWT.

### Aislamiento Para LOGISTICS

Si el usuario autenticado tiene rol `LOGISTICS`, el backend fuerza `user.getAssignedCity()` como ciudad efectiva.

Esto evita que el frontend pueda manipular parámetros como:

- `?city=Bogotá`
- `/city/Medellín`
- exportaciones con ciudad no asignada.

### Respuestas 403

Se agregó rechazo explícito para accesos cross-city en:

- Consulta de driver por ID.
- Cambio de estado de driver.
- Actualización de ubicación de driver.
- Asignación de driver a batch.
- Consulta de batch por ID.
- Optimización de batch.
- Actualización de manifiesto.
- Smart dispatch.
- Consulta de órdenes por ID.
- Cambio de estado de órdenes.
- Eliminación de órdenes.
- `/orders/city/{city}`.
- `/orders/city/{city}/count`.
- Exportaciones de reportes con ciudad manipulada.

La respuesta aplicada es:

```java
ResponseEntity.status(HttpStatus.FORBIDDEN).build();
```

No se retorna body con detalles para evitar filtrado de información.

## Cambios por Controlador

### AnalyticsController

- `delivery-summary` y `driver-ranking` usan ciudad efectiva.
- `financial-summary` ahora también fuerza ciudad para `LOGISTICS`.

### DriverController

- Listados usan filtro de ciudad efectivo.
- Operaciones por ID validan `assignedCity` del driver.
- Si el driver no pertenece a la ciudad asignada del usuario `LOGISTICS`, retorna `403`.

### BatchController

- Listados y pendientes usan ciudad efectiva.
- Accesos por ID validan ciudad del batch.
- Asignación de driver valida tanto ciudad del batch como ciudad del driver.
- Smart dispatch fuerza ciudad para usuarios `LOGISTICS`.
- Smart dispatch apply rechaza clusters cross-city.

### OrderController

- Listado general y pendientes usan ciudad efectiva.
- Consulta, actualización y eliminación por ID validan ciudad de la orden.
- Endpoints `/city/{city}` y `/city/{city}/count` retornan `403` en intento cross-city.

### ReportController

- Se validan solicitudes con ciudad explícita.
- Para `LOGISTICS`, una ciudad distinta a la asignada retorna `403`.
- Se preserva comportamiento global/admin.
- Exportaciones de Word/Excel reciben:

```java
status
driver
search
```

Estos filtros se reenvían a `ReportService`.

## Cambios Frontend

### OrdersManagementModule.tsx

Se actualizó el reset de filtros:

- Limpia búsqueda.
- Limpia estado.
- Limpia filtro de driver.
- Reinicia página actual.
- Limpia selección masiva.
- Recarga dataset por API sin refrescar la página.

El botón usa la clase solicitada:

```tsx
className="bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl px-4 py-2 text-sm font-bold transition-all"
```

La exportación Excel ya construye `URLSearchParams` dinámicamente y evita enviar filtros vacíos:

- `city`
- `status`
- `driver`
- `search`
- timestamp anti-cache `t`

### LogisticsDispatchCenter.tsx

`generatePDF()` ahora envía la ciudad activa si existe:

```ts
/reports/generate-global?city=<city>
```

El backend sigue siendo la autoridad final. Para `LOGISTICS`, si la ciudad enviada no coincide con la asignada, el controlador responde `403`.

## Verificación Ejecutada

### Backend

Comando:

```powershell
mvn clean test -DskipTests
```

Resultado:

```text
BUILD SUCCESS
```

### Frontend

Comando:

```powershell
npm run build
```

Resultado:

```text
built successfully
```

Vite reportó únicamente warning de chunks grandes, no error de compilación.

## Nota Sobre Errores del IDE

Después de corregir los errores reales, IntelliJ mostró problemas stale como:

- `SmartDispatchPlanDTO cannot be resolved`
- import no resuelto de `com.routeoptimizer.dto.SmartDispatchPlanDTO`

Se verificó que:

- `SmartDispatchPlanDTO.java` existe.
- El paquete declarado es correcto: `com.routeoptimizer.dto`.
- Maven compila limpio con `mvn clean test -DskipTests`.

Por lo tanto, esos mensajes corresponden a caché/índice del IDE. Acción recomendada:

- Reload All Maven Projects.
- Si persiste: `File > Invalidate Caches`.

## Riesgos y Consideraciones

- El hotfix fue intencionalmente quirúrgico; no se refactorizó arquitectura.
- Los servicios y repositorios ya estaban preparados y no se reescribieron.
- La capa backend queda como autoridad para ciudad, no el frontend.
- Algunos endpoints debug existentes no fueron eliminados porque estaban fuera del alcance funcional solicitado.
- El build backend y frontend pasan tras las correcciones.

