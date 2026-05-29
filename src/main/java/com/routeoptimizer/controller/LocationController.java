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

    public LocationController(ProximityAlertService proximityAlertService) {
        this.proximityAlertService = proximityAlertService;
    }

    /**
     * El Frontend llama a este endpoint cada 3-5 segundos
     * enviando la posición fresca del repartidor.
     * 
     * Payload esperado: {"repartidorId": 12, "lat": 4.609, "lng": -74.081}
     */
    @PostMapping("/ping")
    public ResponseEntity<Void> receiveLocationPing(@RequestBody Map<String, Object> payload) {

        Number repId = (Number) payload.getOrDefault("driverId", payload.get("repartidorId"));
        Number latNum = (Number) payload.get("lat");
        Number lngNum = (Number) payload.get("lng");

        if (repId != null && latNum != null && lngNum != null) {
            // El cerebrito de alertas se encarga del resto
            proximityAlertService.processDriverLocation(
                    repId.longValue(),
                    latNum.doubleValue(),
                    lngNum.doubleValue());
        }

        // Respondemos muy rápido con 200 OK para no bloquear el celular
        return ResponseEntity.ok().build();
    }
}
