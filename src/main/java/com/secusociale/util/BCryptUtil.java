package com.secusociale.util;

import org.jboss.logging.Logger;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Utilitaire BCrypt compatible avec l'environnement Keycloak
 * Utilise l'implémentation BCrypt native sans dépendance Spring Security
 */
public class BCryptUtil {

    private static final Logger logger = Logger.getLogger(BCryptUtil.class);

    // Pattern BCrypt validé
    private static final String BCRYPT_REGEX = "^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";
    private static final Pattern BCRYPT_PATTERN = Pattern.compile(BCRYPT_REGEX);

    // Alphabet BCrypt
    private static final String BCRYPT_ALPHABET = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Vérifie si une chaîne est un hash BCrypt valide
     */
    public static boolean isBCryptHash(String hash) {
        if (hash == null || hash.length() != 60) {
            logger.debugf("Hash invalide: longueur incorrecte ou null. Longueur: %s", 
                hash != null ? hash.length() : "null");
            return false;
        }

        // Vérification des caractères de structure BCrypt
        if (hash.charAt(0) != '$' || hash.charAt(3) != '$' || hash.charAt(6) != '$') {
            logger.debugf("Hash invalide: structure incorrecte");
            return false;
        }

        // Vérification de la version BCrypt
        String version = hash.substring(1, 3);
        if (!version.equals("2a") && !version.equals("2b") && !version.equals("2x") && !version.equals("2y")) {
            logger.debugf("Hash invalide: version BCrypt non supportée: %s", version);
            return false;
        }

        // Vérification du coût (rounds)
        try {
            int cost = Integer.parseInt(hash.substring(4, 6));
            if (cost < 4 || cost > 31) {
                logger.debugf("Hash invalide: coût BCrypt hors limites: %d", cost);
                return false;
            }
        } catch (NumberFormatException e) {
            logger.debugf("Hash invalide: coût BCrypt non numérique");
            return false;
        }

        // Vérification finale avec regex
        boolean isValid = BCRYPT_PATTERN.matcher(hash).matches();
        logger.debugf("Validation finale du hash BCrypt: %s", isValid);
        return isValid;
    }

    /**
     * Compare un mot de passe en clair avec un hash BCrypt
     * Utilise l'implémentation BCrypt de jBCrypt (compatible Keycloak)
     */
    public static boolean matches(String plaintext, String hashed) {
        if (plaintext == null) {
            logger.debugf("Mot de passe en clair null");
            return false;
        }
        
        if (hashed == null) {
            logger.debugf("Hash null");
            return false;
        }
        
        if (!isBCryptHash(hashed)) {
            logger.debugf("Hash BCrypt invalide: %s", hashed);
            return false;
        }

        try {
            // Utiliser jBCrypt qui est compatible avec Keycloak
            boolean matches = org.mindrot.jbcrypt.BCrypt.checkpw(plaintext, hashed);
            logger.debugf("Résultat de la comparaison BCrypt: %s", matches);
            return matches;
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la comparaison BCrypt: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Encode un mot de passe en BCrypt (utile pour les tests)
     */
    public static String encode(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être null");
        }
        return org.mindrot.jbcrypt.BCrypt.hashpw(plaintext, org.mindrot.jbcrypt.BCrypt.gensalt());
    }

    /**
     * Vérifie la force d'un hash BCrypt
     */
    public static int getCost(String hash) {
        if (!isBCryptHash(hash)) {
            return -1;
        }
        try {
            return Integer.parseInt(hash.substring(4, 6));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Génère un salt BCrypt avec le coût spécifié
     */
    public static String generateSalt(int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("Le coût BCrypt doit être entre 4 et 31");
        }
        return org.mindrot.jbcrypt.BCrypt.gensalt(cost);
    }

    /**
     * Encode un mot de passe avec un coût spécifique
     */
    public static String encode(String plaintext, int cost) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être null");
        }
        return org.mindrot.jbcrypt.BCrypt.hashpw(plaintext, generateSalt(cost));
    }
}