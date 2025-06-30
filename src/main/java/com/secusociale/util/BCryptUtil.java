package com.secusociale.util;

import org.jboss.logging.Logger;
import java.util.regex.Pattern;

public class BCryptUtil {

    private static final Logger logger = Logger.getLogger(BCryptUtil.class);

    // Regex finale validée
    private static final String BCRYPT_REGEX = "^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";
    private static final Pattern BCRYPT_PATTERN = Pattern.compile(BCRYPT_REGEX);

    public static boolean isBCryptHash(String hash) {
        if (hash == null || hash.length() != 60) {
            return false;
        }

        // Vérification des parties fixes
        if (hash.charAt(0) != '$' || hash.charAt(3) != '$' || hash.charAt(6) != '$') {
            return false;
        }

        // Vérification de la version
        String version = hash.substring(1, 3);
        if (!version.equals("2a") && !version.equals("2b") && !version.equals("2y")) {
            return false;
        }

        // Vérification du coût
        try {
            int cost = Integer.parseInt(hash.substring(4, 6));
            if (cost < 4 || cost > 31) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // Vérification finale avec regex
        return BCRYPT_PATTERN.matcher(hash).matches();
    }

    public static boolean matches(String plaintext, String hashed) {
        if (plaintext == null || hashed == null || !isBCryptHash(hashed)) {
            return false;
        }
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches(plaintext, hashed);
    }
}