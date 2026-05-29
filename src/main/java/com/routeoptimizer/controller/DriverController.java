package com.routeoptimizer.controller;

import com.routeoptimizer.dto.DriverCreateDTO;
import com.routeoptimizer.dto.DriverResponseDTO;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.enums.DriverStatus;
import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.service.DriverService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<List<DriverResponseDTO>> listAll() {
    List<DriverResponseDTO> drivers = driverService.findAll().stream()
        .map(DriverResponseDTO::fromEntity)
        .collect(Collectors.toList());
    return ResponseEntity.ok(drivers);
  }

  @GetMapping("/available")
  public ResponseEntity<List<DriverResponseDTO>> listAvailable() {
    List<DriverResponseDTO> available = driverService.findAvailable().stream()
        .map(DriverResponseDTO::fromEntity)
        .collect(Collectors.toList());
    return ResponseEntity.ok(available);
  }

  @GetMapping("/{id}")
  public ResponseEntity<DriverResponseDTO> getDriver(@PathVariable Long id) {
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(driverService.findById(id)));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<DriverResponseDTO> changeStatus(
      @PathVariable Long id,
      @RequestParam DriverStatus status) {
    Driver updated = driverService.changeStatus(id, status);
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(updated));
  }

  @PostMapping("/{id}/assign-to-batch")
  public ResponseEntity<DriverResponseDTO> assignToBatch(@PathVariable Long id) {
    Driver driver = driverService.assignDriverToBatch(id);
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(driver));
  }

  @PatchMapping("/{id}/location")
  public ResponseEntity<DriverResponseDTO> updateLocation(
      @PathVariable Long id,
      @RequestBody Coordinate location) {
    Driver updated = driverService.updateLocation(id, location);
    return ResponseEntity.ok(DriverResponseDTO.fromEntity(updated));
  }

  @GetMapping("/fleet-status")
  public ResponseEntity<List<DriverResponseDTO>> getFleetStatus() {
    return ResponseEntity.ok(driverService.getFleetStatus());
  }

}
