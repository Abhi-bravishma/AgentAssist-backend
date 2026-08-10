package com.agentassist.ai.support;

import lombok.extern.slf4j.Slf4j;

/**
 * JSON extraction/repair for LLM responses. Moved VERBATIM from
 * BaseAiProvider.cleanJson in the Part 2 split — same behaviour, same logs.
 */
@Slf4j
public final class AiJson {

    private AiJson() {
    }

    /**
     * Clean JSON response from markdown formatting and extract JSON object.
     * Handles cases where LLM adds extra text before/after JSON.
     * Also attempts to repair truncated JSON.
     */
    public static String cleanJson(String text) {
        if (text == null || text.isBlank()) return "";

        String original = text;
        text = text.trim();

        // Remove markdown code blocks
        text = text.replace("```json", "")
                .replace("```", "")
                .trim();

        // Try to extract JSON object from response
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            String extracted = text.substring(firstBrace, lastBrace + 1);
            log.debug("[AI] Extracted JSON from position {} to {}", firstBrace, lastBrace);
            return extracted;
        }

        // JSON might be truncated - try to repair it
        if (firstBrace != -1 && lastBrace == -1) {
            log.warn("[AI] JSON appears truncated, attempting repair. Raw response: {}",
                    original.length() > 500 ? original.substring(0, 500) + "..." : original);

            String partial = text.substring(firstBrace);

            // Count unclosed braces and brackets
            int openBraces = 0;
            int openBrackets = 0;
            boolean inString = false;
            char prevChar = 0;

            for (char c : partial.toCharArray()) {
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (c == '{') openBraces++;
                    else if (c == '}') openBraces--;
                    else if (c == '[') openBrackets++;
                    else if (c == ']') openBrackets--;
                }
                prevChar = c;
            }

            // Close any unclosed strings, arrays, and objects
            StringBuilder repaired = new StringBuilder(partial);
            if (inString) {
                repaired.append("\"");
            }
            for (int i = 0; i < openBrackets; i++) {
                repaired.append("]");
            }
            for (int i = 0; i < openBraces; i++) {
                repaired.append("}");
            }

            log.info("[AI] Repaired truncated JSON by adding {} closing braces, {} closing brackets",
                    openBraces, openBrackets);
            return repaired.toString();
        }

        // If no braces found at all, return cleaned text as-is
        log.warn("[AI] Could not find JSON braces in response: {}",
                original.length() > 200 ? original.substring(0, 200) + "..." : original);
        return text;
    }
}
