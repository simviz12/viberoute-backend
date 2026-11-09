package com.routeoptimizer.controller;

import com.routeoptimizer.dto.DeliveryStatusRequestDto;
import com.routeoptimizer.dto.DeliveryStopDto;
import com.routeoptimizer.dto.RouteSummaryDto;
import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.service.WearableRouteService;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST exclusivo para aplicaciones Standalone de Smartwatch (Wear OS).
 * Aísla el tráfico del reloj del panel web administrativo mediante el prefijo /api/wearable.
 */
@RestController
@RequestMapping({"/api/wearable", "/wearable"})
public class WearableRouteController {

    private static final Logger log = LoggerFactory.getLogger(WearableRouteController.class);

    private final WearableRouteService wearableRouteService;

    public WearableRouteController(WearableRouteService wearableRouteService) {
        this.wearableRouteService = wearableRouteService;
    }

    private static final Long DEFAULT_DEVELOPMENT_DRIVER_ID = 3L;

    /**
     * Sincroniza la ruta activa asignada al conductor autenticado para visualización en Smartwatch.
     * En entorno de desarrollo permite acceso directo tomando al conductor activo (Driver #3).
     *
     * @return RouteSummaryDto con metadatos de ruta y lista secuencial de paradas
     */
    @GetMapping("/routes/active")
    public ResponseEntity<RouteSummaryDto> getActiveRoute() {
        User user = authenticatedUser();
        Long driverId;

        if (user != null) {
            if (user.getRole() != Role.DRIVER && user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) {
                throw new AccessDeniedException("El usuario no tiene rol DRIVER para acceder a rutas de smartwatch.");
            }
            driverId = user.getId();
        } else {
            log.info("⌚ [WEAR_OS] Sincronización directa sin token JWT. Asignando conductor por defecto ID: {}", DEFAULT_DEVELOPMENT_DRIVER_ID);
            driverId = DEFAULT_DEVELOPMENT_DRIVER_ID;
        }

        log.info("⌚ [WEAR_OS] Solicitud de ruta activa procesada para conductor ID: {}", driverId);
        RouteSummaryDto routeSummary = wearableRouteService.getActiveRouteForDriver(driverId);
        return ResponseEntity.ok(routeSummary);
    }

    /**
     * Confirma o actualiza el estado de una parada/entrega desde el Smartwatch (Wear OS).
     * Soporta DELIVERED, ABSENT, INCIDENT y emite eventos en tiempo real hacia despachadores.
     *
     * @param deliveryId ID de la entrega/pedido
     * @param requestDto Datos del estado, estampa de tiempo y observaciones
     * @return DeliveryStopDto con la información actualizada de la parada
     */
    @PostMapping("/deliveries/{deliveryId}/status")
    public ResponseEntity<DeliveryStopDto> updateDeliveryStatus(
            @PathVariable Long deliveryId,
            @Valid @RequestBody DeliveryStatusRequestDto requestDto) {
        User user = authenticatedUser();
        Long driverId;

        if (user != null) {
            if (user.getRole() != Role.DRIVER && user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) {
                throw new AccessDeniedException("Solo conductores pueden confirmar estados de entregas desde smartwatch.");
            }
            driverId = user.getId();
        } else {
            log.info("⌚ [WEAR_OS] Confirmación directa sin token JWT para entrega #{}. Asignando conductor por defecto ID: {}", deliveryId, DEFAULT_DEVELOPMENT_DRIVER_ID);
            driverId = DEFAULT_DEVELOPMENT_DRIVER_ID;
        }

        log.info("⌚ [WEAR_OS] Confirmación de entrega #{} recibida desde smartwatch (Conductor #{}): status={}",
                deliveryId, driverId, requestDto.getStatus());

        DeliveryStopDto updatedStop = wearableRouteService.updateDeliveryStatus(driverId, deliveryId, requestDto);
        return ResponseEntity.ok(updatedStop);
    }

    /**
     * Extrae el usuario autenticado desde el contexto de seguridad Spring Security (JWT).
     */
    private User authenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof User user) ? user : null;
    }
}

