package com.routeoptimizer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.routeoptimizer.model.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);
  Optional<User> findByEmailIgnoreCase(String email);

  // Escalar nativo por ID: Resuelve el bug de Optional.empty() cuando la columna enabled es NULL en DB
  @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM users WHERE LOWER(email) = LOWER(?1) LIMIT 1", nativeQuery = true)
  Optional<Long> findPhysicalIdByEmail(String email);

  // Verificación robusta del flag, convirtiendo NULL a false a nivel de base de datos
  @org.springframework.data.jpa.repository.Query(value = "SELECT COALESCE(enabled, false) FROM users WHERE id = ?1", nativeQuery = true)
  boolean isPhysicalUserEnabled(Long id);
}
