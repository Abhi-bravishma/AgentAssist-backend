package com.agentassist.rag;

import com.agentassist.dto.rag.RagDocumentListResponse;
import com.agentassist.dto.rag.RagDocumentListResponse.RagDocumentInfo;
import com.agentassist.dto.rag.RagDocumentUploadResponse;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Match;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * COPY of bravishma-rag's {@code AgentAssistDocumentServiceImpl}, brought
 * in-app: same Tika parsing, same metadata tagging (useCase=agent_assist,
 * projectName, active flag), same Qdrant payload schema — so documents
 * uploaded here are indistinguishable from those uploaded through the old
 * service, in the same collection.
 *
 * <p>Deltas from upstream: package; concrete class; chunk config from
 * {@link InternalRagProperties}; return types are this app's Rag* DTOs
 * (field-identical to upstream's response JSON); upstream's
 * vectorStoreService.invalidateFileNamesCache() calls dropped (no such cache
 * here — listing queries Qdrant directly). Since the Spring AI 1.1.5 upgrade
 * the splitter call and getText() usage match upstream exactly.</p>
 */
@Slf4j
@Service
public class AgentAssistDocumentService {

    private static final String USE_CASE_AGENT_ASSIST = "agent_assist";

    /** Payload key and values for the searchable/paused flag. */
    public static final String ACTIVE_KEY = "active";
    public static final String ACTIVE_TRUE = "true";
    public static final String ACTIVE_FALSE = "false";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".doc", ".odt", ".rtf",
            ".txt", ".md", ".xlsx", ".xls", ".csv", ".pptx", ".ppt"
    );
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int QDRANT_TIMEOUT_SECONDS = 30;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;
    private final FileStorageService fileStorageService;
    private final String collectionName;
    private final int chunkSize;
    private final int chunkOverlap;

    public AgentAssistDocumentService(QdrantVectorStore agentAssistVectorStore,
                                      QdrantClient qdrantClient,
                                      FileStorageService fileStorageService,
                                      InternalRagProperties properties) {
        this.vectorStore = agentAssistVectorStore;
        this.qdrantClient = qdrantClient;
        this.fileStorageService = fileStorageService;
        this.collectionName = properties.getQdrant().getCollection();
        this.chunkSize = properties.getChunk().getSize();
        this.chunkOverlap = properties.getChunk().getOverlap();
    }

    public RagDocumentUploadResponse uploadDocuments(List<MultipartFile> files, Long companyId,
                                                     String category, String projectName) {
        log.info("Uploading {} documents for Agent Assist, companyId: {}, projectName: {}",
                files.size(), companyId, projectName);

        List<String> processedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();

            // Validate file
            if (fileName == null || fileName.isEmpty()) {
                failedFiles.add("unknown (no filename)");
                continue;
            }

            // Validate extension
            String lowerName = fileName.toLowerCase();
            boolean validExtension = ALLOWED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
            if (!validExtension) {
                log.warn("Unsupported file type: {}", fileName);
                failedFiles.add(fileName + " (unsupported type)");
                continue;
            }

            // Validate size
            if (file.getSize() > MAX_FILE_SIZE) {
                log.warn("File too large: {} ({}MB)", fileName, file.getSize() / (1024 * 1024));
                failedFiles.add(fileName + " (too large)");
                continue;
            }

            try {
                processDocument(file, companyId, category, projectName);
                processedFiles.add(fileName);
                log.info("Successfully processed: {}", fileName);
            } catch (Exception e) {
                log.error("Failed to process document {}: {}", fileName, e.getMessage(), e);
                failedFiles.add(fileName + " (" + e.getMessage() + ")");
            }
        }

        return RagDocumentUploadResponse.builder()
                .message(failedFiles.isEmpty() ? "All documents uploaded successfully" : "Some documents failed to upload")
                .totalFiles(files.size())
                .processedFiles(processedFiles.size())
                .failedFiles(failedFiles.size())
                .fileNames(processedFiles)
                .failedFileNames(failedFiles.isEmpty() ? null : failedFiles)
                .build();
    }

    private void processDocument(MultipartFile file, Long companyId, String category, String projectName) throws IOException {
        String fileName = file.getOriginalFilename();
        log.info("Processing document for Agent Assist: {}, projectName: {}", fileName, projectName);

        // Store original file for later download
        if (fileStorageService.isEnabled()) {
            try {
                fileStorageService.store(file, companyId);
                log.info("Original file stored: {}", fileName);
            } catch (Exception e) {
                log.warn("Failed to store original file (continuing with vector processing): {}", e.getMessage());
            }
        }

        // Read file content
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> docs = reader.get();
        log.info("Tika extracted {} document(s) from {}", docs.size(), fileName);

        // Prepare metadata
        long uploadTimestamp = System.currentTimeMillis();
        String uploadDate = Instant.ofEpochMilli(uploadTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DATE_FORMAT);

        // Add metadata to all documents - including useCase for filtering
        docs.forEach(d -> {
            d.getMetadata().put("filename", fileName);
            d.getMetadata().put("source", getFileExtension(fileName));
            d.getMetadata().put("companyId", String.valueOf(companyId));
            d.getMetadata().put("uploadDate", uploadDate);
            // Double, not long: M4's QdrantValueFactory rejects Long metadata
            // ("Unsupported Qdrant value type") — found by the local smoke test.
            // Qdrant range filters compare numerics, so date filtering still works.
            d.getMetadata().put("uploadTimestamp", (double) uploadTimestamp);
            d.getMetadata().put("status", "UPLOADED");
            d.getMetadata().put("useCase", USE_CASE_AGENT_ASSIST);  // Tag for agent assist
            // Searchable by default. Pausing sets this to "false"; retrieval excludes
            // only explicit "false", so documents uploaded before this flag existed
            // (which carry no value at all) keep working.
            d.getMetadata().put("active", ACTIVE_TRUE);
            if (category != null && !category.isEmpty()) {
                d.getMetadata().put("category", category);
            }
            // Tag with projectName for project-based filtering (METRO, ALLIANZ, SCB, etc.)
            if (projectName != null && !projectName.isEmpty()) {
                d.getMetadata().put("projectName", projectName);
            }
        });

        // Split into chunks — upstream's exact splitter call
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 2048, true,
                java.util.List.of('.', '?', '!', ';', ':', '\n'));
        List<Document> chunks = splitter.apply(docs);
        log.info("Created {} chunks from {}", chunks.size(), fileName);

        // Filter invalid chunks and add chunk metadata
        AtomicInteger chunkIndex = new AtomicInteger(0);
        List<Document> safeChunks = new ArrayList<>();

        for (Document chunk : chunks) {
            if (chunk.getText() == null || chunk.getText().length() >= 4000) {
                continue;
            }
            chunk.getMetadata().put("chunkIndex", chunkIndex.getAndIncrement());
            // Ensure useCase is preserved in chunks
            if (!chunk.getMetadata().containsKey("useCase")) {
                chunk.getMetadata().put("useCase", USE_CASE_AGENT_ASSIST);
            }
            safeChunks.add(chunk);
        }

        if (!safeChunks.isEmpty()) {
            // Add total_chunks to all chunks
            int totalChunks = safeChunks.size();
            safeChunks.forEach(c -> c.getMetadata().put("total_chunks", totalChunks));

            vectorStore.add(safeChunks);
            log.info("Added {} chunks to vector store for {}", safeChunks.size(), fileName);
        } else {
            log.warn("No valid chunks to add for: {}", fileName);
            throw new RuntimeException("No valid content extracted from document");
        }
    }

    public RagDocumentListResponse listDocuments(Long companyId, int page, int size) {
        log.info("Listing Agent Assist documents for companyId: {}, page: {}, size: {}", companyId, page, size);

        try {
            // Build filter for companyId AND useCase=agent_assist
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("companyId")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(String.valueOf(companyId))
                                            .build())
                                    .build())
                            .build())
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("useCase")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(USE_CASE_AGENT_ASSIST)
                                            .build())
                                    .build())
                            .build())
                    .build();

            // Scroll through all points to get unique filenames
            Set<String> uniqueFileNames = new HashSet<>();
            Map<String, RagDocumentInfo> documentInfoMap = new HashMap<>();
            String nextOffset = null;

            do {
                Points.ScrollPoints.Builder scrollBuilder = Points.ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(1000)
                        .setFilter(filter)
                        .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build());

                if (nextOffset != null) {
                    scrollBuilder.setOffset(Points.PointId.newBuilder().setUuid(nextOffset).build());
                }

                Points.ScrollResponse response = qdrantClient.scrollAsync(scrollBuilder.build())
                        .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                for (Points.RetrievedPoint point : response.getResultList()) {
                    Map<String, JsonWithInt.Value> payload = point.getPayloadMap();

                    String filename = payload.containsKey("filename")
                            ? payload.get("filename").getStringValue()
                            : "unknown";

                    if (!uniqueFileNames.contains(filename)) {
                        uniqueFileNames.add(filename);

                        String cat = payload.containsKey("category")
                                ? payload.get("category").getStringValue()
                                : null;
                        String uploadDate = payload.containsKey("uploadDate")
                                ? payload.get("uploadDate").getStringValue()
                                : null;
                        int totalChunks = payload.containsKey("total_chunks")
                                ? (int) payload.get("total_chunks").getIntegerValue()
                                : 1;
                        String project = payload.containsKey("projectName")
                                ? payload.get("projectName").getStringValue()
                                : null;
                        // Absent flag means the document predates pausing - treat as active
                        boolean isActive = !payload.containsKey(ACTIVE_KEY)
                                || !ACTIVE_FALSE.equalsIgnoreCase(payload.get(ACTIVE_KEY).getStringValue());

                        documentInfoMap.put(filename, RagDocumentInfo.builder()
                                .fileName(filename)
                                .category(cat)
                                .uploadDate(uploadDate)
                                .chunkCount(totalChunks)
                                .projectName(project)
                                .active(isActive)
                                .build());
                    }
                }

                nextOffset = response.hasNextPageOffset()
                        ? response.getNextPageOffset().getUuid()
                        : null;

            } while (nextOffset != null);

            // Convert to list and sort by filename
            List<RagDocumentInfo> allDocuments = new ArrayList<>(documentInfoMap.values());
            allDocuments.sort(Comparator.comparing(RagDocumentInfo::getFileName));

            // Apply pagination
            int totalDocuments = allDocuments.size();
            int totalPages = (int) Math.ceil((double) totalDocuments / size);
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, totalDocuments);

            List<RagDocumentInfo> paginatedDocuments;
            if (fromIndex >= totalDocuments) {
                paginatedDocuments = List.of();
            } else {
                paginatedDocuments = allDocuments.subList(fromIndex, toIndex);
            }

            return RagDocumentListResponse.builder()
                    .documents(paginatedDocuments)
                    .totalDocuments(totalDocuments)
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .hasNext(page < totalPages - 1)
                    .hasPrevious(page > 0)
                    .build();

        } catch (Exception e) {
            log.error("Failed to list Agent Assist documents: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list documents", e);
        }
    }

    /**
     * Pause or resume a document without deleting it.
     * <p>
     * Sets the {@code active} flag on every chunk of the document. Paused documents stay
     * in Qdrant and keep their embeddings - they are simply excluded from Agent-Assist
     * retrieval - so resuming is instant and needs no re-upload or re-embedding.
     *
     * @return number of chunks updated, 0 if the document was not found
     */
    public long setDocumentActive(String fileName, Long companyId, boolean active) {
        log.info("Setting Agent Assist document '{}' active={} for companyId: {}", fileName, active, companyId);

        try {
            Filter filter = documentFilter(fileName, companyId);

            long count = qdrantClient.countAsync(collectionName, filter, true)
                    .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (count == 0) {
                log.warn("No Agent Assist document found with filename: {}", fileName);
                return 0;
            }

            Map<String, JsonWithInt.Value> payload = Map.of(
                    ACTIVE_KEY,
                    JsonWithInt.Value.newBuilder()
                            .setStringValue(active ? ACTIVE_TRUE : ACTIVE_FALSE)
                            .build());

            qdrantClient.setPayloadAsync(collectionName, payload, filter, true, null, null)
                    .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Updated active={} on {} chunks for document: {}", active, count, fileName);
            return count;

        } catch (Exception e) {
            log.error("Failed to update active flag for document '{}': {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Failed to update document status", e);
        }
    }

    public long deleteDocument(String fileName, Long companyId) {
        log.info("Deleting Agent Assist document: {} for companyId: {}", fileName, companyId);

        try {
            Filter filter = documentFilter(fileName, companyId);

            // Count points before deletion
            long count = qdrantClient.countAsync(collectionName, filter, true)
                    .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (count == 0) {
                log.warn("No Agent Assist document found with filename: {}", fileName);
                return 0;
            }

            // Delete points
            qdrantClient.deleteAsync(collectionName, filter)
                    .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Deleted {} chunks for Agent Assist document: {}", count, fileName);

            // Delete original file from storage
            if (fileStorageService.isEnabled()) {
                boolean fileDeleted = fileStorageService.delete(fileName, companyId);
                log.info("Original file deleted: {} (success: {})", fileName, fileDeleted);
            }

            return count;

        } catch (Exception e) {
            log.error("Failed to delete Agent Assist document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    /** companyId + useCase + filename filter, shared by delete and pause. */
    private Filter documentFilter(String fileName, Long companyId) {
        return Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("companyId")
                                .setMatch(Match.newBuilder()
                                        .setKeyword(String.valueOf(companyId))
                                        .build())
                                .build())
                        .build())
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("useCase")
                                .setMatch(Match.newBuilder()
                                        .setKeyword(USE_CASE_AGENT_ASSIST)
                                        .build())
                                .build())
                        .build())
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("filename")
                                .setMatch(Match.newBuilder()
                                        .setKeyword(fileName)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
