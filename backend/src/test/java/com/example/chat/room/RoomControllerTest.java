package com.example.chat.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.chat.user.AppUser;
import com.example.chat.user.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class RoomControllerTest {
    private ChatRoomRepository rooms;
    private RoomController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        rooms = mock(ChatRoomRepository.class);
        controller = new RoomController(rooms, mock(UserRepository.class));
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");
    }

    @Test
    void returnsRoomForMember() {
        UUID roomId = UUID.randomUUID();
        ChatRoom room = mock(ChatRoom.class);
        AppUser member = new AppUser("alice", "alice@example.com", "hash");
        when(rooms.findByIdAndMembersUsername(roomId, "alice")).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getName()).thenReturn("Project");
        when(room.getType()).thenReturn("group");
        when(room.getMembers()).thenReturn(Set.of(member));

        RoomController.RoomResponse response = controller.get(roomId, authentication);

        assertEquals(roomId, response.id());
        assertEquals("Project", response.name());
        assertEquals(java.util.List.of("alice"), response.members());
    }

    @Test
    void hidesRoomFromNonMembers() {
        UUID roomId = UUID.randomUUID();
        when(rooms.findByIdAndMembersUsername(roomId, "alice")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class, () -> controller.get(roomId, authentication));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(rooms).findByIdAndMembersUsername(roomId, "alice");
    }
}
