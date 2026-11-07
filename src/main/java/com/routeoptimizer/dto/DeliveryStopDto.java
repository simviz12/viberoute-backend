package com.routeoptimizer.dto;

import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.model.enums.OrderStatus;
import com.routeoptimizer.model.enums.Priority;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * DTO que representa una parada individual de entrega en la ruta para el Smartwatch (Wear OS).
 * Diseñado con bajo overhead de serialización para optimizar ancho de banda y batería del wearable.
 */
public class DeliveryStopDto {

    private Long deliveryId;
    private Long orderId;
    private String clientName;
    private String clientReference;
    private String address;
    private String city;
    private Double latitude;
    private Double longitude;
    private Integer deliveryOrder;
    private OrderStatus status;
    private Priority priority;
    private LocalTime timeWindowStart;
    private LocalTime timeWindowEnd;
    private BigDecimal price;
    private String notes;

    public DeliveryStopDto() {
    }

    public static DeliveryStopDto fromEntity(Order order) {
        if (order == null) {
            return null;
        }

        DeliveryStopDto dto = new DeliveryStopDto();
        dto.setDeliveryId(order.getId());
        dto.setOrderId(order.getId());
        dto.setClientName(order.getClientName());
        dto.setClientReference(order.getClientReference());
        dto.setAddress(order.getAddress());
        dto.setCity(order.getCity());
        if (order.getLocation() != null) {
            dto.setLatitude(order.getLocation().getLat());
            dto.setLongitude(order.getLocation().getLng());
        }
        dto.setDeliveryOrder(order.getDeliveryOrder());
        dto.setStatus(order.getStatus());
        dto.setPriority(order.getPriority());
        dto.setTimeWindowStart(order.getTimeWindowStart());
        dto.setTimeWindowEnd(order.getTimeWindowEnd());
        dto.setPrice(order.getPrice());
        dto.setNotes(order.getNonDeliveryReason());
        return dto;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getDeliveryOrder() {
        return deliveryOrder;
    }

    public void setDeliveryOrder(Integer deliveryOrder) {
        this.deliveryOrder = deliveryOrder;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalTime getTimeWindowStart() {
        return timeWindowStart;
    }

    public void setTimeWindowStart(LocalTime timeWindowStart) {
        this.timeWindowStart = timeWindowStart;
    }

    public LocalTime getTimeWindowEnd() {
        return timeWindowEnd;
    }

    public void setTimeWindowEnd(LocalTime timeWindowEnd) {
        this.timeWindowEnd = timeWindowEnd;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

