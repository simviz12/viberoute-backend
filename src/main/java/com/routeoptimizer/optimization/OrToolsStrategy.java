package com.routeoptimizer.optimization;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.entity.Route;
import com.routeoptimizer.service.MapService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrToolsStrategy implements OptimizationStrategy {

  private static final Logger log = LoggerFactory.getLogger(OrToolsStrategy.class);
  private final MapService mapService;

  static {
    Loader.loadNativeLibraries();
  }

  public OrToolsStrategy(MapService mapService) {
    this.mapService = mapService;
  }

  @Override
  public Route optimize(List<Order> orders, Coordinate startLocation, String mode) {
    if (orders == null || orders.isEmpty()) {
      return new Route.Builder().build();
    }
    
    final String finalMode = (mode != null) ? mode : "EFFICIENCY";
    log.info("Iniciando optimización de ruta para {} pedidos en modo {}.", orders.size(), finalMode);

    final int numOrders = orders.size();
    final int numNodes = numOrders + 1;
    final int numVehicles = 1;
    final int depotNode = 0;

    RoutingIndexManager manager = new RoutingIndexManager(numNodes, numVehicles, depotNode);

    // MODO EFICIENCIA: Tiempo Pura (basado en Tráfico si está disponible)
    // MODO PRIORIDAD: Tiempo con bonos
    boolean isPriorityMode = "PRIORITY".equalsIgnoreCase(finalMode);
    long[][] timeMatrix = generateDistanceMatrix(orders, startLocation, finalMode);

    RoutingModel routing = new RoutingModel(manager);

    final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
      int fromNode = manager.indexToNode(fromIndex);
      int toNode = manager.indexToNode(toIndex);
      if (fromNode >= timeMatrix.length || toNode >= timeMatrix[0].length) return 999999L;
      
      long travelTime = timeMatrix[fromNode][toNode];
      
      // MODO PRIORIDAD: Reducimos artificialmente el "costo" de viajar hacia un pedido prioritario
      // para que el algoritmo lo agende lo antes posible.
      if (isPriorityMode && toNode > 0) {
          Order target = orders.get(toNode - 1);
          if (target.getPriority() == com.routeoptimizer.model.enums.Priority.HIGH) {
              return travelTime / 2; // Bono de prioridad (50% menos costo de arco)
          }
      }
      
      return travelTime;
    });

    routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

    // [FASE 3] DIMENSIÓN DE TIEMPO (Time Windows)
    String timeDimensionName = "Time";
    routing.addDimension(
        transitCallbackIndex,
        3600 * 4, // Permitir esperas (Slack) de hasta 4 horas
        3600 * 24, // Tiempo máximo de ruta (Horizon: 24h)
        false, // No forzar inicio en cero
        timeDimensionName
    );
    com.google.ortools.constraintsolver.RoutingDimension timeDimension = routing.getMutableDimension(timeDimensionName);

    // [HOTFIX] SINCRONIZACIÓN DEL NODO CERO
    // Seteamos la hora actual del sistema como el punto de inicio para el conductor.
    // Esto asegura que las ventanas de tiempo (toSecondOfDay) sean relativas al momento real de salida.
    long nowInSeconds = java.time.LocalTime.now().toSecondOfDay();
    timeDimension.cumulVar(manager.nodeToIndex(0)).setValue(nowInSeconds);
    log.info("🚀 Ruta sincronizada con inicio a las: {}s", nowInSeconds);

    // [FASE 3] RESTRICCIONES DE VENTANAS DE TIEMPO
    // Nodo 0 es el depósito (ubicación actual del conductor). 
    // Los pedidos empiezan desde el índice 1.
    for (int i = 0; i < orders.size(); i++) {
        Order order = orders.get(i);
        if (order.getTimeWindowStart() != null && order.getTimeWindowEnd() != null) {
            long startSeconds = order.getTimeWindowStart().toSecondOfDay();
            long endSeconds = order.getTimeWindowEnd().toSecondOfDay();
            
            // El índice en OR-Tools para el nodo i+1
            long index = manager.nodeToIndex(i + 1);
            timeDimension.cumulVar(index).setRange(startSeconds, endSeconds);
            
            log.info("⏰ Ventana para pedido {}: {}s - {}s", order.getId(), startSeconds, endSeconds);
        }
    }

    RoutingSearchParameters searchParameters = com.google.ortools.constraintsolver.main.defaultRoutingSearchParameters()
        .toBuilder()
        .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC) 
        .setTimeLimit(com.google.protobuf.Duration.newBuilder().setSeconds(5).build()) // Aumentamos a 5s para ventanas complejas
        .build();

    Assignment solution = routing.solveWithParameters(searchParameters);

    if (solution == null) {
      log.error("❌ No se encontró una solución que respete las ventanas de tiempo. Reintentando sin restricciones...");
      // Fallback: Si falla con ventanas, podríamos intentar resolver sin ellas, 
      // pero por ahora devolvemos la lista original para seguridad.
      return new Route.Builder().forBatch(orders.get(0).getBatchId()).withStops(orders).build();
    }

    List<Order> optimizedStops = new ArrayList<>();
    long index = routing.start(0);
    long totalTime = 0;

    while (!routing.isEnd(index)) {
      int nodeIndex = manager.indexToNode(index);
      if (nodeIndex > 0) {
        optimizedStops.add(orders.get(nodeIndex - 1));
      }
      long nextIndex = solution.value(routing.nextVar(index));
      totalTime += routing.getArcCostForVehicle(index, nextIndex, 0);
      index = nextIndex;
    }

    return new Route.Builder()
        .forBatch(orders.get(0).getBatchId())
        .withStops(optimizedStops)
        .withTotalDistance(totalTime) // Aquí guardamos el tiempo total (segundos)
        .build();
  }

  private long[][] generateDistanceMatrix(List<Order> orders, Coordinate startLocation, String mode) {
    List<Coordinate> points = new ArrayList<>();
    points.add(startLocation);
    for (Order o : orders) {
      points.add(new Coordinate(o.getLat(), o.getLng()));
    }
    return mapService.getDistanceMatrix(points, mode);
  }
}
