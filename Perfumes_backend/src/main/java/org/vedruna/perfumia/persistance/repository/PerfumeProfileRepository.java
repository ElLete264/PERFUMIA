package org.vedruna.perfumia.persistance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vedruna.perfumia.persistance.model.PerfumeProfile;
import org.vedruna.perfumia.persistance.model.User;

@Repository
public interface PerfumeProfileRepository extends JpaRepository<PerfumeProfile, Integer> {
    Optional<PerfumeProfile> findByUser(User user);
}
