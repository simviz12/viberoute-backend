package com.routeoptimizer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.routeoptimizer.model.entity.Route;
import com.routeoptimizer.service.RouteService;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/routes", "/routes"})
public class RouteController {

  private final RouteService routeService;

  public RouteController(RouteService routeService) {
    this.routeService = routeService;
  }

  @GetMapping
  public ResponseEntity<List<Route>> findAll() {
    return ResponseEntity.ok(routeService.findAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Route> findById(@PathVariable Long id) {
    return ResponseEntity.ok(routeService.findById(id));
  }

  @GetMapping("/batch/{batchId}")
  public ResponseEntity<?> findByBatchId(@PathVariable Long batchId) {
    try {
      Route route = routeService.findByBatchId(batchId);
      return ResponseEntity.ok(route);
    } catch (Exception e) {
      // Si no hay ruta, devolvemos un 204 (No Content) en lugar de un error 400
      return ResponseEntity.noContent().build();
    }
  }

  @GetMapping("/driver/{driverId}")
  public ResponseEntity<Route> findByDriverId(@PathVariable Long driverId) {
    return ResponseEntity.ok(routeService.findByDriverId(driverId));
  }
}
