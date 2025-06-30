package com.secusociale.provider;

import com.secusociale.entity.Authority;
import com.secusociale.entity.User;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.*;
import java.util.stream.Stream;

public class UserAdapter extends AbstractUserAdapterFederatedStorage {

    private User user;
    private String keycloakId;

    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, User user) {
        super(session, realm, model);
        this.user = user;
        this.keycloakId = StorageId.keycloakId(model, String.valueOf(user.getId()));
    }

    @Override
    public String getId() {
        return keycloakId;
    }

    @Override
    public String getUsername() {
        return user.getLogin();
    }

    @Override
    public void setUsername(String username) {
        // Lecture seule - ne pas permettre la modification
        throw new UnsupportedOperationException("Modification du username non autorisée via ce provider");
    }

    @Override
    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public void setEmail(String email) {
        // Lecture seule - ne pas permettre la modification
        throw new UnsupportedOperationException("Modification de l'email non autorisée via ce provider");
    }

    @Override
    public boolean isEmailVerified() {
        return user.isActivated();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        // Lecture seule - ne pas permettre la modification
        throw new UnsupportedOperationException("Modification de la vérification email non autorisée via ce provider");
    }

    @Override
    public String getFirstName() {
        return user.getFirstName();
    }

    @Override
    public void setFirstName(String firstName) {
        // Lecture seule - ne pas permettre la modification
        throw new UnsupportedOperationException("Modification du prénom non autorisée via ce provider");
    }

    @Override
    public String getLastName() {
        return user.getLastName();
    }

    @Override
    public void setLastName(String lastName) {
        // Lecture seule - ne pas permettre la modification
        throw new UnsupportedOperationException("Modification du nom non autorisée via ce provider");
    }

    @Override
    public boolean isEnabled() {
        return user.isActivated() && !user.isLocked();
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Lecture seule - ne pas permettre la modification
        throw new UnsupportedOperationException("Modification du statut activé non autorisée via ce provider");
    }

    @Override
    public Long getCreatedTimestamp() {
        // Pas de date de création dans l'entité User actuelle
        return null;
    }

    // ========== ATTRIBUTS PERSONNALISÉS ==========

    @Override
    public Stream<String> getAttributeStream(String name) {
        List<String> result = new ArrayList<>();

        switch (name) {
            case "phone":
                if (user.getPhone() != null) {
                    result.add(user.getPhone());
                }
                break;
            case "typeCompte":
                if (user.getTypeCompte() != null) {
                    result.add(user.getTypeCompte());
                }
                break;
            case "institution":
                if (user.getInstitution() != null) {
                    result.add(user.getInstitution());
                }
                break;
            case "agence":
                if (user.getAgence() != null) {
                    result.add(user.getAgence());
                }
                break;
            case "langKey":
                if (user.getLangKey() != null) {
                    result.add(user.getLangKey());
                }
                break;
            case "imageUrl":
                if (user.getImageUrl() != null) {
                    result.add(user.getImageUrl());
                }
                break;
            case "hasPasswordUpdated":
                result.add(String.valueOf(user.isHasPasswordUpdated()));
                break;
            case "expirationDate":
                if (user.getExpirationDate() != null) {
                    result.add(user.getExpirationDate().toString());
                }
                break;
            case "locked":
                result.add(String.valueOf(user.isLocked()));
                break;
            default:
                return super.getAttributeStream(name);
        }

        return result.stream();
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> attrs = super.getAttributes();

        if (user.getPhone() != null) {
            attrs.put("phone", Collections.singletonList(user.getPhone()));
        }
        if (user.getTypeCompte() != null) {
            attrs.put("typeCompte", Collections.singletonList(user.getTypeCompte()));
        }
        if (user.getInstitution() != null) {
            attrs.put("institution", Collections.singletonList(user.getInstitution()));
        }
        if (user.getAgence() != null) {
            attrs.put("agence", Collections.singletonList(user.getAgence()));
        }
        if (user.getLangKey() != null) {
            attrs.put("langKey", Collections.singletonList(user.getLangKey()));
        }
        if (user.getImageUrl() != null) {
            attrs.put("imageUrl", Collections.singletonList(user.getImageUrl()));
        }
        attrs.put("hasPasswordUpdated", Collections.singletonList(String.valueOf(user.isHasPasswordUpdated())));
        attrs.put("locked", Collections.singletonList(String.valueOf(user.isLocked())));

        if (user.getExpirationDate() != null) {
            attrs.put("expirationDate", Collections.singletonList(user.getExpirationDate().toString()));
        }

        return attrs;
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        // Lecture seule - ne pas permettre la modification des attributs
        throw new UnsupportedOperationException("Modification des attributs non autorisée via ce provider");
    }

    @Override
    public void removeAttribute(String name) {
        // Lecture seule - ne pas permettre la suppression des attributs
        throw new UnsupportedOperationException("Suppression des attributs non autorisée via ce provider");
    }

    // ========== GESTION DES RÔLES ==========

    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        if (user.getAuthorities() == null || user.getAuthorities().isEmpty()) {
            return Stream.empty();
        }

        return user.getAuthorities().stream()
                .map(Authority::getName)
                .map(roleName -> realm.getRole(roleName))
                .filter(Objects::nonNull);
    }

    @Override
    public Stream<RoleModel> getClientRoleMappingsStream(org.keycloak.models.ClientModel client) {
        // Pour ce cas d'usage, nous nous concentrons sur les rôles realm
        return Stream.empty();
    }

    @Override
    public boolean hasRole(RoleModel role) {
        if (user.getAuthorities() == null) {
            return false;
        }

        return user.getAuthorities().stream()
                .anyMatch(auth -> auth.getName().equals(role.getName()));
    }

    @Override
    public void grantRole(RoleModel role) {
        // Lecture seule - ne pas permettre l'ajout de rôles
        throw new UnsupportedOperationException("Ajout de rôles non autorisé via ce provider");
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        return getRealmRoleMappingsStream();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        // Lecture seule - ne pas permettre la suppression de rôles
        throw new UnsupportedOperationException("Suppression de rôles non autorisée via ce provider");
    }

    // ========== MÉTHODES UTILITAIRES ==========

    public User getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "UserAdapter{" +
                "id=" + getId() +
                ", username='" + getUsername() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", enabled=" + isEnabled() +
                ", locked=" + user.isLocked() +
                '}';
    }
}