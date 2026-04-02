package com.agentassist.controller;

import com.agentassist.dto.requestDTO.FollowUpCheckRequest;
import com.agentassist.dto.requestDTO.RegenerateRequest;
import com.agentassist.dto.responseDTO.*;
import jakarta.validation.Valid;
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
                request.getMessage(),
                request.getChecklistContext(),
                request.getCustomerName()
        );

        SuggestionsResponse r = new SuggestionsResponse();
        r.setReplies(replies);

        return ResponseEntity.ok(r);
    }

    // FOLLOW-UP CHECK - Called at end of interaction
    /**
     * Analyze a completed conversation to determine if follow-up is required.
     * Call this endpoint when an interaction ends to get AI-powered follow-up recommendations.
     *
     * @param request Contains interactionId and optional transcript/customerName
     * @return Follow-up analysis with requirement, urgency, suggested actions, and reasoning
     */
    @PostMapping("/follow-up/check")
    public ResponseEntity<FollowUpCheckResponse> checkFollowUpRequired(@Valid @RequestBody FollowUpCheckRequest request) {

        FollowUpCheckResponse response;

        // If transcript is provided, use it directly
        if (request.getTranscript() != null && !request.getTranscript().isEmpty()) {
            response = analysisService.analyzeFollowUpRequirementFromTranscript(
                    request.getTranscript(),
                    request.getCustomerName()
            );
        } else {
            // Otherwise, fetch from DB using interactionId
            response = analysisService.analyzeFollowUpRequirement(
                    request.getInteractionId(),
                    request.getCustomerName()
            );
        }

        return ResponseEntity.ok(response);
    }

    // GET version for simple lookup by interactionId
    /**
     * Quick follow-up check using just the interaction ID.
     * Fetches conversation from database and analyzes.
     */
    @GetMapping("/{interactionId}/follow-up")
    public ResponseEntity<FollowUpCheckResponse> checkFollowUpByInteraction(@PathVariable String interactionId) {
        return ResponseEntity.ok(analysisService.analyzeFollowUpRequirement(interactionId));
    }
}
