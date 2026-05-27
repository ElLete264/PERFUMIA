package org.vedruna.perfumia.persistance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vedruna.perfumia.persistance.model.CommunityMessage;

@Repository
public interface CommunityMessageRepository extends JpaRepository<CommunityMessage, Integer> {
    List<CommunityMessage> findTop60ByOrderByCreateDateDesc();
}
