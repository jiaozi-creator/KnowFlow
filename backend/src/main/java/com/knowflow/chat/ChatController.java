package com.knowflow.chat;

import com.knowflow.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) { this.service = service; }

    @GetMapping("/conversations")
    public ApiResponse<List<ChatDtos.ConversationView>> conversations() {
        return ApiResponse.ok(service.conversations());
    }

    @PostMapping("/conversations")
    public ApiResponse<ChatDtos.ConversationView> create(@RequestBody ChatDtos.CreateConversationRequest request) {
        return ApiResponse.ok(service.createConversation(request));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<ChatDtos.MessageView>> messages(@PathVariable Long id) {
        return ApiResponse.ok(service.messages(id));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatDtos.StreamRequest request) {
        return service.stream(request);
    }
}
