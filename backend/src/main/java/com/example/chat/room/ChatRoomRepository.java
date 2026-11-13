package com.example.chat.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    List<ChatRoom> findDistinctByMembersUsernameOrderByCreatedAtDesc(String username);
    boolean existsByIdAndMembersUsername(UUID id, String username);
    Optional<ChatRoom> findByIdAndMembersUsername(UUID id, String username);
}
