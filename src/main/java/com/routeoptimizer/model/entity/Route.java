package com.routeoptimizer.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity that represents an optimized Route persisted in the database.
 * Implements the Builder design pattern.
 */
@Entity
@Table(name = "routes")
public class Route {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "batch_id", nullable = false)
  private Long batchId;

  @Column(name = "driver_id")
  private Long driverId;

  @Column(name = "total_distance_meters")
  private long totalDistanceMeters;

  @Column(name = "estimated_time_seconds")
  private long estimatedTimeSeconds;

  @Column(name = "creation_date")
  private LocalDateTime creationDate;

  @Column(name = "encoded_polyline", columnDefinition = "TEXT")
  private String encodedPolyline;

  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(name = "route_id")
  @OrderBy("deliveryOrder ASC")
  private List<Order> stops = new ArrayList<>();

  @Column(name = "last_latitude")
  private Double lastLatitude;

  @Column(name = "last_longitude")
  private Double lastLongitude;

  @Column(name = "last_updated_date")
  private LocalDateTime lastUpdatedDate;

  // Protected constructor for JPA
  protected Route() {
  }

  private Route(Builder builder) {
    this.batchId = builder.batchId;
    this.driverId = builder.driverId;
    this.totalDistanceMeters = builder.totalDistanceMeters;
    this.estimatedTimeSeconds = builder.estimatedTimeSeconds;
    this.stops = builder.stops;
    this.encodedPolyline = builder.encodedPolyline;
    this.creationDate = LocalDateTime.now();
  }

  // Getters
  public Long getId() {
    return id;
  }

  public Long getBatchId() {
    return batchId;
  }

  public Long getDriverId() {
    return driverId;
  }

  public List<Order> getStops() {
    return stops;
  }

  public long getTotalDistanceMeters() {
    return totalDistanceMeters;
  }

  public long getEstimatedTimeSeconds() {
    return estimatedTimeSeconds;
  }

  public LocalDateTime getCreationDate() {
    return creationDate;
  }

  public String getEncodedPolyline() {
    return encodedPolyline;
  }

  public Double getLastLatitude() {
    return lastLatitude;
  }

  public void setLastLatitude(Double lastLatitude) {
    this.lastLatitude = lastLatitude;
  }

  public Double getLastLongitude() {
    return lastLongitude;
  }

  public void setLastLongitude(Double lastLongitude) {
    this.lastLongitude = lastLongitude;
  }

  public LocalDateTime getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
    this.lastUpdatedDate = lastUpdatedDate;
  }

  /**
   * Static Builder for the Route class.
   */
  public static class Builder {
    private Long batchId;
    private Long driverId;
    private List<Order> stops = new ArrayList<>();
    private long totalDistanceMeters;
    private long estimatedTimeSeconds;
    private String encodedPolyline;

    public Builder forBatch(Long batchId) {
      this.batchId = batchId;
      return this;
    }

    public Builder withEncodedPolyline(String polyline) {
      this.encodedPolyline = polyline;
      return this;
    }

    public Builder forDriver(Long driverId) {
      this.driverId = driverId;
      return this;
    }

    public Builder withStops(List<Order> stops) {
      this.stops = new ArrayList<>(stops);
      return this;
    }

    public Builder withTotalDistance(long meters) {
      this.totalDistanceMeters = meters;
      return this;
    }

    public Builder withEstimatedTime(long seconds) {
      this.estimatedTimeSeconds = seconds;
      return this;
    }

    public Route build() {
      if (batchId == null) {
        throw new IllegalStateException("The batchId is mandatory to build a Route.");
      }
      return new Route(this);
    }
  }
}
