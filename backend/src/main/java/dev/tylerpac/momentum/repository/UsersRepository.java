package dev.tylerpac.momentum.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.momentum.model.Users;

public interface UsersRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<Users> findByExternalSubject(String externalSubject);
}
