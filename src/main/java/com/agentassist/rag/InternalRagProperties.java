package com.agentassist.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the IN-APP agent-assist RAG lane (copied from
 * bravishma-rag; see package docs on the service classes). Defaults mirror the
 * values that lane ran with in production: topK 3, vectorScore 0.3,
 * minRelevanceScore 0.55, chunks 300/50.
 *
 * <p>The Qdrant server/collection default to the EXISTING shared collection so
 * every document uploaded through the old service keeps working — isolation
 * inside it is by the {@code useCase="agent_assist"} payload tag, exactly as
 * before. Embeddings are pinned to Ollama's mxbai-embed-large because the
 * stored vectors were produced with it.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.internal")
public class InternalRagProperties {

    /** Master switch for the internal lane (mirrors the old rag.enabled). */
    private boolean enabled = true;

    private Qdrant qdrant = new Qdrant();

    private Embedding embedding = new Embedding();

    /** Results fetched per search. */
    private int topK = 3;

    /** Initial Qdrant similarity threshold. */
    private double vectorScore = 0.3;

    /** Post-filter: only documents at or above this score reach the prompt. */
    private double minRelevanceScore = 0.55;

    private Chunk chunk = new Chunk();

    private FileStorage fileStorage = new FileStorage();

    @Data
    public static class Qdrant {
        private String host = "74.225.250.214";
        private int port = 6334;
        /**
         * OpenAI-embedded collection (1536-dim), auto-created on first boot.
         * The legacy mxbai collection (pdf_docs_new_mxbai-embed-large, 1024-dim)
         * is dimension-incompatible with OpenAI embeddings — switch back to it
         * only together with embedding.provider=ollama.
         */
        private String collection = "agent_assist_docs_openai";
        private boolean useTls = false;
        /** Create the collection if absent. No-op when it already exists. */
        private boolean initializeSchema = true;
    }

    @Data
    public static class Embedding {
        /**
         * openai (default — no Ollama server available right now) | ollama.
         * When the Ollama server is ready: set provider=ollama, point
         * spring.ai.ollama.base-url at it, and switch qdrant.collection back to
         * pdf_docs_new_mxbai-embed-large to reuse the previously uploaded
         * documents (they were embedded with mxbai-embed-large).
         */
        private String provider = "openai";
    }

    @Data
    public static class Chunk {
        private int size = 300;
        private int overlap = 50;
    }

    @Data
    public static class FileStorage {
        private String path = "./agent-assist-files";
        private boolean enabled = true;
    }
}
