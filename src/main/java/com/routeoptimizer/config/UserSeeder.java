package com.routeoptimizer.config;

import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincronizador Maestro de Usuarios y Esquema.
 * Forzado para ejecutarse al final del arranque de la aplicación.
 * Repara la base de datos eliminando restricciones antiguas que bloquean el
 * Super Admin.
 */
@Component
public class UserSeeder implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Value("${ADMIN_EMAIL}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void run(String... args) throws Exception {
        try {
            log.info("=".repeat(60));
            log.info("🛡️ [SECURITY] Iniciando sincronización de cuenta maestra...");
            log.info("📧 [SYNC] Email detectado: {}", adminEmail);
            log.info("=".repeat(60));

            // PASO 1: Eliminar restricción de base de datos que bloquea SUPER_ADMIN
            // (PostgreSQL fix)
            try {
                jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            } catch (Exception sqlEx) {
                // Silencioso si ya se eliminó
            }

            // PASO 2: Sincronizar/Crear el Super Admin forzosamente
            String hashedMasterPass = passwordEncoder.encode(adminPassword);

            // Única consulta escalar 100% segura basada en ID
            java.util.Optional<Long> physicalIdOpt = userRepository.findPhysicalIdByEmail(adminEmail);

            if (physicalIdOpt.isPresent()) {
                Long userId = physicalIdOpt.get();
                boolean isEnabled = userRepository.isPhysicalUserEnabled(userId);

                // Si está eliminado lógicamente (soft-delete), abortamos la inserción y evitamos conflictos
                if (!isEnabled) {
                    log.warn("⚠️ [SYNC] La cuenta maestra existe (ID: {}) pero está deshabilitada (soft-deleted). Se omite creación/actualización.", userId);
                    log.info("=".repeat(60));
                    return;
                }

                // Si existe físicamente y ESTÁ habilitado, ahora SÍ usamos JPQL seguro para obtener la entidad polimórfica
                User user = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow(() -> 
                    new IllegalStateException("Inconsistencia en BD: Usuario existe y está activo físicamente, pero JPQL no lo encontró."));

                // Conservamos la lógica actual de actualizar sus credenciales
                user.setRole(Role.SUPER_ADMIN);
                user.setPasswordHash(hashedMasterPass);
                user.setName("Arquitecto Maestro (VibeRoute)");
                user.setAssignedCity("Global");
                userRepository.save(user);
                log.info("✅ [SYNC] Cuenta maestra ACTUALIZADA con credenciales del .env.");
            } else {
                // Si no existe físicamente en lo absoluto, es 100% seguro hacer el insert.
                User superAdmin = new User();
                superAdmin.setName("Arquitecto Maestro (VibeRoute)");
                superAdmin.setEmail(adminEmail);
                superAdmin.setPasswordHash(hashedMasterPass);
                superAdmin.setRole(Role.SUPER_ADMIN);
                superAdmin.setAssignedCity("Global");
                superAdmin.setPhone("3000000000");
                userRepository.save(superAdmin);
                log.info("⭐ [SEEDER] Cuenta maestra CREADA desde cero.");
            }
            log.info("=".repeat(60));
        } catch (Exception e) {
            // Protección defensiva extrema: El Seeder JAMÁS debe tumbar la aplicación
            log.error("⚠️ [CRITICAL] Fallo inesperado en el Seeder de usuarios: {}", e.getMessage(), e);
            log.error("La aplicación ignorará este fallo y continuará su arranque con normalidad...");
        }
    }
}
