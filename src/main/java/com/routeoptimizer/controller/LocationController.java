package com.routeoptimizer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.routeoptimizer.service.ProximityAlertService;

import java.util.Map;

/**
 * Controlador que recibe los "pings" de ubicación del celular del repartidor
 * y alimenta al servicio de WebSockets de Colisiones.
 */
@RestController
@RequestMapping({"/api/v1/locations", "/locations"})
public class LocationController {

    private final ProximityAlertService proximityAlertService;
    private final com.routeoptimizer.service.DriverService driverService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public LocationController(
            ProximityAlertService proximityAlertService,
            com.routeoptimizer.service.DriverService driverService,
            org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.proximityAlertService = proximityAlertService;
        this.driverService = driverService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * El Frontend llama a este endpoint cada 3-5 segundos
     * enviando la posición fresca del repartidor.
     * 
     * Payload esperado: {"driverId": 12, "lat": 4.609, "lng": -74.081}
     */
    @PostMapping("/ping")
    public ResponseEntity<Void> receiveLocationPing(@RequestBody Map<String, Object> payload) {

        Number repId = (Number) payload.getOrDefault("driverId", payload.get("repartidorId"));
        Number latNum = (Number) payload.get("lat");
        Number lngNum = (Number) payload.get("lng");

        if (repId != null && latNum != null && lngNum != null) {
            long driverId = repId.longValue();
            double lat = latNum.doubleValue();
            double lng = lngNum.doubleValue();

            // 1. Persistir la ubicación real en la base de datos para que no se pierda al salir de la pantalla
            try {
                com.routeoptimizer.model.entity.Driver updatedDriver = driverService.updateLocation(driverId, new com.routeoptimizer.model.Coordinate(lat, lng));
                
                // 2. Broadcast en tiempo real por WebSocket a todos los paneles de administración y mapas
                Map<String, Object> fleetUpdate = Map.of(
                    "driverId", driverId,
                    "driverName", updatedDriver.getName(),
                    "lat", lat,
                    "lng", lng,
                    "status", updatedDriver.getStatus().name(),
                    "assignedCity", updatedDriver.getAssignedCity() != null ? updatedDriver.getAssignedCity() : "Pasto",
                    "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSend("/topic/fleet/locations", fleetUpdate);
            } catch (Exception ex) {
                // Silencioso para no bloquear el hilo telemático
            }

            // 3. Evaluar colisiones con paquetes cercanos (<= 5 metros)
            proximityAlertService.processDriverLocation(driverId, lat, lng);
        }

        return ResponseEntity.ok().build();
    }
}
