package com.example.chat.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_secrets")
public class MessageEncryptionKey {
    public static final String KEY_NAME = "chat-message-aes-256-gcm-v1";

    @Id
    @Column(name = "key_name", nullable = false, length = 100)
    private String name;

    @Column(name = "secret_value", nullable = false, columnDefinition = "text")
    private String value;

    protected MessageEncryptionKey() {}

    public MessageEncryptionKey(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return name; }
    public String getValue() { return value; }
}
