package com.routeoptimizer.controller;

import com.routeoptimizer.dto.DailySummaryRequest;
import com.routeoptimizer.dto.DeviationRequest;
import com.routeoptimizer.model.entity.Batch;
import com.routeoptimizer.service.AIRouteSuggestionService;
import com.routeoptimizer.service.BatchService;
import com.routeoptimizer.service.ContextualAdvisor;
import com.routeoptimizer.service.DeviationDetector;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for AI features.
 * Refactored to remove business logic and use typed DTOs, as per professor
 * requirements.
 */
@RestController
@RequestMapping({"/api/v1/ai", "/ai"})
public class AIController {

  private final AIRouteSuggestionService aiRouteSuggestionService;
  private final DeviationDetector deviationDetector;
  private final ContextualAdvisor contextualAdvisor;
  private final BatchService batchService;

  public AIController(AIRouteSuggestionService aiRouteSuggestionService,
      DeviationDetector deviationDetector,
      ContextualAdvisor contextualAdvisor,
      BatchService batchService) {
    this.aiRouteSuggestionService = aiRouteSuggestionService;
    this.deviationDetector = deviationDetector;
    this.contextualAdvisor = contextualAdvisor;
    this.batchService = batchService;
  }

  @GetMapping("/suggestions/{batchId}")
  public ResponseEntity<Map<String, Object>> getSuggestions(@PathVariable Long batchId) {
    Batch batch = batchService.findById(batchId);
    return ResponseEntity.ok(aiRouteSuggestionService.sugerirOptimizacion(batch));
  }

  @PostMapping("/deviation")
  public ResponseEntity<Map<String, Object>> detectDeviation(@RequestBody DeviationRequest request) {
    Map<String, Object> result = deviationDetector.verifyDeviation(
        request.getCurrentCoordinate(),
        request.getStopCoordinate(),
        request.getStopAddress(),
        request.getDriverName(),
        request.getCity());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/daily-summary")
  public ResponseEntity<Map<String, Object>> dailySummary(@RequestBody DailySummaryRequest request) {
    String summary = contextualAdvisor.generateDailySummary(
        request.getTotalDelivered(),
        request.getTotalPending(),
        request.getTotalCancelled(),
        request.getTotalReturned(),
        request.getEfficiencyScore(),
        request.getTotalHours(),
        request.getCity());

    return ResponseEntity.ok(Map.of(
        "summary", summary,
        "totalDelivered", request.getTotalDelivered(),
        "totalPending", request.getTotalPending(),
        "totalHours", request.getTotalHours(),
        "performance", calculatePerformance(request.getTotalDelivered(), request.getTotalPending())));
  }

  // Private helper to keep logic out of Service if it's purely presentation
  // related
  private String calculatePerformance(int delivered, int pending) {
    if (pending == 0)
      return "EXCELLENT";
    return delivered > pending ? "GOOD" : "NEEDS_IMPROVEMENT";
  }
}
