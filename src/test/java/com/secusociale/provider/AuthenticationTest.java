package com.secusociale.provider;

import com.secusociale.entity.User;
import com.secusociale.util.BCryptUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests d'authentification pour le provider Keycloak")
class AuthenticationTest {

    @Test
    @DisplayName("Simulation d'authentification utilisateur admin")
    void testAdminAuthentication() {
        System.out.println("=== Simulation d'authentification admin ===");
        
        // Simuler un utilisateur admin avec mot de passe BCrypt
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setLogin("admin");
        adminUser.setEmail("admin@example.com");
        adminUser.setActivated(true);
        adminUser.setLocked(false);
        adminUser.setHasPasswordUpdated(true);
        
        // Générer un hash BCrypt pour le mot de passe "admin"
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String adminPasswordHash = encoder.encode("admin");
        adminUser.setPassword(adminPasswordHash);
        
        System.out.println("Utilisateur: " + adminUser.getLogin());
        System.out.println("Email: " + adminUser.getEmail());
        System.out.println("Hash du mot de passe: " + adminPasswordHash);
        System.out.println("Activé: " + adminUser.isActivated());
        System.out.println("Verrouillé: " + adminUser.isLocked());
        
        // Test d'authentification
        String inputPassword = "admin";
        
        // Vérifications préalables
        assertTrue(adminUser.isActivated(), "L'utilisateur doit être activé");
        assertFalse(adminUser.isLocked(), "L'utilisateur ne doit pas être verrouillé");
        assertNotNull(adminUser.getPassword(), "Le mot de passe ne doit pas être null");
        assertTrue(BCryptUtil.isBCryptHash(adminUser.getPassword()), "Le hash doit être un BCrypt valide");
        
        // Test de validation du mot de passe
        boolean isValid = BCryptUtil.matches(inputPassword, adminUser.getPassword());
        
        System.out.println("Mot de passe saisi: " + inputPassword);
        System.out.println("Authentification réussie: " + isValid);
        
        assertTrue(isValid, "L'authentification avec le mot de passe 'admin' devrait réussir");
        
        // Test avec un mauvais mot de passe
        boolean isInvalid = BCryptUtil.matches("wrongpassword", adminUser.getPassword());
        assertFalse(isInvalid, "L'authentification avec un mauvais mot de passe devrait échouer");
        
        System.out.println("Test avec mauvais mot de passe: " + isInvalid + " (attendu: false)");
    }

    @Test
    @DisplayName("Test de différents états d'utilisateur")
    void testUserStates() {
        System.out.println("\n=== Test des différents états d'utilisateur ===");
        
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String passwordHash = encoder.encode("password123");
        
        // Utilisateur activé et non verrouillé (OK)
        User activeUser = createTestUser("activeuser", passwordHash, true, false);
        assertTrue(canAuthenticate(activeUser, "password123"), "Utilisateur actif devrait pouvoir s'authentifier");
        
        // Utilisateur non activé (KO)
        User inactiveUser = createTestUser("inactiveuser", passwordHash, false, false);
        assertFalse(canAuthenticate(inactiveUser, "password123"), "Utilisateur inactif ne devrait pas pouvoir s'authentifier");
        
        // Utilisateur verrouillé (KO)
        User lockedUser = createTestUser("lockeduser", passwordHash, true, true);
        assertFalse(canAuthenticate(lockedUser, "password123"), "Utilisateur verrouillé ne devrait pas pouvoir s'authentifier");
        
        // Utilisateur avec mot de passe null (KO)
        User noPasswordUser = createTestUser("nopassworduser", null, true, false);
        assertFalse(canAuthenticate(noPasswordUser, "password123"), "Utilisateur sans mot de passe ne devrait pas pouvoir s'authentifier");
        
        System.out.println("Tous les tests d'état utilisateur ont réussi");
    }

    @Test
    @DisplayName("Test de validation avec différents formats de hash")
    void testDifferentHashFormats() {
        System.out.println("\n=== Test avec différents formats de hash ===");
        
        String password = "testpassword";
        
        // Hash BCrypt valide
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String bcryptHash = encoder.encode(password);
        User bcryptUser = createTestUser("bcryptuser", bcryptHash, true, false);
        assertTrue(canAuthenticate(bcryptUser, password), "Authentification BCrypt devrait réussir");
        
        // Hash invalide (plaintext)
        User plaintextUser = createTestUser("plaintextuser", password, true, false);
        assertFalse(canAuthenticate(plaintextUser, password), "Authentification avec plaintext devrait échouer");
        
        // Hash invalide (MD5-like)
        User md5User = createTestUser("md5user", "5d41402abc4b2a76b9719d911017c592", true, false);
        assertFalse(canAuthenticate(md5User, password), "Authentification avec hash MD5 devrait échouer");
        
        System.out.println("Tests de formats de hash terminés");
    }

    private User createTestUser(String login, String passwordHash, boolean activated, boolean locked) {
        User user = new User();
        user.setId(System.currentTimeMillis()); // ID unique
        user.setLogin(login);
        user.setEmail(login + "@test.com");
        user.setPassword(passwordHash);
        user.setActivated(activated);
        user.setLocked(locked);
        user.setHasPasswordUpdated(true);
        return user;
    }

    private boolean canAuthenticate(User user, String inputPassword) {
        // Simulation de la logique d'authentification du provider
        
        // Vérifications de sécurité
        if (!user.isActivated()) {
            System.out.println("❌ Utilisateur " + user.getLogin() + " non activé");
            return false;
        }
        
        if (user.isLocked()) {
            System.out.println("❌ Utilisateur " + user.getLogin() + " verrouillé");
            return false;
        }
        
        String storedPassword = user.getPassword();
        if (storedPassword == null || storedPassword.trim().isEmpty()) {
            System.out.println("❌ Mot de passe vide pour " + user.getLogin());
            return false;
        }
        
        if (!BCryptUtil.isBCryptHash(storedPassword)) {
            System.out.println("❌ Hash invalide pour " + user.getLogin());
            return false;
        }
        
        boolean isValid = BCryptUtil.matches(inputPassword, storedPassword);
        System.out.println((isValid ? "✅" : "❌") + " Authentification pour " + user.getLogin() + ": " + isValid);
        
        return isValid;
    }
}