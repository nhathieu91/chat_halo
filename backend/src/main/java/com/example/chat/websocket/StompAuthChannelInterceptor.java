package com.example.chat.websocket;

import com.example.chat.room.ChatRoomRepository;
import com.example.chat.security.JwtService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms/([0-9a-fA-F-]{36})$");
    private final JwtService jwtService;
    private final ChatRoomRepository rooms;

    public StompAuthChannelInterceptor(JwtService jwtService, ChatRoomRepository rooms) {
        this.jwtService = jwtService;
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                throw new IllegalArgumentException("A bearer token is required");
            }
            String username = jwtService.getUsername(header.substring(7));
            accessor.setUser(new UsernamePasswordAuthenticationToken(username, null, java.util.List.of()));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Matcher matcher = destination == null ? null : ROOM_TOPIC.matcher(destination);
            if (matcher != null && matcher.matches()) {
                String username = accessor.getUser() == null ? null : accessor.getUser().getName();
                if (username == null || !rooms.existsByIdAndMembersUsername(
                    java.util.UUID.fromString(matcher.group(1)), username)) {
                    throw new IllegalArgumentException("You are not a member of this room");
                }
            }
        }
        return message;
    }
}
