package com.agentassist.controller;

import com.agentassist.dto.requestDTO.KnowledgeArticleRequest;
import com.agentassist.dto.responseDTO.KnowledgeArticleDTO;
import com.agentassist.service.knowledge.KnowledgeArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge-articles")
@RequiredArgsConstructor
@Tag(name = "Knowledge Articles", description = "Knowledge base management APIs")
public class KnowledgeArticleController {

    private final KnowledgeArticleService service;

    @GetMapping
    @Operation(summary = "Get all articles (paginated)")
    public Page<KnowledgeArticleDTO> getArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("[API] GET /knowledge-articles?page={}&size={}", page, size);
        return service.getArticles(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get article by ID")
    public ResponseEntity<KnowledgeArticleDTO> getArticleById(@PathVariable Long id) {
        log.info("[API] GET /knowledge-articles/{}", id);
        return service.getArticleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @Operation(summary = "Search articles by name or content")
    public List<KnowledgeArticleDTO> searchArticles(@RequestParam String query) {
        log.info("[API] GET /knowledge-articles/search?query={}", query);
        return service.searchArticles(query);
    }

    @PostMapping
    @Operation(summary = "Create new article")
    public ResponseEntity<KnowledgeArticleDTO> createArticle(
            @Valid @RequestBody KnowledgeArticleRequest request) {
        log.info("[API] POST /knowledge-articles - name: {}", request.getName());
        KnowledgeArticleDTO created = service.createArticle(request);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update existing article")
    public ResponseEntity<KnowledgeArticleDTO> updateArticle(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeArticleRequest request) {
        log.info("[API] PUT /knowledge-articles/{}", id);
        return service.updateArticle(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete article")
    public ResponseEntity<Void> deleteArticle(@PathVariable Long id) {
        log.info("[API] DELETE /knowledge-articles/{}", id);
        if (service.deleteArticle(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".txt", ".md", ".json", ".html", ".xml", ".csv",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx"
    );

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file as knowledge article")
    public ResponseEntity<KnowledgeArticleDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type) {
        log.info("[API] POST /knowledge-articles/upload - file: {}, size: {}, type: {}",
                file.getOriginalFilename(), file.getSize(), type);

        // Validate file
        String validationError = validateFile(file);
        if (validationError != null) {
            log.warn("[API] File validation failed: {}", validationError);
            return ResponseEntity.badRequest().build();
        }

        try {
            KnowledgeArticleDTO uploaded = service.uploadFile(file, type);
            return ResponseEntity.ok(uploaded);
        } catch (IOException e) {
            log.error("[API] File upload failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "File is empty";
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return "File size exceeds maximum limit of 10MB";
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return "Filename is required";
        }
        String lowerName = filename.toLowerCase();
        boolean validExtension = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!validExtension) {
            return "File type not allowed. Allowed: " + ALLOWED_EXTENSIONS;
        }
        return null;
    }

    @PostMapping(value = "/upload/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk upload multiple files")
    public ResponseEntity<List<KnowledgeArticleDTO>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "type", required = false) String type) {
        log.info("[API] POST /knowledge-articles/upload/bulk - {} files, type: {}",
                files.size(), type);

        // Validate all files first
        for (MultipartFile file : files) {
            String validationError = validateFile(file);
            if (validationError != null) {
                log.warn("[API] Bulk upload validation failed for {}: {}",
                        file.getOriginalFilename(), validationError);
                return ResponseEntity.badRequest().build();
            }
        }

        List<KnowledgeArticleDTO> uploaded = service.uploadFiles(files, type);
        return ResponseEntity.ok(uploaded);
    }
}
