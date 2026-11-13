package com.example.chat.message;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class MessageEncryptionKeyProvider implements ApplicationRunner {
    private static final int KEY_LENGTH_BYTES = 32;
    private final MessageEncryptionKeyRepository keys;
    private final SecureRandom secureRandom = new SecureRandom();
    private volatile SecretKey key;

    public MessageEncryptionKeyProvider(MessageEncryptionKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    public void run(ApplicationArguments args) {
        MessageEncryptionKey stored = keys.findById(MessageEncryptionKey.KEY_NAME)
            .orElseGet(this::createKey);
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(stored.getValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored message encryption key is not valid Base64", exception);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("Stored message encryption key must be 256 bits");
        }
        key = new SecretKeySpec(keyBytes, "AES");
    }

    public SecretKey getKey() {
        SecretKey current = key;
        if (current == null) {
            throw new IllegalStateException("Message encryption key has not been initialized");
        }
        return current;
    }

    private MessageEncryptionKey createKey() {
        byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
        secureRandom.nextBytes(keyBytes);
        return keys.saveAndFlush(new MessageEncryptionKey(
            MessageEncryptionKey.KEY_NAME, Base64.getEncoder().encodeToString(keyBytes)));
    }
}
