package com.knowflow.knowledge;

import com.knowflow.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<KnowledgeDtos.View>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeDtos.View> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @PostMapping
    public ApiResponse<KnowledgeDtos.View> create(@Valid @RequestBody KnowledgeDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeDtos.View> update(@PathVariable Long id, @Valid @RequestBody KnowledgeDtos.UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
