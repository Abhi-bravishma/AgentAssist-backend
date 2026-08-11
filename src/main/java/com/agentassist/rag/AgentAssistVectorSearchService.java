package com.agentassist.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Match;
import io.qdrant.client.grpc.Points.Range;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * VERBATIM COPY of bravishma-rag's frozen {@code AgentAssistVectorSearchServiceImpl},
 * brought in-app so agent-assist retrieval no longer depends on the external
 * RAG application. Same Qdrant filters (companyId, useCase=agent_assist,
 * projectName, active != "false"), same payload reading, same behaviour.
 *
 * <p>Deltas from upstream: package; concrete class instead of iface+impl;
 * collection name and the QdrantClient come from {@link InternalRagProperties}
 * / {@link InternalRagConfig}; the embedding model is the lane's dedicated
 * {@code agentAssistEmbeddingModel} bean — selected by
 * {@code rag.internal.embedding.provider} and paired with a matching
 * collection, since vectors from one embedder cannot be searched by another.</p>
 *
 * <p>As upstream documents: NO RBAC, NO sensitivity, NO domain filters —
 * those broke Agent-Assist in production once and are deliberately absent.</p>
 */
@Slf4j
@Service
public class AgentAssistVectorSearchService {

    private static final int QDRANT_TIMEOUT_SECONDS = 30;

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final String collectionName;

    public AgentAssistVectorSearchService(QdrantClient qdrantClient,
                                          @Qualifier("agentAssistEmbeddingModel") EmbeddingModel embeddingModel,
                                          InternalRagProperties properties) {
        this.qdrantClient = qdrantClient;
        this.embeddingModel = embeddingModel;
        this.collectionName = properties.getQdrant().getCollection();
    }

    public List<Document> search(String query, Long companyId, AgentAssistSearchFilter filters,
                                 int topK, double similarityThreshold) {
        log.info("========== AGENT-ASSIST VECTOR SEARCH START ==========");
        // NOTE: the upstream per-search "collection sanity check" (an extra
        // getCollectionInfo round-trip used only for a log line) was removed -
        // it doubled Qdrant traffic and stalled messages for up to 30s+ when
        // the server was slow. The real search below fails loudly on its own.

        log.info("Query: '{}'", truncateForLog(query));
        log.info("CompanyId: {}, TopK: {}, Threshold: {}", companyId, topK, similarityThreshold);

        long startTime = System.currentTimeMillis();
        List<String> activeFilters = new ArrayList<>();

        try {
            // Stage 1: Build pre-filter
            log.info("--- Stage 1: Building Pre-Filters ---");
            Filter.Builder filterBuilder = Filter.newBuilder();
            boolean hasFilter = false;

            // Multi-tenant isolation (always applied)
            if (companyId != null && companyId > 0) {
                filterBuilder.addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("companyId")
                                .setMatch(Match.newBuilder()
                                        .setKeyword(String.valueOf(companyId))
                                        .build())
                                .build())
                        .build());
                hasFilter = true;
                activeFilters.add("companyId=" + companyId);
                log.info("  [MUST] companyId = '{}' (string match for multi-tenant isolation)", companyId);
            } else {
                log.warn("  [WARNING] No companyId filter - searching ALL companies!");
            }

            // RBAC INTENTIONALLY NOT APPLIED.
            // Adding sensitivity/domain MUST clauses caused a production regression
            // because old Agent-Assist documents lack those payload fields.

            // Paused documents are excluded. Expressed as MUST_NOT active="false" rather
            // than MUST active="true" for the reason given directly above: documents
            // uploaded before this flag existed carry no `active` payload at all, and a
            // MUST clause would silently drop every one of them.
            filterBuilder.addMustNot(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey("active")
                            .setMatch(Match.newBuilder().setKeyword("false").build())
                            .build())
                    .build());
            hasFilter = true;
            activeFilters.add("active!=false");
            log.info("  [MUST NOT] active = 'false' (paused documents excluded)");

            if (filters != null) {
                // fileNames
                if (filters.getFileNames() != null && !filters.getFileNames().isEmpty()) {
                    for (String fileName : filters.getFileNames()) {
                        filterBuilder.addShould(Condition.newBuilder()
                                .setField(FieldCondition.newBuilder()
                                        .setKey("filename")
                                        .setMatch(Match.newBuilder().setKeyword(fileName).build())
                                        .build())
                                .build());
                    }
                    hasFilter = true;
                    activeFilters.add("fileNames=" + filters.getFileNames());
                    log.info("  [SHOULD] filename IN {}", filters.getFileNames());
                }

                // categories
                if (filters.getCategories() != null && !filters.getCategories().isEmpty()) {
                    for (String category : filters.getCategories()) {
                        filterBuilder.addShould(Condition.newBuilder()
                                .setField(FieldCondition.newBuilder()
                                        .setKey("category")
                                        .setMatch(Match.newBuilder().setKeyword(category).build())
                                        .build())
                                .build());
                    }
                    hasFilter = true;
                    activeFilters.add("categories=" + filters.getCategories());
                    log.info("  [SHOULD] category IN {}", filters.getCategories());
                }

                // dateRange
                if (filters.hasDateFilter()) {
                    Range.Builder rangeBuilder = Range.newBuilder();
                    if (filters.getDateFrom() != null) {
                        long fromTs = filters.getDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                        rangeBuilder.setGte(fromTs);
                        activeFilters.add("dateFrom=" + filters.getDateFrom());
                        log.info("  [MUST] uploadTimestamp >= {} ({})", fromTs, filters.getDateFrom());
                    }
                    if (filters.getDateTo() != null) {
                        long toTs = filters.getDateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                        rangeBuilder.setLte(toTs);
                        activeFilters.add("dateTo=" + filters.getDateTo());
                        log.info("  [MUST] uploadTimestamp <= {} ({})", toTs, filters.getDateTo());
                    }
                    filterBuilder.addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("uploadTimestamp")
                                    .setRange(rangeBuilder.build())
                                    .build())
                            .build());
                    hasFilter = true;
                }

                // sourceType
                if (filters.getSourceType() != null && !filters.getSourceType().isEmpty()) {
                    filterBuilder.addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("source")
                                    .setMatch(Match.newBuilder().setKeyword(filters.getSourceType()).build())
                                    .build())
                            .build());
                    hasFilter = true;
                    activeFilters.add("sourceType=" + filters.getSourceType());
                    log.info("  [MUST] source = {}", filters.getSourceType());
                }

                // useCase (always "agent_assist" in practice)
                if (filters.getUseCase() != null && !filters.getUseCase().isEmpty()) {
                    filterBuilder.addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("useCase")
                                    .setMatch(Match.newBuilder().setKeyword(filters.getUseCase()).build())
                                    .build())
                            .build());
                    hasFilter = true;
                    activeFilters.add("useCase=" + filters.getUseCase());
                    log.info("  [MUST] useCase = {}", filters.getUseCase());
                }

                // projectName
                if (filters.getProjectName() != null && !filters.getProjectName().isEmpty()) {
                    filterBuilder.addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("projectName")
                                    .setMatch(Match.newBuilder().setKeyword(filters.getProjectName()).build())
                                    .build())
                            .build());
                    hasFilter = true;
                    activeFilters.add("projectName=" + filters.getProjectName());
                    log.info("  [MUST] projectName = {}", filters.getProjectName());
                }

                // overrides
                if (filters.getTopK() != null && filters.getTopK() > 0) {
                    topK = filters.getTopK();
                    log.info("  TopK overridden to: {}", topK);
                }
                if (filters.getMinScore() != null && filters.getMinScore() > 0) {
                    similarityThreshold = filters.getMinScore();
                    log.info("  Threshold overridden to: {}", similarityThreshold);
                }
            }

            log.info("Active filters: {}", activeFilters.isEmpty() ? "NONE" : activeFilters);

            // Stage 2: Embed query
            log.info("--- Stage 2: Embedding Query ---");
            long embedStart = System.currentTimeMillis();
            float[] queryEmbedding = embeddingModel.embed(query);
            long embedTime = System.currentTimeMillis() - embedStart;
            log.info("  Query embedded in {}ms (vector dim: {})", embedTime, queryEmbedding.length);

            List<Float> queryVector = new ArrayList<>(queryEmbedding.length);
            for (float f : queryEmbedding) {
                queryVector.add(f);
            }

            // Stage 3: Execute filtered vector search
            log.info("--- Stage 3: Executing Qdrant Search ---");
            long qdrantStart = System.currentTimeMillis();

            Points.SearchPoints.Builder searchBuilder = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryVector)
                    .setLimit(topK)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setScoreThreshold((float) similarityThreshold);

            if (hasFilter) {
                searchBuilder.setFilter(filterBuilder.build());
                log.info("  Pre-filter applied: YES");
            } else {
                log.info("  Pre-filter applied: NO (searching all documents)");
            }

            List<ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchBuilder.build())
                    .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long qdrantTime = System.currentTimeMillis() - qdrantStart;
            log.info("  Qdrant search completed in {}ms, found {} results", qdrantTime, scoredPoints.size());

            // Stage 4: Convert to Spring AI Document
            log.info("--- Stage 4: Processing Results ---");
            List<Document> results = new ArrayList<>();
            int idx = 0;
            for (ScoredPoint point : scoredPoints) {
                String content = "";
                if (point.getPayloadMap().containsKey("doc_content")) {
                    content = point.getPayloadMap().get("doc_content").getStringValue();
                } else if (point.getPayloadMap().containsKey("text")) {
                    content = point.getPayloadMap().get("text").getStringValue();
                } else if (point.getPayloadMap().containsKey("content")) {
                    content = point.getPayloadMap().get("content").getStringValue();
                }

                Map<String, Object> metadata = extractMetadata(point);
                metadata.put("score", point.getScore());

                results.add(new Document(content, metadata));

                log.info("  Result[{}]: score={}, filename='{}', companyId={}, contentLen={}",
                        idx++,
                        String.format("%.4f", point.getScore()),
                        metadata.get("filename"),
                        metadata.get("companyId"),
                        content.length());
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("========== AGENT-ASSIST VECTOR SEARCH COMPLETE ==========");
            log.info("Total: {} results in {}ms (embed: {}ms, search: {}ms, preFilter: {})",
                    results.size(), totalTime, embedTime, qdrantTime, hasFilter ? "YES" : "NO");

            return results;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Search interrupted", e);
            return java.util.Collections.emptyList();
        } catch (ExecutionException e) {
            log.error("Search execution failed: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        } catch (TimeoutException e) {
            log.error("Search timed out after {}s", QDRANT_TIMEOUT_SECONDS, e);
            return java.util.Collections.emptyList();
        }
    }

    private Map<String, Object> extractMetadata(ScoredPoint point) {
        Map<String, Object> metadata = new HashMap<>();
        for (Map.Entry<String, io.qdrant.client.grpc.JsonWithInt.Value> entry : point.getPayloadMap().entrySet()) {
            String key = entry.getKey();
            io.qdrant.client.grpc.JsonWithInt.Value value = entry.getValue();
            if (key.equals("doc_content") || key.equals("text") || key.equals("content")) {
                continue;
            }
            switch (value.getKindCase()) {
                case STRING_VALUE -> metadata.put(key, value.getStringValue());
                case INTEGER_VALUE -> metadata.put(key, value.getIntegerValue());
                case DOUBLE_VALUE -> metadata.put(key, value.getDoubleValue());
                case BOOL_VALUE -> metadata.put(key, value.getBoolValue());
                default -> metadata.put(key, value.toString());
            }
        }
        return metadata;
    }

    private String truncateForLog(String text) {
        if (text == null) return "null";
        if (text.length() <= 50) return text;
        return text.substring(0, 50) + "...";
    }
}
