package com.routeoptimizer.config;
 
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;

/**
 * Configuration to initialize Firebase Admin SDK.
 * Supports loading credentials from a configurable file path (local/volumes)
 * or via a Base64-encoded environment variable (PaaS/Docker without volumes).
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${FIREBASE_CREDENTIALS_PATH:serviceAccountKey.json}")
    private String credentialsPath;

    @Value("${FIREBASE_CREDENTIALS_BASE64:}")
    private String credentialsBase64;

    @PostConstruct
    public void initialize() {
        try {
            InputStream serviceAccount;

            // Estrategia 1: Carga en memoria vía variable de entorno (Seguro para Docker/PaaS)
            if (credentialsBase64 != null && !credentialsBase64.trim().isEmpty()) {
                byte[] decodedBytes = Base64.getDecoder().decode(credentialsBase64);
                serviceAccount = new ByteArrayInputStream(decodedBytes);
                log.info("🔥 [FIREBASE] Cargando credenciales desde variable de entorno Base64.");
            } 
            // Estrategia 2: Carga por archivo tradicional (Local dev o Docker Volumes)
            else {
                serviceAccount = new FileInputStream(credentialsPath);
                log.info("🔥 [FIREBASE] Cargando credenciales desde archivo: {}", credentialsPath);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("✅ [FIREBASE] Admin SDK inicializado exitosamente.");
            }
        } catch (Exception e) {
            // Logueamos como warn para no tumbar la app si no hay Firebase configurado
            log.warn("⚠️ [FIREBASE] No se pudo inicializar el SDK. Las subidas fallarán. Causa: {}", e.getMessage());
        }
    }
}
