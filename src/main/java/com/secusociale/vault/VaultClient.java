package com.secusociale.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Client pour interagir avec HashiCorp Vault
 * Récupère les credentials de base de données de manière sécurisée
 */
public class VaultClient {

    private static final Logger logger = Logger.getLogger(VaultClient.class);
    
    private final String vaultUrl;
    private final String vaultToken;
    private final ObjectMapper objectMapper;
    
    // Cache pour éviter les appels répétés à Vault
    private static final Map<String, VaultCredentials> credentialsCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    public VaultClient(String vaultUrl, String vaultToken) {
        this.vaultUrl = vaultUrl;
        this.vaultToken = vaultToken;
        this.objectMapper = new ObjectMapper();
        logger.infof("🔐 VaultClient initialisé avec URL: %s", vaultUrl);
    }
    
    /**
     * Récupère les credentials de base de données depuis Vault
     */
    public VaultCredentials getDatabaseCredentials(String secretPath) {
        logger.debugf("🔍 Récupération des credentials depuis Vault: %s", secretPath);
        
        // Vérifier le cache d'abord
        VaultCredentials cached = getCachedCredentials(secretPath);
        if (cached != null && !cached.isExpired()) {
            logger.debugf("✅ Credentials trouvés dans le cache pour: %s", secretPath);
            return cached;
        }
        
        try {
            String fullUrl = vaultUrl + "/v1/" + secretPath;
            logger.debugf("🌐 Appel Vault: %s", fullUrl);
            
            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Configuration de la requête
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-Vault-Token", vaultToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(10000); // 10 secondes
            connection.setReadTimeout(10000); // 10 secondes
            
            int responseCode = connection.getResponseCode();
            logger.debugf("📡 Code de réponse Vault: %d", responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Lire la réponse
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Parser la réponse JSON
                VaultCredentials credentials = parseVaultResponse(response.toString());
                
                // Mettre en cache
                cacheCredentials(secretPath, credentials);
                
                logger.infof("✅ Credentials récupérés avec succès depuis Vault pour: %s", secretPath);
                return credentials;
                
            } else {
                // Lire l'erreur
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
                
                logger.errorf("❌ Erreur Vault (code %d): %s", responseCode, errorResponse.toString());
                throw new VaultException("Erreur lors de l'accès à Vault: " + responseCode + " - " + errorResponse.toString());
            }
            
        } catch (IOException e) {
            logger.errorf(e, "❌ Erreur de connexion à Vault: %s", e.getMessage());
            throw new VaultException("Impossible de se connecter à Vault", e);
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur inattendue lors de l'accès à Vault: %s", e.getMessage());
            throw new VaultException("Erreur inattendue lors de l'accès à Vault", e);
        }
    }
    
    /**
     * Parse la réponse JSON de Vault
     */
    private VaultCredentials parseVaultResponse(String jsonResponse) throws Exception {
        logger.debugf("🔍 Parsing de la réponse Vault: %s", 
            jsonResponse.length() > 100 ? jsonResponse.substring(0, 100) + "..." : jsonResponse);
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode dataNode = rootNode.path("data").path("data");
            
            if (dataNode.isMissingNode()) {
                throw new VaultException("Structure de réponse Vault invalide: pas de nœud data.data");
            }
            
            String url = dataNode.path("ndamli_db_backend_url").asText();
            String username = dataNode.path("ndamli_db_backend_username").asText();
            String password = dataNode.path("ndamli_db_backend_password").asText();
            
            if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                throw new VaultException("Credentials incomplets dans la réponse Vault");
            }
            
            logger.debugf("✅ Credentials parsés - URL: %s, Username: %s", 
                url.substring(0, Math.min(50, url.length())) + "...", username);
            
            return new VaultCredentials(url, username, password);
            
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors du parsing de la réponse Vault: %s", e.getMessage());
            throw new VaultException("Impossible de parser la réponse Vault", e);
        }
    }
    
    /**
     * Récupère les credentials depuis le cache
     */
    private VaultCredentials getCachedCredentials(String secretPath) {
        synchronized (credentialsCache) {
            return credentialsCache.get(secretPath);
        }
    }
    
    /**
     * Met en cache les credentials
     */
    private void cacheCredentials(String secretPath, VaultCredentials credentials) {
        synchronized (credentialsCache) {
            credentialsCache.put(secretPath, credentials);
            logger.debugf("💾 Credentials mis en cache pour: %s", secretPath);
        }
    }
    
    /**
     * Nettoie le cache des entrées expirées
     */
    public static void cleanExpiredCache() {
        synchronized (credentialsCache) {
            credentialsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
    
    /**
     * Vide complètement le cache (utile pour les tests)
     */
    public static void clearCache() {
        synchronized (credentialsCache) {
            credentialsCache.clear();
            logger.debug("🧹 Cache Vault vidé");
        }
    }
    
    /**
     * Test de connectivité avec Vault
     */
    public boolean testConnection() {
        try {
            logger.info("🧪 Test de connexion à Vault...");
            
            URL url = new URL(vaultUrl + "/v1/sys/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            boolean isHealthy = responseCode == 200 || responseCode == 429; // 429 = sealed but responsive
            
            logger.infof("🏥 Santé de Vault: %s (code: %d)", isHealthy ? "OK" : "KO", responseCode);
            return isHealthy;
            
        } catch (Exception e) {
            logger.warnf(e, "⚠️ Test de connexion Vault échoué: %s", e.getMessage());
            return false;
        }
    }
}