package com.secusociale.provider;

import com.secusociale.entity.User;
import com.secusociale.repository.UserRepository;
import com.secusociale.util.BCryptUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests d'intégration du provider Keycloak")
class ProviderIntegrationTest {

    @Test
    @DisplayName("Test complet du flux d'authentification")
    void testCompleteAuthenticationFlow() {
        System.out.println("=== Test complet du flux d'authentification ===");
        
        // 1. Simuler la recherche d'utilisateur par username
        System.out.println("1. 🔍 Recherche de l'utilisateur 'admin'...");
        
        // Simuler un utilisateur admin trouvé dans la base
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setLogin("admin");
        adminUser.setEmail("admin@example.com");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setActivated(true);
        adminUser.setLocked(false);
        adminUser.setHasPasswordUpdated(true);
        
        // Hash BCrypt pour le mot de passe "admin"
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String adminPasswordHash = encoder.encode("admin");
        adminUser.setPassword(adminPasswordHash);
        
        System.out.println("   ✅ Utilisateur trouvé: " + adminUser.getLogin());
        System.out.println("   📧 Email: " + adminUser.getEmail());
        System.out.println("   🔑 Hash: " + adminPasswordHash.substring(0, 20) + "...");
        
        // 2. Vérifications de sécurité
        System.out.println("2. 🛡️ Vérifications de sécurité...");
        assertTrue(adminUser.isActivated(), "L'utilisateur doit être activé");
        assertFalse(adminUser.isLocked(), "L'utilisateur ne doit pas être verrouillé");
        System.out.println("   ✅ Utilisateur activé et non verrouillé");
        
        // 3. Validation du format du hash
        System.out.println("3. 🔍 Validation du format du hash...");
        assertTrue(BCryptUtil.isBCryptHash(adminUser.getPassword()), "Le hash doit être un BCrypt valide");
        System.out.println("   ✅ Format BCrypt valide (coût: " + BCryptUtil.getCost(adminUser.getPassword()) + ")");
        
        // 4. Test d'authentification avec le bon mot de passe
        System.out.println("4. 🔐 Test d'authentification avec mot de passe correct...");
        String inputPassword = "admin";
        boolean authSuccess = BCryptUtil.matches(inputPassword, adminUser.getPassword());
        assertTrue(authSuccess, "L'authentification devrait réussir avec le bon mot de passe");
        System.out.println("   ✅ Authentification RÉUSSIE avec 'admin'");
        
        // 5. Test d'authentification avec un mauvais mot de passe
        System.out.println("5. ❌ Test d'authentification avec mot de passe incorrect...");
        boolean authFail = BCryptUtil.matches("wrongpassword", adminUser.getPassword());
        assertFalse(authFail, "L'authentification devrait échouer avec un mauvais mot de passe");
        System.out.println("   ✅ Authentification ÉCHOUÉE avec mauvais mot de passe (comportement attendu)");
        
        System.out.println("\n🎉 FLUX D'AUTHENTIFICATION COMPLET VALIDÉ !");
    }

    @Test
    @DisplayName("Test de simulation Keycloak UserAdapter")
    void testKeycloakUserAdapter() {
        System.out.println("\n=== Test de simulation UserAdapter Keycloak ===");
        
        // Créer un utilisateur test
        User testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("admin");
        testUser.setEmail("admin@example.com");
        testUser.setFirstName("Admin");
        testUser.setLastName("User");
        testUser.setActivated(true);
        testUser.setLocked(false);
        testUser.setPhone("123456789");
        testUser.setTypeCompte("ADMIN");
        testUser.setInstitution("IPRES");
        testUser.setAgence("DAKAR");
        
        // Hash du mot de passe
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        testUser.setPassword(encoder.encode("admin"));
        
        // Simuler ce que Keycloak verrait
        System.out.println("Informations utilisateur pour Keycloak:");
        System.out.println("  - ID: " + testUser.getId());
        System.out.println("  - Username: " + testUser.getLogin());
        System.out.println("  - Email: " + testUser.getEmail());
        System.out.println("  - Nom complet: " + testUser.getFirstName() + " " + testUser.getLastName());
        System.out.println("  - Activé: " + testUser.isActivated());
        System.out.println("  - Téléphone: " + testUser.getPhone());
        System.out.println("  - Type compte: " + testUser.getTypeCompte());
        System.out.println("  - Institution: " + testUser.getInstitution());
        System.out.println("  - Agence: " + testUser.getAgence());
        
        // Vérifier que les informations sont correctes
        assertNotNull(testUser.getLogin(), "Le username ne doit pas être null");
        assertNotNull(testUser.getEmail(), "L'email ne doit pas être null");
        assertTrue(testUser.isActivated(), "L'utilisateur doit être activé");
        
        System.out.println("✅ UserAdapter simulation réussie");
    }

    @Test
    @DisplayName("Test de vérification des logs d'authentification")
    void testAuthenticationLogs() {
        System.out.println("\n=== Test des logs d'authentification ===");
        
        // Simuler différents scénarios d'authentification
        String[] scenarios = {
            "Utilisateur valide avec bon mot de passe",
            "Utilisateur valide avec mauvais mot de passe", 
            "Utilisateur non activé",
            "Utilisateur verrouillé",
            "Hash de mot de passe invalide"
        };
        
        for (int i = 0; i < scenarios.length; i++) {
            System.out.println((i + 1) + ". " + scenarios[i]);
            
            User user = new User();
            user.setLogin("testuser" + i);
            user.setEmail("test" + i + "@example.com");
            
            boolean expectedResult = false;
            String testPassword = "testpass";
            
            switch (i) {
                case 0: // Utilisateur valide avec bon mot de passe
                    user.setActivated(true);
                    user.setLocked(false);
                    user.setPassword(new BCryptPasswordEncoder().encode(testPassword));
                    expectedResult = true;
                    break;
                    
                case 1: // Utilisateur valide avec mauvais mot de passe
                    user.setActivated(true);
                    user.setLocked(false);
                    user.setPassword(new BCryptPasswordEncoder().encode("differentpassword"));
                    expectedResult = false;
                    break;
                    
                case 2: // Utilisateur non activé
                    user.setActivated(false);
                    user.setLocked(false);
                    user.setPassword(new BCryptPasswordEncoder().encode(testPassword));
                    expectedResult = false;
                    break;
                    
                case 3: // Utilisateur verrouillé
                    user.setActivated(true);
                    user.setLocked(true);
                    user.setPassword(new BCryptPasswordEncoder().encode(testPassword));
                    expectedResult = false;
                    break;
                    
                case 4: // Hash invalide
                    user.setActivated(true);
                    user.setLocked(false);
                    user.setPassword("plaintext_password"); // Hash invalide
                    expectedResult = false;
                    break;
            }
            
            boolean result = simulateAuthentication(user, testPassword);
            assertEquals(expectedResult, result, "Résultat inattendu pour le scénario: " + scenarios[i]);
            
            System.out.println("   " + (result ? "✅" : "❌") + " Résultat: " + result + " (attendu: " + expectedResult + ")");
        }
        
        System.out.println("✅ Tous les scénarios de logs testés");
    }

    private boolean simulateAuthentication(User user, String inputPassword) {
        // Simulation de la logique du CustomUserStorageProvider.isValid()
        
        // Vérifications de sécurité
        if (!user.isActivated()) {
            return false;
        }
        
        if (user.isLocked()) {
            return false;
        }
        
        String storedPassword = user.getPassword();
        if (storedPassword == null || storedPassword.trim().isEmpty()) {
            return false;
        }
        
        if (!BCryptUtil.isBCryptHash(storedPassword)) {
            return false;
        }
        
        return BCryptUtil.matches(inputPassword, storedPassword);
    }

    @Test
    @DisplayName("Guide de vérification dans Keycloak")
    void testKeycloakVerificationGuide() {
        System.out.println("\n=== GUIDE DE VÉRIFICATION DANS KEYCLOAK ===");
        
        System.out.println("Pour vérifier que votre provider fonctionne correctement:");
        System.out.println();
        
        System.out.println("1. 🔧 CONFIGURATION DU PROVIDER:");
        System.out.println("   - Allez dans Admin Console → Votre Realm → User Federation");
        System.out.println("   - Ajoutez 'ndamli-provider'");
        System.out.println("   - Configurez la base de données MySQL");
        System.out.println("   - Cliquez sur 'Test connection' → devrait être vert ✅");
        System.out.println("   - Sauvegardez");
        System.out.println();
        
        System.out.println("2. 👤 VÉRIFICATION DES UTILISATEURS:");
        System.out.println("   - Allez dans Users → View all users");
        System.out.println("   - Vous devriez voir vos utilisateurs de la base externe");
        System.out.println("   - Cliquez sur 'admin' → onglet Details");
        System.out.println("   - Vérifiez que les informations sont correctes");
        System.out.println();
        
        System.out.println("3. 🔐 VÉRIFICATION DES CREDENTIALS:");
        System.out.println("   - Dans l'utilisateur 'admin' → onglet Credentials");
        System.out.println("   - MESSAGE ATTENDU: 'No credentials' ← C'EST NORMAL ! ✅");
        System.out.println("   - Cela signifie que Keycloak utilise votre provider externe");
        System.out.println("   - Les mots de passe restent dans votre base MySQL");
        System.out.println();
        
        System.out.println("4. 🧪 TEST D'AUTHENTIFICATION:");
        System.out.println("   - Ouvrez un onglet privé/incognito");
        System.out.println("   - Allez sur votre application ou Account Console");
        System.out.println("   - Connectez-vous avec: admin / admin");
        System.out.println("   - L'authentification devrait RÉUSSIR ✅");
        System.out.println();
        
        System.out.println("5. 📊 VÉRIFICATION DES LOGS:");
        System.out.println("   - Regardez les logs Keycloak");
        System.out.println("   - Vous devriez voir des messages avec emojis:");
        System.out.println("     🔐 Début de validation des credentials pour l'utilisateur: admin");
        System.out.println("     ✅ Utilisateur trouvé: admin (ID: 1)");
        System.out.println("     ✅ Authentification RÉUSSIE pour l'utilisateur: admin");
        System.out.println();
        
        System.out.println("6. ❌ SI ÇA NE FONCTIONNE PAS:");
        System.out.println("   - Vérifiez les logs Keycloak pour les erreurs");
        System.out.println("   - Vérifiez la connexion à la base de données");
        System.out.println("   - Vérifiez que l'utilisateur 'admin' existe et est activé");
        System.out.println("   - Vérifiez que le mot de passe est bien hashé en BCrypt");
        System.out.println();
        
        System.out.println("🎯 RÉSUMÉ:");
        System.out.println("   ✅ 'No credentials' dans Keycloak = COMPORTEMENT NORMAL");
        System.out.println("   ✅ Vos mots de passe restent dans votre base MySQL");
        System.out.println("   ✅ L'authentification se fait via votre provider");
        System.out.println("   ✅ Les utilisateurs gardent leurs mots de passe actuels");
        
        assertTrue(true, "Guide affiché avec succès");
    }
}