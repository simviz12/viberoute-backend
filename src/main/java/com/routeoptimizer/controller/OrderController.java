package com.routeoptimizer.controller;

import com.routeoptimizer.dto.OrderCreateDTO;
import com.routeoptimizer.dto.OrderResponseDTO;
import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.service.OrderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/orders", "/orders"})
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  public ResponseEntity<?> createOrder(@RequestBody OrderCreateDTO dto) {
    try {
      Order createdOrder = orderService.createOrder(dto);
      return new ResponseEntity<>(OrderResponseDTO.fromEntity(createdOrder), HttpStatus.CREATED);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping
  public ResponseEntity<List<OrderResponseDTO>> listAll(@RequestParam(required = false) String city) {
    String filterCity = resolveCity(city);
    
    // AISLAMIENTO REGIONAL: Forzar filtro por ciudad si el usuario es Logístico
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
        if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS) {
            filterCity = user.getAssignedCity();
        }
    }

    List<Order> rawOrders;
    if (filterCity != null && !filterCity.isEmpty()) {
        rawOrders = orderService.findByCity(filterCity);
    } else {
        rawOrders = orderService.findAll();
    }

    List<OrderResponseDTO> orders = rawOrders.stream()
        .map(orderService::enrichOrderResponse)
        .collect(Collectors.toList());
    return ResponseEntity.ok(orders);
  }

  @GetMapping("/pending")
  public ResponseEntity<List<OrderResponseDTO>> listPendingWithoutBatch(@RequestParam(required = false) String city) {
    String searchCity = resolveCity(city);
    
    // Auto-filtro por ciudad si el usuario es Logístico y no especificó ciudad
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
        if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS && (searchCity == null || searchCity.isEmpty())) {
            searchCity = user.getAssignedCity();
        }
    }

    List<OrderResponseDTO> orders = orderService.findPendingWithoutBatch(searchCity).stream()
        .map(orderService::enrichOrderResponse)
        .collect(Collectors.toList());
    return ResponseEntity.ok(orders);
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable Long id) {
    Order order = orderService.findById(id);
    if (isForbiddenForLogistics(order.getCity())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok(orderService.enrichOrderResponse(order));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<?> updateStatus(
      @PathVariable Long id,
      @RequestParam OrderStatus status,
      @RequestParam(required = false) String reason) {
    try {
      Order existing = orderService.findById(id);
      if (isForbiddenForLogistics(existing.getCity())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      Order updated = orderService.updateStatus(id, status, reason);
      return ResponseEntity.ok(orderService.enrichOrderResponse(updated));
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/city/{city}")
  public ResponseEntity<?> listByCity(@PathVariable String city) {
    if (isForbiddenForLogistics(city)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    // SEGURIDAD: Rechazar acceso cross-city para usuarios LOGISTICS
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
      if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS) {
        if (!city.equalsIgnoreCase(user.getAssignedCity())) {
          return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
              .body(Map.of("error", "No tiene autorización para consultar pedidos de la ciudad: " + city));
        }
      }
    }
    List<OrderResponseDTO> orders = orderService.findByCity(city).stream()
        .map(orderService::enrichOrderResponse)
        .collect(Collectors.toList());
    return ResponseEntity.ok(orders);
  }

  @GetMapping("/city/{city}/count")
  public ResponseEntity<Map<String, Object>> getCountByStatusAndCity(
      @PathVariable String city,
      @RequestParam OrderStatus status) {
    if (isForbiddenForLogistics(city)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    long count = orderService.countByStatusAndCity(status, city);
    return ResponseEntity.ok(Map.of(
        "city", city,
        "status", status,
        "count", count));
  }

  @PostMapping("/bulk")
  public ResponseEntity<?> createOrdersBulk(@RequestBody List<OrderCreateDTO> dtos) {
    List<OrderResponseDTO> created = new java.util.ArrayList<>();
    List<Map<String, String>> errors = new java.util.ArrayList<>();

    for (int i = 0; i < dtos.size(); i++) {
      OrderCreateDTO dto = dtos.get(i);
      try {
        Order order = orderService.createOrder(dto);
        created.add(OrderResponseDTO.fromEntity(order));
      } catch (Exception e) {
        String ref = dto.getClientReference() != null ? dto.getClientReference() : "index-" + i;
        errors.add(Map.of("reference", ref, "error", e.getMessage()));
      }
    }

    Map<String, Object> result = Map.of(
        "created", created,
        "createdCount", created.size(),
        "errorCount", errors.size(),
        "errors", errors);

    if (created.isEmpty() && !errors.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteOrder(@PathVariable Long id) {
    try {
      Order existing = orderService.findById(id);
      if (isForbiddenForLogistics(existing.getCity())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      orderService.deleteOrder(id);
      return ResponseEntity.ok(Map.of("message", "Pedido eliminado correctamente"));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
  }

  private String resolveCity(String requestedCity) {
    User user = authenticatedUser();
    if (user != null && user.getRole() == Role.LOGISTICS) {
      return user.getAssignedCity();
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
}
