package com.knowflow.document;

import com.knowflow.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<List<DocumentDtos.View>> list(
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.ok(service.list(knowledgeBaseId));
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<DocumentDtos.UploadResponse> upload(
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(
                service.upload(knowledgeBaseId, file)
        );
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents/index-status")
    public ApiResponse<DocumentDtos.IndexStatus> indexStatus(
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.ok(
                service.indexStatus(knowledgeBaseId)
        );
    }

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents/repair-indexes")
    public ApiResponse<DocumentDtos.BatchReindexResponse> repairIndexes(
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.ok(
                service.repairIndexes(knowledgeBaseId)
        );
    }

    @GetMapping("/documents/{documentId}/task")
    public ApiResponse<DocumentDtos.TaskView> task(
            @PathVariable Long documentId
    ) {
        return ApiResponse.ok(service.task(documentId));
    }

    @PostMapping("/documents/{documentId}/reindex")
    public ApiResponse<DocumentDtos.TaskView> reindex(
            @PathVariable Long documentId
    ) {
        return ApiResponse.ok(service.reindex(documentId));
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable Long documentId
    ) {
        DocumentDtos.Content content =
                service.content(documentId);

        StreamingResponseBody body =
                output -> {
                    try (var input = content.inputStream()) {
                        input.transferTo(output);
                    }
                };

        String filename =
                URLEncoder
                        .encode(
                                content.filename(),
                                StandardCharsets.UTF_8
                        )
                        .replace("+", "%20");

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        content.contentType()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + filename
                )
                .body(body);
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(
            @PathVariable Long documentId
    ) {
        service.delete(documentId);
        return ApiResponse.ok();
    }
}