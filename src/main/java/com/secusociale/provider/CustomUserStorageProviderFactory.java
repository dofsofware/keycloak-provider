package com.secusociale.provider;

import com.secusociale.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomUserStorageProviderFactory implements UserStorageProviderFactory<CustomUserStorageProvider> {

    private static final Logger logger = Logger.getLogger(CustomUserStorageProviderFactory.class);
    public static final String PROVIDER_NAME = "ipres-css-user-provider";

    // Configuration properties keys
    protected static final String CONFIG_CONNECTION_URL = "connectionUrl";
    protected static final String CONFIG_CONNECTION_USERNAME = "connectionUsername";
    protected static final String CONFIG_CONNECTION_PASSWORD = "connectionPassword";
    protected static final String CONFIG_CONNECTION_DRIVER = "connectionDriver";
    protected static final String CONFIG_PERSISTENCE_UNIT = "persistenceUnitName";

    // Default values
    private static final String DEFAULT_CONNECTION_URL = "jdbc:mysql://127.0.0.1:3306/cssipres_preprod?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC&createDatabaseIfNotExist=true";
    private static final String DEFAULT_CONNECTION_USERNAME = "suntel";
    private static final String DEFAULT_CONNECTION_PASSWORD = "suntel";
    private static final String DEFAULT_CONNECTION_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_PERSISTENCE_UNIT = "user-store";

    // Cache des EntityManagerFactory par configuration
    private static final Map<String, EntityManagerFactory> entityManagerFactories = new ConcurrentHashMap<>();

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
                .add()
                .property()
                .name(CONFIG_CONNECTION_DRIVER)
                .label("Database Driver")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Driver JDBC")
                .defaultValue(DEFAULT_CONNECTION_DRIVER)
                .add()
                .property()
                .name(CONFIG_PERSISTENCE_UNIT)
                .label("Persistence Unit Name")
                .type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Nom de l'unité de persistance JPA")
                .defaultValue(DEFAULT_PERSISTENCE_UNIT)
                .add()
                .build();
    }

    @Override
    public CustomUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.infof("Creating CustomUserStorageProvider instance for model: %s", model.getName());

        EntityManager entityManager = getEntityManager(model);
        return new CustomUserStorageProvider(session, model, (UserRepository) entityManager);
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }

    @Override
    public String getHelpText() {
        return "Custom User Storage Provider pour IPRES/CSS - Utilise la table jhi_user existante";
    }

    @Override
    public void close() {
        logger.info("Closing CustomUserStorageProviderFactory");
        entityManagerFactories.values().forEach(emf -> {
            if (emf != null && emf.isOpen()) {
                try {
                    emf.close();
                } catch (Exception e) {
                    logger.warnf(e, "Error closing EntityManagerFactory: %s", e.getMessage());
                }
            }
        });
        entityManagerFactories.clear();
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        logger.info("Initializing CustomUserStorageProviderFactory");
    }

    @Override
    public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {
        logger.info("Post-init CustomUserStorageProviderFactory");
    }

    private EntityManager getEntityManager(ComponentModel model) {
        String configKey = generateConfigKey(model);
        EntityManagerFactory emf = entityManagerFactories.computeIfAbsent(configKey, k -> createEntityManagerFactory(model));

        if (!emf.isOpen()) {
            entityManagerFactories.remove(configKey);
            emf = createEntityManagerFactory(model);
            entityManagerFactories.put(configKey, emf);
        }

        return emf.createEntityManager();
    }

    private String generateConfigKey(ComponentModel model) {
        String url = model.get(CONFIG_CONNECTION_URL, DEFAULT_CONNECTION_URL);
        String username = model.get(CONFIG_CONNECTION_USERNAME, DEFAULT_CONNECTION_USERNAME);
        String persistenceUnit = model.get(CONFIG_PERSISTENCE_UNIT, DEFAULT_PERSISTENCE_UNIT);

        return String.format("%s_%s_%s_%s", model.getId(), url, username, persistenceUnit);
    }

    private EntityManagerFactory createEntityManagerFactory(ComponentModel model) {
        logger.info("Creating EntityManagerFactory");

        String url = model.get(CONFIG_CONNECTION_URL, DEFAULT_CONNECTION_URL);
        String username = model.get(CONFIG_CONNECTION_USERNAME, DEFAULT_CONNECTION_USERNAME);
        String password = model.get(CONFIG_CONNECTION_PASSWORD, DEFAULT_CONNECTION_PASSWORD);
        String driver = model.get(CONFIG_CONNECTION_DRIVER, DEFAULT_CONNECTION_DRIVER);
        String persistenceUnit = model.get(CONFIG_PERSISTENCE_UNIT, DEFAULT_PERSISTENCE_UNIT);

        Map<String, Object> properties = new HashMap<>();

        // Configuration JDBC standard
        properties.put("jakarta.persistence.jdbc.url", url);
        properties.put("jakarta.persistence.jdbc.user", username);
        properties.put("jakarta.persistence.jdbc.password", password);
        properties.put("jakarta.persistence.jdbc.driver", driver);

        // Configuration Hibernate - Connexions de base
        properties.put("hibernate.connection.url", url);
        properties.put("hibernate.connection.username", username);
        properties.put("hibernate.connection.password", password);
        properties.put("hibernate.connection.driver_class", driver);

        // Configuration Hibernate spécifique
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.format_sql", "false");

        // Configuration des connexions Hibernate
        properties.put("hibernate.connection.pool_size", "10");
        properties.put("hibernate.connection.autocommit", "false");
        properties.put("hibernate.connection.isolation", "2"); // READ_COMMITTED
        properties.put("hibernate.connection.release_mode", "after_transaction");

        // Configuration du pool de connexions C3P0 (optionnel)
        properties.put("hibernate.c3p0.min_size", "5");
        properties.put("hibernate.c3p0.max_size", "20");
        properties.put("hibernate.c3p0.timeout", "300");
        properties.put("hibernate.c3p0.max_statements", "50");
        properties.put("hibernate.c3p0.idle_test_period", "3000");
        properties.put("hibernate.c3p0.acquire_increment", "2");
        properties.put("hibernate.c3p0.validate", "true");
        properties.put("hibernate.c3p0.preferredTestQuery", "SELECT 1");

        // Configuration pour MySQL spécifique
        properties.put("hibernate.connection.characterEncoding", "UTF-8");
        properties.put("hibernate.connection.useUnicode", "true");
        properties.put("hibernate.connection.zeroDateTimeBehavior", "convertToNull");
        properties.put("hibernate.connection.serverTimezone", "UTC");
        properties.put("hibernate.connection.allowPublicKeyRetrieval", "true");
        properties.put("hibernate.connection.useSSL", "false");

        // Configuration du cache
        properties.put("hibernate.cache.use_second_level_cache", "false");
        properties.put("hibernate.cache.use_query_cache", "false");

        // Configuration pour éviter les warnings et les problèmes de métadonnées
        properties.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        properties.put("hibernate.jdbc.lob.non_contextual_creation", "true");

        // IMPORTANT: Désactiver la validation automatique du schéma
        properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");

        // Configuration des requêtes et performances
        properties.put("hibernate.jdbc.batch_size", "25");
        properties.put("hibernate.order_inserts", "true");
        properties.put("hibernate.order_updates", "true");
        properties.put("hibernate.jdbc.batch_versioned_data", "true");

        // *** CORRECTION CRITIQUE: Configuration des transactions RESOURCE_LOCAL ***
        properties.put("hibernate.transaction.coordinator_class", "jdbc");
        properties.put("hibernate.current_session_context_class", "thread");
        properties.put("hibernate.transaction.flush_before_completion", "true");
        properties.put("hibernate.transaction.auto_close_session", "true");

        // IMPORTANT: Éviter JTA dans Keycloak
        properties.put("hibernate.transaction.jta.platform", "none");

        // Logging et debug
        properties.put("hibernate.generate_statistics", "false");
        properties.put("hibernate.use_sql_comments", "false");

        // Configuration pour éviter les problèmes de compatibilité
        properties.put("hibernate.id.new_generator_mappings", "true");
        properties.put("hibernate.jdbc.use_streams_for_binary", "true");

        try {
            EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnit, properties);
            logger.infof("EntityManagerFactory created successfully for database: %s", url);

            // Test de connexion simplifié
            try (EntityManager testEm = emf.createEntityManager()) {
                testEm.getTransaction().begin();
                testEm.getTransaction().rollback();
                logger.info("Database connection test successful");
            }

            return emf;
        } catch (Exception e) {
            logger.errorf(e, "Failed to create EntityManagerFactory for database: %s", url);
            throw new RuntimeException("Cannot create EntityManagerFactory: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        logger.info("Validating configuration for CustomUserStorageProvider");

        try {
            // Test de création de l'EntityManagerFactory
            EntityManagerFactory testEMF = createEntityManagerFactory(model);

            try (EntityManager testEM = testEMF.createEntityManager()) {
                // Test simple d'une requête
                testEM.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult();
                logger.info("Configuration validation successful");
            } finally {
                testEMF.close();
            }
        } catch (Exception e) {
            logger.errorf(e, "Configuration validation failed: %s", e.getMessage());
            throw new RuntimeException("Configuration validation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        logger.info("Configuration updated, clearing EntityManagerFactory cache");

        String oldConfigKey = generateConfigKey(oldModel);
        EntityManagerFactory oldEmf = entityManagerFactories.remove(oldConfigKey);

        if (oldEmf != null && oldEmf.isOpen()) {
            try {
                oldEmf.close();
                logger.info("Old EntityManagerFactory closed successfully");
            } catch (Exception e) {
                logger.warnf(e, "Error closing old EntityManagerFactory: %s", e.getMessage());
            }
        }
    }
}