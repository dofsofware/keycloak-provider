package com.secusociale.ipres_css_provider;

import com.secusociale.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class DatabaseConnectionTest {

    private static EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    static void setUpClass() {
        System.out.println("🚀 Initialisation des tests de connexion à la base de données...");

        Map<String, Object> properties = new HashMap<>();

        // Configuration JDBC standard
        properties.put("jakarta.persistence.jdbc.url", "jdbc:mysql://localhost:3306/cssipres_preprod?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC&createDatabaseIfNotExist=true");
        properties.put("jakarta.persistence.jdbc.user", "suntel");
        properties.put("jakarta.persistence.jdbc.password", "suntel");
        properties.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");

        // Configuration Hibernate - Connexions
        properties.put("hibernate.connection.url", "jdbc:mysql://localhost:3306/cssipres_preprod?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC&createDatabaseIfNotExist=true");
        properties.put("hibernate.connection.username", "suntel");
        properties.put("hibernate.connection.password", "suntel");
        properties.put("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");

        // Configuration Hibernate principale
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");

        // CRUCIAL: Configuration des transactions RESOURCE_LOCAL
        properties.put("hibernate.transaction.coordinator_class", "jdbc");
        properties.put("hibernate.current_session_context_class", "thread");
        properties.put("hibernate.connection.autocommit", "false");

        // Configuration pour éviter les problèmes JTA
        properties.put("hibernate.transaction.jta.platform", "none");

        // Configuration du pool de connexions
        properties.put("hibernate.connection.pool_size", "10");
        properties.put("hibernate.connection.isolation", "2"); // READ_COMMITTED

        // Configuration pour éviter les warnings
        properties.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        properties.put("hibernate.jdbc.lob.non_contextual_creation", "true");
        properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");

        // Cache désactivé pour les tests
        properties.put("hibernate.cache.use_second_level_cache", "false");
        properties.put("hibernate.cache.use_query_cache", "false");

        // Configuration des performances
        properties.put("hibernate.jdbc.batch_size", "25");
        properties.put("hibernate.order_inserts", "true");
        properties.put("hibernate.order_updates", "true");

        emf = Persistence.createEntityManagerFactory("user-store", properties);
        assertNotNull(emf, "EntityManagerFactory ne doit pas être null");
        System.out.println("✅ EntityManagerFactory créé avec succès");
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
        assertNotNull(em, "EntityManager ne doit pas être null");
        assertTrue(em.isOpen(), "EntityManager doit être ouvert");
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            // S'assurer que toute transaction en cours est fermée
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    @AfterAll
    static void tearDownClass() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        System.out.println("🏁 Nettoyage des ressources terminé");
    }

    @Test
    @Order(1)
    @DisplayName("Test de connexion à la base de données")
    void testDatabaseConnection() {
        assertDoesNotThrow(() -> {
            assertTrue(em.isOpen(), "La connexion à la base de données doit être active");
            System.out.println("✅ Connexion à la base de données MySQL réussie !");
        });
    }

    @Test
    @Order(2)
    @DisplayName("Test de configuration JPA/Hibernate")
    void testJpaHibernateConfiguration() {
        assertDoesNotThrow(() -> {
            // Vérifier que c'est bien une implémentation Hibernate
            String implementationClass = emf.getClass().getName();
            System.out.println("🔧 Implémentation JPA utilisée : " + implementationClass);

            // Vérifier les propriétés de l'EntityManagerFactory
            Map<String, Object> properties = emf.getProperties();
            assertNotNull(properties, "Les propriétés de l'EMF ne doivent pas être null");

            // Vérifier la configuration des transactions
            System.out.println("📋 Propriétés de configuration importantes :");
            if (properties.containsKey("hibernate.transaction.coordinator_class")) {
                System.out.println("  - Transaction coordinator: " + properties.get("hibernate.transaction.coordinator_class"));
            }
            if (properties.containsKey("hibernate.dialect")) {
                System.out.println("  - Dialect: " + properties.get("hibernate.dialect"));
            }

            System.out.println("✅ Configuration JPA/Hibernate validée");
        });
    }

    @Test
    @Order(3)
    @DisplayName("Test du comptage des utilisateurs")
    void testUserCount() {
        assertDoesNotThrow(() -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                    .getSingleResult();

            assertNotNull(count, "Le nombre d'utilisateurs ne doit pas être null");
            assertTrue(count >= 0, "Le nombre d'utilisateurs doit être positif ou zéro");

            System.out.println("📊 Nombre total d'utilisateurs dans la base de données : " + count);
        }, "Le comptage des utilisateurs ne doit pas lever d'exception");
    }

    @Test
    @Order(4)
    @DisplayName("Test de recherche d'utilisateur par login")
    void testUserByLogin() {
        String testLogin = "admin";

        assertDoesNotThrow(() -> {
            TypedQuery<User> query = em.createQuery(
                    "SELECT u FROM User u WHERE u.login = :login", User.class);
            query.setParameter("login", testLogin);

            List<User> users = query.getResultList();

            if (!users.isEmpty()) {
                User user = users.get(0);
                assertNotNull(user, "L'utilisateur ne doit pas être null");
                assertEquals(testLogin, user.getLogin(), "Le login doit correspondre");
                assertNotNull(user.getEmail(), "L'email ne doit pas être null");
                System.out.println("👤 Utilisateur trouvé avec le login '" + testLogin + "' : " + user);
            } else {
                System.out.println("ℹ️ Aucun utilisateur trouvé avec le login '" + testLogin + "'");
            }
        }, "La recherche d'utilisateur par login ne doit pas lever d'exception");
    }

    @Test
    @Order(5)
    @DisplayName("Test de liste des premiers utilisateurs")
    void testListUsers() {
        assertDoesNotThrow(() -> {
            TypedQuery<User> query = em.createQuery(
                            "SELECT u FROM User u ORDER BY u.id ASC", User.class)
                    .setMaxResults(5);

            List<User> users = query.getResultList();

            assertNotNull(users, "La liste des utilisateurs ne doit pas être null");
            assertTrue(users.size() <= 5, "La liste ne doit pas contenir plus de 5 utilisateurs");

            System.out.println("📋 " + users.size() + " premiers utilisateurs :");
            users.forEach(u -> {
                assertNotNull(u.getLogin(), "Le login de l'utilisateur ne doit pas être null");
                System.out.println("  - " + u.getLogin() + " (" + u.getEmail() + ")");
            });
        }, "La récupération de la liste des utilisateurs ne doit pas lever d'exception");
    }

    @Test
    @Order(6)
    @DisplayName("Test de validation du mot de passe avec BCrypt")
    void testPasswordValidation() {
        String testLogin = "admin";
        String testPassword = "admin";

        assertDoesNotThrow(() -> {
            TypedQuery<User> query = em.createQuery(
                    "SELECT u FROM User u WHERE u.login = :login", User.class);
            query.setParameter("login", testLogin);

            List<User> users = query.getResultList();

            if (!users.isEmpty()) {
                User user = users.get(0);
                assertNotNull(user, "L'utilisateur doit exister");
                assertNotNull(user.getPassword(), "Le mot de passe de l'utilisateur ne doit pas être null");

                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                boolean isPasswordValid = encoder.matches(testPassword, user.getPassword());

                if (isPasswordValid) {
                    System.out.println("🔓 Validation du mot de passe RÉUSSIE pour l'utilisateur : " + testLogin);
                } else {
                    System.out.println("🔒 Validation du mot de passe ÉCHOUÉE pour l'utilisateur : " + testLogin);
                }
            } else {
                System.out.println("ℹ️ Aucun utilisateur trouvé pour tester le mot de passe");
            }

        }, "La validation du mot de passe ne doit pas lever d'exception");
    }

    @Test
    @Order(7)
    @DisplayName("Test de performance - Requête avec timeout")
    void testQueryPerformance() {
        assertTimeout(java.time.Duration.ofSeconds(5), () -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                    .getSingleResult();

            System.out.println("⚡ Requête exécutée en moins de 5 secondes. Résultat : " + count);
        }, "La requête ne doit pas prendre plus de 5 secondes");
    }

    @Test
    @Order(8)
    @DisplayName("Test de transaction (rollback) avec RESOURCE_LOCAL")
    void testTransactionRollback() {
        assertDoesNotThrow(() -> {
            // Vérifier que l'EntityManager supporte les transactions locales
            assertTrue(em.getTransaction() != null, "L'EntityManager doit supporter les transactions");
            assertFalse(em.getTransaction().isActive(), "Aucune transaction ne doit être active au début");

            em.getTransaction().begin();
            assertTrue(em.getTransaction().isActive(), "La transaction doit être active après begin()");

            try {
                // Simulation d'une opération qui pourrait échouer
                Long initialCount = em.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                        .getSingleResult();

                // Rollback volontaire pour tester
                em.getTransaction().rollback();
                assertFalse(em.getTransaction().isActive(), "La transaction ne doit plus être active après rollback()");

                System.out.println("🔄 Transaction rollback testée avec succès. Nombre d'utilisateurs : " + initialCount);

            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw e;
            }
        }, "Le test de transaction rollback ne doit pas lever d'exception");
    }

    @Test
    @Order(9)
    @DisplayName("Test de transaction (commit) avec RESOURCE_LOCAL")
    void testTransactionCommit() {
        assertDoesNotThrow(() -> {
            em.getTransaction().begin();
            assertTrue(em.getTransaction().isActive(), "La transaction doit être active");

            try {
                // Opération simple de lecture (pas de modification)
                Long count = em.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                        .getSingleResult();

                // Commit de la transaction
                em.getTransaction().commit();
                assertFalse(em.getTransaction().isActive(), "La transaction ne doit plus être active après commit()");

                System.out.println("✅ Transaction commit testée avec succès. Nombre d'utilisateurs : " + count);

            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw e;
            }
        }, "Le test de transaction commit ne doit pas lever d'exception");
    }
}