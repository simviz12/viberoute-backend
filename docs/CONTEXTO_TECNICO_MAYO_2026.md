# VibeRoute Colombia — Contexto Técnico Detallado del Sistema
**Fecha:** 2026-05-03 (Actualizado)
**Propósito:** Documento de contexto completo para continuar el desarrollo en sesiones futuras. Incluye arquitectura, flujos, patrones de diseño, datos actuales, últimos cambios realizados y consideraciones críticas intocables.

## 1. ARQUITECTURA GENERAL
**Stack Tecnológico:**
- **Backend:** Spring Boot 3 (Java 17+), JPA/Hibernate, PostgreSQL (render.com), WebSocket (STOMP), Google Gemini AI API
- **Frontend:** React 18 + TypeScript, Vite, Zustand (estado global), Framer Motion (animaciones), TailwindCSS, Google Maps API, Lucide Icons
- **Despliegue:** Backend en Render, Frontend local (dev), BD PostgreSQL en Render
- **Ruta del Proyecto:** `C:\Users\Gabriel\Desktop\Tareas\ProyectoFinalPatronesEstrucuras\vibe-route-colombia\`

## 2. ROLES DEL SISTEMA Y SUS DASHBOARDS
| Rol | Dashboard | Descripción |
| :--- | :--- | :--- |
| **SUPER_ADMIN** | `SuperAdminDashboardPage.tsx` | Consola estilo retro/amber tipo terminal. Tiene: Audit Wall (logs en vivo), Master User Index (CRUD de todos los usuarios), Processor Load, Thread Memory. Puede crear/editar/eliminar cualquier usuario. |
| **ADMIN** | `AdminDashboardPage.tsx` | Monitor Global. Tabs: Monitor de Ciudad, Carga Masiva (JSON), Cierres de Caja, Eventos en Vivo, Gestión de Personal. |
| **LOGISTICS** | `AdminDashboardPage.tsx` (mismo componente) | Tabs: Monitor de Ciudad, Consolidar Lotes, Monitor de Despacho, Ver Pedidos, Subir Reporte, Eventos en Vivo, Gestión de Personal. |
| **DRIVER** | `DriverDashboardPage.tsx` | Dashboard móvil-first con 3 vistas: Turno (summary con KPIs), Pedidos (lista de pedidos asignados), Mapa (navegación GPS con Google Maps). |

## 3. MODELO DE DATOS (ENTIDADES JPA)
### 3.1 User (Tabla `users`)
Clase base con herencia JOINED para Driver.
`id, email (unique), passwordHash, name, phone, role (Enum: SUPER_ADMIN, ADMIN, LOGISTICS, DRIVER), assignedCity, pendingPasswordReset`

### 3.2 Driver (Tabla `drivers`, hereda de User)
`maxCapacity, costPerHour, status (Enum: AVAILABLE, ON_ROUTE, INACTIVE, BUSY), location (Coordinate embeddable), completedOrders, failedOrders`
*Importante:* Usa `@Inheritance(strategy = InheritanceType.JOINED)`. Un `findAll()` en DriverRepository hace un JOIN entre users y drivers.

### 3.3 Order (Tabla `orders`)
`id, uuid, address, clientName, location (Coordinate), priority, status, date, timeWindowStart/End, clientReference, city, batchId, deliveryOrder, routeId, actualDeliveryTime, deliveryLat/Lng, nonDeliveryReason, price`
*Patrón implementado:* State Pattern, usando `stateObject` para delegar transiciones de estado a PendingState, OnRouteState, etc.

### 3.4 Batch (Tabla `batches`)
`id, status, city, driver (ManyToOne → Driver), orders (OneToMany → Order), creationDate, manifestUrl, optimizationMode`

### 3.5 Route (Tabla `routes`)
`id, batch (OneToOne → Batch), stops (JSON/List), totalDistance, estimatedTime, aiAnalysis`

## 4. FLUJO OPERATIVO COMPLETO
### 4.1 Ingesta de Pedidos
- **Carga masiva JSON** (`BulkImportModule.tsx` → `POST /orders/bulk`): El admin sube un archivo JSON con pedidos.
- **Geocodificación forzada:** Si el JSON trae coords, se ignoran (ver sección 10) para forzar al backend a calcular las coordenadas reales vía Google Maps en `OrderService.createOrder()`.

### 4.2 Consolidación de Lotes (Panel Logístico)
- **Manual** (`BatchConsolidationModule.tsx`): Selecciona PENDING sin lote y crea lote.
- **Smart Dispatch IA** (`SmartDispatchModal.tsx`): Usa K-Means clustering (determina K basado en pedidos y conductores), asigna por distancia Euclidiana, genera reporte con Gemini AI, muestra Plan Principal y Plan Alternativo.

### 4.3 Asignación de Driver y Optimización
- Al asignar un driver, los pedidos pasan a `ON_ROUTE`. 
- El backend procesa las rutas pasando por `RouteService` -> `RouteOptimizer` -> `OrToolsStrategy`, buscando el algoritmo TSP.

### 4.4 Panel del Driver
- Busca lote por el ID almacenado en la caché (`missionStore`), consulta los tips al backend.
- Modos de optimización de Google Maps (`EFFICIENCY` vs `PRIORITY`).

## 5. SERVICIOS DE IA (Gemini)
1. **ContextualAdvisor:** Recomendación pre-ruta, Desvío de ruta, y Resumen de la jornada.
2. **AIRouteSuggestionService:** Optimización macro del lote.
3. **DeviationDetector:** Genera alerta si se excede el umbral de distancia.
4. **SmartDispatchService:** K-Means clustering + Generación de reporte IA de despacho.

## 6. ENDPOINTS PRINCIPALES
- `POST /auth/login` | `POST /auth/register`
- `POST /orders/bulk`
- `PATCH /orders/{id}/status?status=X` (Usa Patrón State)
- `GET /batches/smart-dispatch/suggest?city=X`
- `POST /batches/{id}/optimize`
- `GET /drivers/fleet-status` | `GET /drivers/debug-all`

## 7. PATRONES DE DISEÑO IMPLEMENTADOS
- **State Pattern:** Transiciones de estado de `Order`.
- **Strategy Pattern:** Modos de optimización de ruta (EFFICIENCY vs PRIORITY) para `OrToolsStrategy`.
- **Observer/WebSocket:** STOMP sobre WebSockets (`/topic/logistica`).
- **DTO Pattern:** Desacople entre backend y frontend (`OrderResponseDTO`, etc).
- **Singleton:** Stores de Zustand en React.

---

## 8. ÚLTIMOS CAMBIOS REALIZADOS Y SOLUCIONES (SESIÓN ACTUAL)
### Solución de Errores y Estabilidad:
1. **Resolución del Error 400 Bad Request al Cargar Mapa:** Se corrigió un error persistente en el que el dashboard del conductor fallaba al intentar obtener información del lote activo. El problema ocurría porque `BatchController.getBatch` intentaba devolver la entidad `Batch` cruda provocando una `LazyInitializationException` en Hibernate (las relaciones `@OneToMany` colapsaban el serializador). Se reemplazó por la devolución de un `Map` seguro.
2. **Límites Excedidos de Google Maps API (Distancias):** Se detectó que el modo `EFFICIENCY` trazaba rutas sin sentido porque `GoogleMapsAdapter` excedía el límite de 100 elementos del API de Google Distance Matrix para lotes grandes (ej. 12 puntos = 144 cálculos). Se implementó un "Fallback" hacia la distancia geométrica Euclidiana en caso de que Google retorne error, estabilizando así el motor OR-Tools.
3. **Mapeo Real vs Simulado en BulkImport:** Se detectó que las "chinchetas" en el mapa estaban en zonas donde Google Maps ni siquiera encontraba carreteras (rompiendo el trazado de la ruta verde) porque el `BulkImportModule.tsx` inyectaba coordenadas falsas generadas en el JSON. Se obligó a ignorar dichas coordenadas falsas y forzar una geocodificación real con el backend.

---

## 9. ⚠️ REGLAS CRÍTICAS / ARCHIVOS INTOCABLES ⚠️
Para garantizar que el sistema no vuelva a fallar con errores de red o cálculos erráticos, se establecen las siguientes reglas de oro:

1. **`BatchController.java` (Endpoint `GET /{id}`):**
   - **NUNCA** intentar retornar directamente la entidad `Batch`. `spring.jpa.open-in-view` está en `false` por razones de arquitectura. Se debe mantener el `Map.of()` que extrae únicamente `id`, `aiCopilotTips` y la información básica del `driver`. Retornar el `Batch` completo reiniciará los errores HTTP 400.

2. **`GoogleMapsAdapter.java` (Lógica Euclidiana):**
   - La matriz Euclidiana implementada en `getDistanceMatrix()` es el salvavidas contra los límites del *Free Tier* de Google Maps. Bajo ninguna circunstancia se debe remover la condición `if ("OK".equals(status)) { ... } else { // Fallback euclidiano }`. Si se quita, OR-Tools volverá a optimizar las rutas con distancias "0", trazando rutas que saltan caóticamente por la ciudad.

3. **`BulkImportModule.tsx` (Ignorar Location JSON):**
   - El mapeo del frontend **debe** establecer `location: undefined` de forma obligatoria cuando parsea el archivo masivo JSON. NUNCA recuperar la línea comentada que leía el `lat/lng` del archivo inyectado, puesto que Google Directions fallará al intentar trazar líneas verdes sobre coordenadas irreales o caídas en el océano. 

4. **Persistencia de `RouteService.java`:**
   - La reescritura en la base de datos `o.setDeliveryOrder(i + 1)` sobre los `Stops` es fundamental. Es lo que permite que el driver preserve su secuencia al cambiar de modos de navegación en la interfaz web. No alterar la iteración que guarda ese orden en el `orderRepository`.

## 10. PRÓXIMOS PASOS (BACKLOG DE TAREAS)
A continuación, se detalla la hoja de ruta para las siguientes iteraciones. **IMPORTANTE PARA LA IA ASISTENTE QUE TOME ESTE CONTEXTO:** Sigue estrictamente las advertencias para no romper la estabilidad actual del sistema.

### 1. Corrección de Reportes PDF
- **Tarea:** Arreglar la generación del PDF de "Planilla Semanal" en el panel logístico y el "Cierre Operativo" en el panel de administrador.
- **⚠️ Advertencia Intocable:** Al modificar `DocumentHubModule.tsx` (frontend) o `ReportService.java` / `ReportController.java` (backend), **NO** alteres los DTOs de respuestas existentes (`OrderResponseDTO`, `SmartDispatchPlanDTO`). Si necesitas más datos para el PDF, crea nuevos endpoints dedicados al reporte o expande los DTOs, pero sin romper la serialización de la vista del Driver o de la vista de Consolidación.

### 2. Diferenciación de Reportes Gerenciales IA
- **Tarea:** Separar la lógica del Reporte Gerencial IA: El panel logístico debe ver un reporte IA local (de su ciudad asignada), mientras que el administrador debe ver un análisis IA a nivel nacional (todas las ciudades).
- **⚠️ Advertencia Intocable:** Al modificar `ContextualAdvisor.java` o `AIController.java` para añadir estos prompts, **NO** modifiques los prompts actuales de `generateDeviationInstruction`, `generatePreRouteRecommendation` o `generateDailySummary`. Estos están finamente calibrados y funcionando perfecto para el repartidor. Crea métodos nuevos específicos para los reportes gerenciales.

### 3. Filtro de Territorios en Panel Administrador
- **Tarea:** Corregir el panel de administrador para que las estadísticas y datos cambien correctamente al alternar entre territorios (Pasto, Bogotá, Medellín). Actualmente muestra los datos de Pasto en todos.
- **⚠️ Advertencia Intocable:** Al enviar el parámetro `city` desde el frontend (`AdminDashboardPage.tsx`) a los endpoints como `/orders/pending` o los de estadísticas, asegúrate de mantener la lógica actual del backend donde, si el usuario es `LOGISTICS`, el sistema ignora el parámetro y fuerza su ciudad asignada. El cambio de ciudad solo debe aplicar libremente si el rol es `ADMIN` o `SUPER_ADMIN`.

### 4. Monitoreo de Logísticos y Repartidores (Panel Admin)
- **Tarea:** Añadir al panel del administrador la capacidad de monitorear a los logísticos de cada ciudad y a la flota de repartidores por territorio.
- **⚠️ Advertencia Intocable:** Al obtener el estado de la flota, **NO** alteres el método core `DriverService.getFleetStatus()` ya que este fue estabilizado y es vital para el despacho inteligente (`SmartDispatchService`). Si necesitas datos extra (como a qué logístico pertenece), crea un nuevo DTO o un nuevo endpoint de monitoreo global exclusivamente para la vista de Admin.

### 5. Pulido del Hub de Documentación
- **Tarea:** Mejorar los detalles visuales y la interfaz del Hub de Documentación.
- **⚠️ Advertencia Intocable:** Mantener el uso de TailwindCSS y los componentes de Framer Motion existentes. No introducir librerías de estilos externas para evitar conflictos con el diseño corporativo actual del dashboard.

### 6. Pruebas de Campo (Eventos Real-Time)
- **Tarea:** Probar los eventos Real-Time (WebSockets) saliendo a probar el sistema físicamente en un carro.
- **⚠️ Advertencia Intocable:** El sistema de WebSockets (STOMP) transmitiendo en `/topic/logistica` ya está configurado y respondiendo en el `OrderService` (cambios de estado). No alterar el Bean de configuración de WebSockets ni las dependencias actuales para evitar desconexiones en móviles.

### 7. Verificación del Copiloto de Voz (IA)
- **Tarea:** Comprobar si el Copiloto de Voz (Web Speech API) sigue funcionando correctamente en el frontend (Ej: "Ey se te olvidó un pedido por acá"). Asegurarse de que no se haya deshabilitado o roto durante las recientes refactorizaciones estructurales.
- **⚠️ Advertencia Intocable:** Si se necesita reconectar el audio, revisar los componentes dentro de `frontend/src/features/ai/` (ej. `DriverCopilotBanner.tsx` o `DeviationDetector`). **NO** alterar la lógica central de Zustand (`missionStore`, `routeStore`) ni el `MapsNavigationModule.tsx` para inyectar esto; usar *useEffect* aislados que escuchen el store global sin forzar re-renderizados pesados en el mapa.

### 8. Navegación 3D Centrada en el Conductor y Copiloto Turn-by-Turn
- **Tarea:** Implementar un botón opcional en el mapa para activar una "Vista 3D de Navegación" (estilo Uber o InDrive). El mapa debe centrarse en el GPS en vivo del conductor (`driverPos`), inclinar la cámara (`tilt`) y seguir la ruta dinámicamente. Esto debe combinarse armónicamente con el Copiloto de Voz para dar instrucciones en tiempo real (ej: "Gira a la derecha en 500 metros"), pero de una manera útil y poco estresante.
- **⚠️ Advertencia Intocable:** Al modificar el `MapsNavigationModule.tsx` para lograr el efecto 3D, **mantén intacta** la lógica original de trazado de polilíneas (`ds.route`) y el renderizado inicial de todos los marcadores (`displayStops`). Usa los métodos de instancia `map.setCenter()`, `map.setHeading()` y `map.setTilt(45)` de Google Maps sin desmontar ni destruir el mapa base. El modo 2D superior (top-down) debe seguir siendo el predeterminado y seguir funcionando sin errores de cámara.

---

## 11. PLAN DE PRUEBAS DE CAMPO (FLUJO E2E EN "EL DRAGÓN")
Este es el protocolo oficial para la prueba de campo final (End-to-End). Caliche y Juanguito deberán simular una operación logística completa saliendo a la calle en "El Dragón" (el carro de pruebas). 

### 🚗 Protocolo de Flujo (Doble Vía)
1. **Fase 1: Preparación (Super Admin / Admin)**
   - El *Super Admin* se asegura de que los usuarios Logísticos y Drivers (Caliche/Juanguito) existan y estén asignados a la ciudad correcta.
   - El *Admin* sube la carga masiva (JSON) simulando los pedidos del día y verifica que no haya errores de importación.
2. **Fase 2: Despacho Inteligente (Logístico)**
   - El *Logístico* abre su panel, usa el "Smart Dispatch IA" (Botón de IA), genera los clústeres geográficos y le asigna el Lote correspondiente al conductor que irá en El Dragón.
3. **Fase 3: Operación en Terreno (Driver en El Dragón)**
   - El *Driver* abre la app en su celular. Revisa la pestaña "Turno" (KPIs) y se dirige a la pestaña "Mapa".
   - Arranca El Dragón. Comienza a seguir la línea verde.
   - **Checklist durante la ruta:** 
     - ¿La chincheta del GPS se mueve fluido?
     - ¿El copiloto de voz interviene correctamente?
     - ¿La app soporta si se apaga la pantalla del celular por un minuto y se vuelve a prender?
     - *Cambiar estado:* Confirmar un par de pedidos como Entregados, y marcar uno como Cancelado (agregando justificación).
4. **Fase 4: Retorno y Monitoreo (Logístico)**
   - Mientras El Dragón está en la calle, el *Logístico* (desde una laptop) monitorea la vista de "Fleet Monitor". 
   - Debe comprobar que los pedidos cambian de color en tiempo real (WebSockets STOMP) sin recargar la página.
5. **Fase 5: Cierre (Admin)**
   - Al finalizar, el *Admin* genera el PDF de "Cierre Operativo" y se valida que el reporte coincida con el dinero y novedades reportadas en El Dragón.

### 📝 Sugerencias de Auditoría (Para Caliche y Juanguito)
- **Lleven un Block de Notas (o chat de WhatsApp fijado):** Anoten cualquier "clic" que se sienta lento o poco intuitivo.
- **Prueben Zonas Muertas:** ¿Qué pasa si pierden los datos móviles por 30 segundos? ¿La app se recupera sola al volver la señal?
- **Ojo con los errores de Consola:** Si están desde una laptop en el carro, dejen abierta la consola (`F12`) para cazar advertencias de React o errores de red.
- **Validar el Resumen IA:** Al entregar el último pedido, verifiquen que el mensaje de cierre (Gemini) tenga sentido con el recorrido que acaban de hacer.

### 🚫 ADVERTENCIA CRÍTICA PARA EL EQUIPO (SOBRE AWS EC2)
Dado que el proyecto ya se encuentra en producción real en **AWS EC2 con Ubuntu**, queda **ESTRICTAMENTE PROHIBIDO** hacer modificaciones directas en:
1. `backend/docker-compose.yml`
2. `backend/Dockerfile`
3. `backend/src/main/resources/application.properties` (Especialmente puertos, strings de conexión a la BD, o variables de entorno nativas).
*Cualquier alteración en la infraestructura de despliegue puede derribar el servidor de AWS y corromper el contenedor de la base de datos de producción.* Si necesitan variables nuevas, úsenlas desde el `.env` local del frontend o dejen que Gabriel actualice el servidor.
