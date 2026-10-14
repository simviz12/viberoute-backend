package com.routeoptimizer.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.model.Coordinate;
import com.routeoptimizer.service.MapService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OPEN SOURCE ROUTING & MAPPING ADAPTER (OSRM + Nominatim / OpenStreetMap).
 * 100% Free, sin necesidad de API Keys ni facturación propietaria.
 */
@Service
@ConditionalOnProperty(name = "viberoute.map.provider", havingValue = "osrm", matchIfMissing = true)
public class OpenSourceRoutingAdapter implements MapService {

  private static final Logger log = LoggerFactory.getLogger(OpenSourceRoutingAdapter.class);
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${osrm.router.url:https://router.project-osrm.org}")
  private String osrmBaseUrl;

  @Value("${nominatim.url:https://nominatim.openstreetmap.org}")
  private String nominatimBaseUrl;

  public OpenSourceRoutingAdapter() {
    this.restTemplate = new RestTemplate();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  @Cacheable(value = "geocoding", key = "#address + '|' + #city")
  public Coordinate geocode(String address, String city) {
    if (address == null || address.trim().isEmpty()) {
      return getCityFallback(city);
    }

    String cleanAddress = address.trim()
        .replaceAll("(?i)Calle", "CL")
        .replaceAll("(?i)Carrera", "KR")
        .replaceAll("(?i)Avenida", "AV")
        .replaceAll("\\s+", " ");

    try {
      java.net.URI uri = UriComponentsBuilder.fromHttpUrl(nominatimBaseUrl + "/search")
          .queryParam("q", cleanAddress + ", " + (city != null ? city : "Pasto") + ", Colombia")
          .queryParam("format", "json")
          .queryParam("countrycodes", "co")
          .queryParam("limit", 1)
          .build()
          .encode()
          .toUri();

      log.info("🌐 OpenStreetMap Nominatim Geocoding: {} ({})", address, city);

      // Nominatim requiere User-Agent descriptivo según su política de uso libre
      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.set("User-Agent", "VibeRouteColombia-OpenSource/1.0");
      org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

      org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
          uri, org.springframework.http.HttpMethod.GET, entity, String.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        JsonNode root = objectMapper.readTree(response.getBody());
        if (root.isArray() && root.size() > 0) {
          JsonNode first = root.get(0);
          double lat = first.path("lat").asDouble();
          double lng = first.path("lon").asDouble();
          log.info("✅ Nominatim OK: [{}, {}]", lat, lng);
          return new Coordinate(lat, lng);
        }
      }
    } catch (Exception e) {
      log.warn("⚠️ Nominatim Geocoding failed: {}. Using city fallback.", e.getMessage());
    }

    return getCityFallback(city);
  }

  private Coordinate getCityFallback(String city) {
    if (city == null) return new Coordinate(1.2136, -77.2811);
    if (city.equalsIgnoreCase("Medellín") || city.equalsIgnoreCase("Medellin")) return new Coordinate(6.2442, -75.5812);
    if (city.equalsIgnoreCase("Ipiales")) return new Coordinate(0.8286, -77.6397);
    if (city.equalsIgnoreCase("Tumaco")) return new Coordinate(1.8066, -78.7646);
    if (city.equalsIgnoreCase("Bogotá") || city.equalsIgnoreCase("Bogota")) return new Coordinate(4.6097, -74.0817);
    return new Coordinate(1.2136, -77.2811); // Pasto default
  }

  @Override
  @Cacheable(value = "distances", key = "#origin.lat + ',' + #origin.lng + '|' + #destination.lat + ',' + #destination.lng")
  public Double calculateDistance(Coordinate origin, Coordinate destination) {
    if (origin == null || destination == null) return 1000.0;
    try {
      // OSRM Route: {lng},{lat};{lng},{lat}
      String url = String.format("%s/route/v1/driving/%f,%f;%f,%f?overview=false",
          osrmBaseUrl, origin.getLng(), origin.getLat(), destination.getLng(), destination.getLat());
      
      JsonNode node = restTemplate.getForObject(url, JsonNode.class);
      if (node != null && "Ok".equalsIgnoreCase(node.path("code").asText())) {
        JsonNode routes = node.path("routes");
        if (routes.isArray() && routes.size() > 0) {
          return routes.get(0).path("distance").asDouble(); // metros
        }
      }
    } catch (Exception e) {
      log.warn("⚠️ OSRM distance fallback to Haversine: {}", e.getMessage());
    }
    return haversineMeters(origin, destination);
  }

  @Override
  public List<String> suggestAddress(String text) {
    return Collections.emptyList();
  }

  @Override
  public long[][] getDistanceMatrix(List<Coordinate> points, String mode) {
    if (points == null || points.isEmpty()) return new long[0][0];
    int n = points.size();
    long[][] matrix = new long[n][n];

    if (n == 1) {
      matrix[0][0] = 0;
      return matrix;
    }

    try {
      // OSRM Table Service: /table/v1/driving/{lon1},{lat1};{lon2},{lat2}...
      StringBuilder coords = new StringBuilder();
      for (int i = 0; i < n; i++) {
        Coordinate p = points.get(i);
        coords.append(p.getLng()).append(",").append(p.getLat());
        if (i < n - 1) coords.append(";");
      }

      String url = String.format("%s/table/v1/driving/%s?annotations=duration", osrmBaseUrl, coords.toString());
      log.info("🌐 OSRM Table Matrix ({} points, mode: {})", n, mode);

      JsonNode response = restTemplate.getForObject(url, JsonNode.class);
      if (response != null && "Ok".equalsIgnoreCase(response.path("code").asText())) {
        JsonNode durations = response.path("durations");
        for (int i = 0; i < n; i++) {
          JsonNode row = durations.get(i);
          for (int j = 0; j < n; j++) {
            if (row != null && row.has(j) && !row.get(j).isNull()) {
              matrix[i][j] = Math.round(row.get(j).asDouble());
            } else {
              matrix[i][j] = (i == j) ? 0 : 999999L;
            }
          }
        }
        return matrix;
      }
    } catch (Exception e) {
      log.warn("⚠️ OSRM Table request failed ({}), falling back to Haversine matrix.", e.getMessage());
    }

    // Fallback: Haversine travel time (avg 30 km/h = 8.33 m/s)
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (i == j) {
          matrix[i][j] = 0;
        } else {
          double d = haversineMeters(points.get(i), points.get(j));
          matrix[i][j] = (long) (d / 8.33);
        }
      }
    }
    return matrix;
  }

  @Override
  public Double getTrafficFactor(String city) {
    return 1.0;
  }

  @Override
  public String getDirections(Coordinate origin, Coordinate destination, List<Coordinate> waypoints) {
    if (origin == null || destination == null) return "";
    try {
      StringBuilder sb = new StringBuilder();
      sb.append(origin.getLng()).append(",").append(origin.getLat()).append(";");
      if (waypoints != null) {
        for (Coordinate wp : waypoints) {
          if (wp != null) {
            sb.append(wp.getLng()).append(",").append(wp.getLat()).append(";");
          }
        }
      }
      sb.append(destination.getLng()).append(",").append(destination.getLat());

      String url = String.format("%s/route/v1/driving/%s?overview=full&geometries=polyline", osrmBaseUrl, sb.toString());
      JsonNode node = restTemplate.getForObject(url, JsonNode.class);
      if (node != null && "Ok".equalsIgnoreCase(node.path("code").asText())) {
        return node.path("routes").get(0).path("geometry").asText();
      }
    } catch (Exception e) {
      log.warn("⚠️ OSRM route polyline failed: {}", e.getMessage());
    }
    return "";
  }

  private double haversineMeters(Coordinate p1, Coordinate p2) {
    if (p1 == null || p2 == null || p1.getLat() == null || p2.getLat() == null) return 1000.0;
    double lat1 = Math.toRadians(p1.getLat());
    double lon1 = Math.toRadians(p1.getLng());
    double lat2 = Math.toRadians(p2.getLat());
    double lon2 = Math.toRadians(p2.getLng());
    double dlat = lat2 - lat1;
    double dlon = lon2 - lon1;
    double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return 6371000.0 * c;
  }
}
