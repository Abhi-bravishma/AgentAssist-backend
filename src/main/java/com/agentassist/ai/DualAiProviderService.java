package com.agentassist.ai;

import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.ComparisonResponse;
import com.agentassist.dto.responseDTO.ProviderAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that manages multiple AI providers and supports comparison mode.
 * Can run OpenAI and Ollama in parallel for side-by-side comparison.
 */
@Slf4j
@Service
public class DualAiProviderService {

    private final AiProvider openAiProvider;
    private final AiProvider ollamaProvider;
    private final ExecutorService executor;

    @Autowired
    public DualAiProviderService(
            @Qualifier("openAiProvider") @Autowired(required = false) AiProvider openAiProvider,
            @Qualifier("ollamaProvider") @Autowired(required = false) AiProvider ollamaProvider
    ) {
        this.openAiProvider = openAiProvider;
        this.ollamaProvider = ollamaProvider;
        this.executor = Executors.newFixedThreadPool(2);

        log.info("========================================");
        log.info("DUAL AI PROVIDER SERVICE INITIALIZED");
        log.info("OpenAI available: {}", openAiProvider != null);
        log.info("Ollama available: {}", ollamaProvider != null);
        log.info("========================================");
    }

    /**
     * Get the provider for a specific type.
     */
    public AiProvider getProvider(ProviderType type) {
        return switch (type) {
            case OPENAI -> openAiProvider;
            case OLLAMA -> ollamaProvider;
            case BOTH -> openAiProvider; // Default to OpenAI for single operations
        };
    }

    /**
     * Check if a specific provider is available.
     */
    public boolean isProviderAvailable(ProviderType type) {
        return switch (type) {
            case OPENAI -> openAiProvider != null;
            case OLLAMA -> ollamaProvider != null;
            case BOTH -> openAiProvider != null && ollamaProvider != null;
        };
    }

    /**
     * Analyze conversation with a specific provider.
     */
    public AiAnalysisBundle analyzeConversation(ProviderType type, List<String> messages, String latestUserMsg) {
        AiProvider provider = getProvider(type);
        if (provider == null) {
            log.warn("Provider {} not available, returning empty bundle", type);
            return createEmptyBundle();
        }
        return provider.analyzeConversation(messages, latestUserMsg);
    }

    /**
     * Analyze conversation with both providers in parallel.
     * Returns comparison results.
     */
    public ComparisonResponse analyzeConversationComparison(List<String> messages, String latestUserMsg) {
        log.info("[Comparison] Starting parallel analysis with OpenAI and Ollama");

        CompletableFuture<ProviderAnalysisResult> openAiFuture = CompletableFuture.supplyAsync(
                () -> analyzeWithProvider(openAiProvider, ProviderType.OPENAI, messages, latestUserMsg),
                executor
        );

        CompletableFuture<ProviderAnalysisResult> ollamaFuture = CompletableFuture.supplyAsync(
                () -> analyzeWithProvider(ollamaProvider, ProviderType.OLLAMA, messages, latestUserMsg),
                executor
        );

        // Wait for both to complete
        CompletableFuture.allOf(openAiFuture, ollamaFuture).join();

        ProviderAnalysisResult openAiResult = openAiFuture.join();
        ProviderAnalysisResult ollamaResult = ollamaFuture.join();

        // Build comparison summary
        ComparisonResponse.ComparisonSummary summary = buildComparisonSummary(openAiResult, ollamaResult);

        log.info("[Comparison] Analysis complete - OpenAI: {}ms, Ollama: {}ms",
                openAiResult.getLatencyMs(), ollamaResult.getLatencyMs());

        return ComparisonResponse.builder()
                .openai(openAiResult)
                .ollama(ollamaResult)
                .comparison(summary)
                .build();
    }

    /**
     * Translate text to English with both providers.
     */
    public ComparisonResponse translateToEnglishComparison(String text) {
        log.info("[Comparison] Starting parallel translation to English");

        CompletableFuture<ProviderAnalysisResult> openAiFuture = CompletableFuture.supplyAsync(
                () -> translateWithProvider(openAiProvider, ProviderType.OPENAI, text, true, null),
                executor
        );

        CompletableFuture<ProviderAnalysisResult> ollamaFuture = CompletableFuture.supplyAsync(
                () -> translateWithProvider(ollamaProvider, ProviderType.OLLAMA, text, true, null),
                executor
        );

        CompletableFuture.allOf(openAiFuture, ollamaFuture).join();

        ProviderAnalysisResult openAiResult = openAiFuture.join();
        ProviderAnalysisResult ollamaResult = ollamaFuture.join();

        return ComparisonResponse.builder()
                .openai(openAiResult)
                .ollama(ollamaResult)
                .comparison(buildComparisonSummary(openAiResult, ollamaResult))
                .build();
    }

    /**
     * Detect language with both providers.
     */
    public ComparisonResponse detectLanguageComparison(String text) {
        log.info("[Comparison] Starting parallel language detection");

        CompletableFuture<ProviderAnalysisResult> openAiFuture = CompletableFuture.supplyAsync(
                () -> detectLanguageWithProvider(openAiProvider, ProviderType.OPENAI, text),
                executor
        );

        CompletableFuture<ProviderAnalysisResult> ollamaFuture = CompletableFuture.supplyAsync(
                () -> detectLanguageWithProvider(ollamaProvider, ProviderType.OLLAMA, text),
                executor
        );

        CompletableFuture.allOf(openAiFuture, ollamaFuture).join();

        ProviderAnalysisResult openAiResult = openAiFuture.join();
        ProviderAnalysisResult ollamaResult = ollamaFuture.join();

        return ComparisonResponse.builder()
                .openai(openAiResult)
                .ollama(ollamaResult)
                .comparison(buildComparisonSummary(openAiResult, ollamaResult))
                .build();
    }

    /**
     * Translate with a specific provider.
     */
    public String translateToEnglish(ProviderType type, String text) {
        AiProvider provider = getProvider(type);
        if (provider == null) {
            log.warn("Provider {} not available for translation", type);
            return text;
        }
        return provider.translateToEnglish(text);
    }

    /**
     * Translate from English with a specific provider.
     */
    public String translateFromEnglish(ProviderType type, String english, String targetLang) {
        AiProvider provider = getProvider(type);
        if (provider == null) {
            log.warn("Provider {} not available for translation", type);
            return english;
        }
        return provider.translateFromEnglish(english, targetLang);
    }

    /**
     * Detect language with a specific provider.
     */
    public String detectLanguage(ProviderType type, String text) {
        AiProvider provider = getProvider(type);
        if (provider == null) {
            log.warn("Provider {} not available for language detection", type);
            return "en";
        }
        return provider.detectLanguage(text);
    }

    // -------------------------------------------------------------------------
    // Private helper methods
    // -------------------------------------------------------------------------

    private ProviderAnalysisResult analyzeWithProvider(
            AiProvider provider,
            ProviderType type,
            List<String> messages,
            String latestUserMsg
    ) {
        if (provider == null) {
            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue() + ":unavailable")
                    .success(false)
                    .errorMessage("Provider not configured")
                    .build();
        }

        long startTime = System.currentTimeMillis();
        try {
            AiAnalysisBundle bundle = provider.analyzeConversation(messages, latestUserMsg);
            long latency = System.currentTimeMillis() - startTime;

            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue())
                    .overallSentiment(bundle.getOverall_sentiment_score())
                    .currentSentiment(bundle.getCurrent_sentiment_score())
                    .sentimentLabel(bundle.getCurrent_sentiment_label())
                    .summary(bundle.getSummary())
                    .suggestions(bundle.getSuggestions())
                    .latencyMs(latency)
                    .success(true)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[{}] Analysis failed: {}", type, e.getMessage(), e);
            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue())
                    .latencyMs(latency)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private ProviderAnalysisResult translateWithProvider(
            AiProvider provider,
            ProviderType type,
            String text,
            boolean toEnglish,
            String targetLang
    ) {
        if (provider == null) {
            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue() + ":unavailable")
                    .success(false)
                    .errorMessage("Provider not configured")
                    .build();
        }

        long startTime = System.currentTimeMillis();
        try {
            String translation = toEnglish
                    ? provider.translateToEnglish(text)
                    : provider.translateFromEnglish(text, targetLang);
            long latency = System.currentTimeMillis() - startTime;

            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue())
                    .englishTranslation(translation)
                    .latencyMs(latency)
                    .success(true)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[{}] Translation failed: {}", type, e.getMessage(), e);
            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue())
                    .latencyMs(latency)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private ProviderAnalysisResult detectLanguageWithProvider(
            AiProvider provider,
            ProviderType type,
            String text
    ) {
        if (provider == null) {
            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue() + ":unavailable")
                    .success(false)
                    .errorMessage("Provider not configured")
                    .build();
        }

        long startTime = System.currentTimeMillis();
        try {
            String language = provider.detectLanguage(text);
            long latency = System.currentTimeMillis() - startTime;

            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue())
                    .detectedLanguage(language)
                    .latencyMs(latency)
                    .success(true)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[{}] Language detection failed: {}", type, e.getMessage(), e);
            return ProviderAnalysisResult.builder()
                    .provider(type)
                    .providerName(type.getValue())
                    .latencyMs(latency)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private ComparisonResponse.ComparisonSummary buildComparisonSummary(
            ProviderAnalysisResult openAiResult,
            ProviderAnalysisResult ollamaResult
    ) {
        double sentimentDiff = 0;
        boolean labelMatch = true;
        String fasterProvider = "unknown";
        long latencyDiff = 0;

        if (openAiResult.isSuccess() && ollamaResult.isSuccess()) {
            sentimentDiff = openAiResult.getOverallSentiment() - ollamaResult.getOverallSentiment();

            if (openAiResult.getSentimentLabel() != null && ollamaResult.getSentimentLabel() != null) {
                labelMatch = openAiResult.getSentimentLabel().equalsIgnoreCase(ollamaResult.getSentimentLabel());
            }

            latencyDiff = Math.abs(openAiResult.getLatencyMs() - ollamaResult.getLatencyMs());
            fasterProvider = openAiResult.getLatencyMs() <= ollamaResult.getLatencyMs() ? "openai" : "ollama";
        }

        return ComparisonResponse.ComparisonSummary.builder()
                .sentimentDifference(sentimentDiff)
                .sentimentLabelMatch(labelMatch)
                .fasterProvider(fasterProvider)
                .latencyDifferenceMs(latencyDiff)
                .build();
    }

    private AiAnalysisBundle createEmptyBundle() {
        AiAnalysisBundle bundle = new AiAnalysisBundle();
        bundle.setOverall_sentiment_score(0.0);
        bundle.setCurrent_sentiment_score(0.0);
        bundle.setCurrent_sentiment_label("neutral");
        bundle.setSummary("");
        bundle.setSuggestions(List.of());
        return bundle;
    }
}
