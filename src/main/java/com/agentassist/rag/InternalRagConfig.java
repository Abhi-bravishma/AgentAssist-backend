package com.agentassist.rag;

import io.grpc.ManagedChannel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
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

import java.util.concurrent.TimeUnit;

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
    private final RestClient.Builder restClientBuilder;

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}")
    private String openAiEmbeddingModelName;

    /**
     * A healthy embedding call takes 0.5-2s; the connection is kept warm by
     * {@code EmbeddingWarmupService}, so cold-start handshakes are not on the
     * request path. Measured over two days, everything past ~12s was a stall
     * that either never returned or returned far too late to matter, while
     * sentiment and summary were already on screen. So: fail at 12s and let the
     * caller fall back, rather than hold a finished answer hostage to this call.
     */
    @Value("${rag.internal.embedding.timeout-seconds:12}")
    private int embeddingTimeoutSeconds;

    /**
     * Qdrant client over a tuned gRPC channel.
     *
     * <p>The channel settings are not optional. Left at gRPC's defaults the
     * channel holds one connection for 30 minutes and never learns when it dies
     * underneath — the next call then sits PENDING until the caller's 30s
     * deadline and surfaces to the portal as "no documents". Keepalive detects a
     * dead connection in ~70s, and idling out after 5 minutes means a call after
     * a quiet spell opens a fresh connection rather than trusting a stale one.
     *
     * <p>Values copied from bravishma-rag's {@code QdrantClientConfig}, which
     * fixed the same failure ({@code UNAVAILABLE: io exception}) against this
     * same Qdrant server. Part 4 copied the agent-assist lane but not that
     * class, which is how this app ended up back on the untuned defaults.</p>
     */
    @Bean
    public QdrantClient qdrantClient() {
        InternalRagProperties.Qdrant qdrant = properties.getQdrant();

        NettyChannelBuilder builder = NettyChannelBuilder
                .forAddress(qdrant.getHost(), qdrant.getPort())
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .idleTimeout(300, TimeUnit.SECONDS);

        if (qdrant.isUseTls()) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }

        ManagedChannel channel = builder.build();
        log.info("[InternalRag] Qdrant channel {}:{} (tls={}, keepalive 60s, idle 300s)",
                qdrant.getHost(), qdrant.getPort(), qdrant.isUseTls());
        return new QdrantClient(QdrantGrpcClient.newBuilder(channel).build());
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
        if (openAi.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "rag.internal.embedding.provider=openai but no OpenAI embedding model is configured");
        }
        log.info("[InternalRag] Embeddings: OPENAI model={} timeout={}s (collection {})",
                openAiEmbeddingModelName, embeddingTimeoutSeconds,
                properties.getQdrant().getCollection());
        return tightlyTimedOpenAiEmbeddings();
    }

    /**
     * An OpenAI embedding model with its own, much shorter HTTP timeout.
     *
     * <p>Chat and embeddings share Spring AI's auto-configured client, whose
     * read timeout has to be generous enough for a completion (60s). Embeddings
     * have a completely different profile — a short query vectorises in well
     * under a second — so that ceiling let one stalled call burn 59.6s of a
     * 66s response before returning. Ten seconds is ~20x headroom for the work
     * actually being done, and a stall now fails fast and is retried instead.
     *
     * <p>Deliberately NOT a replacement for the auto-configured
     * {@code OpenAiEmbeddingModel} bean: this one is private to the RAG lane, so
     * nothing else in the app changes behaviour.</p>
     */
    private EmbeddingModel tightlyTimedOpenAiEmbeddings() {
        RestClient.Builder tightClient = restClientBuilder.clone()
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(Duration.ofSeconds(5))
                                .withReadTimeout(Duration.ofSeconds(embeddingTimeoutSeconds))));

        OpenAiApi api = OpenAiApi.builder()
                .apiKey(openAiApiKey)
                .baseUrl(openAiBaseUrl)
                .restClientBuilder(tightClient)
                .build();

        return new OpenAiEmbeddingModel(
                api,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(openAiEmbeddingModelName).build(),
                embeddingRetryTemplate());
    }

    /**
     * Two attempts, not the shared three - and read timeouts count as retryable.
     * The shared template retries only {@code TransientAiException}, so a timed-out
     * embedding call failed outright; one retry after a stall has succeeded in
     * practice, two more just extend the wait. Chat completions keep the shared
     * template untouched.
     */
    private static RetryTemplate embeddingRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .retryOn(TransientAiException.class)
                .retryOn(RestClientException.class)
                .exponentialBackoff(1000, 2.0, 4000)
                .build();
    }

    @Bean
    public QdrantVectorStore agentAssistVectorStore(QdrantClient qdrantClient,
                                                    @Qualifier("agentAssistEmbeddingModel") EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(properties.getQdrant().getCollection())
                .initializeSchema(properties.getQdrant().isInitializeSchema())
                .build();
    }
}
