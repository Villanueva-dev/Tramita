package com.uniremington.api.tramita.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** El caller normaliza el email con {@link EmailNormalizer} antes de buscar. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
