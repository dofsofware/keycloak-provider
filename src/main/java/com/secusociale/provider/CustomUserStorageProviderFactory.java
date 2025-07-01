package com.secusociale.provider;

import com.secusociale.repository.UserRepository;
import com.secusociale.vault.VaultClient;
import com.secusociale.vault.VaultCredentials;
import com.secusociale.vault.VaultException;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CustomUserStorageProviderFactory implements UserStorageProviderFactory<CustomUserStorageProvider> {

    private static final Logger logger = Logger.getLogger(CustomUserStorageProviderFactory.class);
    public static final String PROVIDER_NAME = "ndamli-provider";

    // Configuration properties keys
    protected static final String CONFIG_VAULT_URL = "vaultUrl";
    protected static final String CONFIG_VAULT_TOKEN = "vaultToken";
    protected static final String CONFIG_VAULT_SECRET_PATH = "vaultSecretPath";
    protected static final String CONFIG_CONNECTION_DRIVER = "connectionDriver";

    // Default values
    private static final String DEFAULT_VAULT_URL = "http://localhost:8200";
    private static final String DEFAULT_VAULT_TOKEN = "hvs.hWP5WownECWEzuDigz3QRGfZ";
    private static final String DEFAULT_VAULT_SECRET_PATH = "secret/data/ndamli_db_access_dev";
    private static final String DEFAULT_CONNECTION_DRIVER = "com.mysql.cj.jdbc.Driver";

    // Cache pour les DataSources
    private static final ConcurrentHashMap<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();
    
    // Cache pour les clients Vault
    private static final ConcurrentHashMap<String, VaultClient> vaultClientCache = new ConcurrentHashMap<>();

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CONFIG_VAULT_URL)
                .label("Vault URL")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("URL du serveur HashiCorp Vault")
                .defaultValue(DEFAULT_VAULT_URL)
                .add()
                .property()
                .name(CONFIG_VAULT_TOKEN)
                .label("Vault Token")
                .type(ProviderConfigProperty.PASSWORD)
                .helpText("Token d'authentification Vault")
                .secret(true)
                .defaultValue(DEFAULT_VAULT_TOKEN)
                .add()
                .property()
                .name(CONFIG_VAULT_SECRET_PATH)
                .label("Vault Secret Path")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Chemin du secret dans Vault contenant les credentials DB")
                .defaultValue(DEFAULT_VAULT_SECRET_PATH)
                .add()
                .property()
                .name(CONFIG_CONNECTION_DRIVER)
                .label("Database Driver")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Driver JDBC")
                .defaultValue(DEFAULT_CONNECTION_DRIVER)
                .add()
                .build();
    }

    @Override
    public CustomUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.infof("🚀 Création d'une instance CustomUserStorageProvider pour le modèle: %s", model.getName());

        try {
            // Créer ou récupérer le client Vault
            VaultClient vaultClient = getOrCreateVaultClient(model);
            
            // Récupérer les credentials depuis Vault
            VaultCredentials credentials = getCredentialsFromVault(vaultClient, model);
            
            // Créer une DataSource avec les credentials Vault
            DataSource dataSource = getOrCreateDataSource(model, credentials);
            UserRepository userRepository = new UserRepository(dataSource);

            CustomUserStorageProvider provider = new CustomUserStorageProvider(session, model, userRepository);
            logger.infof("✅ CustomUserStorageProvider créé avec succès pour: %s", model.getName());

            return provider;
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la création du provider: %s", e.getMessage());
            throw new RuntimeException("Impossible de créer le CustomUserStorageProvider", e);
        }
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }

    @Override
    public String getHelpText() {
        return "Provider de stockage utilisateur personnalisé pour IPRES/CSS - Utilise HashiCorp Vault pour les credentials DB";
    }

    @Override
    public void close() {
        logger.info("🔒 Fermeture de CustomUserStorageProviderFactory");
        
        // Fermer toutes les DataSources en cache
        dataSourceCache.values().forEach(ds -> {
            try {
                if (ds instanceof com.zaxxer.hikari.HikariDataSource) {
                    ((com.zaxxer.hikari.HikariDataSource) ds).close();
                }
            } catch (Exception e) {
                logger.warnf(e, "⚠️ Erreur lors de la fermeture de la DataSource: %s", e.getMessage());
            }
        });
        dataSourceCache.clear();
        
        // Nettoyer les caches Vault
        vaultClientCache.clear();
        VaultClient.clearCache();
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        logger.info("🔧 Initialisation de CustomUserStorageProviderFactory avec support Vault");
    }

    @Override
    public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {
        logger.info("⚙️ Post-initialisation de CustomUserStorageProviderFactory");
    }

    /**
     * Crée ou récupère un client Vault pour la configuration donnée
     */
    private VaultClient getOrCreateVaultClient(ComponentModel model) {
        String vaultUrl = model.get(CONFIG_VAULT_URL, DEFAULT_VAULT_URL);
        String vaultToken = model.get(CONFIG_VAULT_TOKEN, DEFAULT_VAULT_TOKEN);
        
        String cacheKey = vaultUrl + ":" + vaultToken.hashCode(); // Hash du token pour la sécurité
        
        return vaultClientCache.computeIfAbsent(cacheKey, key -> {
            logger.infof("🔐 Création d'un nouveau VaultClient pour: %s", vaultUrl);
            return new VaultClient(vaultUrl, vaultToken);
        });
    }
    
    /**
     * Récupère les credentials depuis Vault
     */
    private VaultCredentials getCredentialsFromVault(VaultClient vaultClient, ComponentModel model) {
        String secretPath = model.get(CONFIG_VAULT_SECRET_PATH, DEFAULT_VAULT_SECRET_PATH);
        
        try {
            logger.infof("🔍 Récupération des credentials depuis Vault: %s", secretPath);
            VaultCredentials credentials = vaultClient.getDatabaseCredentials(secretPath);
            logger.infof("✅ Credentials récupérés avec succès depuis Vault (âge: %d secondes)", 
                credentials.getAgeInSeconds());
            return credentials;
        } catch (VaultException e) {
            logger.errorf(e, "❌ Erreur lors de la récupération des credentials Vault: %s", e.getMessage());
            throw new RuntimeException("Impossible de récupérer les credentials depuis Vault", e);
        }
    }

    /**
     * Crée ou récupère une DataSource pour la configuration donnée
     */
    private DataSource getOrCreateDataSource(ComponentModel model, VaultCredentials credentials) {
        String cacheKey = credentials.getUrl() + ":" + credentials.getUsername();

        return dataSourceCache.computeIfAbsent(cacheKey, key -> {
            logger.infof("💾 Création d'une nouvelle DataSource pour: %s", 
                credentials.getUrl().substring(0, Math.min(50, credentials.getUrl().length())) + "...");
            return createHikariDataSource(credentials);
        });
    }

    /**
     * Crée une DataSource HikariCP avec les credentials Vault
     */
    private DataSource createHikariDataSource(VaultCredentials credentials) {
        try {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setJdbcUrl(credentials.getUrl());
            config.setUsername(credentials.getUsername());
            config.setPassword(credentials.getPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Configuration du pool
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(60000);

            // Configuration MySQL spécifique
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

            logger.infof("✅ DataSource HikariCP créée avec succès");
            return new com.zaxxer.hikari.HikariDataSource(config);
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la création de la DataSource: %s", e.getMessage());
            throw new RuntimeException("Impossible de créer la DataSource", e);
        }
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        logger.info("🔍 Validation de la configuration pour CustomUserStorageProvider avec Vault");

        try {
            // 1. Test de connexion à Vault
            VaultClient vaultClient = getOrCreateVaultClient(model);
            
            if (!vaultClient.testConnection()) {
                throw new RuntimeException("Impossible de se connecter à Vault");
            }
            logger.info("✅ Connexion à Vault validée");
            
            // 2. Test de récupération des credentials
            VaultCredentials credentials = getCredentialsFromVault(vaultClient, model);
            logger.infof("✅ Credentials récupérés depuis Vault: %s", credentials);
            
            // 3. Test de connexion à la base de données
            testDatabaseConnection(credentials);
            logger.info("✅ Connexion à la base de données validée");
            
            logger.info("🎉 Validation de la configuration réussie avec Vault");
        } catch (Exception e) {
            logger.errorf(e, "❌ Échec de validation de la configuration: %s", e.getMessage());
            throw new RuntimeException("Échec de validation de la configuration: " + e.getMessage(), e);
        }
    }

    private void testDatabaseConnection(VaultCredentials credentials) {
        try {
            // Charger le driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Test de connexion simple
            try (Connection conn = java.sql.DriverManager.getConnection(
                    credentials.getUrl(), credentials.getUsername(), credentials.getPassword())) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM jhi_user")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long count = rs.getLong(1);
                            logger.infof("✅ Test de connexion DB réussi. Nombre d'utilisateurs: %d", count);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.errorf(e, "❌ Échec du test de connexion DB: %s", e.getMessage());
            throw new RuntimeException("Échec du test de connexion DB: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        logger.info("🔄 Configuration mise à jour pour CustomUserStorageProvider");

        // Vérifier si la configuration Vault a changé
        String oldVaultUrl = oldModel.get(CONFIG_VAULT_URL, DEFAULT_VAULT_URL);
        String oldVaultToken = oldModel.get(CONFIG_VAULT_TOKEN, DEFAULT_VAULT_TOKEN);
        String oldSecretPath = oldModel.get(CONFIG_VAULT_SECRET_PATH, DEFAULT_VAULT_SECRET_PATH);
        
        String newVaultUrl = newModel.get(CONFIG_VAULT_URL, DEFAULT_VAULT_URL);
        String newVaultToken = newModel.get(CONFIG_VAULT_TOKEN, DEFAULT_VAULT_TOKEN);
        String newSecretPath = newModel.get(CONFIG_VAULT_SECRET_PATH, DEFAULT_VAULT_SECRET_PATH);

        boolean vaultConfigChanged = !oldVaultUrl.equals(newVaultUrl) || 
                                   !oldVaultToken.equals(newVaultToken) ||
                                   !oldSecretPath.equals(newSecretPath);

        if (vaultConfigChanged) {
            logger.info("🔄 Configuration Vault modifiée, nettoyage des caches...");
            
            // Nettoyer les caches
            String oldVaultCacheKey = oldVaultUrl + ":" + oldVaultToken.hashCode();
            vaultClientCache.remove(oldVaultCacheKey);
            VaultClient.clearCache();
            
            // Nettoyer les DataSources (elles seront recréées avec les nouveaux credentials)
            dataSourceCache.values().forEach(ds -> {
                try {
                    if (ds instanceof com.zaxxer.hikari.HikariDataSource) {
                        ((com.zaxxer.hikari.HikariDataSource) ds).close();
                    }
                } catch (Exception e) {
                    logger.warnf(e, "⚠️ Erreur lors de la fermeture de l'ancienne DataSource: %s", e.getMessage());
                }
            });
            dataSourceCache.clear();
            
            logger.info("✅ Caches nettoyés suite à la mise à jour de configuration Vault");
        }
    }
}