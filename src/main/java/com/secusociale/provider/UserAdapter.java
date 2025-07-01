package com.secusociale.provider;

import com.secusociale.entity.Authority;
import com.secusociale.entity.User;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Stream;

public class UserAdapter extends AbstractUserAdapterFederatedStorage {

    private User user;
    private String keycloakId;
    private static final Logger logger = Logger.getLogger(UserAdapter.class);
    
    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, User user) {
        super(session, realm, model);
        this.user = user;
        this.keycloakId = StorageId.keycloakId(model, String.valueOf(user.getId()));
        
        // Log des authorities pour debug
        if (user.getAuthorities() != null && !user.getAuthorities().isEmpty()) {
            logger.infof("👥 Utilisateur %s a %d authorities: %s", 
                user.getLogin(), 
                user.getAuthorities().size(),
                user.getAuthorities().stream()
                    .map(Authority::getName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("aucune"));
        } else {
            logger.warnf("⚠️ Utilisateur %s n'a aucune authority", user.getLogin());
        }
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
        logger.debugf("🔍 getRealmRoleMappingsStream appelé pour l'utilisateur: %s", user.getLogin());
        
        if (user.getAuthorities() == null || user.getAuthorities().isEmpty()) {
            logger.warnf("⚠️ Aucune authority trouvée pour l'utilisateur: %s", user.getLogin());
            return Stream.empty();
        }

        List<RoleModel> roles = new ArrayList<>();
        
        for (Authority authority : user.getAuthorities()) {
            String roleName = authority.getName();
            logger.debugf("🔍 Recherche du rôle Keycloak: %s", roleName);
            
            // Chercher le rôle dans Keycloak
            RoleModel role = realm.getRole(roleName);
            
            if (role != null) {
                roles.add(role);
                logger.debugf("✅ Rôle trouvé et ajouté: %s", roleName);
            } else {
                logger.warnf("⚠️ Rôle non trouvé dans Keycloak: %s - Création automatique", roleName);
                
                // Créer automatiquement le rôle s'il n'existe pas
                try {
                    RoleModel newRole = realm.addRole(roleName);
                    if (newRole != null) {
                        newRole.setDescription("Rôle créé automatiquement depuis la base de données externe");
                        roles.add(newRole);
                        logger.infof("✅ Rôle créé automatiquement: %s", roleName);
                    }
                } catch (Exception e) {
                    logger.errorf(e, "❌ Erreur lors de la création du rôle %s: %s", roleName, e.getMessage());
                }
            }
        }
        
        logger.infof("👥 Rôles finaux pour %s: %s", 
            user.getLogin(), 
            roles.stream()
                .map(RoleModel::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("aucun"));
        
        return roles.stream();
    }

    @Override
    public Stream<RoleModel> getClientRoleMappingsStream(org.keycloak.models.ClientModel client) {
        // Pour ce cas d'usage, nous nous concentrons sur les rôles realm
        logger.debugf("🔍 getClientRoleMappingsStream appelé pour le client: %s", client.getClientId());
        return Stream.empty();
    }

    @Override
    public boolean hasRole(RoleModel role) {
        if (user.getAuthorities() == null) {
            return false;
        }

        boolean hasRole = user.getAuthorities().stream()
                .anyMatch(auth -> auth.getName().equals(role.getName()));
        
        logger.debugf("🔍 hasRole(%s) pour %s: %s", role.getName(), user.getLogin(), hasRole);
        return hasRole;
    }

    @Override
    public void grantRole(RoleModel role) {
        // Lecture seule - ne pas permettre l'ajout de rôles
        logger.warnf("⚠️ Tentative d'ajout du rôle %s à %s (opération non autorisée)", role.getName(), user.getLogin());
        throw new UnsupportedOperationException("Ajout de rôles non autorisé via ce provider");
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        logger.debugf("🔍 getRoleMappingsStream appelé pour l'utilisateur: %s", user.getLogin());
        return getRealmRoleMappingsStream();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        // Lecture seule - ne pas permettre la suppression de rôles
        logger.warnf("⚠️ Tentative de suppression du rôle %s pour %s (opération non autorisée)", role.getName(), user.getLogin());
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
                ", authorities=" + (user.getAuthorities() != null ? user.getAuthorities().size() : 0) +
                '}';
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        logger.infof("🔍 getRequiredActionsStream appelé pour l'utilisateur: %s", getUsername());
        logger.infof("📊 État utilisateur - Activé: %s, PasswordUpdated: %s, Locked: %s",
                user.isActivated(), user.isHasPasswordUpdated(), user.isLocked());

        // Pour éviter les actions requises automatiques, retourner un stream vide
        // ou gérer selon votre logique métier

        List<String> requiredActions = new ArrayList<>();

        // Exemple de logique conditionnelle :
        // Si l'utilisateur n'a jamais mis à jour son mot de passe
        if (!user.isHasPasswordUpdated()) {
            logger.infof("⚠️ Utilisateur %s n'a pas mis à jour son mot de passe", getUsername());
            // Vous pouvez forcer une mise à jour du mot de passe
            // requiredActions.add(UserModel.RequiredAction.UPDATE_PASSWORD.toString());
        }

        // Si l'email n'est pas vérifié et vous voulez le forcer
        if (!user.isActivated()) {
            logger.infof("⚠️ Utilisateur %s n'est pas activé", getUsername());
            // requiredActions.add(UserModel.RequiredAction.VERIFY_EMAIL.toString());
        }

        logger.infof("✅ Actions requises pour %s: %s", getUsername(),
                requiredActions.isEmpty() ? "Aucune" : String.join(", ", requiredActions));

        // Pour l'instant, on retourne un stream vide pour éviter les blocages
        return requiredActions.stream();
    }

    @Override
    public void addRequiredAction(String action) {
        // Optionnel : loguer ou ignorer les ajouts d'actions
        logger.infof("➕ Tentative d'ajout de l'action requise '%s' pour l'utilisateur %s (ignorée)", action, getUsername());
    }

    @Override
    public void removeRequiredAction(String action) {
        // Optionnel : loguer les suppressions
        logger.infof("➖ Suppression de l'action requise '%s' pour l'utilisateur %s", action, getUsername());
    }

    @Override
    public void addRequiredAction(UserModel.RequiredAction action) {
        addRequiredAction(action.toString());
    }

    @Override
    public void removeRequiredAction(UserModel.RequiredAction action) {
        removeRequiredAction(action.toString());
    }
}