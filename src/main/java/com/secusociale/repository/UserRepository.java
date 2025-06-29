package com.secusociale.repository;

import com.secusociale.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA simple pour les utilisateurs - Compatible avec Keycloak JTA
 */
public class UserRepository {
    
    private static final Logger logger = Logger.getLogger(UserRepository.class);
    private EntityManager entityManager;
    
    public UserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
        logger.debug("UserRepository créé avec EntityManager");
    }
    
    public Optional<User> findById(Long id) {
        try {
            logger.debugf("Recherche utilisateur par ID: %d", id);
            User user = entityManager.find(User.class, id);
            if (user != null) {
                logger.debugf("Utilisateur trouvé: %s", user.getLogin());
            } else {
                logger.debugf("Aucun utilisateur trouvé pour l'ID: %d", id);
            }
            return Optional.ofNullable(user);
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par ID %d: %s", id, e.getMessage());
            return Optional.empty();
        }
    }
    
    public Optional<User> findOneByLogin(String login) {
        try {
            logger.debugf("Recherche utilisateur par login: %s", login);
            
            // Utiliser une requête native pour éviter les problèmes de mapping
            TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.login = :login", User.class);
            query.setParameter("login", login);
            
            try {
                User user = query.getSingleResult();
                logger.debugf("Utilisateur trouvé par login %s: %s", login, user.getEmail());
                return Optional.of(user);
            } catch (NoResultException e) {
                logger.debugf("Aucun utilisateur trouvé pour le login: %s", login);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par login %s: %s", login, e.getMessage());
            return Optional.empty();
        }
    }
    
    public Optional<User> findOneByEmailIgnoreCase(String email) {
        try {
            logger.debugf("Recherche utilisateur par email: %s", email);
            
            TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class);
            query.setParameter("email", email);
            
            try {
                User user = query.getSingleResult();
                logger.debugf("Utilisateur trouvé par email %s: %s", email, user.getLogin());
                return Optional.of(user);
            } catch (NoResultException e) {
                logger.debugf("Aucun utilisateur trouvé pour l'email: %s", email);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par email %s: %s", email, e.getMessage());
            return Optional.empty();
        }
    }
    
    public List<User> findAllByEmailLike(String email) {
        try {
            logger.debugf("Recherche utilisateurs par email like: %s", email);
            
            TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(:email)", User.class);
            query.setParameter("email", email);
            query.setMaxResults(50); // Limiter les résultats pour éviter les problèmes de performance
            
            List<User> users = query.getResultList();
            logger.debugf("Trouvé %d utilisateurs pour la recherche: %s", users.size(), email);
            return users;
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par email like %s: %s", email, e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Test de connectivité de base
     */
    public boolean testConnection() {
        try {
            Long count = entityManager.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                    .getSingleResult();
            logger.infof("Test de connexion réussi. Nombre d'utilisateurs: %d", count);
            return true;
        } catch (Exception e) {
            logger.errorf(e, "Échec du test de connexion: %s", e.getMessage());
            return false;
        }
    }
}