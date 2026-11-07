package com.routeoptimizer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.routeoptimizer.model.entity.Route;

import java.util.Optional;
import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
  @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Route r LEFT JOIN FETCH r.stops WHERE r.batchId = :batchId ORDER BY r.id DESC")
  List<Route> findRoutesByBatchId(@org.springframework.data.repository.query.Param("batchId") Long batchId);

  @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Route r LEFT JOIN FETCH r.stops WHERE r.driverId = :driverId ORDER BY r.id DESC")
  List<Route> findRoutesByDriverId(@org.springframework.data.repository.query.Param("driverId") Long driverId);

  default Optional<Route> findByBatchId(Long batchId) {
    List<Route> list = findRoutesByBatchId(batchId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  default Optional<Route> findByDriverId(Long driverId) {
    List<Route> list = findRoutesByDriverId(driverId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Route r LEFT JOIN FETCH r.stops")
  List<Route> findAllOptimized();
}
