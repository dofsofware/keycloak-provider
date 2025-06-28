package com.secusociale.provider;

import com.secusociale.entity.User;
import com.secusociale.repository.UserRepository;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomUserStorageProvider implements UserStorageProvider,
        UserRegistrationProvider,
        UserQueryProvider, UserLookupProvider {

    private ComponentModel componentModel;
    private KeycloakSession keycloakSession;
    private UserRepository userRepository;

    public CustomUserStorageProvider(KeycloakSession keycloakSession, ComponentModel componentModel, UserRepository userRepository) {
        this.keycloakSession = keycloakSession;
        this.componentModel = componentModel;
        this.userRepository = userRepository;
    }

    @Override
    public void close() {
        // Méthode vide - la gestion des ressources est déléguée au repository Spring
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realmModel, Map<String, String> map, Integer firstResult, Integer maxResults) {
        String searchParam = map.getOrDefault("keycloak.session.realm.users.query.search",
                map.getOrDefault("email", map.getOrDefault("username", "")));

        if (searchParam.isEmpty()) {
            return Stream.empty();
        }

        // Utilisation du repository pour rechercher par email
        List<User> users = userRepository.findAllByEmailLike("%" + searchParam + "%");

        return users.stream()
                .limit(maxResults != null ? maxResults : Integer.MAX_VALUE)
                .skip(firstResult != null ? firstResult : 0)
                .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user));
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realmModel, GroupModel groupModel, Integer firstResult, Integer maxResults) {
        // Non implémenté dans cette version
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realmModel, String attributeName, String attributeValue) {
        // Non implémenté dans cette version
        return Stream.empty();
    }

    @Override
    public UserModel addUser(RealmModel realmModel, String username) {
        // Non implémenté - l'ajout d'utilisateurs se fait via l'application principale
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realmModel, UserModel userModel) {
        // Non implémenté - la suppression d'utilisateurs se fait via l'application principale
        return false;
    }

    @Override
    public UserModel getUserById(RealmModel realmModel, String id) {
        try {
            long persistenceId = Long.parseLong(StorageId.externalId(id));
            Optional<User> userOptional = userRepository.findById(persistenceId);

            return userOptional
                    .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user))
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public UserModel getUserByUsername(RealmModel realmModel, String userName) {
        Optional<User> userOptional = userRepository.findOneByLogin(userName);

        return userOptional
                .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user))
                .orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realmModel, String email) {
        Optional<User> userOptional = userRepository.findOneByEmailIgnoreCase(email);

        return userOptional
                .map(user -> new UserAdapter(keycloakSession, realmModel, componentModel, user))
                .orElse(null);
    }

    // Setters pour l'injection de dépendances
    public void setModel(ComponentModel componentModel) {
        this.componentModel = componentModel;
    }

    public void setSession(KeycloakSession keycloakSession) {
        this.keycloakSession = keycloakSession;
    }

    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}