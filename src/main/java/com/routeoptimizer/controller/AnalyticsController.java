package com.routeoptimizer.controller;

import com.routeoptimizer.dto.DeliveryStatisticsDTO;
import com.routeoptimizer.dto.DriverRankingDTO;
import com.routeoptimizer.service.AnalyticsService;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<DeliveryStatisticsDTO> getDeliverySummary() {
        return ResponseEntity.ok(analyticsService.getDeliveryStatistics());
    }

    /**
     * Obtiene el Top 5 de repartidores más efectivos.
     */
    @GetMapping("/driver-ranking")
    public ResponseEntity<List<DriverRankingDTO>> getDriverRanking() {
        return ResponseEntity.ok(analyticsService.getEfficiencyRanking());
    }

    /**
     * Gets the financial profitability summary (Revenue, Costs, Profit).
     */
    @GetMapping("/financial-summary")
    public ResponseEntity<com.routeoptimizer.dto.FinancialAnalyticsDTO> getFinancialSummary(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String city) {
        return ResponseEntity.ok(analyticsService.getFinancialAnalytics(city));
    }
}
