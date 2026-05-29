package com.routeoptimizer.controller;

import com.routeoptimizer.dto.AuthenticationResponse;
import com.routeoptimizer.dto.LoginRequest;
import com.routeoptimizer.dto.RegisterRequest;
import com.routeoptimizer.dto.PasswordResetRequest;
import com.routeoptimizer.service.AuthService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Controlador para gestionar el registro y la autenticación de usuarios.
 */
@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Endpoint para registrar un nuevo usuario en el sistema.
   */
  @PostMapping("/register")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'LOGISTICS')")
  public ResponseEntity<AuthenticationResponse> register(@jakarta.validation.Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
  }

  /**
   * Endpoint para iniciar sesión y obtener un JWT.
   */
  @PostMapping("/login")
  public ResponseEntity<AuthenticationResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  /**
   * Endpoint para restablecer la contraseña validando el correo y el número de teléfono.
   */
  @PostMapping("/reset-password")
  public ResponseEntity<java.util.Map<String, String>> resetPassword(@jakarta.validation.Valid @RequestBody PasswordResetRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(java.util.Map.of("message", "Contraseña restablecida con éxito"));
  }
}
