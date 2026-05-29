package com.routeoptimizer.controller;

import com.routeoptimizer.dto.DeliveryStatisticsDTO;
import com.routeoptimizer.dto.DriverRankingDTO;
import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.service.AnalyticsService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador para exponer las métricas y analíticas del sistema.
 */
@RestController
@RequestMapping({"/api/v1/stats", "/stats"})
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Obtiene el resumen de entregas (Hoy, Semana, Mes).
     */
    @GetMapping("/delivery-summary")
    public ResponseEntity<DeliveryStatisticsDTO> getDeliverySummary(
            @RequestParam(required = false) String city) {
        String cityFilter = resolveCity(city);
        return ResponseEntity.ok(analyticsService.getDeliveryStatistics(cityFilter));
    }

    /**
     * Obtiene el Top 5 de repartidores más efectivos.
     */
    @GetMapping("/driver-ranking")
    public ResponseEntity<List<DriverRankingDTO>> getDriverRanking(
            @RequestParam(required = false) String city) {
        String cityFilter = resolveCity(city);
        return ResponseEntity.ok(analyticsService.getEfficiencyRanking(cityFilter));
    }

    /**
     * Resuelve la ciudad efectiva: si el usuario es LOGISTICS, fuerza su ciudad asignada.
     */
    private String resolveCity(String requestedCity) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getRole() == Role.LOGISTICS) {
                return user.getAssignedCity();
            }
        }
        return requestedCity;
    }

    /**
     * Gets the financial profitability summary (Revenue, Costs, Profit).
     */
    @GetMapping("/financial-summary")
    public ResponseEntity<com.routeoptimizer.dto.FinancialAnalyticsDTO> getFinancialSummary(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String city) {
        String cityFilter = resolveCity(city);
        return ResponseEntity.ok(analyticsService.getFinancialAnalytics(cityFilter));
    }
}
