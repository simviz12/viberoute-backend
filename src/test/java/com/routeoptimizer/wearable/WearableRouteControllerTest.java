package com.routeoptimizer.wearable;

import com.routeoptimizer.controller.WearableRouteController;
import com.routeoptimizer.dto.DeliveryStatusRequestDto;
import com.routeoptimizer.dto.DeliveryStopDto;
import com.routeoptimizer.dto.RouteSummaryDto;
import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.service.WearableRouteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WearableRouteControllerTest {

    @Mock
    private WearableRouteService wearableRouteService;

    @InjectMocks
    private WearableRouteController wearableRouteController;

    private User driverUser;

    @BeforeEach
    void setUp() throws Exception {
        driverUser = new User();
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(driverUser, 10L);
        driverUser.setEmail("driver@viberoute.com");
        driverUser.setRole(Role.DRIVER);

        var auth = new UsernamePasswordAuthenticationToken(driverUser, null, driverUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("GET /api/wearable/routes/active debe retornar 200 OK con RouteSummaryDto")
    void testGetActiveRoute_Success() {
        RouteSummaryDto summary = new RouteSummaryDto();
        summary.setRouteId(50L);
        summary.setDriverId(10L);
        summary.setDriverName("Carlos Conductor");
        summary.setTotalStops(2);
        summary.setStops(List.of(new DeliveryStopDto(), new DeliveryStopDto()));

        when(wearableRouteService.getActiveRouteForDriver(10L)).thenReturn(summary);

        ResponseEntity<RouteSummaryDto> response = wearableRouteController.getActiveRoute();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(50L, response.getBody().getRouteId());
        assertEquals(2, response.getBody().getTotalStops());
    }

    @Test
    @DisplayName("GET /api/wearable/routes/active debe rechazar con 403 si el rol no es DRIVER o ADMIN")
    void testGetActiveRoute_ForbiddenRole() {
        driverUser.setRole(Role.LOGISTICS);

        assertThrows(AccessDeniedException.class, () -> {
            wearableRouteController.getActiveRoute();
        });
    }

    @Test
    @DisplayName("POST /api/wearable/deliveries/{id}/status debe retornar 200 OK con DeliveryStopDto actualizado")
    void testUpdateDeliveryStatus_Success() {
        DeliveryStatusRequestDto request = new DeliveryStatusRequestDto(
                "DELIVERED",
                LocalDateTime.now(),
                "Entregado en portería"
        );

        DeliveryStopDto updatedStop = new DeliveryStopDto();
        updatedStop.setDeliveryId(1L);
        updatedStop.setStatus(OrderStatus.DELIVERED);

        when(wearableRouteService.updateDeliveryStatus(eq(10L), eq(1L), eq(request))).thenReturn(updatedStop);

        ResponseEntity<DeliveryStopDto> response = wearableRouteController.updateDeliveryStatus(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getDeliveryId());
        assertEquals(OrderStatus.DELIVERED, response.getBody().getStatus());
    }
}

