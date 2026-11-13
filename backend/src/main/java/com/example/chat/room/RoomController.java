package com.example.chat.room;

import com.example.chat.user.AppUser;
import com.example.chat.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final ChatRoomRepository rooms;
    private final UserRepository users;

    public RoomController(ChatRoomRepository rooms, UserRepository users) {
        this.rooms = rooms;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RoomResponse> list(Authentication authentication) {
        return rooms.findDistinctByMembersUsernameOrderByCreatedAtDesc(authentication.getName())
            .stream().map(this::toResponse).toList();
    }

    @GetMapping("/{roomId}")
    @Transactional(readOnly = true)
    public RoomResponse get(@PathVariable UUID roomId, Authentication authentication) {
        ChatRoom room = rooms.findByIdAndMembersUsername(roomId, authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        return toResponse(room);
    }

    @PostMapping
    @Transactional
    public RoomResponse create(@Valid @RequestBody CreateRoomRequest request, Authentication authentication) {
        String type = request.type().toLowerCase();
        if (!type.equals("direct") && !type.equals("group")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room type must be direct or group");
        }
        HashSet<String> usernames = new HashSet<>(request.members());
        usernames.add(authentication.getName());
        if (type.equals("direct") && usernames.size() != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct rooms require exactly two members");
        }
        List<AppUser> members = users.findAllByUsernameIn(usernames);
        if (members.size() != usernames.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more users do not exist");
        }
        ChatRoom room = rooms.save(new ChatRoom(request.name().trim(), type, new HashSet<>(members)));
        return toResponse(room);
    }

    private RoomResponse toResponse(ChatRoom room) {
        return new RoomResponse(room.getId(), room.getName(), room.getType(),
            room.getMembers().stream().map(AppUser::getUsername).sorted().toList());
    }

    public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String type,
        @NotEmpty List<@NotBlank String> members
    ) {}
    public record RoomResponse(UUID id, String name, String type, List<String> members) {}
}
