package com.routeoptimizer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * DTO para la confirmación de estado de entrega desde Wear OS (Smartwatch).
 * Soporta estados como DELIVERED, ABSENT, INCIDENT.
 * Compatible tanto con timestamps numéricos (epoch millis de Kotlin) como ISO 8601.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryStatusRequestDto {

    @NotBlank(message = "El estado es requerido (DELIVERED, ABSENT, INCIDENT)")
    private String status;

    private LocalDateTime timestamp;

    private String notes;

    public DeliveryStatusRequestDto() {
        this.timestamp = LocalDateTime.now();
    }

    public DeliveryStatusRequestDto(String status, LocalDateTime timestamp, String notes) {
        this.status = status;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp != null ? timestamp : LocalDateTime.now();
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @JsonProperty("timestamp")
    public void setTimestampFromAny(Object raw) {
        if (raw instanceof Number num) {
            this.timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(num.longValue()), ZoneId.systemDefault());
        } else if (raw instanceof String str) {
            try {
                this.timestamp = LocalDateTime.parse(str);
            } catch (Exception e) {
                this.timestamp = LocalDateTime.now();
            }
        } else if (raw == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
