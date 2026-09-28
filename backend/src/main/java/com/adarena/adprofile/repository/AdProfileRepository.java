package com.adarena.adprofile.repository;

import com.adarena.adprofile.domain.AdProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdProfileRepository extends JpaRepository<AdProfile, UUID> {

    Optional<AdProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<AdProfile> findByUserIdIn(Collection<UUID> userIds);
}
