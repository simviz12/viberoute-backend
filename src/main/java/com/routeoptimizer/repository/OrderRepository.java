package com.routeoptimizer.repository;

import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.enums.OrderStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for Order entity.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    Optional<Order> findByUuid(UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.batchId IS NULL")
    List<Order> findByBatchIdIsNullForUpdate();

    List<Order> findByBatchIdIsNull();

    @Query("SELECT o FROM Order o WHERE o.batchId IS NULL AND o.city = :city")
    List<Order> findByBatchIdIsNullAndCity(@Param("city") String city);

    @Query(value = "SELECT * FROM orders o WHERE o.location_lat IS NOT NULL AND o.location_lng IS NOT NULL AND ST_DWithin(" +
            "CAST(ST_SetSRID(ST_MakePoint(o.location_lng, o.location_lat), 4326) AS geography), " +
            "CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), " +
            ":radiusMeters)", nativeQuery = true)
    List<Order> findNearbyOrders(@Param("lat") double lat, @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters);

    @Query("SELECT o FROM Order o JOIN Batch b ON o.batchId = b.id WHERE b.driver.id = :driverId")
    List<Order> findByDriverId(@Param("driverId") Long driverId);

    List<Order> findByCity(String city);

    long countByStatusAndCity(OrderStatus status, String city);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED AND o.actualDeliveryTime >= :start")
    long countSuccessfulDeliveriesSince(@Param("start") java.time.LocalDateTime start);

    @Query("SELECT new com.routeoptimizer.dto.DriverRankingDTO(" +
            "r.name, " +
            "SUM(CASE WHEN o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED THEN 1L ELSE 0L END), " +
            "(SUM(CASE WHEN o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED THEN 1.0 ELSE 0.0 END) / COUNT(o)) * 100.0, "
            +
            "'') " +
            "FROM Order o JOIN Batch b ON o.batchId = b.id JOIN b.driver r " +
            "WHERE o.status IN (com.routeoptimizer.model.enums.OrderStatus.DELIVERED, com.routeoptimizer.model.enums.OrderStatus.RETURNED, com.routeoptimizer.model.enums.OrderStatus.CANCELLED) "
            +
            "GROUP BY r.name " +
            "ORDER BY SUM(CASE WHEN o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED THEN 1L ELSE 0L END) DESC")
    List<com.routeoptimizer.dto.DriverRankingDTO> getDriverRankings(
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT SUM(o.price) FROM Order o WHERE o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED")
    java.math.BigDecimal getTotalRevenue();

    @Query("SELECT SUM(o.price) FROM Order o WHERE o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED AND o.city = :city")
    java.math.BigDecimal getRevenueByCity(@Param("city") String city);

    @Query(value = "SELECT TRIM(TO_CHAR(o.actual_delivery_time, 'Month')) as month, COALESCE(SUM(o.price), 0) as total "
            +
            "FROM orders o WHERE o.status = 'DELIVERED' AND o.actual_delivery_time IS NOT NULL " +
            "AND (:city IS NULL OR o.city = :city) " +
            "GROUP BY month", nativeQuery = true)
    List<Object[]> getMonthlyRevenueNative(@Param("city") String city);

    @Query("SELECT COUNT(o) FROM Order o JOIN Batch b ON o.batchId = b.id JOIN b.driver r WHERE r.name = :name AND o.status = com.routeoptimizer.model.enums.OrderStatus.DELIVERED")
    long countSuccessfulDeliveriesForDriver(@Param("name") String name);

    @Query("SELECT COUNT(o) FROM Order o JOIN Batch b ON o.batchId = b.id JOIN b.driver r WHERE r.name = :name AND (o.status = com.routeoptimizer.model.enums.OrderStatus.CANCELLED OR o.status = com.routeoptimizer.model.enums.OrderStatus.RETURNED)")
    long countFailedDeliveriesForDriver(@Param("name") String name);

    @Query("SELECT o FROM Order o LEFT JOIN Batch b ON o.batchId = b.id LEFT JOIN b.driver d")
    List<Order> findAllWithDriver();
}
