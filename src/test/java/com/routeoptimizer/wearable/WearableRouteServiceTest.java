package com.routeoptimizer.wearable;

import com.routeoptimizer.dto.DeliveryStatusRequestDto;
import com.routeoptimizer.dto.DeliveryStopDto;
import com.routeoptimizer.dto.RouteSummaryDto;
import com.routeoptimizer.exception.RouteNotFoundException;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.entity.Batch;
import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.entity.Route;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.model.enums.Priority;
import com.routeoptimizer.repository.BatchRepository;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.OrderRepository;
import com.routeoptimizer.repository.RouteRepository;
import com.routeoptimizer.service.BatchService;
import com.routeoptimizer.service.OrderService;
import com.routeoptimizer.service.WearableRouteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WearableRouteServiceTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private BatchService batchService;

    @Mock
    private OrderService orderService;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WearableRouteService wearableRouteService;

    private Driver testDriver;
    private Batch testBatch;
    private Route testRoute;
    private Order stop1;
    private Order stop2;

    @BeforeEach
    void setUp() throws Exception {
        testDriver = new Driver();
        setPrivateField(testDriver, "id", 10L);
        testDriver.setName("Carlos Conductor");

        testBatch = new Batch();
        testBatch.setId(100L);
        testBatch.setDriver(testDriver);
        testBatch.setStatus("ASSIGNED");

        stop1 = new Order();
        setPrivateField(stop1, "id", 1L);
        stop1.setAddress("Calle 10 # 5-20");
        stop1.setClientName("Cliente Uno");
        stop1.setCity("Bogotá");
        stop1.setLocation(new Coordinate(4.60, -74.08));
        stop1.setDeliveryOrder(1);
        stop1.setStatus(OrderStatus.ON_ROUTE);
        stop1.setPriority(Priority.HIGH);
        stop1.setBatchId(testBatch.getId());

        stop2 = new Order();
        setPrivateField(stop2, "id", 2L);
        stop2.setAddress("Carrera 7 # 71-21");
        stop2.setClientName("Cliente Dos");
        stop2.setCity("Bogotá");
        stop2.setLocation(new Coordinate(4.65, -74.05));
        stop2.setDeliveryOrder(2);
        stop2.setStatus(OrderStatus.PENDING);
        stop2.setPriority(Priority.MEDIUM);
        stop2.setBatchId(testBatch.getId());

        List<Order> stops = new ArrayList<>();
        stops.add(stop2); // insertadas en orden invertido para comprobar ordenamiento secuencial
        stops.add(stop1);

        testRoute = new Route.Builder()
                .forBatch(testBatch.getId())
                .forDriver(testDriver.getId())
                .withStops(stops)
                .withTotalDistance(12500)
                .withEstimatedTime(2500)
                .withEncodedPolyline("abcde_polyline")
                .build();
        setPrivateField(testRoute, "id", 50L);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Debe sincronizar ruta activa y ordenar paradas por deliveryOrder ascendente")
    void testGetActiveRouteForDriver_Success() {
        when(batchService.findActiveBatchByDriverId(10L)).thenReturn(Optional.of(testBatch));
        when(routeRepository.findByBatchId(100L)).thenReturn(Optional.of(testRoute));
        when(driverRepository.findById(10L)).thenReturn(Optional.of(testDriver));

        RouteSummaryDto result = wearableRouteService.getActiveRouteForDriver(10L);

        assertNotNull(result);
        assertEquals(50L, result.getRouteId());
        assertEquals(100L, result.getBatchId());
        assertEquals(10L, result.getDriverId());
        assertEquals("Carlos Conductor", result.getDriverName());
        assertEquals(2, result.getTotalStops());
        assertEquals(2, result.getPendingStops());
        assertEquals(0, result.getCompletedStops());

        // Verificación de orden secuencial
        assertEquals(1L, result.getStops().get(0).getDeliveryId());
        assertEquals(1, result.getStops().get(0).getDeliveryOrder());
        assertEquals(2L, result.getStops().get(1).getDeliveryId());
        assertEquals(2, result.getStops().get(1).getDeliveryOrder());
    }

    @Test
    @DisplayName("Debe lanzar RouteNotFoundException si no hay lote ni ruta activa para el conductor")
    void testGetActiveRouteForDriver_NotFound() {
        when(batchService.findActiveBatchByDriverId(99L)).thenReturn(Optional.empty());
        when(routeRepository.findByDriverId(99L)).thenReturn(Optional.empty());
        when(batchRepository.findAll()).thenReturn(List.of());

        assertThrows(RouteNotFoundException.class, () -> {
            wearableRouteService.getActiveRouteForDriver(99L);
        });
    }

    @Test
    @DisplayName("Debe confirmar entrega DELIVERED y notificar a despachadores vía WebSockets")
    void testUpdateDeliveryStatus_Delivered() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(stop1));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(testBatch));
        when(driverRepository.findById(10L)).thenReturn(Optional.of(testDriver));

        Order deliveredOrder = new Order();
        try {
            setPrivateField(deliveredOrder, "id", 1L);
        } catch (Exception ignored) {}
        deliveredOrder.setStatus(OrderStatus.DELIVERED);
        deliveredOrder.setClientName("Cliente Uno");
        deliveredOrder.setAddress("Calle 10 # 5-20");
        deliveredOrder.setCity("Bogotá");

        when(orderService.updateStatus(1L, OrderStatus.DELIVERED, null)).thenReturn(deliveredOrder);

        DeliveryStatusRequestDto request = new DeliveryStatusRequestDto(
                "DELIVERED",
                LocalDateTime.of(2026, 11, 7, 18, 0, 0),
                "Entregado en recepción"
        );

        DeliveryStopDto response = wearableRouteService.updateDeliveryStatus(10L, 1L, request);

        assertNotNull(response);
        assertEquals(OrderStatus.DELIVERED, response.getStatus());

        // Verificar que emitió evento WebSocket hacia /topic/logistics
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/logistics"), any(Map.class));
    }

    @Test
    @DisplayName("Debe procesar parada como ABSENT (mapea a RETURNED con motivo) y alertar despachadores")
    void testUpdateDeliveryStatus_Absent() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(stop1));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(testBatch));
        when(driverRepository.findById(10L)).thenReturn(Optional.of(testDriver));

        Order returnedOrder = new Order();
        try {
            setPrivateField(returnedOrder, "id", 1L);
        } catch (Exception ignored) {}
        returnedOrder.setStatus(OrderStatus.RETURNED);
        returnedOrder.setNonDeliveryReason("ABSENT: Nadie abrió la puerta");
        returnedOrder.setAddress("Calle 10 # 5-20");
        returnedOrder.setCity("Bogotá");

        when(orderService.updateStatus(eq(1L), eq(OrderStatus.RETURNED), contains("ABSENT"))).thenReturn(returnedOrder);

        DeliveryStatusRequestDto request = new DeliveryStatusRequestDto(
                "ABSENT",
                LocalDateTime.now(),
                "Nadie abrió la puerta"
        );

        DeliveryStopDto response = wearableRouteService.updateDeliveryStatus(10L, 1L, request);

        assertNotNull(response);
        assertEquals(OrderStatus.RETURNED, response.getStatus());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/logistics"), any(Map.class));
    }

    @Test
    @DisplayName("Debe procesar parada como INCIDENT (mapea a CANCELLED con motivo) y alertar despachadores")
    void testUpdateDeliveryStatus_Incident() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(stop1));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(testBatch));
        when(driverRepository.findById(10L)).thenReturn(Optional.of(testDriver));

        Order cancelledOrder = new Order();
        try {
            setPrivateField(cancelledOrder, "id", 1L);
        } catch (Exception ignored) {}
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setNonDeliveryReason("INCIDENT: Paquete averiado en tránsito");
        cancelledOrder.setAddress("Calle 10 # 5-20");
        cancelledOrder.setCity("Bogotá");

        when(orderService.updateStatus(eq(1L), eq(OrderStatus.CANCELLED), contains("INCIDENT"))).thenReturn(cancelledOrder);

        DeliveryStatusRequestDto request = new DeliveryStatusRequestDto(
                "INCIDENT",
                LocalDateTime.now(),
                "Paquete averiado en tránsito"
        );

        DeliveryStopDto response = wearableRouteService.updateDeliveryStatus(10L, 1L, request);

        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/logistics"), any(Map.class));
    }

    @Test
    @DisplayName("Debe rechazar con AccessDeniedException si el pedido no está asignado al conductor")
    void testUpdateDeliveryStatus_AccessDenied() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(stop1));
        when(batchRepository.findById(100L)).thenReturn(Optional.of(testBatch));

        DeliveryStatusRequestDto request = new DeliveryStatusRequestDto("DELIVERED", LocalDateTime.now(), null);

        // Conductor 999 no es el dueño del lote (dueño es 10L)
        assertThrows(AccessDeniedException.class, () -> {
            wearableRouteService.updateDeliveryStatus(999L, 1L, request);
        });
    }
}

