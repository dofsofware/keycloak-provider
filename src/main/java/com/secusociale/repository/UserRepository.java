package com.secusociale.repository;

import com.secusociale.entity.Authority;
import com.secusociale.entity.User;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository utilisant une connexion JDBC directe vers la base de données externe
 */
public class UserRepository {

    private static final Logger logger = Logger.getLogger(UserRepository.class);
    private DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        logger.debug("UserRepository créé avec DataSource");
    }

    public Optional<User> findById(Long id) {
        try {
            logger.debugf("Recherche utilisateur par ID: %d", id);

            String sql = "SELECT u.id, u.login, u.password_hash, u.first_name, u.last_name, u.phone, u.email, " +
                    "u.activated, u.locked, u.has_password_updated, u.lang_key, u.image_url, u.type_compte, " +
                    "u.institution, u.agence, u.activation_key, u.reset_key, u.reset_date, u.expiration_date, u.otp, u.cachet " +
                    "FROM jhi_user u WHERE u.id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, id);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        User user = mapResultSetToUser(rs);
                        // Charger les authorities
                        loadUserAuthorities(user, conn);
                        logger.debugf("Utilisateur trouvé: %s", user.getLogin());
                        return Optional.of(user);
                    } else {
                        logger.debugf("Aucun utilisateur trouvé pour l'ID: %d", id);
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par ID %d: %s", id, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<User> findOneByLogin(String login) {
        try {
            logger.debugf("Recherche utilisateur par login: %s", login);

            String sql = "SELECT u.id, u.login, u.password_hash, u.first_name, u.last_name, u.phone, u.email, " +
                    "u.activated, u.locked, u.has_password_updated, u.lang_key, u.image_url, u.type_compte, " +
                    "u.institution, u.agence, u.activation_key, u.reset_key, u.reset_date, u.expiration_date, u.otp, u.cachet " +
                    "FROM jhi_user u WHERE u.login = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, login);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        User user = mapResultSetToUser(rs);
                        // Charger les authorities
                        loadUserAuthorities(user, conn);
                        logger.debugf("Utilisateur trouvé par login %s: %s", login, user.getEmail());
                        return Optional.of(user);
                    } else {
                        logger.debugf("Aucun utilisateur trouvé pour le login: %s", login);
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par login %s: %s", login, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<User> findOneByEmailIgnoreCase(String email) {
        try {
            logger.debugf("Recherche utilisateur par email: %s", email);

            String sql = "SELECT u.id, u.login, u.password_hash, u.first_name, u.last_name, u.phone, u.email, " +
                    "u.activated, u.locked, u.has_password_updated, u.lang_key, u.image_url, u.type_compte, " +
                    "u.institution, u.agence, u.activation_key, u.reset_key, u.reset_date, u.expiration_date, u.otp, u.cachet " +
                    "FROM jhi_user u WHERE LOWER(u.email) = LOWER(?)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, email);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        User user = mapResultSetToUser(rs);
                        // Charger les authorities
                        loadUserAuthorities(user, conn);
                        logger.debugf("Utilisateur trouvé par email %s: %s", email, user.getLogin());
                        return Optional.of(user);
                    } else {
                        logger.debugf("Aucun utilisateur trouvé pour l'email: %s", email);
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par email %s: %s", email, e.getMessage());
            return Optional.empty();
        }
    }

    public List<User> findAllByEmailLike(String searchTerm) {
        try {
            logger.debugf("Recherche utilisateurs par terme: %s", searchTerm);

            String sql = "SELECT u.id, u.login, u.password_hash, u.first_name, u.last_name, u.phone, u.email, " +
                    "u.activated, u.locked, u.has_password_updated, u.lang_key, u.image_url, u.type_compte, " +
                    "u.institution, u.agence, u.activation_key, u.reset_key, u.reset_date, u.expiration_date, u.otp, u.cachet " +
                    "FROM jhi_user u WHERE LOWER(u.email) LIKE LOWER(?) OR LOWER(u.login) LIKE LOWER(?) " +
                    "ORDER BY u.id LIMIT 50";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, searchTerm);
                stmt.setString(2, searchTerm);

                List<User> users = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        User user = mapResultSetToUser(rs);
                        // Charger les authorities pour chaque utilisateur
                        loadUserAuthorities(user, conn);
                        users.add(user);
                    }
                }

                logger.debugf("Trouvé %d utilisateurs pour la recherche: %s", users.size(), searchTerm);
                return users;
            }
        } catch (Exception e) {
            logger.errorf(e, "Erreur lors de la recherche par terme %s: %s", searchTerm, e.getMessage());
            return List.of();
        }
    }

    /**
     * Charge les authorities d'un utilisateur
     */
    private void loadUserAuthorities(User user, Connection conn) throws SQLException {
        String sql = "SELECT a.name FROM jhi_authority a " +
                "INNER JOIN jhi_user_authority ua ON a.name = ua.authority_name " +
                "WHERE ua.user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, user.getId());

            Set<Authority> authorities = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Authority authority = new Authority();
                    authority.setName(rs.getString("name"));
                    authorities.add(authority);
                }
            }

            user.setAuthorities(authorities);
            logger.debugf("Chargé %d authorities pour l'utilisateur %s", authorities.size(), user.getLogin());
        }
    }

    /**
     * Mappe un ResultSet vers un objet User
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();

        user.setId(rs.getLong("id"));
        user.setLogin(rs.getString("login"));
        user.setPassword(rs.getString("password_hash"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPhone(rs.getString("phone"));
        user.setEmail(rs.getString("email"));
        user.setActivated(rs.getBoolean("activated"));
        user.setLocked(rs.getBoolean("locked"));
        user.setHasPasswordUpdated(rs.getBoolean("has_password_updated"));
        user.setLangKey(rs.getString("lang_key"));
        user.setImageUrl(rs.getString("image_url"));
        user.setTypeCompte(rs.getString("type_compte"));
        user.setInstitution(rs.getString("institution"));
        user.setAgence(rs.getString("agence"));
        user.setActivationKey(rs.getString("activation_key"));
        user.setResetKey(rs.getString("reset_key"));

        // Gestion des dates
        Timestamp resetDate = rs.getTimestamp("reset_date");
        if (resetDate != null) {
            user.setResetDate(resetDate.toInstant());
        }

        Timestamp expirationDate = rs.getTimestamp("expiration_date");
        if (expirationDate != null) {
            user.setExpirationDate(expirationDate.toInstant());
        }

        user.setOtp(rs.getString("otp"));
        user.setCachet(rs.getString("cachet"));

        return user;
    }

    /**
     * Test de connectivité de base
     */
    public boolean testConnection() {
        try {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM jhi_user");
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    long count = rs.getLong(1);
                    logger.infof("Test de connexion réussi. Nombre d'utilisateurs: %d", count);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.errorf(e, "Échec du test de connexion: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Ferme les ressources si nécessaire
     */
    public void close() {
        try {
            // La DataSource est gérée par le factory, pas besoin de la fermer ici
            logger.debug("UserRepository fermé");
        } catch (Exception e) {
            logger.warnf(e, "Erreur lors de la fermeture du repository: %s", e.getMessage());
        }
    }
}