package com.agentassist.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans for the in-app RAG lane.
 *
 * <p>The embedding model is selected by {@code rag.internal.embedding.provider}
 * — OpenAI today (no Ollama server available), Ollama later by flipping the
 * property and pointing {@code spring.ai.ollama.base-url} at it. The choice is
 * INSEPARABLE from the collection: vectors embedded by one model cannot be
 * searched with another (mxbai is 1024-dim, OpenAI 1536-dim — Qdrant rejects
 * the query outright). That pairing lives in the properties, documented there.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalRagConfig {

    private final InternalRagProperties properties;

    @Bean
    public QdrantClient qdrantClient() {
        InternalRagProperties.Qdrant qdrant = properties.getQdrant();
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getPort(), qdrant.isUseTls())
                        .build());
    }

    /** The embedder the agent-assist lane uses for BOTH ingestion and search. */
    @Bean(name = "agentAssistEmbeddingModel")
    public EmbeddingModel agentAssistEmbeddingModel(ObjectProvider<OpenAiEmbeddingModel> openAi,
                                                    ObjectProvider<OllamaEmbeddingModel> ollama) {
        String provider = properties.getEmbedding().getProvider();
        if ("ollama".equalsIgnoreCase(provider)) {
            OllamaEmbeddingModel model = ollama.getIfAvailable();
            if (model == null) {
                throw new IllegalStateException(
                        "rag.internal.embedding.provider=ollama but no Ollama embedding model is configured");
            }
            log.info("[InternalRag] Embeddings: OLLAMA (collection {})",
                    properties.getQdrant().getCollection());
            return model;
        }
        OpenAiEmbeddingModel model = openAi.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException(
                    "rag.internal.embedding.provider=openai but no OpenAI embedding model is configured");
        }
        log.info("[InternalRag] Embeddings: OPENAI (collection {})",
                properties.getQdrant().getCollection());
        return model;
    }

    @Bean
    public QdrantVectorStore agentAssistVectorStore(QdrantClient qdrantClient,
                                                    @Qualifier("agentAssistEmbeddingModel") EmbeddingModel embeddingModel) {
        return new QdrantVectorStore(qdrantClient,
                properties.getQdrant().getCollection(),
                embeddingModel,
                properties.getQdrant().isInitializeSchema());
    }
}
