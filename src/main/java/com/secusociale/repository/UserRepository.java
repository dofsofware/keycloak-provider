package com.secusociale.repository;

import com.secusociale.entity.Authority;
import com.secusociale.entity.User;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * Spring Data JPA repository for the {@link User} entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String USERS_BY_LOGIN_CACHE = "usersByLogin";

    String USERS_BY_EMAIL_CACHE = "usersByEmail";

    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant dateTime);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByPhoneIgnoreCase(String phone);



    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.phone) = LOWER(:phone)")
    long countByPhoneIgnoreCase(@Param("phone") String phone);

    Optional<User> findOneByLogin(String login);
    Optional<User> findOneByEmail(String email);

    List<User> findAllByEmailLike(String email);

    User findByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesById(Long id);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

    Page<User> findAllByLoginNot(Pageable pageable, String login);

    Page<User> findAllByTypeCompteAndAuthoritiesContains(Pageable pageable, String typeCompte, Authority authority);

    Page<User> findAllByTypeCompteInAndAuthoritiesContains(Pageable pageable, List<String> typeComptes, Authority authority);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.otpCodes otp WHERE u.id = :userId AND otp.code = :codeOTP AND otp.expirationDate > :now")
    boolean existsByIdAndValidCodeOTP(@Param("userId") Long userId, @Param("codeOTP") String codeOTP, @Param("now") Instant now );

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE (u.login = :userName OR u.phone = :telephone) AND u.typeCompte = :typeCompte")
    boolean existsByUserNameOrTelephoneAndUserTypeCompte(
            @Param("userName") String userName,
            @Param("telephone") String telephone,
            @Param("typeCompte") String typeCompte);

    @Query("SELECT u FROM User u WHERE (u.login = :userName OR u.phone = :telephone) AND u.typeCompte = :typeCompte")
    Optional<User> findByUserNameOrPhoneNumber(
            @Param("userName") String userName,
            @Param("telephone") String telephone,
            @Param("typeCompte") String typeCompte);

    Optional<User> findFirstByEmailIgnoreCase(String email);

    Long countByTypeCompteAndCreatedDateBetween(String typeCompte, Instant  fromDate, Instant  toDate);
    Long countByTypeCompteAndActivatedTrueAndCreatedDateBetween(String typeCompte, Instant  fromDate, Instant  toDate);
    Long countByTypeCompteInAndCreatedDateBetween(List<String> typeComptes, Instant  fromDate, Instant  toDate);
    Long countByTypeCompteInAndActivatedTrueAndCreatedDateBetween(List<String> typeComptes, Instant  fromDate, Instant  toDate);
}

