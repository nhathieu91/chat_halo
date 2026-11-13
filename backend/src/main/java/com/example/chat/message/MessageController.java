package com.example.chat.message;

import com.example.chat.room.ChatRoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class MessageController {
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final MessageEncryptionService encryption;

    public MessageController(
        ChatRoomRepository rooms, ChatMessageRepository messages, MessageEncryptionService encryption
    ) {
        this.rooms = rooms;
        this.messages = messages;
        this.encryption = encryption;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<MessageResponse> history(@PathVariable UUID roomId, Authentication authentication) {
        if (!rooms.existsByIdAndMembersUsername(roomId, authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        return messages.findTop100ByRoomIdOrderByCreatedAtAsc(roomId).stream()
            .map(message -> new MessageResponse(message.getId(), roomId,
                message.getSender().getUsername(),
                encryption.decrypt(message.getContent(), message.getEncryptionNonce()), message.getCreatedAt()))
            .toList();
    }

    public record MessageResponse(UUID id, UUID roomId, String sender, String content, Instant createdAt) {}
}
