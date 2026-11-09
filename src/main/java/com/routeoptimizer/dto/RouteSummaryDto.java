package com.routeoptimizer.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO que encapsula el resumen de la ruta activa y sus paradas ordenadas
 * para la aplicación Standalone de Wear OS (Smartwatch).
 */
public class RouteSummaryDto {

    private Long routeId;
    private Long batchId;
    private Long driverId;
    private String driverName;
    private long totalDistanceMeters;
    private long estimatedTimeSeconds;
    private int totalStops;
    private int completedStops;
    private int pendingStops;
    private String status;
    private String encodedPolyline;
    private String city;
    private List<DeliveryStopDto> stops = new ArrayList<>();

    public RouteSummaryDto() {
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public long getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public void setTotalDistanceMeters(long totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public long getEstimatedTimeSeconds() {
        return estimatedTimeSeconds;
    }

    public void setEstimatedTimeSeconds(long estimatedTimeSeconds) {
        this.estimatedTimeSeconds = estimatedTimeSeconds;
    }

    public int getTotalStops() {
        return totalStops;
    }

    public void setTotalStops(int totalStops) {
        this.totalStops = totalStops;
    }

    public int getCompletedStops() {
        return completedStops;
    }

    public void setCompletedStops(int completedStops) {
        this.completedStops = completedStops;
    }

    public int getPendingStops() {
        return pendingStops;
    }

    public void setPendingStops(int pendingStops) {
        this.pendingStops = pendingStops;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEncodedPolyline() {
        return encodedPolyline;
    }

    public void setEncodedPolyline(String encodedPolyline) {
        this.encodedPolyline = encodedPolyline;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public List<DeliveryStopDto> getStops() {
        return stops;
    }

    public void setStops(List<DeliveryStopDto> stops) {
        this.stops = stops != null ? stops : new ArrayList<>();
    }

    // ── Compatibilidad directa con App Wear OS (Smartwatch) ──
    public String getId() {
        return routeId != null ? String.valueOf(routeId) : (batchId != null ? String.valueOf(batchId) : "1");
    }

    public String getRouteName() {
        return "Ruta Bogotá - " + (city != null ? city : "Centro");
    }

    public String getVehicleType() {
        return "MOTO";
    }

    public double getTotalDistanceKm() {
        return Math.round((totalDistanceMeters / 1000.0) * 10.0) / 10.0;
    }
}

