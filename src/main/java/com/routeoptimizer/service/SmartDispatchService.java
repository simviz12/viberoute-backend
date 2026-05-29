package com.routeoptimizer.service;

import com.routeoptimizer.dto.ClusterDTO;
import com.routeoptimizer.dto.SmartDispatchPlanDTO;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.entity.Driver;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.enums.DriverStatus;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SmartDispatchService {

    private static final Logger log = LoggerFactory.getLogger(SmartDispatchService.class);
    private final OrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final ContextualAdvisor contextualAdvisor;
    private final DriverService driverService;
    private final BatchService batchService;

    public SmartDispatchService(OrderRepository orderRepository, DriverRepository driverRepository, ContextualAdvisor contextualAdvisor, DriverService driverService, BatchService batchService) {
        this.orderRepository = orderRepository;
        this.driverRepository = driverRepository;
        this.contextualAdvisor = contextualAdvisor;
        this.driverService = driverService;
        this.batchService = batchService;
    }

    private static final Map<String, SmartDispatchPlanDTO> planCache = new HashMap<>();
    private static final Map<String, Integer> orderHashCache = new HashMap<>();

    public SmartDispatchPlanDTO suggestDispatchPlan(String city) {
        log.info("Generating Smart Dispatch Plan for city: {}", city);

        // SINCRONIZACIÓN PREVIA: Forzamos la limpieza de conductores antes de calcular el plan
        driverService.getFleetStatus(city);

        List<Order> allPending = orderRepository.findByStatus(OrderStatus.PENDING);
        
        // FILTRO CRÍTICO: Solo tomar pedidos que NO tengan un batchId (no asignados)
        List<Order> unassignedPending = allPending.stream()
                .filter(o -> o.getBatchId() == null)
                .collect(Collectors.toList());

        // Detección automática de ciudad si viene vacía
        if ((city == null || city.isEmpty()) && !unassignedPending.isEmpty()) {
            city = unassignedPending.stream()
                .collect(Collectors.groupingBy(Order::getCity, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Pasto");
        }
        
        final String finalCity = (city != null && !city.isEmpty()) ? city : "Pasto";
        List<Order> pendingOrders = unassignedPending.stream()
                .filter(o -> finalCity.equalsIgnoreCase(o.getCity()))
                .collect(Collectors.toList());

        if (pendingOrders.isEmpty()) {
            return new SmartDispatchPlanDTO();
        }

        // --- SISTEMA DE CACHÉ ---
        // Forzamos limpieza si hay pedidos para asegurar datos frescos de conductores
        if (!unassignedPending.isEmpty()) {
            planCache.remove(finalCity);
        }
        
        int currentOrderHash = pendingOrders.stream().map(o -> o.getId().toString()).collect(Collectors.joining(",")).hashCode();
        if (planCache.containsKey(finalCity) && orderHashCache.getOrDefault(finalCity, 0) == currentOrderHash) {
            log.info("Returning cached plan for city: {}", finalCity);
            return planCache.get(finalCity);
        }
        // -------------------------

        List<Driver> cityDrivers = driverRepository.findByAssignedCityIgnoreCase(finalCity);
        log.info("City drivers for '{}': {} total", finalCity, cityDrivers.size());

        // SINCRONIZACIÓN DINÁMICA: Usamos el estado real de la flota para garantizar datos frescos
        List<com.routeoptimizer.dto.DriverResponseDTO> fleetStatus = driverService.getFleetStatus(finalCity);
        
        List<Driver> availableDrivers = cityDrivers.stream()
                .filter(d -> fleetStatus.stream()
                    .anyMatch(fs -> fs.getId().equals(d.getId()) && fs.getStatus() == DriverStatus.AVAILABLE))
                .collect(Collectors.toList());

        List<Driver> onRouteDrivers = cityDrivers.stream()
                .filter(d -> fleetStatus.stream()
                    .anyMatch(fs -> fs.getId().equals(d.getId()) && fs.getStatus() == DriverStatus.ON_ROUTE))
                .collect(Collectors.toList());

        // Determinar K (número de zonas)
        int k = availableDrivers.size();
        if (k == 0) {
            k = Math.max(1, (int) Math.ceil(pendingOrders.size() / 15.0));
        } else {
            k = Math.min(k, Math.max(1, (int) Math.ceil(pendingOrders.size() / 5.0)));
        }

        // Ejecutar K-Means
        List<ClusterDTO> rawClusters = executeKMeans(pendingOrders, k);
        labelClustersGeographically(rawClusters);

        // Generar opciones principales
        List<ClusterDTO> primaryClusters = assignDriversToClusters(rawClusters, availableDrivers);
        
        // Generar opciones alternativas
        List<ClusterDTO> clonedRaw = rawClusters.stream().map(c -> {
            ClusterDTO clone = new ClusterDTO();
            clone.setCentroidLat(c.getCentroidLat());
            clone.setCentroidLng(c.getCentroidLng());
            clone.setZoneName(c.getZoneName());
            clone.setOrders(new ArrayList<>(c.getOrders()));
            return clone;
        }).collect(Collectors.toList());
        List<ClusterDTO> alternativeClusters = assignDriversToClusters(clonedRaw, onRouteDrivers);

        // Generar Reporte de IA
        String aiReport = generateAIReport(primaryClusters, alternativeClusters, finalCity);

        SmartDispatchPlanDTO plan = new SmartDispatchPlanDTO();
        plan.setPrimaryClusters(primaryClusters);
        plan.setAlternativeClusters(alternativeClusters);
        plan.setAiReport(aiReport);

        // Guardar en caché antes de retornar (Nota: En desarrollo forzamos refresco para evitar datos viejos de conductores)
        planCache.put(finalCity, plan);
        orderHashCache.put(finalCity, currentOrderHash);
        
        // Limpiamos caché de otras ciudades para liberar memoria
        if (planCache.size() > 5) {
            planCache.clear();
            orderHashCache.clear();
        }

        return plan;
    }

    private List<ClusterDTO> executeKMeans(List<Order> orders, int k) {
        List<ClusterDTO> clusters = new ArrayList<>();
        if (orders.isEmpty() || k <= 0) return clusters;

        // Inicializar K centroides al azar
        Random rand = new Random(42); // Seed para reproducibilidad en la misma sesión
        List<Coordinate> centroids = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            Order randomOrder = orders.get(rand.nextInt(orders.size()));
            centroids.add(new Coordinate(randomOrder.getLat(), randomOrder.getLng()));
            ClusterDTO cluster = new ClusterDTO();
            cluster.setOrders(new ArrayList<>());
            clusters.add(cluster);
        }

        boolean changed = true;
        int maxIterations = 50;
        int iteration = 0;

        while (changed && iteration < maxIterations) {
            changed = false;
            // Limpiar clusters
            for (ClusterDTO c : clusters) {
                c.getOrders().clear();
            }

            // Asignar pedidos al centroide más cercano
            for (Order o : orders) {
                int closestIndex = 0;
                double minDistance = Double.MAX_VALUE;
                for (int i = 0; i < k; i++) {
                    double dist = getEuclideanDistance(o.getLat(), o.getLng(), centroids.get(i).getLat(), centroids.get(i).getLng());
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestIndex = i;
                    }
                }
                clusters.get(closestIndex).getOrders().add(o);
            }

            // Recalcular centroides
            for (int i = 0; i < k; i++) {
                List<Order> clusterOrders = clusters.get(i).getOrders();
                if (clusterOrders.isEmpty()) continue;

                double sumLat = 0;
                double sumLng = 0;
                for (Order o : clusterOrders) {
                    sumLat += o.getLat();
                    sumLng += o.getLng();
                }
                double newLat = sumLat / clusterOrders.size();
                double newLng = sumLng / clusterOrders.size();

                if (Math.abs(centroids.get(i).getLat() - newLat) > 0.0001 || Math.abs(centroids.get(i).getLng() - newLng) > 0.0001) {
                    changed = true;
                }
                centroids.get(i).setLat(newLat);
                centroids.get(i).setLng(newLng);
                clusters.get(i).setCentroidLat(newLat);
                clusters.get(i).setCentroidLng(newLng);
            }
            iteration++;
        }

        // Filtrar clusters vacíos
        return clusters.stream().filter(c -> !c.getOrders().isEmpty()).collect(Collectors.toList());
    }

    private void labelClustersGeographically(List<ClusterDTO> clusters) {
        if (clusters.isEmpty()) return;

        double avgLat = clusters.stream().mapToDouble(ClusterDTO::getCentroidLat).average().orElse(0);
        double avgLng = clusters.stream().mapToDouble(ClusterDTO::getCentroidLng).average().orElse(0);

        for (ClusterDTO cluster : clusters) {
            double lat = cluster.getCentroidLat();
            double lng = cluster.getCentroidLng();

            if (lat > avgLat + 0.002) cluster.setZoneName("Zona Norte");
            else if (lat < avgLat - 0.002) cluster.setZoneName("Zona Sur");
            else if (lng > avgLng + 0.002) cluster.setZoneName("Zona Oriente");
            else if (lng < avgLng - 0.002) cluster.setZoneName("Zona Occidente");
            else cluster.setZoneName("Zona Centro");
        }
    }

    private List<ClusterDTO> assignDriversToClusters(List<ClusterDTO> clusters, List<Driver> drivers) {
        List<Driver> unassignedDrivers = new ArrayList<>(drivers);

        for (ClusterDTO cluster : clusters) {
            if (unassignedDrivers.isEmpty()) break;

            Driver closestDriver = null;
            double minDistance = Double.MAX_VALUE;

            for (Driver d : unassignedDrivers) {
                // Si el conductor no tiene GPS todavía (es nuevo), usamos el centroide del cluster 
                // como su posición teórica para que sea asignado al primer cluster disponible.
                double driverLat = (d.getLocation() != null && d.getLocation().getLat() != null) 
                                   ? d.getLocation().getLat() : cluster.getCentroidLat();
                double driverLng = (d.getLocation() != null && d.getLocation().getLng() != null) 
                                   ? d.getLocation().getLng() : cluster.getCentroidLng();

                double dist = getEuclideanDistance(cluster.getCentroidLat(), cluster.getCentroidLng(), driverLat, driverLng);
                if (dist < minDistance) {
                    minDistance = dist;
                    closestDriver = d;
                }
            }

            if (closestDriver != null) {
                cluster.setSuggestedDriverId(closestDriver.getId());
                cluster.setSuggestedDriverName(closestDriver.getName());
                cluster.setDriverStatus(closestDriver.getStatus().name());
                unassignedDrivers.remove(closestDriver);
            }
        }
        return clusters;
    }

    private double getEuclideanDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 999999;
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lng1 - lng2, 2));
    }

    private String generateAIReport(List<ClusterDTO> primary, List<ClusterDTO> alternative, String city) {
        try {
            int totalOrders = primary.stream().mapToInt(c -> c.getOrders().size()).sum();
            int zones = primary.size();

            String currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new java.util.Locale("es", "ES")));
            
            StringBuilder context = new StringBuilder();
            context.append("Actúa como un Consultor Senior de Logística e IA de VibeRoute. ");
            context.append("TU OBJETIVO: Generar un reporte ejecutivo de alto nivel para el despacho en ").append(city).append(". ");
            context.append("FECHA ACTUAL: ").append(currentDate).append(". ");
            context.append("DATOS ACTUALES: ").append(totalOrders).append(" pedidos agrupados en ").append(zones).append(" zonas geográficas optimizadas. ");
            context.append("No pidas más información ni uses placeholders, genera el reporte analítico directamente. ");
            
            context.append("\n\nESTRUCTURA DEL REPORTE (Asegúrate de incluir la fecha actual en el encabezado del texto):\n");
            context.append("1. **RESUMEN OPERATIVO**: Análisis de la zonificación y eficiencia de la flota.\n");
            context.append("2. **IMPACTO ESTIMADO**: Reducción de backtracking, ahorro de combustible y mejora en tiempos.\n");
            context.append("3. **RECOMENDACIÓN**: Estrategia sugerida para el despacho inmediato.\n");
            context.append("\nUsa un tono corporativo y tecnológico. Puedes usar negritas (**).");
            
            return contextualAdvisor.askGeminiDirect(context.toString());
        } catch (Exception e) {
            log.error("Error generating AI Report: {}", e.getMessage());
            return "**REPORTE OPERATIVO**: Optimizando " + primary.size() + " zonas en " + city + ". Se recomienda iniciar despacho.";
        }
    }

    public void applyDispatchPlan(SmartDispatchPlanDTO plan, String selectedStrategy) {
        List<ClusterDTO> clustersToApply = "ALTERNATIVE".equalsIgnoreCase(selectedStrategy) ? plan.getAlternativeClusters() : plan.getPrimaryClusters();

        if (clustersToApply == null || clustersToApply.isEmpty()) return;

        for (ClusterDTO cluster : clustersToApply) {
            if (cluster.getOrders() == null || cluster.getOrders().isEmpty()) continue;

            // Extraemos solo los IDs de forma segura
            List<Long> orderIds = cluster.getOrders().stream()
                .map(o -> {
                    if (o == null) return null;
                    try {
                        // Intentamos obtener el ID de varias formas por si Jackson lo deserializó como Integer o Long
                        Object idObj = o.getId();
                        if (idObj instanceof Number) return ((Number) idObj).longValue();
                        if (idObj instanceof String) return Long.parseLong((String) idObj);
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (orderIds.isEmpty()) continue;

            // Recuperamos los pedidos reales de la BD para tener la data completa y fresca
            List<Order> realOrders = orderRepository.findAllById(orderIds);
            if (realOrders.isEmpty()) continue;

            String city = realOrders.get(0).getCity();
            
            // Creamos el lote
            com.routeoptimizer.model.entity.Batch newBatch = batchService.createManualBatch(orderIds, city);
            
            // Asignamos el conductor si existe la sugerencia
            if (cluster.getSuggestedDriverId() != null) {
                try {
                    batchService.assignDriverToBatch(newBatch.getId(), cluster.getSuggestedDriverId());
                } catch (Exception e) {
                    log.error("Error asignando conductor {} al lote {}: {}", cluster.getSuggestedDriverId(), newBatch.getId(), e.getMessage());
                }
            }
        }
    }
}
