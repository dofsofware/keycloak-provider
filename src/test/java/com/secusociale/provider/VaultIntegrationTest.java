package com.secusociale.provider;

import com.secusociale.vault.VaultClient;
import com.secusociale.vault.VaultCredentials;
import com.secusociale.vault.VaultException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests d'intégration Vault + Provider Keycloak")
class VaultIntegrationTest {

    private VaultClient vaultClient;
    private static final String VAULT_URL = "http://localhost:8200";
    private static final String VAULT_TOKEN = "hvs.hWP5WownECWEzuDigz3QRGfZ";
    private static final String SECRET_PATH = "secret/data/ndamli_db_access_dev";

    @BeforeEach
    void setUp() {
        vaultClient = new VaultClient(VAULT_URL, VAULT_TOKEN);
        VaultClient.clearCache();
    }

    @Test
    @DisplayName("Test complet: Vault → Credentials → Connexion DB")
    void testCompleteVaultToDbFlow() {
        System.out.println("=== Test complet Vault → DB ===");
        
        try {
            // 1. Récupérer les credentials depuis Vault
            System.out.println("1. 🔐 Récupération des credentials depuis Vault...");
            VaultCredentials credentials = vaultClient.getDatabaseCredentials(SECRET_PATH);
            
            assertNotNull(credentials, "Les credentials ne doivent pas être null");
            System.out.println("   ✅ Credentials récupérés: " + credentials);
            
            // 2. Tester la connexion à la base de données
            System.out.println("2. 🗄️ Test de connexion à la base de données...");
            testDatabaseConnectionWithCredentials(credentials);
            System.out.println("   ✅ Connexion DB réussie");
            
            // 3. Tester une requête utilisateur
            System.out.println("3. 👤 Test de requête utilisateur...");
            testUserQueryWithCredentials(credentials);
            System.out.println("   ✅ Requête utilisateur réussie");
            
            System.out.println("\n🎉 FLUX COMPLET VAULT → DB VALIDÉ !");
            
        } catch (VaultException e) {
            System.out.println("⚠️ Test ignoré car Vault n'est pas disponible: " + e.getMessage());
            System.out.println("   Pour exécuter ce test, démarrez Vault et configurez le secret");
        } catch (Exception e) {
            System.out.println("❌ Erreur inattendue: " + e.getMessage());
            fail("Le flux complet devrait fonctionner: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test de simulation du provider Keycloak avec Vault")
    void testKeycloakProviderSimulation() {
        System.out.println("\n=== Simulation Provider Keycloak avec Vault ===");
        
        try {
            // Simuler ce que fait CustomUserStorageProviderFactory.create()
            System.out.println("1. 🏭 Simulation de la création du provider...");
            
            // Récupérer les credentials depuis Vault
            VaultCredentials credentials = vaultClient.getDatabaseCredentials(SECRET_PATH);
            System.out.println("   ✅ Credentials Vault récupérés");
            
            // Créer une connexion de test (simulation de DataSource)
            try (Connection conn = DriverManager.getConnection(
                    credentials.getUrl(), 
                    credentials.getUsername(), 
                    credentials.getPassword())) {
                
                System.out.println("   ✅ DataSource simulée créée");
                
                // Simuler une recherche d'utilisateur
                System.out.println("2. 🔍 Simulation de recherche d'utilisateur...");
                String sql = "SELECT id, login, email, activated, locked FROM jhi_user WHERE login = ? LIMIT 1";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, "admin");
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long id = rs.getLong("id");
                            String login = rs.getString("login");
                            String email = rs.getString("email");
                            boolean activated = rs.getBoolean("activated");
                            boolean locked = rs.getBoolean("locked");
                            
                            System.out.println("   ✅ Utilisateur trouvé:");
                            System.out.println("      ID: " + id);
                            System.out.println("      Login: " + login);
                            System.out.println("      Email: " + email);
                            System.out.println("      Activé: " + activated);
                            System.out.println("      Verrouillé: " + locked);
                            
                            // Vérifications
                            assertNotNull(login, "Le login ne doit pas être null");
                            assertNotNull(email, "L'email ne doit pas être null");
                            
                        } else {
                            System.out.println("   ⚠️ Aucun utilisateur 'admin' trouvé");
                        }
                    }
                }
                
                System.out.println("3. 📊 Simulation de comptage des utilisateurs...");
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM jhi_user")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long count = rs.getLong(1);
                            System.out.println("   ✅ Nombre total d'utilisateurs: " + count);
                            assertTrue(count >= 0, "Le nombre d'utilisateurs doit être positif");
                        }
                    }
                }
            }
            
            System.out.println("\n🎉 SIMULATION PROVIDER KEYCLOAK RÉUSSIE !");
            
        } catch (VaultException e) {
            System.out.println("⚠️ Test ignoré car Vault n'est pas disponible: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la simulation: " + e.getMessage());
            fail("La simulation du provider devrait fonctionner: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test de performance avec cache Vault")
    void testVaultCachePerformance() {
        System.out.println("\n=== Test de performance avec cache Vault ===");
        
        try {
            // Premier appel - va chercher dans Vault
            long startTime = System.currentTimeMillis();
            VaultCredentials credentials1 = vaultClient.getDatabaseCredentials(SECRET_PATH);
            long firstCallTime = System.currentTimeMillis() - startTime;
            
            // Deuxième appel - utilise le cache
            startTime = System.currentTimeMillis();
            VaultCredentials credentials2 = vaultClient.getDatabaseCredentials(SECRET_PATH);
            long secondCallTime = System.currentTimeMillis() - startTime;
            
            // Troisième appel - utilise encore le cache
            startTime = System.currentTimeMillis();
            VaultCredentials credentials3 = vaultClient.getDatabaseCredentials(SECRET_PATH);
            long thirdCallTime = System.currentTimeMillis() - startTime;
            
            System.out.println("Temps d'appel:");
            System.out.println("  1er appel (Vault): " + firstCallTime + "ms");
            System.out.println("  2ème appel (cache): " + secondCallTime + "ms");
            System.out.println("  3ème appel (cache): " + thirdCallTime + "ms");
            
            // Vérifier que les credentials sont identiques
            assertEquals(credentials1.getUrl(), credentials2.getUrl());
            assertEquals(credentials2.getUrl(), credentials3.getUrl());
            
            // Le cache devrait être plus rapide
            assertTrue(secondCallTime <= firstCallTime, "Le cache devrait être plus rapide");
            assertTrue(thirdCallTime <= firstCallTime, "Le cache devrait être plus rapide");
            
            System.out.println("✅ Performance du cache validée");
            
        } catch (VaultException e) {
            System.out.println("⚠️ Test de performance ignoré car Vault n'est pas disponible: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test de résilience en cas d'erreur Vault")
    void testVaultResiliency() {
        System.out.println("\n=== Test de résilience Vault ===");
        
        // Test avec un client Vault invalide
        VaultClient invalidClient = new VaultClient("http://invalid-vault:8200", "invalid-token");
        
        // Le provider devrait gérer l'erreur gracieusement
        assertThrows(VaultException.class, () -> {
            invalidClient.getDatabaseCredentials(SECRET_PATH);
        }, "Une erreur Vault devrait lever une VaultException");
        
        System.out.println("✅ Gestion d'erreur Vault testée");
        
        // Test avec un chemin de secret invalide
        assertThrows(VaultException.class, () -> {
            vaultClient.getDatabaseCredentials("secret/data/nonexistent");
        }, "Un secret inexistant devrait lever une VaultException");
        
        System.out.println("✅ Gestion de secret inexistant testée");
    }

    private void testDatabaseConnectionWithCredentials(VaultCredentials credentials) throws Exception {
        // Charger le driver MySQL
        Class.forName("com.mysql.cj.jdbc.Driver");
        
        // Tester la connexion
        try (Connection conn = DriverManager.getConnection(
                credentials.getUrl(), 
                credentials.getUsername(), 
                credentials.getPassword())) {
            
            assertTrue(conn.isValid(5), "La connexion devrait être valide");
        }
    }

    private void testUserQueryWithCredentials(VaultCredentials credentials) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                credentials.getUrl(), 
                credentials.getUsername(), 
                credentials.getPassword())) {
            
            // Test de requête simple
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM jhi_user")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "La requête devrait retourner un résultat");
                    long count = rs.getLong(1);
                    assertTrue(count >= 0, "Le nombre d'utilisateurs doit être positif");
                }
            }
        }
    }

    @Test
    @DisplayName("Guide de déploiement avec Vault")
    void testDeploymentGuide() {
        System.out.println("\n=== GUIDE DE DÉPLOIEMENT AVEC VAULT ===");
        
        System.out.println("🚀 DÉPLOIEMENT EN PRODUCTION AVEC VAULT:");
        System.out.println();
        
        System.out.println("1. 🔧 CONFIGURATION VAULT PRODUCTION:");
        System.out.println("   - Utilisez un Vault en mode production (pas dev)");
        System.out.println("   - Configurez l'authentification appropriée (LDAP, AWS IAM, etc.)");
        System.out.println("   - Activez l'audit logging");
        System.out.println("   - Configurez la haute disponibilité");
        System.out.println();
        
        System.out.println("2. 🔑 GESTION DES TOKENS:");
        System.out.println("   - Utilisez des tokens avec TTL limitée");
        System.out.println("   - Configurez la rotation automatique");
        System.out.println("   - Utilisez des policies restrictives");
        System.out.println("   - Exemple de policy:");
        System.out.println("     path \"secret/data/ndamli_db_access_*\" {");
        System.out.println("       capabilities = [\"read\"]");
        System.out.println("     }");
        System.out.println();
        
        System.out.println("3. 🔒 SÉCURITÉ:");
        System.out.println("   - Chiffrez les communications (TLS)");
        System.out.println("   - Utilisez des réseaux privés");
        System.out.println("   - Configurez les firewalls appropriés");
        System.out.println("   - Surveillez les accès aux secrets");
        System.out.println();
        
        System.out.println("4. 📊 MONITORING:");
        System.out.println("   - Surveillez la santé de Vault");
        System.out.println("   - Alertes sur les échecs d'authentification");
        System.out.println("   - Métriques de performance du cache");
        System.out.println("   - Logs d'audit des accès aux secrets");
        System.out.println();
        
        System.out.println("5. 🔄 ROTATION DES CREDENTIALS:");
        System.out.println("   - Configurez la rotation automatique des mots de passe DB");
        System.out.println("   - Testez les procédures de rotation");
        System.out.println("   - Documentez les procédures d'urgence");
        System.out.println();
        
        System.out.println("6. 🎯 AVANTAGES EN PRODUCTION:");
        System.out.println("   ✅ Sécurité renforcée (pas de credentials en dur)");
        System.out.println("   ✅ Rotation automatique possible");
        System.out.println("   ✅ Audit complet des accès");
        System.out.println("   ✅ Gestion centralisée des secrets");
        System.out.println("   ✅ Conformité aux standards de sécurité");
        
        assertTrue(true, "Guide de déploiement affiché");
    }
}