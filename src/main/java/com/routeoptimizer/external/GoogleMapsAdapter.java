package com.routeoptimizer.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.service.MapService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * GOOGLE MAPS OFFICIAL Adapter (v3).
 * Handles professional geocoding for Colombia with local nomenclature cleaning.
 */
@Service
public class GoogleMapsAdapter implements MapService {

  private static final Logger log = LoggerFactory.getLogger(GoogleMapsAdapter.class);
  private final RestTemplate restTemplate;

  @Value("${google.maps.api.key:}")
  private String apiKey;

  public GoogleMapsAdapter() {
    this.restTemplate = new RestTemplate();
  }

  @Override
  @Cacheable(value = "geocoding", key = "#address + '|' + #city")
  public Coordinate geocode(String address, String city) {
    if (apiKey == null || apiKey.isEmpty()) {
      log.error("Google Maps API Key not detected. Using City Fallback for {}.", city);
      return getCityFallback(city);
    }

    // 1. Professional cleaning and preparation for Colombia
    String cleanAddress = address.trim()
        .replaceAll("(?i)Calle", "CL")
        .replaceAll("(?i)Carrera", "KR")
        .replaceAll("(?i)Avenida", "AV")
        .replaceAll("\\s+", " ");

    try {
      java.net.URI uri = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
          .queryParam("address", cleanAddress + ", " + city)
          .queryParam("key", apiKey)
          .queryParam("components", "country:CO|locality:" + city)
          .queryParam("region", "co")
          .build()
          .encode()
          .toUri();

      log.info("🌐 Professional Geocoding: {} -> {}", address, uri);

      String rawResponse = restTemplate.getForObject(uri, String.class);
      JsonNode response = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawResponse);

      if (response != null && "OK".equals(response.path("status").asText())) {
        JsonNode location = response.path("results").get(0).path("geometry").path("location");
        double lat = location.path("lat").asDouble();
        double lng = location.path("lng").asDouble();

        log.info("✅ Google OK: [{}, {}]", lat, lng);
        return new Coordinate(lat, lng);
      } else {
        String errorStatus = response != null ? response.path("status").asText() : "UNKNOWN";
        log.warn("❌ Google Geocode failed ({}). Using city fallback for {}.", errorStatus, city);
        return getCityFallback(city);
      }
    } catch (Exception e) {
      log.error("💥 Critical error in Google Maps Adapter: {}", e.getMessage());
      return getCityFallback(city);
    }
  }

  private Coordinate getCityFallback(String city) {
    if (city.equalsIgnoreCase("Medellín")) return new Coordinate(6.2442, -75.5812);
    if (city.equalsIgnoreCase("Pasto")) return new Coordinate(1.2136, -77.2811);
    return new Coordinate(4.6097, -74.0817); // Bogotá default
  }

  @Override
  @Cacheable(value = "distances", key = "#origin.lat + ',' + #origin.lng + '|' + #destination.lat + ',' + #destination.lng")
  public Double calculateDistance(Coordinate origin, Coordinate destination) {
    if (apiKey == null || apiKey.isEmpty())
      return 1000.0;

    String url = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/distancematrix/json")
        .queryParam("origins", origin.getLat() + "," + origin.getLng())
        .queryParam("destinations", destination.getLat() + "," + destination.getLng())
        .queryParam("key", apiKey)
        .build()
        .encode()
        .toUriString();

    try {
      JsonNode response = restTemplate.getForObject(url, JsonNode.class);
      if (response != null && "OK".equals(response.path("status").asText())) {
        JsonNode element = response.path("rows").get(0).path("elements").get(0);
        if ("OK".equals(element.path("status").asText())) {
          return element.path("distance").path("value").asDouble(); // Meters
        }
      }
    } catch (Exception e) {
      log.warn("Error in Distance Matrix: {}", e.getMessage());
    }
    return 1000.0; // Fallback 1km
  }

  @Override
  public List<String> suggestAddress(String text) {
    if (apiKey == null || apiKey.isEmpty())
      return Collections.emptyList();

    String url = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/place/autocomplete/json")
        .queryParam("input", text)
        .queryParam("key", apiKey)
        .queryParam("components", "country:co")
        .queryParam("language", "en")
        .build()
        .encode()
        .toUriString();

    try {
      JsonNode response = restTemplate.getForObject(url, JsonNode.class);
      if (response != null && "OK".equals(response.path("status").asText())) {
        List<String> suggestions = new java.util.ArrayList<>();
        for (JsonNode pred : response.path("predictions")) {
          suggestions.add(pred.path("description").asText());
        }
        return suggestions;
      }
    } catch (Exception e) {
      log.warn("Error in Places Autocomplete: {}", e.getMessage());
    }
    return Collections.singletonList(text);
  }

  @Override
  public long[][] getDistanceMatrix(List<Coordinate> points, String mode) {
    int n = points.size();
    long[][] matrix = new long[n][n];

    if (apiKey == null || apiKey.isEmpty() || n <= 1) {
      log.warn("Google Maps API Key not detected. Calculating Euclidean travel time (seconds) for optimization.");
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          if (i == j) {
            matrix[i][j] = 0;
          } else {
            Coordinate p1 = points.get(i);
            Coordinate p2 = points.get(j);
            
            if (p1 == null || p2 == null || p1.getLat() == null || p2.getLat() == null) {
                matrix[i][j] = 999999;
            } else {
                // Simple Euclidean distance (approximate meters) / 8.33 m/s (approx 30km/h) = seconds
                double d = Math.sqrt(Math.pow(p1.getLat() - p2.getLat(), 2) + Math.pow(p1.getLng() - p2.getLng(), 2)) * 111320;
                matrix[i][j] = (long) (d / 8.33); 
            }
          }
        }
      }
      return matrix;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(points.get(i).getLat()).append(",").append(points.get(i).getLng());
      if (i < n - 1)
        sb.append("|");
    }
    String pointsString = sb.toString();

    // [HOTFIX] TOGGLE DE TRÁFICO: Solo inyectamos tráfico real si el modo es PRIORITY
    // Esto ahorra costos significativos (Tier Standard vs Tier Advanced en Google Cloud)
    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/distancematrix/json")
        .queryParam("origins", pointsString)
        .queryParam("destinations", pointsString)
        .queryParam("key", apiKey);

    if ("PRIORITY".equalsIgnoreCase(mode)) {
        builder.queryParam("departure_time", "now")
               .queryParam("traffic_model", "best_guess");
        log.info("🚀 TRAFFIC MODE ENABLED (Tier Advanced Billing)");
    } else {
        log.info("🌿 EFFICIENCY MODE ENABLED (Tier Standard Billing)");
    }

    String url = builder.build().encode().toUriString();

    try {
      JsonNode response = restTemplate.getForObject(url, JsonNode.class);
      if (response != null && "OK".equals(response.path("status").asText())) {
        JsonNode rows = response.path("rows");
        for (int i = 0; i < n; i++) {
          JsonNode elements = rows.get(i).path("elements");
          for (int j = 0; j < n; j++) {
            JsonNode element = elements.get(j);
            if ("OK".equals(element.path("status").asText())) {
              // [FASE 3] OPTIMIZACIÓN POR TIEMPO: Usamos duration_in_traffic si está disponible
              if (element.has("duration_in_traffic")) {
                  matrix[i][j] = element.path("duration_in_traffic").path("value").asLong();
              } else {
                  matrix[i][j] = element.path("duration").path("value").asLong();
              }
            } else {
              matrix[i][j] = 999999; // Penalty for invalid route
            }
          }
        }
        return matrix;
      } else {
        log.warn("Google Maps API returned {}. Falling back to Euclidean.", response != null ? response.path("status").asText() : "null");
      }
    } catch (Exception e) {
      log.error("Error getting distance matrix with traffic: {}", e.getMessage());
    }

    log.warn("Calculating Euclidean travel time for optimization as fallback.");
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (i == j) {
          matrix[i][j] = 0;
        } else {
          Coordinate p1 = points.get(i);
          Coordinate p2 = points.get(j);
          if (p1 == null || p2 == null || p1.getLat() == null || p2.getLat() == null) {
              matrix[i][j] = 999999;
          } else {
              double d = Math.sqrt(Math.pow(p1.getLat() - p2.getLat(), 2) + Math.pow(p1.getLng() - p2.getLng(), 2)) * 111320;
              matrix[i][j] = (long) (d / 8.33); // 30km/h avg
          }
        }
      }
    }

    return matrix;
  }

  @Override
  public String getDirections(Coordinate origin, Coordinate destination, List<Coordinate> waypoints) {
    if (apiKey == null || apiKey.isEmpty())
      return null;

    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/directions/json")
        .queryParam("origin", origin.getLat() + "," + origin.getLng())
        .queryParam("destination", destination.getLat() + "," + destination.getLng())
        .queryParam("key", apiKey);

    if (waypoints != null && !waypoints.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      // Google standard limit is 25 waypoints (23 intermediate + origin + dest)
      int limit = Math.min(waypoints.size(), 23);
      for (int i = 0; i < limit; i++) {
        sb.append(waypoints.get(i).getLat()).append(",").append(waypoints.get(i).getLng());
        if (i < limit - 1)
          sb.append("|");
      }
      builder.queryParam("waypoints", sb.toString());
    }

    try {
      JsonNode response = restTemplate.getForObject(builder.build().encode().toUriString(), JsonNode.class);
      if (response != null && "OK".equals(response.path("status").asText())) {
        return response.path("routes").get(0).path("overview_polyline").path("points").asText();
      }
    } catch (Exception e) {
      log.error("Error in Directions API: {}", e.getMessage());
    }
    return null;
  }

  @Override
  public Double getTrafficFactor(String city) {
    if (apiKey == null || apiKey.isEmpty()) {
      log.warn("API Key not configured, using traffic factor based on local time.");
      return calculateTrafficFactorByHour();
    }

    try {
      String origin = getCityCenter(city);
      String destination = getCityPerimeter(city);

      String url = UriComponentsBuilder
          .fromHttpUrl("https://maps.googleapis.com/maps/api/distancematrix/json")
          .queryParam("origins", origin)
          .queryParam("destinations", destination)
          .queryParam("departure_time", "now")
          .queryParam("traffic_model", "best_guess")
          .queryParam("key", apiKey)
          .build()
          .encode()
          .toUriString();

      JsonNode response = restTemplate.getForObject(url, JsonNode.class);
      if (response != null && "OK".equals(response.path("status").asText())) {
        JsonNode element = response.path("rows").get(0).path("elements").get(0);
        if ("OK".equals(element.path("status").asText())) {
          double normalDuration = element.path("duration").path("value").asDouble();
          double trafficDuration = element.has("duration_in_traffic")
              ? element.path("duration_in_traffic").path("value").asDouble()
              : normalDuration;

          double factor = (trafficDuration - normalDuration) / normalDuration;
          factor = Math.max(0.0, Math.min(1.0, factor));

          log.info("REAL traffic factor for {}: {}% congestion", city, (int) (factor * 100));
          return factor;
        }
      }
    } catch (Exception e) {
      log.warn("Error querying real traffic for {}: {}. Using fallback by hour.", city, e.getMessage());
    }

    return calculateTrafficFactorByHour();
  }

  private enum CityConfig {
    BOGOTA("Bogotá", "4.6097,-74.0817", "4.6580,-74.0936"),
    MEDELLIN("Medellín", "6.2442,-75.5812", "6.2006,-75.5710"),
    CALI("Cali", "3.4516,-76.5320", "3.3950,-76.5250");

    private final String cityName;
    private final String center;
    private final String perimeter;

    CityConfig(String cityName, String center, String perimeter) {
      this.cityName = cityName;
      this.center = center;
      this.perimeter = perimeter;
    }

    public static CityConfig fromName(String name) {
      for (CityConfig config : values()) {
        if (config.cityName.equalsIgnoreCase(name)) return config;
      }
      return BOGOTA; // Default
    }
  }

  private Double calculateTrafficFactorByHour() {
    int hour = java.time.LocalTime.now().getHour();
    if (hour >= 7 && hour <= 9) return 0.45;  // Peak morning
    if (hour >= 17 && hour <= 19) return 0.55; // Peak afternoon
    if (hour >= 12 && hour <= 14) return 0.25; // Lunch
    return 0.10; // Base traffic
  }

  private String getCityCenter(String city) {
    return CityConfig.fromName(city).center;
  }

  private String getCityPerimeter(String city) {
    return CityConfig.fromName(city).perimeter;
  }
}
