package com.routeoptimizer.controller;

import com.routeoptimizer.dto.DriverCreateDTO;
import com.routeoptimizer.dto.DriverResponseDTO;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.enums.DriverStatus;
import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.service.DriverService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador REST para Drivers.
 */
@RestController
@RequestMapping({"/api/v1/drivers", "/drivers"})
public class DriverController {

  private final DriverService driverService;

  public DriverController(DriverService driverService) {
    this.driverService = driverService;
  }

  @PostMapping
  public ResponseEntity<DriverResponseDTO> createDriver(@RequestBody DriverCreateDTO dto) {
    Driver driver = driverService.createDriver(dto);
    return new ResponseEntity<>(DriverResponseDTO.fromEntity(driver), HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<List<DriverResponseDTO>> listAll(@RequestParam(required = false) String city) {
    String cityFilter = resolveCity(city);
    List<DriverResponseDTO> drivers = driverService.findAll(cityFilter).stream()
        .map(DriverResponseDTO::fromEntity)
        .collect(Collectors.toList());
    return ResponseEntity.ok(drivers);
  }

  @GetMapping("/available")
  public ResponseEntity<List<DriverResponseDTO>> listAvailable(@RequestParam(required = false) String city) {
    String cityFilter = resolveCity(city);
    List<DriverResponseDTO> available = driverService.findAvailable(cityFilter).stream()
        .map(DriverResponseDTO::fromEntity)
        .collect(Collectors.toList());
    return ResponseEntity.ok(available);
  }

  @GetMapping("/{id}")
  public ResponseEntity<DriverResponseDTO> getDriver(@PathVariable Long id) {
    Driver driver = driverService.findById(id);
    if (isForbiddenForLogistics(driver.getAssignedCity())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(driver));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<DriverResponseDTO> changeStatus(
      @PathVariable Long id,
      @RequestParam DriverStatus status) {
    Driver existing = driverService.findById(id);
    if (isForbiddenForLogistics(existing.getAssignedCity())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    Driver updated = driverService.changeStatus(id, status);
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(updated));
  }

  @PostMapping("/{id}/assign-to-batch")
  public ResponseEntity<DriverResponseDTO> assignToBatch(@PathVariable Long id) {
    Driver existing = driverService.findById(id);
    if (isForbiddenForLogistics(existing.getAssignedCity())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    Driver driver = driverService.assignDriverToBatch(id);
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(driver));
  }

  @PatchMapping("/{id}/location")
  public ResponseEntity<DriverResponseDTO> updateLocation(
      @PathVariable Long id,
      @RequestBody Coordinate location) {
    Driver existing = driverService.findById(id);
    if (isForbiddenForLogistics(existing.getAssignedCity())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    Driver updated = driverService.updateLocation(id, location);
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(updated));
  }

  @GetMapping("/fleet-status")
  public ResponseEntity<List<DriverResponseDTO>> getFleetStatus(@RequestParam(required = false) String city) {
    String cityFilter = resolveCity(city);
    return ResponseEntity.ok(driverService.getFleetStatus(cityFilter));
  }

  private String resolveCity(String requestedCity) {
    User user = authenticatedUser();
    if (user != null) {
      if (user.getRole() == Role.LOGISTICS) {
        return user.getAssignedCity();
      }
    }
    return requestedCity;
  }

  private boolean isForbiddenForLogistics(String resourceCity) {
    User user = authenticatedUser();
    return user != null
        && user.getRole() == Role.LOGISTICS
        && (resourceCity == null || !resourceCity.equalsIgnoreCase(user.getAssignedCity()));
  }

  private User authenticatedUser() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof User user ? user : null;
  }

  @GetMapping("/debug-all")
  public ResponseEntity<List<java.util.Map<String, Object>>> debugAllDrivers() {
    List<Driver> all = driverService.findAll();
    List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
    for (Driver d : all) {
      java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
      map.put("id", d.getId());
      map.put("name", d.getName());
      map.put("email", d.getEmail());
      map.put("status", d.getStatus());
      map.put("assignedCity", d.getAssignedCity());
      map.put("role", d.getRole());
      result.add(map);
    }
    return ResponseEntity.ok(result);
  }
}
