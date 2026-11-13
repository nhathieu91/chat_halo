package com.example.chat.message;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)
public class LegacyMessageEncryptionRunner implements ApplicationRunner {
    private final ChatMessageRepository messages;
    private final MessageEncryptionService encryption;

    public LegacyMessageEncryptionRunner(ChatMessageRepository messages, MessageEncryptionService encryption) {
        this.messages = messages;
        this.encryption = encryption;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ChatMessage> plaintextMessages = messages.findByEncryptionNonceIsNull();
        for (ChatMessage message : plaintextMessages) {
            MessageEncryptionService.EncryptedMessage encrypted = encryption.encrypt(message.getContent());
            message.setEncryptedContent(encrypted.ciphertext(), encrypted.nonce());
        }
    }
}
