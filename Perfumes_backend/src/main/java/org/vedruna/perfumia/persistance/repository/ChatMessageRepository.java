package org.vedruna.perfumia.persistance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vedruna.perfumia.persistance.model.ChatMessage;
import org.vedruna.perfumia.persistance.model.User;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {
    List<ChatMessage> findTop12ByUserOrderByCreateDateDesc(User user);
    void deleteByUser(User user);
}
