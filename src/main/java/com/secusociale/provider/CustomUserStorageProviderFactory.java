package com.secusociale.provider;

import com.secusociale.repository.UserRepository;
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
    protected static final String CONFIG_CONNECTION_URL = "connectionUrl";
    protected static final String CONFIG_CONNECTION_USERNAME = "connectionUsername";
    protected static final String CONFIG_CONNECTION_PASSWORD = "connectionPassword";
    protected static final String CONFIG_CONNECTION_DRIVER = "connectionDriver";

    // Default values
    private static final String DEFAULT_CONNECTION_URL = "jdbc:mysql://localhost:3306/cssipres_preprod?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC&createDatabaseIfNotExist=true";
    private static final String DEFAULT_CONNECTION_USERNAME = "suntel";
    private static final String DEFAULT_CONNECTION_PASSWORD = "suntel";
    private static final String DEFAULT_CONNECTION_DRIVER = "com.mysql.cj.jdbc.Driver";

    // Cache pour les DataSources
    private static final ConcurrentHashMap<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CONFIG_CONNECTION_URL)
                .label("Database URL")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("JDBC URL de la base de données")
                .defaultValue(DEFAULT_CONNECTION_URL)
                .add()
                .property()
                .name(CONFIG_CONNECTION_USERNAME)
                .label("Database Username")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Nom d'utilisateur de la base de données")
                .defaultValue(DEFAULT_CONNECTION_USERNAME)
                .add()
                .property()
                .name(CONFIG_CONNECTION_PASSWORD)
                .label("Database Password")
                .type(ProviderConfigProperty.PASSWORD)
                .helpText("Mot de passe de la base de données")
                .secret(true)
                .defaultValue(DEFAULT_CONNECTION_PASSWORD)
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
        logger.infof("Création d'une instance CustomUserStorageProvider pour le modèle: %s", model.getName());

        try {
            // Créer une DataSource pour la base de données externe
            DataSource dataSource = getOrCreateDataSource(model);
            UserRepository userRepository = new UserRepository(dataSource);

            CustomUserStorageProvider provider = new CustomUserStorageProvider(session, model, userRepository);
            logger.infof("CustomUserStorageProvider créé avec succès pour: %s", model.getName());

            return provider;
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la création du provider: %s", e.getMessage());
            throw new RuntimeException("Impossible de créer le CustomUserStorageProvider", e);
        }
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }

    @Override
    public String getHelpText() {
        return "Provider de stockage utilisateur personnalisé pour IPRES/CSS - Utilise la table jhi_user existante";
    }

    @Override
    public void close() {
        logger.info("Fermeture de CustomUserStorageProviderFactory");
        // Fermer toutes les DataSources en cache
        dataSourceCache.values().forEach(ds -> {
            try {
                if (ds instanceof com.zaxxer.hikari.HikariDataSource) {
                    ((com.zaxxer.hikari.HikariDataSource) ds).close();
                }
            } catch (Exception e) {
                logger.warnf(e, "Erreur lors de la fermeture de la DataSource: %s", e.getMessage());
            }
        });
        dataSourceCache.clear();
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        logger.info("Initialisation de CustomUserStorageProviderFactory");
    }

    @Override
    public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {
        logger.info("Post-initialisation de CustomUserStorageProviderFactory");
    }

    /**
     * Crée ou récupère une DataSource pour la configuration donnée
     */
    private DataSource getOrCreateDataSource(ComponentModel model) {
        String url = model.get(CONFIG_CONNECTION_URL, DEFAULT_CONNECTION_URL);
        String username = model.get(CONFIG_CONNECTION_USERNAME, DEFAULT_CONNECTION_USERNAME);
        String password = model.get(CONFIG_CONNECTION_PASSWORD, DEFAULT_CONNECTION_PASSWORD);

        String cacheKey = url + ":" + username;

        return dataSourceCache.computeIfAbsent(cacheKey, key -> {
            logger.infof("Création d'une nouvelle DataSource pour: %s", url);
            return createHikariDataSource(url, username, password);
        });
    }

    /**
     * Crée une DataSource HikariCP
     */
    private DataSource createHikariDataSource(String url, String username, String password) {
        try {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
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

            return new com.zaxxer.hikari.HikariDataSource(config);
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la création de la DataSource: %s", e.getMessage());
            throw new RuntimeException("Impossible de créer la DataSource", e);
        }
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        logger.info("Validation de la configuration pour CustomUserStorageProvider");

        try {
            // Test de connexion simple avec JDBC direct
            String url = model.get(CONFIG_CONNECTION_URL, DEFAULT_CONNECTION_URL);
            String username = model.get(CONFIG_CONNECTION_USERNAME, DEFAULT_CONNECTION_USERNAME);
            String password = model.get(CONFIG_CONNECTION_PASSWORD, DEFAULT_CONNECTION_PASSWORD);

            // Test de connexion JDBC basique
            testJdbcConnection(url, username, password);

            logger.info("Validation de la configuration réussie");
        } catch (Exception e) {
            logger.errorf(e, "Échec de validation de la configuration: %s", e.getMessage());
            throw new RuntimeException("Échec de validation de la configuration: " + e.getMessage(), e);
        }
    }

    private void testJdbcConnection(String url, String username, String password) {
        try {
            // Charger le driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Test de connexion simple
            try (Connection conn = java.sql.DriverManager.getConnection(url, username, password)) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM jhi_user")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long count = rs.getLong(1);
                            logger.infof("Test de connexion réussi. Nombre d'utilisateurs: %d", count);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.errorf(e, "Échec du test de connexion JDBC: %s", e.getMessage());
            throw new RuntimeException("Échec du test de connexion: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        logger.info("Configuration mise à jour pour CustomUserStorageProvider");

        // Invalider le cache de DataSource si la configuration a changé
        String oldUrl = oldModel.get(CONFIG_CONNECTION_URL, DEFAULT_CONNECTION_URL);
        String oldUsername = oldModel.get(CONFIG_CONNECTION_USERNAME, DEFAULT_CONNECTION_USERNAME);
        String newUrl = newModel.get(CONFIG_CONNECTION_URL, DEFAULT_CONNECTION_URL);
        String newUsername = newModel.get(CONFIG_CONNECTION_USERNAME, DEFAULT_CONNECTION_USERNAME);

        if (!oldUrl.equals(newUrl) || !oldUsername.equals(newUsername)) {
            String oldCacheKey = oldUrl + ":" + oldUsername;
            DataSource oldDataSource = dataSourceCache.remove(oldCacheKey);
            if (oldDataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                try {
                    ((com.zaxxer.hikari.HikariDataSource) oldDataSource).close();
                    logger.info("Ancienne DataSource fermée suite à la mise à jour de configuration");
                } catch (Exception e) {
                    logger.warnf(e, "Erreur lors de la fermeture de l'ancienne DataSource: %s", e.getMessage());
                }
            }
        }
    }
}