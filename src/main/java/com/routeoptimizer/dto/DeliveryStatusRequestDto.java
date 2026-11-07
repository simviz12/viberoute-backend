package com.routeoptimizer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * DTO para la confirmación de estado de entrega desde Wear OS (Smartwatch).
 * Soporta estados como DELIVERED, ABSENT, INCIDENT.
 */
public class DeliveryStatusRequestDto {

    @NotBlank(message = "El estado es requerido (DELIVERED, ABSENT, INCIDENT)")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private String notes;

    public DeliveryStatusRequestDto() {
    }

    public DeliveryStatusRequestDto(String status, LocalDateTime timestamp, String notes) {
        this.status = status;
        this.timestamp = timestamp;
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

