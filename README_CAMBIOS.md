# Bitácora de Cambios del Proyecto VibeRoute Colombia
**Ramas integradas:** `el_nuevomapa` ➔ `develop`  
**Fecha:** Octubre 2026  
**Entorno de ejecución:** Docker Compose (Frontend Vite en puerto 3000, Backend Spring Boot en puerto 8080, PostgreSQL 17 en puerto 5433).

---

## 1. Resumen Ejecutivo de la Migración
Se completó con éxito la transición técnica integral hacia un ecosistema de mapeo y enrutamiento **100% de código abierto (Open Source)**, eliminando la dependencia obligatoria de APIs comerciales de terceros con costo recurrente (Google Maps Platform y CartoDB), logrando:
1. **Coste cero de licenciamiento por consumo de mapas**.
2. **Eliminación total de marcas de agua restrictivas** (`API KEY REQUIRED`).
3. **Optimización geoespacial urbana** para la red vial de Pasto, Colombia.
4. **Seguimiento GPS reactivo y en vivo** del conductor y de toda la flota en tiempo real.

---

## 2. Cambios Implementados en el Frontend (`VibeRouteColombia-Frontend`)

### A. Migración de Mapas a Leaflet + OpenStreetMap
- **Reemplazo de API propietaria:** Sustitución del SDK `@react-google-maps/api` por **Leaflet 1.9.4** montado dinámicamente con estilos CSS oficiales.
- **Teselas 100% Libres:** Configuración de OpenStreetMap (`https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`) como proveedor de mapas base sin requerimiento de API key ni marcas de agua.
- **Modo Oscuro Dinámico Nativo:** Implementación de filtro CSS de luminancia, contraste e inversión controlada sobre el panel de teselas de Leaflet (`.leaflet-tile-pane`), permitiendo conmutar entre modo claro y modo oscuro sin solicitar capas de pago externas.

### B. Corrección de Renderizado Inmediato de Paradas
- **Diagnóstico:** Los marcadores se intentaban renderizar antes de que la inicialización asíncrona de Leaflet completara el montaje del mapa, requiriendo clics del usuario para forzar re-render.
- **Solución:** Introducción del estado `mapReady`, sincronizado con los hooks reactivos de pedidos, flota y polilínea. Al cargar, se invoca `leafletMapRef.current.invalidateSize()` y `fitBounds()` ajustando automáticamente el zoom y encuadre a todos los pedidos activos.

### C. Secuencia de Ruta y Optimización Lógica ("Eficiencia" vs "Prioridad")
- **Modo Prioridad:** Clasifica las paradas situando primero los envíos marcados como `HIGH`, seguidos de `MEDIUM` y `LOW`.
- **Modo Eficiencia:**
  1. Si las órdenes ya cuentan con una secuencia optimizada generada por el backend con OR-Tools (`deliveryOrder` / `delivery_order`), se respeta exactamente dicho orden secuencial.
  2. Si no, aplica un algoritmo progresivo de **Vecino Más Cercano (Nearest Neighbor)** tomando como origen la posición GPS real del conductor, eliminando inversiones artificiales que distorsionaban el sentido vial de la ciudad.

### D. Seguimiento GPS Continuo en Tiempo Real
- **Tracking Activo:** Integración de `navigator.geolocation.watchPosition` con `{ enableHighAccuracy: true, maximumAge: 3000 }`.
- **Efecto en Vivo:** El marcador del conductor (círculo azul con pulso de radar) se desplaza suavemente por las calles a medida que se mueve físicamente, actualizando automáticamente el cálculo de distancia y tiempo estimado (ETA) mediante **OSRM (Open Source Routing Machine)**.

### E. Tarjeta Modal de Pedido y Acción Directa de Entrega
- **Navegación e Interacción:** Al presionar cualquier marcador en el mapa o el banner de "Siguiente Destino", el mapa centra la vista con zoom suave (`setView([lat, lng], 16)`).
- **Detalle Completo:** Visualización de dirección, cliente, referencia, ciudad, valor a cobrar y observaciones/notas.
- **Llamada Telefónica:** Botón directo accesible para llamar al destinatario (`tel:${phone}`).
- **Botón de Entrega Inmediata:** Botón `Marcar como Entregado` que ejecuta `PUT /api/v1/orders/{id}/status?status=DELIVERED` y actualiza la hoja de ruta en tiempo real sin salir de la vista de mapa.

### F. Refactorización de Diseño y UX
- Eliminación de emojis de los botones superiores de control.
- Elevación del card informativo inferior para garantizar la visibilidad permanente de la barra de navegación del conductor (`Turno`, `Mapa`, `Pedidos`).

---

## 3. Cambios Implementados en el Backend (`VibeRouteColombia-Backend`)

### A. Adaptador de Enrutamiento Abierto (`OpenSourceRoutingAdapter.java`)
- Creación del componente Spring `@Component("openSourceRoutingAdapter")` que implementa la interfaz de cálculo de matrices de distancia y tiempos de viaje.
- Integración con el motor público de enrutamiento OSRM (`router.project-osrm.org`) con fallback automático a cálculo geométrico Haversine con factor de corrección de topología urbana para evitar caídas de servicio si no hay conexión externa.

### B. Mantenimiento del Adaptador de Google Maps
- Se mantuvo `GoogleMapsAdapter.java` como opción desacoplada y configurable mediante la propiedad `routing.provider=osrm` o `routing.provider=google` en `application.properties`.

### C. Persistencia y Secuenciación de Envíos
- Enrutador y optimizador (`RouteService.java` y `RouteOptimizer.java`) persisten el atributo `deliveryOrder` secuencial (1..N) en la tabla `orders` tras la optimización de lotes, facilitando que el frontend dibuje las paradas en orden idéntico al cálculo matemático de OR-Tools.

### D. Documentación de Infraestructura y Pruebas
- **`docs/GUIA_DESPLIEGUE_AWS_Y_LOGS.md`:** Manual exhaustivo para despliegue productivo en Amazon Web Services (EC2, ECS, RDS, CloudWatch) y configuración de variables seguras.
- **`docs/INFORME_ESTRES_BACKEND.md`:** Informe técnico de pruebas de carga y estrés del backend bajo concurrencia simula de conductores y lotes.

---

## 4. Estado de las Ramas en GitHub

| Repositorio | Rama de Trabajo | Rama Integrada | Estado |
| :--- | :--- | :--- | :--- |
| **VibeRouteColombia-Frontend** | `el_nuevomapa` | `develop` | ✅ Pushed y al día con `origin/develop` |
| **VibeRouteColombia-Backend** | `el_nuevomapa` | `develop` | ✅ Pushed y al día con `origin/develop` |
