package com.secusociale.provider;

import com.secusociale.entity.User;
import com.secusociale.repository.UserRepository;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomUserStorageProvider implements UserStorageProvider,
        UserQueryProvider, UserLookupProvider {

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
        // Fermer l'EntityManager si nécessaire
        if (userRepository != null) {
            userRepository.close();
        }
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realmModel, Map<String, String> params, Integer firstResult, Integer maxResults) {
        logger.debugf("Recherche d'utilisateurs avec params: %s", params);

        String searchParam = params.getOrDefault("keycloak.session.realm.users.query.search",
                params.getOrDefault("email", params.getOrDefault("username", "")));

        if (searchParam == null || searchParam.trim().isEmpty()) {
            logger.debug("Paramètre de recherche vide, retour d'un stream vide");
            return Stream.empty();
        }

        try {
            List<User> users = userRepository.findAllByEmailLike("%" + searchParam.trim() + "%");
            logger.debugf("Trouvé %d utilisateurs pour la recherche: %s", users.size(), searchParam);

            return users.stream()
                    .skip(firstResult != null ? firstResult : 0)
                    .limit(maxResults != null ? maxResults : Integer.MAX_VALUE)
                    .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user));
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche d'utilisateurs: %s", e.getMessage());
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

    @Override
    public UserModel getUserById(RealmModel realmModel, String id) {
        logger.debugf("Recherche utilisateur par ID: %s", id);

        try {
            String externalId = StorageId.externalId(id);
            long persistenceId = Long.parseLong(externalId);

            Optional<User> userOptional = userRepository.findById(persistenceId);

            if (userOptional.isPresent()) {
                logger.debugf("Utilisateur trouvé par ID %s: %s", id, userOptional.get().getLogin());
                return new UserAdapter(keycloakSession, realmModel, componentModel, userOptional.get());
            } else {
                logger.debugf("Aucun utilisateur trouvé pour l'ID: %s", id);
                return null;
            }
        } catch (NumberFormatException e) {
            logger.warnf("ID invalide: %s", id);
            return null;
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par ID %s: %s", id, e.getMessage());
            return null;
        }
    }

    @Override
    public UserModel getUserByUsername(RealmModel realmModel, String username) {
        logger.debugf("Recherche utilisateur par username: %s", username);

        try {
            Optional<User> userOptional = userRepository.findOneByLogin(username);

            if (userOptional.isPresent()) {
                logger.debugf("Utilisateur trouvé par username %s: %s", username, userOptional.get().getEmail());
                return new UserAdapter(keycloakSession, realmModel, componentModel, userOptional.get());
            } else {
                logger.debugf("Aucun utilisateur trouvé pour le username: %s", username);
                return null;
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par username %s: %s", username, e.getMessage());
            return null;
        }
    }

    @Override
    public UserModel getUserByEmail(RealmModel realmModel, String email) {
        logger.debugf("Recherche utilisateur par email: %s", email);

        try {
            Optional<User> userOptional = userRepository.findOneByEmailIgnoreCase(email);

            if (userOptional.isPresent()) {
                logger.debugf("Utilisateur trouvé par email %s: %s", email, userOptional.get().getLogin());
                return new UserAdapter(keycloakSession, realmModel, componentModel, userOptional.get());
            } else {
                logger.debugf("Aucun utilisateur trouvé pour l'email: %s", email);
                return null;
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par email %s: %s", email, e.getMessage());
            return null;
        }
    }
}