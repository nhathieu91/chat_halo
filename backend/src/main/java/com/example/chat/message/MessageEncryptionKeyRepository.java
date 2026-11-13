package com.example.chat.message;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageEncryptionKeyRepository extends JpaRepository<MessageEncryptionKey, String> {}
