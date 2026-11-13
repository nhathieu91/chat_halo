package com.example.chat.websocket;

import com.example.chat.message.ChatMessage;
import com.example.chat.message.ChatMessageRepository;
import com.example.chat.message.MessageController.MessageResponse;
import com.example.chat.message.MessageEncryptionService;
import com.example.chat.room.ChatRoom;
import com.example.chat.room.ChatRoomRepository;
import com.example.chat.user.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatSocketController {
    private final ChatRoomRepository rooms;
    private final UserRepository users;
    private final ChatMessageRepository messages;
    private final SimpMessagingTemplate messaging;
    private final MessageEncryptionService encryption;

    public ChatSocketController(
        ChatRoomRepository rooms, UserRepository users,
        ChatMessageRepository messages, SimpMessagingTemplate messaging,
        MessageEncryptionService encryption
    ) {
        this.rooms = rooms;
        this.users = users;
        this.messages = messages;
        this.messaging = messaging;
        this.encryption = encryption;
    }

    @MessageMapping("/chat.send")
    @Transactional
    public void send(@Payload SendMessageRequest request, Authentication authentication) {
        if (request == null || request.roomId() == null || request.content() == null
            || request.content().isBlank() || request.content().length() > 4000) {
            throw new IllegalArgumentException("A room and message of 1-4000 characters are required");
        }
        ChatRoom room = rooms.findById(request.roomId())
            .filter(candidate -> rooms.existsByIdAndMembersUsername(candidate.getId(), authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        var sender = users.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        MessageEncryptionService.EncryptedMessage encrypted = encryption.encrypt(request.content().trim());
        ChatMessage saved = messages.save(new ChatMessage(
            room, sender, encrypted.ciphertext(), encrypted.nonce()));
        MessageResponse response = new MessageResponse(
            saved.getId(), room.getId(), sender.getUsername(),
            encryption.decrypt(saved.getContent(), saved.getEncryptionNonce()), saved.getCreatedAt());
        messaging.convertAndSend("/topic/rooms/" + room.getId(), response);
    }

    public record SendMessageRequest(UUID roomId, String content) {}
}
