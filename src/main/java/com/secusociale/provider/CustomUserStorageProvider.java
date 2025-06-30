package com.secusociale.provider;

import com.secusociale.entity.User;
import com.secusociale.repository.UserRepository;
import com.secusociale.util.BCryptUtil;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomUserStorageProvider implements UserStorageProvider,
        UserQueryProvider, UserLookupProvider, CredentialInputValidator {

    private static final Logger logger = Logger.getLogger(CustomUserStorageProvider.class);

    private ComponentModel componentModel;
    private KeycloakSession keycloakSession;
    private UserRepository userRepository;

    public CustomUserStorageProvider(KeycloakSession keycloakSession, ComponentModel componentModel, UserRepository userRepository) {
        this.keycloakSession = keycloakSession;
        this.componentModel = componentModel;
        this.userRepository = userRepository;
        logger.infof("CustomUserStorageProvider créé pour le modèle: %s", componentModel.getName());
    }

    @Override
    public void close() {
        logger.debug("Fermeture du CustomUserStorageProvider");
        if (userRepository != null) {
            userRepository.close();
        }
    }

    // ========== CREDENTIAL VALIDATION ==========
    
    @Override
    public boolean supportsCredentialType(String credentialType) {
        boolean supports = PasswordCredentialModel.TYPE.equals(credentialType);
        logger.debugf("supportsCredentialType(%s) = %s", credentialType, supports);
        return supports;
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        boolean configured = supportsCredentialType(credentialType);
        logger.debugf("isConfiguredFor(user=%s, credentialType=%s) = %s", user.getUsername(), credentialType, configured);
        return configured;
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        logger.infof("🔐 Début de validation des credentials pour l'utilisateur: %s", user.getUsername());
        
        if (!supportsCredentialType(credentialInput.getType())) {
            logger.warnf("❌ Type de credential non supporté: %s", credentialInput.getType());
            return false;
        }

        try {
            // Récupérer l'utilisateur depuis la base externe
            String externalId = StorageId.externalId(user.getId());
            logger.debugf("🔍 ID externe extrait: %s", externalId);
            
            long persistenceId = Long.parseLong(externalId);
            Optional<User> userOptional = userRepository.findById(persistenceId);
            
            if (userOptional.isEmpty()) {
                logger.warnf("❌ Utilisateur non trouvé dans la base externe pour l'ID: %s", externalId);
                return false;
            }

            User externalUser = userOptional.get();
            logger.infof("✅ Utilisateur trouvé: %s (ID: %d)", externalUser.getLogin(), externalUser.getId());
            
            // Vérifications de sécurité
            if (!externalUser.isActivated()) {
                logger.warnf("❌ Utilisateur non activé: %s", user.getUsername());
                return false;
            }
            
            if (externalUser.isLocked()) {
                logger.warnf("❌ Utilisateur verrouillé: %s", user.getUsername());
                return false;
            }

            // Vérification de l'expiration du compte
            if (externalUser.getExpirationDate() != null && 
                externalUser.getExpirationDate().isBefore(Instant.now())) {
                logger.warnf("❌ Compte expiré pour l'utilisateur: %s (expiré le: %s)", 
                    user.getUsername(), externalUser.getExpirationDate());
                return false;
            }

            // Validation du mot de passe
            String inputPassword = credentialInput.getChallengeResponse();
            String storedPassword = externalUser.getPassword();
            
            logger.debugf("🔑 Validation du mot de passe pour: %s", user.getUsername());
            logger.debugf("📝 Hash stocké: %s", storedPassword != null ? storedPassword.substring(0, Math.min(10, storedPassword.length())) + "..." : "null");
            
            if (storedPassword == null || storedPassword.trim().isEmpty()) {
                logger.warnf("❌ Mot de passe vide pour l'utilisateur: %s", user.getUsername());
                return false;
            }

            // Vérifier si c'est un hash BCrypt valide
            if (!BCryptUtil.isBCryptHash(storedPassword)) {
                logger.warnf("❌ Hash de mot de passe invalide pour l'utilisateur: %s (format: %s)", 
                    user.getUsername(), storedPassword.length() > 10 ? storedPassword.substring(0, 10) + "..." : storedPassword);
                return false;
            }

            // Comparaison BCrypt
            boolean isValid = BCryptUtil.matches(inputPassword, storedPassword);
            
            if (isValid) {
                logger.infof("✅ Authentification RÉUSSIE pour l'utilisateur: %s", user.getUsername());
                
                // Log des informations supplémentaires pour debug
                logger.debugf("📊 Détails utilisateur - Email: %s, Activé: %s, Verrouillé: %s, Type: %s", 
                    externalUser.getEmail(), 
                    externalUser.isActivated(), 
                    externalUser.isLocked(),
                    externalUser.getTypeCompte());
            } else {
                logger.warnf("❌ Authentification ÉCHOUÉE pour l'utilisateur: %s", user.getUsername());
                
                // Test de debug pour vérifier si le problème vient du hash
                logger.debugf("🔍 Debug - Longueur mot de passe saisi: %d", inputPassword != null ? inputPassword.length() : 0);
                logger.debugf("🔍 Debug - Longueur hash stocké: %d", storedPassword.length());
                logger.debugf("🔍 Debug - Coût BCrypt: %d", BCryptUtil.getCost(storedPassword));
            }
            
            return isValid;
            
        } catch (NumberFormatException e) {
            logger.errorf("❌ ID externe invalide pour %s: %s", user.getUsername(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la validation des credentials pour %s: %s", user.getUsername(), e.getMessage());
            return false;
        }
    }

    // ========== USER LOOKUP ==========

    @Override
    public UserModel getUserById(RealmModel realmModel, String id) {
        logger.debugf("🔍 Recherche utilisateur par ID: %s", id);

        try {
            String externalId = StorageId.externalId(id);
            long persistenceId = Long.parseLong(externalId);

            Optional<User> userOptional = userRepository.findById(persistenceId);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                logger.debugf("✅ Utilisateur trouvé par ID %s: %s", id, user.getLogin());
                return new UserAdapter(keycloakSession, realmModel, componentModel, user);
            } else {
                logger.debugf("❌ Aucun utilisateur trouvé pour l'ID: %s", id);
                return null;
            }
        } catch (NumberFormatException e) {
            logger.warnf("❌ ID invalide: %s", id);
            return null;
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la recherche par ID %s: %s", id, e.getMessage());
            return null;
        }
    }

    @Override
    public UserModel getUserByUsername(RealmModel realmModel, String username) {
        logger.debugf("🔍 Recherche utilisateur par username: %s", username);

        try {
            Optional<User> userOptional = userRepository.findOneByLogin(username);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                logger.debugf("✅ Utilisateur trouvé par username %s: %s (ID: %d)", username, user.getEmail(), user.getId());
                return new UserAdapter(keycloakSession, realmModel, componentModel, user);
            } else {
                logger.debugf("❌ Aucun utilisateur trouvé pour le username: %s", username);
                return null;
            }
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la recherche par username %s: %s", username, e.getMessage());
            return null;
        }
    }

    @Override
    public UserModel getUserByEmail(RealmModel realmModel, String email) {
        logger.debugf("🔍 Recherche utilisateur par email: %s", email);

        try {
            Optional<User> userOptional = userRepository.findOneByEmailIgnoreCase(email);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                logger.debugf("✅ Utilisateur trouvé par email %s: %s (ID: %d)", email, user.getLogin(), user.getId());
                return new UserAdapter(keycloakSession, realmModel, componentModel, user);
            } else {
                logger.debugf("❌ Aucun utilisateur trouvé pour l'email: %s", email);
                return null;
            }
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la recherche par email %s: %s", email, e.getMessage());
            return null;
        }
    }

    // ========== USER QUERY ==========

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realmModel, Map<String, String> params, Integer firstResult, Integer maxResults) {
        logger.debugf("🔍 Recherche d'utilisateurs avec params: %s", params);

        String searchParam = params.getOrDefault("keycloak.session.realm.users.query.search",
                params.getOrDefault("email", params.getOrDefault("username", "")));

        if (searchParam == null || searchParam.trim().isEmpty()) {
            logger.debug("❌ Paramètre de recherche vide, retour d'un stream vide");
            return Stream.empty();
        }

        try {
            List<User> users = userRepository.findAllByEmailLike("%" + searchParam.trim() + "%");
            logger.debugf("✅ Trouvé %d utilisateurs pour la recherche: %s", users.size(), searchParam);

            return users.stream()
                    .skip(firstResult != null ? firstResult : 0)
                    .limit(maxResults != null ? maxResults : Integer.MAX_VALUE)
                    .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user));
        } catch (Exception e) {
            logger.errorf(e, "❌ Erreur lors de la recherche d'utilisateurs: %s", e.getMessage());
            return Stream.empty();
        }
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realmModel, GroupModel groupModel, Integer firstResult, Integer maxResults) {
        logger.debug("getGroupMembersStream appelé - non implémenté");
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realmModel, String attributeName, String attributeValue) {
        logger.debugf("searchForUserByUserAttributeStream appelé pour %s = %s - non implémenté", attributeName, attributeValue);
        return Stream.empty();
    }
}