package com.example.chat.message;

import com.example.chat.room.ChatRoom;
import com.example.chat.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "encryption_nonce", length = 32)
    private String encryptionNonce;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ChatMessage() {}

    public ChatMessage(ChatRoom room, AppUser sender, String content) {
        this.room = room;
        this.sender = sender;
        this.content = content;
    }

    public ChatMessage(ChatRoom room, AppUser sender, String ciphertext, String nonce) {
        this.room = room;
        this.sender = sender;
        this.content = ciphertext;
        this.encryptionNonce = nonce;
    }

    public UUID getId() { return id; }
    public ChatRoom getRoom() { return room; }
    public AppUser getSender() { return sender; }
    public String getContent() { return content; }
    public String getEncryptionNonce() { return encryptionNonce; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEncryptedContent(String ciphertext, String nonce) {
        this.content = ciphertext;
        this.encryptionNonce = nonce;
    }
}
