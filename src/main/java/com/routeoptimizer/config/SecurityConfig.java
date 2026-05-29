package com.routeoptimizer.config;

import com.routeoptimizer.security.JwtAuthenticationEntryPoint;
import com.routeoptimizer.security.JwtAuthenticationFilter;
import com.routeoptimizer.security.CustomAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de seguridad de Spring Security.
 * Define la cadena de filtros, políticas de sesión y permisos por rol.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;
  private final AuthenticationProvider authenticationProvider;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final CustomAccessDeniedHandler customAccessDeniedHandler;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, 
                        AuthenticationProvider authenticationProvider,
                        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                        CustomAccessDeniedHandler customAccessDeniedHandler) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.authenticationProvider = authenticationProvider;
    this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    this.customAccessDeniedHandler = customAccessDeniedHandler;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/login", "/api/v1/auth/reset-password", "/api/v1/ping", "/error", "/ws-alertas/**", "/auth/login").permitAll()
            // Handshake SockJS público; el JWT se valida en STOMP CONNECT (WebSocketAuthChannelInterceptor)
            .requestMatchers("/api/v1/auth/register", "/auth/register").hasAnyRole("SUPER_ADMIN", "ADMIN", "LOGISTICS")
            .requestMatchers("/api/v1/system/**", "/system/**").hasRole("SUPER_ADMIN")
            
            // Specific DRIVER actions
            .requestMatchers(HttpMethod.PATCH, "/api/v1/drivers/*/status", "/drivers/*/status").hasAnyRole("DRIVER", "ADMIN", "SUPER_ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/status", "/orders/*/status").hasAnyRole("DRIVER", "LOGISTICS", "ADMIN", "SUPER_ADMIN")
            
            // Shared Driver management
            .requestMatchers("/api/v1/drivers", "/api/v1/drivers/**", "/drivers", "/drivers/**").hasAnyRole("ADMIN", "LOGISTICS", "SUPER_ADMIN")
            
            // Shared Logistics/Admin access
            .requestMatchers("/api/v1/orders", "/api/v1/orders/**", "/orders", "/orders/**",
                             "/api/v1/stats", "/api/v1/stats/**", "/stats", "/stats/**",
                             "/api/v1/reports", "/api/v1/reports/**", "/reports", "/reports/**",
                             "/api/v1/batches", "/api/v1/batches/**", "/batches", "/batches/**").hasAnyRole("DRIVER", "LOGISTICS", "ADMIN", "SUPER_ADMIN")
            
            // Operational monitoring (Explicitly permissive for testing)
            .requestMatchers("/api/v1/batches/**", "/batches/**",
                             "/api/v1/routes/**", "/routes/**",
                             "/api/v1/locations/**", "/locations/**").hasAnyRole("DRIVER", "LOGISTICS", "ADMIN", "SUPER_ADMIN")
            
            .requestMatchers("/api/v1/ai/**").authenticated()
            .anyRequest().authenticated())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            .accessDeniedHandler(customAccessDeniedHandler))
        .authenticationProvider(authenticationProvider)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
    org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
    // Restringir a dominios específicos en producción. Para desarrollo, listamos los permitidos explícitamente.
    configuration.setAllowedOrigins(java.util.List.of(
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173")); 
    configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
    configuration.setExposedHeaders(java.util.List.of("Authorization"));
    configuration.setAllowCredentials(true);
    
    org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
