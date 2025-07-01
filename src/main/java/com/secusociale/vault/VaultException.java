package com.secusociale.vault;

/**
 * Exception spécifique pour les erreurs liées à Vault
 */
public class VaultException extends RuntimeException {
    
    public VaultException(String message) {
        super(message);
    }
    
    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}