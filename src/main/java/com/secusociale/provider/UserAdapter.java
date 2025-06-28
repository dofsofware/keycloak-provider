package com.secusociale.provider;

import com.secusociale.entity.Authority;
import com.secusociale.entity.User;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.time.ZoneId;
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
        user.setLogin(username);
    }

    @Override
    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public void setEmail(String email) {
        user.setEmail(email);
    }

    @Override
    public boolean isEmailVerified() {
        return user.isActivated();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        user.setActivated(verified);
    }

    @Override
    public String getFirstName() {
        return user.getFirstName();
    }

    @Override
    public void setFirstName(String firstName) {
        user.setFirstName(firstName);
    }

    @Override
    public String getLastName() {
        return user.getLastName();
    }

    @Override
    public void setLastName(String lastName) {
        user.setLastName(lastName);
    }

    @Override
    public boolean isEnabled() {
        return user.isActivated() && !user.isLocked();
    }

    @Override
    public void setEnabled(boolean enabled) {
        user.setActivated(enabled);
    }

    @Override
    public Long getCreatedTimestamp() {
        // Utiliser la date de création si disponible dans l'entité parente
        return null;
    }

    // Attributs personnalisés
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

        if (user.getExpirationDate() != null) {
            attrs.put("expirationDate", Collections.singletonList(user.getExpirationDate().toString()));
        }

        return attrs;
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        if (values == null || values.isEmpty()) return;
        String value = values.get(0);

        switch (name) {
            case "phone":
                user.setPhone(value);
                break;
            case "typeCompte":
                user.setTypeCompte(value);
                break;
            case "institution":
                user.setInstitution(value);
                break;
            case "agence":
                user.setAgence(value);
                break;
            case "langKey":
                user.setLangKey(value);
                break;
            case "imageUrl":
                user.setImageUrl(value);
                break;
            case "hasPasswordUpdated":
                user.setHasPasswordUpdated(Boolean.parseBoolean(value));
                break;
            default:
                super.setAttribute(name, values);
                break;
        }
    }

    @Override
    public void removeAttribute(String name) {
        switch (name) {
            case "phone":
                user.setPhone(null);
                break;
            case "typeCompte":
                user.setTypeCompte(null);
                break;
            case "institution":
                user.setInstitution(null);
                break;
            case "agence":
                user.setAgence(null);
                break;
            case "langKey":
                user.setLangKey(null);
                break;
            case "imageUrl":
                user.setImageUrl(null);
                break;
            default:
                super.removeAttribute(name);
                break;
        }
    }

    // Gestion des rôles basée sur les authorities
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
        // Cette méthode pourrait être implémentée si vous voulez permettre
        // l'ajout de rôles depuis Keycloak
        // Pour l'instant, nous considérons les rôles comme en lecture seule
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        return getRealmRoleMappingsStream();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        // Rôles en lecture seule pour ce cas d'usage
    }

    // Méthodes utilitaires
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
                '}';
    }
}