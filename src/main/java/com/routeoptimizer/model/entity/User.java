package com.routeoptimizer.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeoptimizer.model.enums.Role;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Collection;
import java.util.List;

/**
 * Entidad base que representa un Usuario en el sistema.
 * Implementa UserDetails para ser integrada con Spring Security.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("USER")
@SQLDelete(sql = "UPDATE users SET enabled = false WHERE id = ?")
@SQLRestriction("enabled = true")
public class User implements UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "phone")
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "assigned_city")
  private String assignedCity;

  @Column(name = "pending_password_reset")
  private Boolean pendingPasswordReset = false;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Column(name = "pending_password_hash")
  private String pendingPasswordHash;

  @Column(name = "enabled")
  private boolean enabled = true;

  public User() {
  }

  // Métodos de UserDetails
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  // Getters y Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public String getAssignedCity() {
    return assignedCity;
  }

  public void setAssignedCity(String assignedCity) {
    this.assignedCity = assignedCity;
  }

  public Boolean getPendingPasswordReset() {
    return pendingPasswordReset;
  }

  public void setPendingPasswordReset(Boolean pendingPasswordReset) {
    this.pendingPasswordReset = pendingPasswordReset;
  }

  public String getPendingPasswordHash() {
    return pendingPasswordHash;
  }

  public void setPendingPasswordHash(String pendingPasswordHash) {
    this.pendingPasswordHash = pendingPasswordHash;
  }

  public boolean isEnabledValue() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
