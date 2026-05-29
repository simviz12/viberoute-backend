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
    @Transactional
    public void run(String... args) throws Exception {
        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🛡️ [SECURITY] Iniciando sincronización de cuenta maestra...");
            System.out.println("=".repeat(60));

            // PASO 1: Eliminar restricción de base de datos que bloquea SUPER_ADMIN
            // (PostgreSQL fix)
            try {
                jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            } catch (Exception sqlEx) {
                // Silencioso si ya se eliminó
            }

            // PASO 2: Sincronizar/Crear el Super Admin forzosamente
            String hashedMasterPass = passwordEncoder.encode(adminPassword);

            userRepository.findByEmail(adminEmail).ifPresentOrElse(
                    user -> {
                        user.setRole(Role.SUPER_ADMIN);
                        user.setName("Arquitecto Maestro (VibeRoute)");
                        user.setAssignedCity("Global");
                        userRepository.save(user);
                        System.out.println("✅ [SYNC] Cuenta maestra verificada (contraseña no modificada).");
                    },
                    () -> {
                        User superAdmin = new User();
                        superAdmin.setName("Arquitecto Maestro (VibeRoute)");
                        superAdmin.setEmail(adminEmail);
                        superAdmin.setPasswordHash(hashedMasterPass);
                        superAdmin.setRole(Role.SUPER_ADMIN);
                        superAdmin.setAssignedCity("Global");
                        superAdmin.setPhone("3000000000");
                        userRepository.save(superAdmin);
                        System.out.println("⭐ [SEEDER] Cuenta maestra CREADA desde cero.");
                    });
            System.out.println("=".repeat(60) + "\n");
        } catch (Exception e) {
            System.err.println("⚠️ [CRITICAL] Fallo en el Seeder de usuarios: " + e.getMessage());
            System.err.println("La aplicación continuará intentando arrancar...");
        }
    }
}
