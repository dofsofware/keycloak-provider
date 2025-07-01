package com.secusociale.vault;

/**
 * Classe pour stocker les credentials de base de données récupérés depuis Vault
 */
public class VaultCredentials {
    
    private final String url;
    private final String username;
    private final String password;
    private final long retrievedAt;
    private static final long TTL_MS = 5 * 60 * 1000; // 5 minutes
    
    public VaultCredentials(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.retrievedAt = System.currentTimeMillis();
    }
    
    public String getUrl() {
        return url;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public long getRetrievedAt() {
        return retrievedAt;
    }
    
    /**
     * Vérifie si les credentials ont expiré
     */
    public boolean isExpired() {
        return (System.currentTimeMillis() - retrievedAt) > TTL_MS;
    }
    
    /**
     * Retourne l'âge des credentials en secondes
     */
    public long getAgeInSeconds() {
        return (System.currentTimeMillis() - retrievedAt) / 1000;
    }
    
    @Override
    public String toString() {
        return "VaultCredentials{" +
                "url='" + (url != null ? url.substring(0, Math.min(30, url.length())) + "..." : "null") + '\'' +
                ", username='" + username + '\'' +
                ", password='***'" +
                ", ageInSeconds=" + getAgeInSeconds() +
                ", expired=" + isExpired() +
                '}';
    }
}