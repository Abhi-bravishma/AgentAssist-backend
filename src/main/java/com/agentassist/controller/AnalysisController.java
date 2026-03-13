package com.agentassist.controller;

import com.agentassist.dto.requestDTO.RegenerateRequest;
import com.agentassist.dto.responseDTO.*;
import com.agentassist.service.analysis.AnalysisService;
import com.agentassist.service.conversation.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

    private final MessageService messageService;
    private final AnalysisService analysisService;

    // OVERALL SENTIMENT
    @GetMapping("/{interactionId}/sentiment/overall")
    public ResponseEntity<SentimentResponse> overallSentiment(@PathVariable String interactionId) {

        var all = messageService.fetchByInteraction(interactionId);
        if (all.isEmpty()) {
            return ResponseEntity.ok(new SentimentResponse("neutral", 0.0));
        }

        var englishList = all.stream()
                .map(m -> m.getEnglishText())
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (englishList.isEmpty()) {
            return ResponseEntity.ok(new SentimentResponse("neutral", 0.0));
        }

        var latest = englishList.get(englishList.size() - 1);
        var bundle = analysisService.analyzeConversation(englishList, latest);

        double score = bundle.getOverall_sentiment_score();
        String label = score > 0.3 ? "positive" : score < -0.3 ? "negative" : "neutral";

        return ResponseEntity.ok(new SentimentResponse(label, score));
    }

    // CURRENT SENTIMENT
    @GetMapping("/{interactionId}/sentiment/current")
    public ResponseEntity<SentimentResponse> currentSentiment(@PathVariable String interactionId) {

        var all = messageService.fetchByInteraction(interactionId);
        if (all.isEmpty()) {
            return ResponseEntity.ok(new SentimentResponse("neutral", 0.0));
        }

        var englishList = all.stream()
                .map(m -> m.getEnglishText())
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (englishList.isEmpty()) {
            return ResponseEntity.ok(new SentimentResponse("neutral", 0.0));
        }

        var latest = englishList.get(englishList.size() - 1);
        var bundle = analysisService.analyzeConversation(englishList, latest);

        String label = bundle.getCurrent_sentiment_label() != null ? bundle.getCurrent_sentiment_label() : "neutral";
        return ResponseEntity.ok(new SentimentResponse(label, bundle.getCurrent_sentiment_score()));
    }

    // REPLY-STYLE SUGGESTIONS
    @GetMapping("/{interactionId}/suggestions")
    public ResponseEntity<SuggestionsResponse> suggestions(@PathVariable String interactionId) {

        var replies = analysisService.buildReplySuggestions(interactionId);

        SuggestionsResponse r = new SuggestionsResponse();
        r.setReplies(replies);

        return ResponseEntity.ok(r);
    }

    // SUMMARY
    @GetMapping("/{interactionId}/summary")
    public ResponseEntity<SummaryResponse> summary(@PathVariable String interactionId) {

        String summary = analysisService.buildConversationSummary(interactionId);

        SummaryResponse r = new SummaryResponse();
        r.setSummary(summary);

        return ResponseEntity.ok(r);
    }

    // REGENERATE SUGGESTIONS
    @PostMapping("/regenerate")
    public ResponseEntity<SuggestionsResponse> regenerateSuggestions(@RequestBody RegenerateRequest request) {

        var replies = analysisService.regenerateSuggestions(
                request.getInteractionId(),
                request.getMessage()
        );

        SuggestionsResponse r = new SuggestionsResponse();
        r.setReplies(replies);

        return ResponseEntity.ok(r);
    }
}
