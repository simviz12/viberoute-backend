package com.routeoptimizer.service;

import com.routeoptimizer.dto.DriverCreateDTO;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.model.enums.DriverStatus;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.OrderRepository;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.routeoptimizer.model.entity.Batch;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.dto.DriverResponseDTO;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica de negocio para Drivers.
 */
@Service
public class DriverService {

  private final DriverRepository driverRepository;
  private final BatchService batchService;
  private final OrderRepository orderRepository;

  @PersistenceContext
  private EntityManager entityManager;

  public DriverService(DriverRepository driverRepository, @Lazy BatchService batchService, OrderRepository orderRepository) {
    this.driverRepository = driverRepository;
    this.batchService = batchService;
    this.orderRepository = orderRepository;
  }

  @Transactional
  public Driver createDriver(DriverCreateDTO dto) {
    Driver driver = new Driver();
    driver.setEmail(dto.getEmail());
    driver.setPasswordHash(dto.getPassword() != null ? dto.getPassword() : "defaultHash");
    driver.setName(dto.getName());
    driver.setPhone(dto.getPhone());
    driver.setRole(Role.DRIVER);

    driver.setMaxCapacity(dto.getMaxCapacity());
    driver.setCostPerHour(dto.getCostPerHour());
    driver.setStatus(DriverStatus.INACTIVE);

    return driverRepository.save(driver);
  }

  @Transactional(readOnly = true)
  public List<Driver> findAll(String cityFilter) {
    if (cityFilter != null && !cityFilter.trim().isEmpty() && !cityFilter.equalsIgnoreCase("Global")) {
      return driverRepository.findByAssignedCityIgnoreCase(cityFilter.trim());
    }
    return driverRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Driver> findAll() {
    return driverRepository.findAll();
  }

  @Transactional(readOnly = true)
  @SuppressWarnings("null")
  public Driver findById(Long id) {
    return driverRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Driver no encontrado con ID: " + id));
  }

  @Transactional(readOnly = true)
  public List<Driver> findAvailable(String cityFilter) {
    if (cityFilter != null && !cityFilter.trim().isEmpty() && !cityFilter.equalsIgnoreCase("Global")) {
      return driverRepository.findByStatusAndAssignedCityIgnoreCase(DriverStatus.AVAILABLE, cityFilter.trim());
    }
    return driverRepository.findByStatus(DriverStatus.AVAILABLE);
  }

  @Transactional(readOnly = true)
  public List<Driver> findAvailable() {
    return driverRepository.findByStatus(DriverStatus.AVAILABLE);
  }

  @Transactional
  public Driver changeStatus(Long id, DriverStatus newStatus) {
    Driver driver = findById(id);
    driver.setStatus(newStatus);
    Driver saved = driverRepository.save(driver);

    // Al pasar a DISPONIBLE, asignar automáticamente al lote pendiente más antiguo
    if (newStatus == DriverStatus.AVAILABLE) {
      batchService.assignAvailableDriverToOldestPendingBatch(saved);
    }

    return saved;
  }

  @Transactional
  public Driver updateLocation(Long id, Coordinate coord) {
    Driver driver = findById(id);
    driver.updateLocation(coord.getLat(), coord.getLng());
    return driverRepository.save(driver);
  }

  /**
   * Asigna manualmente un repartidor al lote más antiguo sin repartidor.
   */
  @Transactional
  public Driver assignDriverToBatch(Long driverId) {
    Driver driver = findById(driverId);
    driver.setStatus(DriverStatus.ON_ROUTE);

    batchService.assignAvailableDriverToOldestPendingBatch(driver);

    return driverRepository.save(driver);
  }

  @Transactional
  public List<DriverResponseDTO> getFleetStatus(String cityFilter) {
    // SINCRONIZACIÓN FORZADA: Limpiar caché de primer nivel de Hibernate para leer la realidad de la DB
    if (entityManager != null) {
        entityManager.clear();
    }
    
    List<Driver> drivers;
    if (cityFilter != null && !cityFilter.trim().isEmpty() && !cityFilter.equalsIgnoreCase("Global")) {
      drivers = driverRepository.findByAssignedCityIgnoreCase(cityFilter.trim());
    } else {
      drivers = driverRepository.findAll();
    }
    java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();

    List<DriverResponseDTO> result = new java.util.ArrayList<>();
    for (Driver d : drivers) {
      try {
        DriverResponseDTO dto = DriverResponseDTO.fromEntity(d);
        
        // SINCRONIZACIÓN REAL: Obtener conteos directamente de la DB para evitar datos desactualizados
        long successful = orderRepository.countSuccessfulDeliveriesForDriver(d.getName());
        long failed = orderRepository.countFailedDeliveriesForDriver(d.getName());
        dto.setCompletedOrders((int)successful);
        dto.setFailedOrders((int)failed);

        // Buscar carga activa por ID del conductor (excluye COMPLETED automáticamente)
        java.util.Optional<Batch> activeBatch = batchService.findActiveBatchByDriverId(d.getId());
        
        boolean hasActualPendingOrders = false;
        if (activeBatch.isPresent()) {
          Batch batch = activeBatch.get();
          // Verificar si realmente hay pedidos que no sean terminales
          hasActualPendingOrders = batch.getOrders().stream().anyMatch(o -> 
              o.getStatus() == com.routeoptimizer.model.enums.OrderStatus.ON_ROUTE || 
              o.getStatus() == com.routeoptimizer.model.enums.OrderStatus.PENDING);

          if (hasActualPendingOrders) {
            dto.setCurrentBatchId(batch.getId());
            dto.setCurrentOrdersCount(batch.getOrders().size());
            dto.setActiveAddresses(batch.getOrders().stream()
                .map(Order::getAddress)
                .limit(3)
                .collect(Collectors.toList()));
          }
        }

        if (!hasActualPendingOrders && d.getStatus() == DriverStatus.ON_ROUTE) {
          // AUTO-CORRECCIÓN DEFINITIVA: Si no hay lote con pedidos reales activos, LIBERAR
          d.setStatus(DriverStatus.AVAILABLE);
          driverRepository.save(d);
          dto.setStatus(DriverStatus.AVAILABLE);
          dto.setCurrentBatchId(null);
          dto.setCurrentOrdersCount(0);
          System.out.println("[FLEET-STATUS] Final-Release: Driver " + d.getName() + " had no active orders. Status fixed to AVAILABLE.");
        }
        
        result.add(dto);
      } catch (Exception e) {
        System.err.println("[FLEET-STATUS] Error procesando driver ID=" + d.getId() + ": " + e.getMessage());
        DriverResponseDTO fallback = DriverResponseDTO.fromEntity(d);
        result.add(fallback);
      }
    }
    return result;
  }
}
