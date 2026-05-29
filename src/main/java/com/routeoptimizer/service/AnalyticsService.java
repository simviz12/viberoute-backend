package com.routeoptimizer.service;

import com.routeoptimizer.dto.DeliveryStatisticsDTO;
import com.routeoptimizer.dto.DriverRankingDTO;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.repository.OrderRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio encargado de generar las analíticas y estadísticas del sistema.
 */
@Service
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final com.routeoptimizer.repository.RouteRepository routeRepository;
    private final com.routeoptimizer.repository.DriverRepository driverRepository;

    public AnalyticsService(OrderRepository orderRepository,
            com.routeoptimizer.repository.RouteRepository routeRepository,
            com.routeoptimizer.repository.DriverRepository driverRepository) {
        this.orderRepository = orderRepository;
        this.routeRepository = routeRepository;
        this.driverRepository = driverRepository;
    }

    /**
     * Calculates financial metrics: Revenue, Costs, and Net Profit.
     */
    @Transactional(readOnly = true)
    public com.routeoptimizer.dto.FinancialAnalyticsDTO getFinancialAnalytics(String cityFilter) {
        String cleanCity = cityFilter;
        if (cleanCity == null || cleanCity.trim().isEmpty() || cleanCity.equalsIgnoreCase("undefined") || cleanCity.equalsIgnoreCase("Global")) {
            cleanCity = null;
        }

        java.math.BigDecimal revenue;
        if (cleanCity != null) {
            revenue = orderRepository.getRevenueByCity(cleanCity);
        } else {
            revenue = orderRepository.getTotalRevenue();
        }

        if (revenue == null)
            revenue = java.math.BigDecimal.ZERO;

        java.math.BigDecimal totalCosts = java.math.BigDecimal.ZERO;
        List<com.routeoptimizer.model.entity.Route> routes = routeRepository.findAllOptimized();

        for (com.routeoptimizer.model.entity.Route route : routes) {
            if (route.getDriverId() != null) {
                java.util.Optional<com.routeoptimizer.model.entity.Driver> driverOpt = driverRepository
                        .findById(route.getDriverId());
                if (driverOpt.isPresent()) {
                    com.routeoptimizer.model.entity.Driver d = driverOpt.get();
                    // Si hay filtro de ciudad, solo sumamos costos de conductores de esa ciudad
                    if (cleanCity != null
                            && !cleanCity.equalsIgnoreCase(d.getAssignedCity())) {
                        continue;
                    }
                    java.math.BigDecimal costPerHour = d.getCostPerHour() != null ? d.getCostPerHour()
                            : java.math.BigDecimal.valueOf(15000);
                    double hours = route.getEstimatedTimeSeconds() / 3600.0;
                    java.math.BigDecimal routeCost = costPerHour.multiply(java.math.BigDecimal.valueOf(hours));
                    totalCosts = totalCosts.add(routeCost);
                }
            }
        }

        java.math.BigDecimal netProfit = revenue.subtract(totalCosts);
        double margin = revenue.compareTo(java.math.BigDecimal.ZERO) > 0
                ? netProfit.divide(revenue, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100
                : 0;

        Map<String, java.math.BigDecimal> byCity = new HashMap<>();
        for (String city : List.of("Bogotá", "Pasto", "Medellín")) {
            java.math.BigDecimal cityRev = orderRepository.getRevenueByCity(city);
            byCity.put(city, cityRev != null ? cityRev : java.math.BigDecimal.ZERO);
        }

        Map<String, java.math.BigDecimal> monthlyRevenue = new HashMap<>();
        List<Object[]> monthlyData = orderRepository.getMonthlyRevenueNative(cleanCity);
        for (Object[] row : monthlyData) {
            String month = (String) row[0];
            Object rawTotal = row[1];
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            if (rawTotal instanceof java.math.BigDecimal) {
                total = (java.math.BigDecimal) rawTotal;
            } else if (rawTotal instanceof Number) {
                total = java.math.BigDecimal.valueOf(((Number) rawTotal).doubleValue());
            }
            monthlyRevenue.put(month.trim(), total);
        }

        return new com.routeoptimizer.dto.FinancialAnalyticsDTO(revenue, totalCosts, netProfit, byCity, monthlyRevenue,
                margin);
    }

    /**
     * Calculates successful deliveries comparing different time periods.
     */
    @Transactional(readOnly = true)
    public DeliveryStatisticsDTO getDeliveryStatistics(String cityFilter) {
        String cleanCity = cityFilter;
        if (cleanCity == null || cleanCity.trim().isEmpty() || cleanCity.equalsIgnoreCase("undefined") || cleanCity.equalsIgnoreCase("Global")) {
            cleanCity = null;
        }

        LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime weekStart = LocalDateTime.now().minusWeeks(1);
        LocalDateTime monthStart = LocalDateTime.now().minusMonths(1);

        long today;
        long week;
        long month;

        if (cleanCity != null) {
            today = orderRepository.countSuccessfulDeliveriesSinceAndCity(todayStart, cleanCity);
            week = orderRepository.countSuccessfulDeliveriesSinceAndCity(weekStart, cleanCity);
            month = orderRepository.countSuccessfulDeliveriesSinceAndCity(monthStart, cleanCity);
        } else {
            today = orderRepository.countSuccessfulDeliveriesSince(todayStart);
            week = orderRepository.countSuccessfulDeliveriesSince(weekStart);
            month = orderRepository.countSuccessfulDeliveriesSince(monthStart);
        }

        Map<String, Long> byCity = new HashMap<>();
        if (cleanCity != null) {
            byCity.put(cleanCity, orderRepository.countByStatusAndCity(OrderStatus.DELIVERED, cleanCity));
        } else {
            byCity.put("Pasto", orderRepository.countByStatusAndCity(OrderStatus.DELIVERED, "Pasto"));
            byCity.put("Bogotá", orderRepository.countByStatusAndCity(OrderStatus.DELIVERED, "Bogotá"));
            byCity.put("Medellín", orderRepository.countByStatusAndCity(OrderStatus.DELIVERED, "Medellín"));
        }

        return new DeliveryStatisticsDTO(today, week, month, byCity);
    }

    /**
     * Obtiene el ranking de los 5 repartidores más efectivos.
     */
    @Transactional(readOnly = true)
    public List<DriverRankingDTO> getEfficiencyRanking(String cityFilter) {
        String cleanCity = cityFilter;
        if (cleanCity == null || cleanCity.trim().isEmpty() || cleanCity.equalsIgnoreCase("undefined") || cleanCity.equalsIgnoreCase("Global")) {
            cleanCity = null;
        }

        List<DriverRankingDTO> original;
        if (cleanCity != null) {
            original = orderRepository.getDriverRankingsByCity(cleanCity, PageRequest.of(0, 5));
        } else {
            original = orderRepository.getDriverRankings(PageRequest.of(0, 5));
        }

        // Post-procesar para asignar tags/condecoraciones según posición
        java.util.List<DriverRankingDTO> ranked = new java.util.ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            DriverRankingDTO dto = original.get(i);
            String tag = (i == 0) ? "🥇 Top Driver"
                    : (i == 1) ? "🥈 Excellent" : (i == 2) ? "🥉 Very Good" : "Efficient";

            ranked.add(new DriverRankingDTO(
                    dto.driverName(),
                    dto.successfulDeliveries(),
                    Math.round(dto.effectivenessPercentage() * 10.0) / 10.0, // Formatear a 1 decimal para evitar
                                                                             // 99.999999
                    tag));
        }
        return ranked;
    }
}
