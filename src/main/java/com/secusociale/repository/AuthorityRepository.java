package com.secusociale.repository;

import com.secusociale.entity.Authority;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the {@link Authority} entity.
 */
public interface AuthorityRepository extends JpaRepository<Authority, String> {
    boolean existsByName(String name);

    Authority findByName(String name);
}
