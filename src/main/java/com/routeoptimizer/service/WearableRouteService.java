package com.routeoptimizer.service;

import com.routeoptimizer.dto.DeliveryStatusRequestDto;
import com.routeoptimizer.dto.DeliveryStopDto;
import com.routeoptimizer.dto.RouteSummaryDto;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.exception.RouteNotFoundException;
import com.routeoptimizer.model.entity.Batch;
import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.entity.Route;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.repository.BatchRepository;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.OrderRepository;
import com.routeoptimizer.repository.RouteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de negocio para la sincronización y confirmación de rutas y entregas
 * desde dispositivos Wear OS (Smartwatches) en modo Standalone.
 */
@Service
public class WearableRouteService {

    private static final Logger log = LoggerFactory.getLogger(WearableRouteService.class);

    private final RouteRepository routeRepository;
    private final OrderRepository orderRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final OrderService orderService;
    private final DriverRepository driverRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WearableRouteService(RouteRepository routeRepository,
                                OrderRepository orderRepository,
                                BatchRepository batchRepository,
                                BatchService batchService,
                                OrderService orderService,
                                DriverRepository driverRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.routeRepository = routeRepository;
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
        this.batchService = batchService;
        this.orderService = orderService;
        this.driverRepository = driverRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Obtiene la ruta activa para el conductor autenticado.
     * Consulta el lote activo (en progreso/asignado) o la ruta directa asociada al conductor.
     *
     * @param driverId ID del conductor autenticado en el JWT
     * @return RouteSummaryDto con los metadatos y paradas ordenadas lógicamente
     * @throws RouteNotFoundException si no hay una ruta activa para el conductor
     */
    @Transactional(readOnly = true)
    public RouteSummaryDto getActiveRouteForDriver(Long driverId) {
        log.info("⌚ [WEAR_OS] Buscando ruta activa para el conductor ID: {}", driverId);

        // 1. Intentar resolver mediante el lote activo más reciente del conductor
        Route activeRoute = null;
        List<Batch> activeBatches = batchRepository.findAll().stream()
                .filter(b -> b.getDriver() != null && b.getDriver().getId().equals(driverId))
                .filter(b -> "ASSIGNED".equalsIgnoreCase(b.getStatus()))
                .sorted(Comparator.comparing(Batch::getId).reversed())
                .toList();

        for (Batch batch : activeBatches) {
            Optional<Route> routeByBatch = routeRepository.findByBatchId(batch.getId());
            if (routeByBatch.isPresent()) {
                activeRoute = routeByBatch.get();
                break;
            }
        }

        // 2. Fallback: buscar directamente por driverId en el repositorio de rutas
        if (activeRoute == null) {
            Optional<Route> routeByDriver = routeRepository.findByDriverId(driverId);
            if (routeByDriver.isPresent()) {
                activeRoute = routeByDriver.get();
            }
        }

        // 3. Fallback adicional: buscar cualquier lote asignado no completado del conductor
        if (activeRoute == null) {
            List<Batch> driverBatches = batchRepository.findAll().stream()
                    .filter(b -> b.getDriver() != null && b.getDriver().getId().equals(driverId))
                    .filter(b -> !"COMPLETED".equalsIgnoreCase(b.getStatus()))
                    .sorted(Comparator.comparing(Batch::getId).reversed())
                    .toList();

            for (Batch b : driverBatches) {
                Optional<Route> candidate = routeRepository.findByBatchId(b.getId());
                if (candidate.isPresent()) {
                    activeRoute = candidate.get();
                    break;
                }
            }
        }

        if (activeRoute == null) {
            log.warn("⌚ [WEAR_OS] No se encontró ninguna ruta activa para el conductor ID: {}", driverId);
            throw new RouteNotFoundException("No se encontró una ruta activa para el conductor con ID: " + driverId);
        }

        // 4. Obtener las paradas de la ruta asegurando el orden secuencial de entrega
        List<Order> stops = activeRoute.getStops();
        if (stops == null || stops.isEmpty()) {
            if (activeRoute.getBatchId() != null) {
                stops = orderRepository.findByBatchIdOrderByDeliveryOrderAsc(activeRoute.getBatchId());
            } else {
                stops = orderRepository.findByRouteIdOrderByDeliveryOrderAsc(activeRoute.getId());
            }
        }

        // Ordenamiento explícito por secuencia lógica (deliveryOrder ASC)
        List<DeliveryStopDto> stopDtos = stops.stream()
                .sorted(Comparator.comparing(
                        Order::getDeliveryOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(DeliveryStopDto::fromEntity)
                .toList();

        // 5. Calcular métricas resumidas para la pantalla del smartwatch
        int totalStops = stopDtos.size();
        int completedStops = (int) stopDtos.stream()
                .filter(s -> s.getStatus() == OrderStatus.DELIVERED)
                .count();
        int pendingStops = (int) stopDtos.stream()
                .filter(s -> s.getStatus() == OrderStatus.PENDING || s.getStatus() == OrderStatus.ON_ROUTE)
                .count();

        String driverName = driverRepository.findById(driverId)
                .map(Driver::getName)
                .orElse("Conductor #" + driverId);

        String routeCity = stops.stream()
                .map(Order::getCity)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("Colombia");

        RouteSummaryDto summaryDto = new RouteSummaryDto();
        summaryDto.setRouteId(activeRoute.getId());
        summaryDto.setBatchId(activeRoute.getBatchId());
        summaryDto.setDriverId(driverId);
        summaryDto.setDriverName(driverName);
        summaryDto.setTotalDistanceMeters(activeRoute.getTotalDistanceMeters());
        summaryDto.setEstimatedTimeSeconds(activeRoute.getEstimatedTimeSeconds());
        summaryDto.setTotalStops(totalStops);
        summaryDto.setCompletedStops(completedStops);
        summaryDto.setPendingStops(pendingStops);
        summaryDto.setStatus("IN_PROGRESS");
        summaryDto.setEncodedPolyline(activeRoute.getEncodedPolyline());
        summaryDto.setCity(routeCity);
        summaryDto.setStops(stopDtos);

        log.info("⌚ [WEAR_OS] Ruta #{} sincronizada para smartwatch: {} paradas totales, {} pendientes.",
                activeRoute.getId(), totalStops, pendingStops);

        return summaryDto;
    }

    /**
     * Confirma o actualiza el estado de una entrega desde el smartwatch Wear OS.
     * Mapea estados como DELIVERED, ABSENT, INCIDENT y emite eventos en tiempo real
     * hacia el panel de despachadores vía WebSockets (/topic/logistics).
     *
     * @param driverId ID del conductor autenticado
     * @param deliveryId ID del pedido/parada
     * @param requestDto DTO con status, timestamp y notas opcionales
     * @return DeliveryStopDto con la información actualizada de la parada
     */
    @Transactional
    public DeliveryStopDto updateDeliveryStatus(Long driverId, Long deliveryId, DeliveryStatusRequestDto requestDto) {
        log.info("⌚ [WEAR_OS] Actualizando entrega #{} por conductor #{} a estado: {}",
                deliveryId, driverId, requestDto.getStatus());

        // 1. Validar existencia del pedido
        Order order = orderRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada con ID: " + deliveryId));

        // 2. Control de seguridad y pertenencia: verificar que el pedido pertenece a este conductor
        boolean belongsToDriver = false;
        if (order.getBatchId() != null) {
            Optional<Batch> batchOpt = batchRepository.findById(order.getBatchId());
            if (batchOpt.isPresent() && batchOpt.get().getDriver() != null) {
                belongsToDriver = batchOpt.get().getDriver().getId().equals(driverId);
            }
        }

        if (!belongsToDriver) {
            // Verificación secundaria en órdenes asignadas al conductor
            belongsToDriver = orderRepository.findByDriverId(driverId).stream()
                    .anyMatch(o -> o.getId().equals(deliveryId));
        }

        if (!belongsToDriver) {
            log.warn("⛔ [WEAR_OS] El conductor #{} intentó actualizar la entrega #{} que no le pertenece.",
                    driverId, deliveryId);
            throw new AccessDeniedException("No tienes permiso para actualizar esta entrega.");
        }

        // 3. Mapeo del estado Wear OS (DELIVERED, ABSENT, INCIDENT) hacia el modelo OrderStatus de la aplicación
        String rawStatus = requestDto.getStatus() != null ? requestDto.getStatus().trim().toUpperCase() : "";
        OrderStatus targetStatus;
        String reason = requestDto.getNotes();

        switch (rawStatus) {
            case "DELIVERED" -> {
                targetStatus = OrderStatus.DELIVERED;
                reason = null;
            }
            case "ABSENT" -> {
                targetStatus = OrderStatus.RETURNED;
                reason = (reason != null && !reason.isBlank())
                        ? "ABSENT: " + reason
                        : "Destinatario Ausente (Reportado desde Smartwatch Wear OS)";
            }
            case "INCIDENT" -> {
                targetStatus = OrderStatus.CANCELLED;
                reason = (reason != null && !reason.isBlank())
                        ? "INCIDENT: " + reason
                        : "Incidente en Entrega (Reportado desde Smartwatch Wear OS)";
            }
            case "RETURNED" -> {
                targetStatus = OrderStatus.RETURNED;
            }
            case "CANCELLED" -> {
                targetStatus = OrderStatus.CANCELLED;
            }
            default -> throw new IllegalArgumentException(
                    "Estado no soportado: '" + rawStatus + "'. Valores permitidos: DELIVERED, ABSENT, INCIDENT");
        }

        // 4. Ejecutar la transición de estado mediante el OrderService
        Order updatedOrder = orderService.updateStatus(deliveryId, targetStatus, reason);

        // Si el smartwatch proporcionó una estampa de tiempo específica, la registramos
        if (requestDto.getTimestamp() != null) {
            updatedOrder.setActualDeliveryTime(requestDto.getTimestamp());
            updatedOrder = orderRepository.save(updatedOrder);
        }

        LocalDateTime eventTimestamp = requestDto.getTimestamp() != null
                ? requestDto.getTimestamp()
                : LocalDateTime.now();

        // 5. Crucial: Notificar al panel web de despachadores en tiempo real vía WebSockets
        String driverName = driverRepository.findById(driverId)
                .map(Driver::getName)
                .orElse("Conductor #" + driverId);

        Map<String, Object> dispatcherAlert = new HashMap<>();
        dispatcherAlert.put("eventType", "WEARABLE_DELIVERY_CONFIRMATION");
        dispatcherAlert.put("source", "WEAR_OS_STANDALONE");
        dispatcherAlert.put("deliveryId", updatedOrder.getId());
        dispatcherAlert.put("orderId", updatedOrder.getId());
        dispatcherAlert.put("driverId", driverId);
        dispatcherAlert.put("driverName", driverName);
        dispatcherAlert.put("wearableStatus", rawStatus);
        dispatcherAlert.put("orderStatus", updatedOrder.getStatus().name());
        dispatcherAlert.put("timestamp", eventTimestamp.toString());
        dispatcherAlert.put("city", updatedOrder.getCity());
        dispatcherAlert.put("address", updatedOrder.getAddress());
        dispatcherAlert.put("clientName", updatedOrder.getClientName());
        dispatcherAlert.put("notes", reason != null ? reason : "");

        try {
            messagingTemplate.convertAndSend("/topic/logistics", dispatcherAlert);
            messagingTemplate.convertAndSend("/topic/wearable/deliveries", dispatcherAlert);
            log.info("📡 [WEAR_OS] Alerta en tiempo real enviada a despachadores (/topic/logistics): Pedido #{} -> {}",
                    updatedOrder.getId(), updatedOrder.getStatus());
        } catch (Exception wsError) {
            log.error("⚠️ [WEAR_OS] Error enviando notificación WebSocket: {}", wsError.getMessage());
        }

        return DeliveryStopDto.fromEntity(updatedOrder);
    }
}

