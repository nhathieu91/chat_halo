package com.example.chat.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageEncryptionServiceTest {
    private MessageEncryptionService encryption;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        java.util.Arrays.fill(keyBytes, (byte) 7);
        MessageEncryptionKeyProvider keyProvider = mock(MessageEncryptionKeyProvider.class);
        when(keyProvider.getKey()).thenReturn(new SecretKeySpec(keyBytes, "AES"));
        encryption = new MessageEncryptionService(keyProvider);
    }

    @Test
    void encryptsAndDecryptsMessage() {
        String plaintext = "Xin chào — hello 🔐";

        MessageEncryptionService.EncryptedMessage encrypted = encryption.encrypt(plaintext);

        assertNotEquals(plaintext, encrypted.ciphertext());
        assertEquals(plaintext, encryption.decrypt(encrypted.ciphertext(), encrypted.nonce()));
    }

    @Test
    void usesDifferentNonceForEachMessage() {
        MessageEncryptionService.EncryptedMessage first = encryption.encrypt("same message");
        MessageEncryptionService.EncryptedMessage second = encryption.encrypt("same message");

        assertNotEquals(first.nonce(), second.nonce());
        assertNotEquals(first.ciphertext(), second.ciphertext());
    }

    @Test
    void rejectsTamperedCiphertext() {
        MessageEncryptionService.EncryptedMessage encrypted = encryption.encrypt("message");
        byte[] ciphertext = Base64.getDecoder().decode(encrypted.ciphertext());
        ciphertext[0] ^= 1;

        assertThrows(IllegalStateException.class, () ->
            encryption.decrypt(Base64.getEncoder().encodeToString(ciphertext), encrypted.nonce()));
    }
}
