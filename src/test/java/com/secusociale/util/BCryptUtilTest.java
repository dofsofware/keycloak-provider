package com.secusociale.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class BCryptUtilTest {

    @Test
    @DisplayName("Test de validation BCrypt avec mot de passe admin")
    void testAdminPasswordValidation() {
        // Générer un hash pour le mot de passe "admin" avec jBCrypt
        String password = "admin";
        String hash = BCryptUtil.encode(password);

        System.out.println("=== Test du mot de passe 'admin' avec jBCrypt ===");
        System.out.println("Mot de passe: " + password);
        System.out.println("Hash généré: " + hash);
        System.out.println("Longueur du hash: " + hash.length());
        System.out.println("Est un hash BCrypt valide: " + BCryptUtil.isBCryptHash(hash));
        System.out.println("Coût BCrypt: " + BCryptUtil.getCost(hash));

        // Test avec notre utilitaire
        boolean matches = BCryptUtil.matches(password, hash);
        System.out.println("Correspondance avec BCryptUtil: " + matches);

        assertTrue(matches, "BCryptUtil devrait valider le mot de passe admin");
        assertTrue(BCryptUtil.isBCryptHash(hash), "Le hash généré devrait être valide");
    }

    @Test
    @DisplayName("Test de compatibilité avec Spring Security")
    void testSpringSecurityCompatibility() {
        // Test avec un hash généré par Spring Security
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "testPassword123";
        String springHash = encoder.encode(password);

        System.out.println("=== Test de compatibilité Spring Security ===");
        System.out.println("Hash Spring Security: " + springHash);

        // Test avec notre utilitaire jBCrypt
        boolean jbcryptMatches = BCryptUtil.matches(password, springHash);
        System.out.println("jBCrypt peut lire hash Spring: " + jbcryptMatches);

        // Test avec Spring Security pour comparaison
        boolean springMatches = encoder.matches(password, springHash);
        System.out.println("Spring Security validation: " + springMatches);

        // Les deux devraient donner le même résultat
        assertEquals(springMatches, jbcryptMatches, "jBCrypt devrait être compatible avec Spring Security");

        // Test inverse : hash jBCrypt lu par Spring
        String jbcryptHash = BCryptUtil.encode(password);
        boolean springReadsJbcrypt = encoder.matches(password, jbcryptHash);
        boolean jbcryptReadsJbcrypt = BCryptUtil.matches(password, jbcryptHash);

        System.out.println("Spring peut lire hash jBCrypt: " + springReadsJbcrypt);
        System.out.println("jBCrypt peut lire son propre hash: " + jbcryptReadsJbcrypt);

        assertTrue(springReadsJbcrypt, "Spring Security devrait pouvoir lire les hash jBCrypt");
        assertTrue(jbcryptReadsJbcrypt, "jBCrypt devrait pouvoir lire ses propres hash");
    }

    @Test
    @DisplayName("Test avec différents mots de passe courants")
    void testCommonPasswords() {
        String[] passwords = {"admin", "password", "123456", "test", "user"};

        System.out.println("\n=== Test de mots de passe courants avec jBCrypt ===");
        
        for (String password : passwords) {
            String hash = BCryptUtil.encode(password);
            boolean isValidHash = BCryptUtil.isBCryptHash(hash);
            boolean matches = BCryptUtil.matches(password, hash);
            
            System.out.printf("Mot de passe: %-10s | Hash valide: %-5s | Correspondance: %-5s%n", 
                password, isValidHash, matches);
            
            assertTrue(isValidHash, "Le hash devrait être valide pour: " + password);
            assertTrue(matches, "Le mot de passe devrait correspondre pour: " + password);
        }
    }

    @Test
    @DisplayName("Test de validation du format BCrypt")
    void testBCryptHashValidation() {
        // Hash BCrypt valides générés par jBCrypt
        String[] validHashes = new String[3];
        validHashes[0] = BCryptUtil.encode("test1");
        validHashes[1] = BCryptUtil.encode("test2");
        validHashes[2] = BCryptUtil.encode("test3");

        System.out.println("\n=== Test de validation du format BCrypt ===");
        
        for (String hash : validHashes) {
            boolean isValid = BCryptUtil.isBCryptHash(hash);
            int cost = BCryptUtil.getCost(hash);
            System.out.printf("Hash: %s... | Valide: %-5s | Coût: %d%n", 
                hash.substring(0, 20), isValid, cost);
            assertTrue(isValid, "Le hash devrait être valide: " + hash);
        }

        // Hash invalides
        String[] invalidHashes = {
            "plaintext",
            "$1$invalid",
            "$2a$5$tooshort", // coût trop bas
            "$2a$99$invalidcost", // coût trop élevé
            "$2a$10$invalidlength", // longueur incorrecte
            null,
            ""
        };

        System.out.println("\n=== Test de hash invalides ===");
        
        for (String hash : invalidHashes) {
            boolean isValid = BCryptUtil.isBCryptHash(hash);
            System.out.printf("Hash: %-20s | Valide: %-5s%n", 
                hash != null ? (hash.length() > 20 ? hash.substring(0, 20) + "..." : hash) : "null", 
                isValid);
            assertFalse(isValid, "Le hash devrait être invalide: " + hash);
        }
    }

    @Test
    @DisplayName("Test avec différents coûts BCrypt")
    void testDifferentCosts() {
        System.out.println("\n=== Test avec différents coûts BCrypt ===");
        
        String password = "testPassword";
        int[] costs = {4, 6, 8, 10, 12};
        
        for (int cost : costs) {
            long startTime = System.currentTimeMillis();
            String hash = BCryptUtil.encode(password, cost);
            long hashTime = System.currentTimeMillis() - startTime;
            
            startTime = System.currentTimeMillis();
            boolean matches = BCryptUtil.matches(password, hash);
            long verifyTime = System.currentTimeMillis() - startTime;
            
            int actualCost = BCryptUtil.getCost(hash);
            
            System.out.printf("Coût: %2d | Hash: %dms | Vérif: %dms | Coût réel: %d | Valide: %s%n",
                cost, hashTime, verifyTime, actualCost, matches);
            
            assertEquals(cost, actualCost, "Le coût réel devrait correspondre au coût demandé");
            assertTrue(matches, "La vérification devrait réussir");
        }
    }

    @Test
    @DisplayName("Test de cas d'erreur")
    void testErrorCases() {
        System.out.println("\n=== Test de cas d'erreur ===");
        
        // Test avec des valeurs null
        assertFalse(BCryptUtil.matches("password", null), "Ne devrait pas correspondre avec hash null");
        assertFalse(BCryptUtil.matches(null, "$2a$10$validhash"), "Ne devrait pas correspondre avec password null");
        assertFalse(BCryptUtil.matches(null, null), "Ne devrait pas correspondre avec les deux null");
        
        // Test avec hash invalide
        assertFalse(BCryptUtil.matches("password", "invalid_hash"), "Ne devrait pas correspondre avec hash invalide");
        
        // Test avec mot de passe vide
        String hash = BCryptUtil.encode("test");
        assertFalse(BCryptUtil.matches("", hash), "Ne devrait pas correspondre avec mot de passe vide");
        
        // Test d'exception pour encode avec null
        assertThrows(IllegalArgumentException.class, () -> {
            BCryptUtil.encode(null);
        }, "encode() devrait lever une exception avec password null");
        
        // Test d'exception pour coût invalide
        assertThrows(IllegalArgumentException.class, () -> {
            BCryptUtil.encode("test", 3); // coût trop bas
        }, "encode() devrait lever une exception avec coût trop bas");
        
        assertThrows(IllegalArgumentException.class, () -> {
            BCryptUtil.encode("test", 32); // coût trop élevé
        }, "encode() devrait lever une exception avec coût trop élevé");
        
        System.out.println("Tous les cas d'erreur ont été testés avec succès");
    }

    @Test
    @DisplayName("Test de performance jBCrypt")
    void testPerformance() {
        System.out.println("\n=== Test de performance jBCrypt ===");
        
        String password = "testPassword123";
        String hash = BCryptUtil.encode(password);
        
        long startTime = System.currentTimeMillis();
        
        // Test de 50 validations (moins que Spring car jBCrypt peut être plus lent)
        for (int i = 0; i < 50; i++) {
            BCryptUtil.matches(password, hash);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("50 validations jBCrypt en %d ms (%.2f ms par validation)%n", 
            duration, duration / 50.0);
        
        // BCrypt devrait prendre un certain temps (c'est voulu pour la sécurité)
        assertTrue(duration > 0, "La validation BCrypt devrait prendre du temps");
        
        // Test de génération de hash
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            BCryptUtil.encode("test" + i);
        }
        endTime = System.currentTimeMillis();
        duration = endTime - startTime;
        
        System.out.printf("10 générations de hash en %d ms (%.2f ms par hash)%n", 
            duration, duration / 10.0);
    }
}