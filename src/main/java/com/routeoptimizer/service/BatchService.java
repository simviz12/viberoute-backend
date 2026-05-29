package com.routeoptimizer.service;

import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.model.entity.Batch;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.enums.DriverStatus;
import com.routeoptimizer.repository.BatchRepository;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.OrderRepository;
import com.routeoptimizer.dto.OrderResponseDTO;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.routeoptimizer.model.enums.OrderStatus;

@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);
    private static final int MAX_ORDERS_PER_BATCH = 20;

    private final BatchRepository batchRepository;
    private final OrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final RouteService routeService;
    private final AIRouteSuggestionService aiService;
    private final ContextualAdvisor contextualAdvisor;

    public BatchService(BatchRepository batchRepository,
            OrderRepository orderRepository,
            DriverRepository driverRepository,
            RouteService routeService,
            AIRouteSuggestionService aiService,
            ContextualAdvisor contextualAdvisor) {
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
        this.driverRepository = driverRepository;
        this.routeService = routeService;
        this.aiService = aiService;
        this.contextualAdvisor = contextualAdvisor;
    }

    @Transactional
    public void addOrderToActiveBatch(Order order) {
        String city = order.getCity() != null ? order.getCity() : "Global";
        Optional<Batch> openBatch = batchRepository.findFirstByStatusAndCityOrderByCreationDateAsc("OPEN", city);

        Batch batch;
        if (openBatch.isPresent()) {
            batch = openBatch.get();
            log.info("Order #{} added to existing batch #{} for city {} ({}/{} orders)",
                    order.getId(), batch.getId(), city, batch.getOrders().size() + 1, MAX_ORDERS_PER_BATCH);
        } else {
            batch = new Batch();
            batch.setStatus("OPEN");
            batch.setCity(city);
            batch = batchRepository.save(batch);
            log.info("New batch #{} created for city {} with order #{}", batch.getId(), city, order.getId());
        }

        order.setBatchId(batch.getId());
        orderRepository.save(order);
        batch.getOrders().add(order);

        if (batch.getOrders().size() >= MAX_ORDERS_PER_BATCH) {
            assignBatchToDriver(batch);
        }
    }

    private void assignBatchToDriver(Batch batch) {
        List<Driver> available = driverRepository.findByStatus(DriverStatus.AVAILABLE);

        if (available.isEmpty()) {
            if (batch.getOrders().size() >= MAX_ORDERS_PER_BATCH) {
                batch.setStatus("FULL_UNASSIGNED");
            }
            batchRepository.save(batch);
            log.warn("Batch #{} without available drivers ({} orders)", batch.getId(), batch.getOrders().size());
            return;
        }

        Driver driver = available.get(0);
        batch.setDriver(driver);
        batch.setStatus("ASSIGNED");

        // Actualizar el estado del conductor a EN RUTA
        driver.setStatus(DriverStatus.ON_ROUTE);
        driverRepository.save(driver);

        // Sincronizar pedidos
        batch.getOrders().forEach(o -> {
            o.setStatus(OrderStatus.ON_ROUTE);
            orderRepository.save(o);
        });

        batchRepository.save(batch);

        executeAIAnalysis(batch);
        generateCopilotTips(batch, driver);

        try {
            routeService.optimizeAndSaveRoute(batch.getId());
        } catch (Exception e) {
            log.error("Error generating automatic route for batch #{}: {}", batch.getId(), e.getMessage());
        }

        log.info("✅ Batch #{} assigned to driver '{}' with {} orders.",
                batch.getId(), driver.getName(), batch.getOrders().size());
    }

    @Transactional
    public boolean assignAvailableDriverToOldestPendingBatch(Driver driver) {
        String city = (driver.getAssignedCity() != null) ? driver.getAssignedCity() : "Global";
        List<Batch> pendingBatches = batchRepository.findByCityAndDriverIsNullOrderByCreationDateAsc(city);

        if (pendingBatches.isEmpty()) {
            log.debug("No batches without driver to assign to '{}'", driver.getName());
            return false;
        }

        Batch batch = pendingBatches.get(0);
        batch.setDriver(driver);
        batch.setStatus("ASSIGNED");

        // Actualizar el estado del conductor a EN RUTA
        driver.setStatus(DriverStatus.ON_ROUTE);
        driverRepository.save(driver);

        // Sincronizar pedidos
        batch.getOrders().forEach(o -> {
            o.setStatus(OrderStatus.ON_ROUTE);
            orderRepository.save(o);
        });

        batchRepository.save(batch);

        log.info("Batch #{} assigned to driver #{} ({})", batch.getId(), driver.getId(), driver.getName());

        executeAIAnalysis(batch);
        generateCopilotTips(batch, driver);

        try {
            routeService.optimizeAndSaveRoute(batch.getId());
        } catch (Exception e) {
            log.error("Error generating automatic route for batch #{}: {}", batch.getId(), e.getMessage());
        }

        return true;
    }

    @Transactional
    public Batch createManualBatch(List<Long> orderIds, String city) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new RuntimeException("Debe seleccionar al menos un pedido para crear un lote.");
        }

        Batch batch = new Batch();
        batch.setStatus("OPEN");
        batch.setCity(city != null ? city : "Global");
        batch = batchRepository.save(batch);

        for (Long orderId : orderIds) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order no encontrado: " + orderId));

            // Validar que el pedido pertenezca a la misma ciudad que el lote (solo si hay
            // una ciudad específica definida)
            boolean isGlobalBatch = batch.getCity() == null || batch.getCity().trim().isEmpty()
                    || batch.getCity().equalsIgnoreCase("Global");
            if (!isGlobalBatch && !order.getCity().equalsIgnoreCase(batch.getCity())) {
                throw new RuntimeException("El pedido #" + orderId + " (" + order.getCity()
                        + ") no pertenece a la ciudad del lote (" + batch.getCity() + ")");
            }

            order.setBatchId(batch.getId());
            orderRepository.save(order);
            batch.getOrders().add(order);
        }

        log.info("Lote manual #{} creado con {} pedidos para {}", batch.getId(), orderIds.size(), city);
        return batch;
    }

    @Transactional
    public Batch assignDriverToBatch(Long batchId, Long driverId) {
        Batch batch = findById(batchId);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with ID: " + driverId));

        batch.setDriver(driver);
        batch.setStatus("ASSIGNED");

        // Cambiar el estado del conductor a En Ruta
        driver.setStatus(DriverStatus.ON_ROUTE);
        driverRepository.save(driver);

        // Sincronizar pedidos
        batch.getOrders().forEach(o -> {
            o.setStatus(OrderStatus.ON_ROUTE);
            orderRepository.save(o);
        });

        batch = batchRepository.save(batch);

        executeAIAnalysis(batch);
        generateCopilotTips(batch, driver);

        try {
            routeService.optimizeAndSaveRoute(batch.getId());
        } catch (Exception e) {
            log.error("Error generating automatic route for batch #{}: {}", batch.getId(), e.getMessage());
        }

        return batch;
    }

    private void executeAIAnalysis(Batch batch) {
        try {
            Map<String, Object> sugerencias = aiService.sugerirOptimizacion(batch);
            log.info("🤖 IA para Batch #{}: {}", batch.getId(), sugerencias.get("aiRecommendation"));
            log.info("   Tráfico: {}, Pedidos prioritarios: {}",
                    sugerencias.get("trafficInsights"),
                    sugerencias.get("urgentOrdersCount"));
        } catch (Exception e) {
            log.warn("Could not get AI suggestions for batch #{}: {}", batch.getId(), e.getMessage());
        }
    }

    /**
     * Generates AI copilot tips for the driver via a single Gemini call.
     * Tips are persisted in the Batch entity (Observer Pattern: assignment triggers
     * generation).
     */
    private void generateCopilotTips(Batch batch, Driver driver) {
        try {
            String copilotTips = contextualAdvisor.generateBatchCopilotTips(batch, driver.getName());
            batch.setAiCopilotTips(copilotTips);
            batchRepository.save(batch);
            log.info("🧠 Copilot tips generated for batch #{} (driver: {})", batch.getId(), driver.getName());
        } catch (Exception e) {
            log.warn("Could not generate copilot tips for batch #{}: {}", batch.getId(), e.getMessage());
        }
    }

    @Transactional
    public int assignPendingBatches() {
        List<Batch> unassigned = batchRepository.findByDriverIsNullOrderByCreationDateAsc();
        int assignedCount = 0;
        for (Batch batch : unassigned) {
            assignBatchToDriver(batch);
            if ("ASSIGNED".equals(batch.getStatus()))
                assignedCount++;
        }
        return assignedCount;
    }

    @Transactional(readOnly = true)
    public List<Batch> findAll(String cityFilter) {
        if (cityFilter != null && !cityFilter.trim().isEmpty() && !cityFilter.equalsIgnoreCase("Global")) {
            return batchRepository.findByCityIgnoreCase(cityFilter.trim());
        }
        return batchRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Batch> findAll() {
        return batchRepository.findAll();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public Batch findById(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Batch not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Batch> getPendingBatches(String city) {
        if (city != null && !city.isEmpty()) {
            return batchRepository.findByCityAndDriverIsNullOrderByCreationDateAsc(city);
        }
        return batchRepository.findAll().stream().filter(b -> b.getDriver() == null).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Batch> findActiveBatchByDriverName(String driverName) {
        return batchRepository.findAll().stream()
                .filter(b -> b.getDriver() != null && b.getDriver().getName().equalsIgnoreCase(driverName))
                .filter(b -> !"COMPLETED".equals(b.getStatus()) && !"CANCELLED".equals(b.getStatus()))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Batch> findActiveBatchByDriverId(Long driverId) {
        return batchRepository.findAll().stream()
                .filter(b -> b.getDriver() != null && b.getDriver().getId().equals(driverId))
                .filter(b -> !"COMPLETED".equals(b.getStatus()) && !"CANCELLED".equals(b.getStatus()))
                .findFirst();
    }

    @Transactional
    public void checkAndCompleteBatch(Long batchId) {
        Batch batch = findById(batchId);
        if ("COMPLETED".equals(batch.getStatus())) {
            return;
        }

        boolean allTerminal = batch.getOrders().stream().allMatch(o -> o.getStatus() == OrderStatus.DELIVERED ||
                o.getStatus() == OrderStatus.RETURNED ||
                o.getStatus() == OrderStatus.CANCELLED);

        if (allTerminal && !batch.getOrders().isEmpty()) {
            batch.setStatus("COMPLETED");
            Driver driver = batch.getDriver();
            if (driver != null) {
                driver.setStatus(DriverStatus.AVAILABLE);
                driverRepository.save(driver);
            }
            batchRepository.save(batch);
            log.info("Lote #{} completado automáticamente. Conductor {} está ahora AVAILABLE.", batchId,
                    driver != null ? driver.getName() : "N/A");
        }
    }

    @Transactional
    public Batch updateManifestUrl(Long id, String manifestUrl) {
        Batch batch = findById(id);
        batch.setManifestUrl(manifestUrl);
        return batchRepository.save(batch);
    }

    public Map<String, Object> convertToSafeMap(Batch batch) {
        if (batch == null)
            return Map.of();

        // Forzar la carga de la colección dentro de la transacción si es necesario
        // (Aunque si este método se llama desde un @Transactional, ya debería estar
        // disponible)
        List<Order> orders = batch.getOrders();

        return Map.of(
                "id", batch.getId(),
                "status", batch.getStatus() != null ? batch.getStatus() : "",
                "city", batch.getCity() != null ? batch.getCity() : "",
                "creationDate", batch.getCreationDate() != null ? batch.getCreationDate().toString() : "",
                "manifestUrl", batch.getManifestUrl() != null ? batch.getManifestUrl() : "",
                "aiCopilotTips", batch.getAiCopilotTips() != null ? batch.getAiCopilotTips() : "",
                "driver",
                batch.getDriver() != null
                        ? Map.of("id", batch.getDriver().getId(), "name", batch.getDriver().getName())
                        : Map.of(),
                "orders", orders != null
                        ? orders.stream().map(OrderResponseDTO::fromEntity).collect(Collectors.toList())
                        : List.of());
    }
}
