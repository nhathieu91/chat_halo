package com.example.chat.websocket;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceService {
    private final ConcurrentMap<String, Integer> sessionsByUser = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messaging;

    public PresenceService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        if (event.getUser() == null) return;
        AtomicBoolean becameOnline = new AtomicBoolean();
        sessionsByUser.compute(event.getUser().getName(), (username, count) -> {
            if (count == null) {
                becameOnline.set(true);
                return 1;
            }
            return count + 1;
        });
        if (becameOnline.get()) broadcast();
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        if (event.getUser() == null) return;
        AtomicBoolean becameOffline = new AtomicBoolean();
        sessionsByUser.computeIfPresent(event.getUser().getName(), (username, count) -> {
            if (count <= 1) {
                becameOffline.set(true);
                return null;
            }
            return count - 1;
        });
        if (becameOffline.get()) broadcast();
    }

    private void broadcast() {
        messaging.convertAndSend("/topic/presence", sessionsByUser.keySet().stream().sorted().toList());
    }
}
