package com.routeoptimizer.model.entity;

import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.model.enums.DriverStatus;

import jakarta.persistence.*;

/**
 * Entidad Driver que hereda los atributos base de User.
 * Mapeada a base de datos.
 */
@Entity
@Table(name = "drivers")
@DiscriminatorValue("DRIVER")
public class Driver extends User {

  @Embedded
  private Coordinate location;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DriverStatus status;

  @Column(name = "max_capacity", nullable = false)
  private Integer maxCapacity = 20;

  @Column(name = "license_plate")
  private String licensePlate;

  @Column(name = "cost_per_hour", precision = 10, scale = 2)
  private java.math.BigDecimal costPerHour;

  @Column(name = "completed_orders")
  private Integer completedOrders = 0;

  @Column(name = "failed_orders")
  private Integer failedOrders = 0;

  public Driver() {
    super();
  }

  // Métodos del diagrama
  public void updateLocation(Double lat, Double lng) {
    if (this.location == null) {
      this.location = new Coordinate(lat, lng);
    } else {
      this.location.setLat(lat);
      this.location.setLng(lng);
    }
  }

  public void confirmDelivery(Long orderId) {
    // Lógica de confirmación se puede implementar usando eventos o llamadas al
    // servicio
  }

  // Getters y Setters
  public Coordinate getLocation() {
    return location;
  }

  public void setLocation(Coordinate location) {
    this.location = location;
  }

  public DriverStatus getStatus() {
    return status;
  }

  public void setStatus(DriverStatus status) {
    this.status = status;
  }

  public Integer getMaxCapacity() {
    return maxCapacity;
  }

  public void setMaxCapacity(Integer maxCapacity) {
    this.maxCapacity = maxCapacity;
  }

  public java.math.BigDecimal getCostPerHour() {
    return costPerHour;
  }

  public void setCostPerHour(java.math.BigDecimal costPerHour) {
    this.costPerHour = costPerHour;
  }

  public Integer getCompletedOrders() {
    return completedOrders;
  }

  public void setCompletedOrders(Integer completedOrders) {
    this.completedOrders = completedOrders;
  }

  public Integer getFailedOrders() {
    return failedOrders;
  }

  public void setFailedOrders(Integer failedOrders) {
    this.failedOrders = failedOrders;
  }

  public String getLicensePlate() {
    return licensePlate;
  }

  public void setLicensePlate(String licensePlate) {
    this.licensePlate = licensePlate;
  }
}
