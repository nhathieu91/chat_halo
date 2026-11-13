package com.example.chat.message;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Service;

@Service
public class MessageEncryptionService {
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final MessageEncryptionKeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public MessageEncryptionService(MessageEncryptionKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public EncryptedMessage encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedMessage(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(nonce));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt chat message", exception);
        }
    }

    public String decrypt(String ciphertext, String encodedNonce) {
        try {
            byte[] nonce = Base64.getDecoder().decode(encodedNonce);
            if (nonce.length != NONCE_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid message encryption nonce");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey key = keyProvider.getKey();
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt chat message", exception);
        }
    }

    public record EncryptedMessage(String ciphertext, String nonce) {}
}
