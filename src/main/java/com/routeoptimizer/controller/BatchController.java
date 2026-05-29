package com.routeoptimizer.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.routeoptimizer.dto.OrderResponseDTO;
import com.routeoptimizer.model.entity.Batch;
import com.routeoptimizer.model.entity.Route;
import com.routeoptimizer.repository.OrderRepository;
import com.routeoptimizer.service.BatchService;
import com.routeoptimizer.service.RouteService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/batches", "/batches" })
public class BatchController {

    private final BatchService batchService;
    private final RouteService routeService;
    private final com.routeoptimizer.service.SmartDispatchService smartDispatchService;
    private final OrderRepository orderRepository;

    public BatchController(BatchService batchService, RouteService routeService,
            com.routeoptimizer.service.SmartDispatchService smartDispatchService, OrderRepository orderRepository) {
        this.batchService = batchService;
        this.routeService = routeService;
        this.smartDispatchService = smartDispatchService;
        this.orderRepository = orderRepository;
    }

    /**
     * Lista todos los lotes. NO serializa entidades Batch con lazy collections.
     * Los drivers NO deben usar este endpoint para cargar sus pedidos
     * (deben usar GET /orders que retorna DTOs seguros).
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> listAll() {
        try {
            List<Map<String, Object>> safeBatches = batchService.findAll().stream()
                    .map(batchService::convertToSafeMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(safeBatches);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al listar lotes: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBatch(@PathVariable Long id) {
        try {
            Batch batch = batchService.findById(id);
            if (batch == null)
                return ResponseEntity.noContent().build();

            // Retornamos un Map seguro para evitar LazyInitializationException en
            // collections
            return ResponseEntity.ok(Map.of(
                    "id", batch.getId(),
                    "aiCopilotTips", batch.getAiCopilotTips() != null ? batch.getAiCopilotTips() : "",
                    "driver",
                    batch.getDriver() != null
                            ? Map.of("id", batch.getDriver().getId(), "name", batch.getDriver().getName())
                            : Map.of()));
        } catch (Exception e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping("/assign-pending")
    public ResponseEntity<Map<String, Integer>> assignPendingBatches() {
        int total = batchService.assignPendingBatches();
        return ResponseEntity.ok(Map.of("batchesAssigned", total));
    }

    @PostMapping("/{id}/optimize")
    public ResponseEntity<Route> optimizeBatch(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Object> body) {
        Double lat = body != null ? (Double) body.get("lat") : null;
        Double lng = body != null ? (Double) body.get("lng") : null;
        String mode = body != null ? (String) body.get("mode") : "EFFICIENCY";

        return ResponseEntity.ok(routeService.optimizeAndSaveRoute(id, lat, lng, mode));
    }

    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getPendingBatches(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String city) {
        List<Map<String, Object>> safeBatches = batchService.getPendingBatches(city).stream()
                .map(batchService::convertToSafeMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(safeBatches);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/manifest")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateManifest(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        String manifestUrl = body.get("manifestUrl");
        Batch batch = batchService.updateManifestUrl(id, manifestUrl);
        return ResponseEntity.ok(batchService.convertToSafeMap(batch));
    }

    @PostMapping("/manual")
    @Transactional
    public ResponseEntity<Map<String, Object>> createManualBatch(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("orderIds");
        List<Long> orderIds = idsRaw.stream().map(Integer::longValue).collect(Collectors.toList());
        String city = (String) body.get("city");
        Batch batch = batchService.createManualBatch(orderIds, city);
        return ResponseEntity.ok(batchService.convertToSafeMap(batch));
    }

    @PostMapping("/{batchId}/assign-driver/{driverId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> assignDriverToBatch(@PathVariable Long batchId,
            @PathVariable Long driverId) {
        Batch batch = batchService.assignDriverToBatch(batchId, driverId);
        return ResponseEntity.ok(batchService.convertToSafeMap(batch));
    }

    @GetMapping("/smart-dispatch/suggest")
    public ResponseEntity<com.routeoptimizer.dto.SmartDispatchPlanDTO> suggestSmartDispatch(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(smartDispatchService.suggestDispatchPlan(city));
    }

    @PostMapping("/smart-dispatch/apply")
    public ResponseEntity<Map<String, String>> applySmartDispatch(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clusters = (List<Map<String, Object>>) body.get("clusters");

            int batchesCreated = 0;
            for (Map<String, Object> cluster : clusters) {
                @SuppressWarnings("unchecked")
                List<Integer> orderIdsRaw = (List<Integer>) cluster.get("orderIds");
                List<Long> orderIds = orderIdsRaw.stream().map(Integer::longValue)
                        .collect(java.util.stream.Collectors.toList());

                Number driverIdNum = (Number) cluster.get("driverId");
                Long driverId = driverIdNum != null ? driverIdNum.longValue() : null;

                if (orderIds.isEmpty())
                    continue;

                // Resolver la ciudad real desde el primer pedido
                String city = "Pasto";
                try {
                    var firstOrder = orderRepository.findById(orderIds.get(0));
                    if (firstOrder.isPresent() && firstOrder.get().getCity() != null) {
                        city = firstOrder.get().getCity();
                    }
                } catch (Exception ignore) {
                }

                com.routeoptimizer.model.entity.Batch newBatch = batchService.createManualBatch(orderIds, city);
                if (driverId != null) {
                    batchService.assignDriverToBatch(newBatch.getId(), driverId);
                }
                batchesCreated++;
            }

            return ResponseEntity
                    .ok(Map.of("status", "success", "message", batchesCreated + " lotes creados exitosamente."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Error al aplicar el plan: " + e.getMessage()));
        }
    }
}
