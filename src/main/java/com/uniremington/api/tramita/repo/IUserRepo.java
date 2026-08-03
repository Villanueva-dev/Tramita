package com.uniremington.api.tramita.repo;

import java.util.Optional;
import java.util.UUID;

import com.uniremington.api.tramita.util.EmailNormalizer;
import com.uniremington.api.tramita.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IUserRepo extends JpaRepository<User, UUID> {

    /** El caller normaliza el email con {@link EmailNormalizer} antes de buscar. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
