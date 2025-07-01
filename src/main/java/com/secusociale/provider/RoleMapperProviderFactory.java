package com.secusociale.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;

import java.util.Date;
import java.util.List;

/**
 * Factory pour la synchronisation des rôles depuis la base de données externe
 */
public class RoleMapperProviderFactory {

    /**
     * Synchronise les rôles depuis la base de données vers Keycloak
     */
    public static class RoleSynchronizer implements ImportSynchronization {

        private final CustomUserStorageProvider provider;

        public RoleSynchronizer(CustomUserStorageProvider provider) {
            this.provider = provider;
        }

        @Override
        public SynchronizationResult sync(KeycloakSessionFactory sessionFactory, String realmId, UserStorageProviderModel model) {
            // Implémentation de la synchronisation des rôles
            // Cette méthode sera appelée périodiquement par Keycloak

            SynchronizationResult result = new SynchronizationResult();

            try (KeycloakSession session = sessionFactory.create()) {
                session.getTransactionManager().begin();

                try {
                    RealmModel realm = session.realms().getRealm(realmId);

                    if (realm == null) {
                        result.setFailed(1);
                        return result;
                    }

                    // Récupérer tous les rôles uniques depuis la base de données
                    // et les créer dans Keycloak s'ils n'existent pas

                    result.setAdded(0);
                    result.setUpdated(0);
                    result.setRemoved(0);
                    result.setFailed(0);

                    session.getTransactionManager().commit();

                } catch (Exception e) {
                    session.getTransactionManager().rollback();
                    throw e;
                }

            } catch (Exception e) {
                result.setFailed(1);
            }

            return result;
        }

        @Override
        public SynchronizationResult syncSince(Date lastSync, KeycloakSessionFactory sessionFactory, String realmId, UserStorageProviderModel model) {
            // Synchronisation incrémentale depuis une date donnée
            return sync(sessionFactory, realmId, model);
        }
    }
}