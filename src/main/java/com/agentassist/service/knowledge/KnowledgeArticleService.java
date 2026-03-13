package com.agentassist.service.knowledge;

import com.agentassist.dto.requestDTO.KnowledgeArticleRequest;
import com.agentassist.dto.responseDTO.KnowledgeArticleDTO;
import com.agentassist.mapper.KnowledgeArticleMapper;
import com.agentassist.model.KnowledgeArticleEntity;
import com.agentassist.repository.KnowledgeArticleRepository;
import com.agentassist.util.FileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeArticleService {

    private final KnowledgeArticleRepository repo;
    private final KnowledgeArticleMapper mapper;
    private final FileParser fileParser;

    /**
     * Get paginated list of articles
     */
    public Page<KnowledgeArticleDTO> getArticles(int page, int size) {
        log.info("[Knowledge] Fetching articles, page: {}, size: {}", page, size);
        Page<KnowledgeArticleEntity> data = repo.findAll(PageRequest.of(page, size));
        log.info("[Knowledge] Found {} articles", data.getTotalElements());
        return data.map(mapper::toDto);
    }

    /**
     * Get single article by ID
     */
    public Optional<KnowledgeArticleDTO> getArticleById(Long id) {
        log.info("[Knowledge] Fetching article by ID: {}", id);
        return repo.findById(id).map(entity -> {
            log.info("[Knowledge] Found article: {}", entity.getName());
            return mapper.toDto(entity);
        });
    }

    /**
     * Search articles by name or content
     */
    public List<KnowledgeArticleDTO> searchArticles(String query) {
        log.info("[Knowledge] Searching articles with query: '{}'", query);
        List<KnowledgeArticleEntity> results = repo.findByNameContainingIgnoreCaseOrContentContainingIgnoreCase(query, query);
        log.info("[Knowledge] Search found {} results", results.size());
        return results.stream().map(mapper::toDto).toList();
    }

    /**
     * Create new article
     */
    @Transactional
    public KnowledgeArticleDTO createArticle(KnowledgeArticleRequest request) {
        log.info("[Knowledge] Creating article: name='{}', type='{}'", request.getName(), request.getType());

        KnowledgeArticleEntity entity = new KnowledgeArticleEntity();
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setContent(request.getContent());

        KnowledgeArticleEntity saved = repo.save(entity);
        log.info("[Knowledge] Article created with ID: {}", saved.getId());

        return mapper.toDto(saved);
    }

    /**
     * Update existing article
     */
    @Transactional
    public Optional<KnowledgeArticleDTO> updateArticle(Long id, KnowledgeArticleRequest request) {
        log.info("[Knowledge] Updating article ID: {}", id);

        return repo.findById(id).map(entity -> {
            entity.setName(request.getName());
            entity.setType(request.getType());
            entity.setContent(request.getContent());

            KnowledgeArticleEntity updated = repo.save(entity);
            log.info("[Knowledge] Article updated: {}", updated.getName());

            return mapper.toDto(updated);
        });
    }

    /**
     * Delete article
     */
    @Transactional
    public boolean deleteArticle(Long id) {
        log.info("[Knowledge] Deleting article ID: {}", id);

        if (repo.existsById(id)) {
            repo.deleteById(id);
            log.info("[Knowledge] Article deleted: {}", id);
            return true;
        }

        log.warn("[Knowledge] Article not found for deletion: {}", id);
        return false;
    }

    /**
     * Upload file as knowledge article (supports PDF, Word, Excel, and text files)
     */
    @Transactional
    public KnowledgeArticleDTO uploadFile(MultipartFile file, String type) throws IOException {
        String filename = file.getOriginalFilename();
        log.info("[Knowledge] Uploading file: '{}', size: {} bytes, type: '{}'",
                filename, file.getSize(), type);

        // Parse file content based on file type
        String content = fileParser.parseFile(file);
        log.info("[Knowledge] File parsed, content length: {} chars", content.length());

        // Detect type if not provided
        String fileType = type;
        if (fileType == null || fileType.isBlank()) {
            fileType = fileParser.detectFileType(filename);
        }

        // Create article from file
        KnowledgeArticleEntity entity = new KnowledgeArticleEntity();
        entity.setName(filename != null ? filename : "Uploaded File");
        entity.setType(fileType);
        entity.setContent(content);

        KnowledgeArticleEntity saved = repo.save(entity);
        log.info("[Knowledge] File uploaded as article ID: {}, name: '{}', type: '{}'",
                saved.getId(), saved.getName(), saved.getType());

        return mapper.toDto(saved);
    }

    /**
     * Bulk upload multiple files
     */
    @Transactional
    public List<KnowledgeArticleDTO> uploadFiles(List<MultipartFile> files, String type) {
        log.info("[Knowledge] Bulk uploading {} files", files.size());

        List<KnowledgeArticleDTO> results = files.stream()
                .map(file -> {
                    try {
                        return uploadFile(file, type);
                    } catch (IOException e) {
                        log.error("[Knowledge] Failed to upload file: {} - {}",
                                file.getOriginalFilename(), e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .toList();

        log.info("[Knowledge] Bulk upload completed: {} of {} files uploaded", results.size(), files.size());
        return results;
    }
}
