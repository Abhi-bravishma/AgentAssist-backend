package com.agentassist.service.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the analysis call's JSON while the model is still writing it.
 *
 * <p>The analysis prompt asks for one object in a fixed order — three short
 * sentiment fields, then {@code summary}, then {@code suggestions}. That order
 * means two pieces are readable long before the object closes: sentiment is
 * complete once its third field ends, and the summary string can be relayed
 * character by character as it arrives. This tap does exactly that and nothing
 * else; the complete JSON is still parsed afterwards, unchanged, and that parse
 * is what gets stored and returned.
 *
 * <p>It is deliberately tolerant of what models actually emit: a
 * <code>```json</code> fence before the object, escaped quotes and newlines inside
 * the summary, a chunk boundary landing in the middle of an escape sequence.
 * Anything it cannot read yet, it waits for; it never guesses.</p>
 */
final class AnalysisStreamTap implements Consumer<String> {

    /** Wire names, shared with the listener that relays them. */
    static final String EVENT_SENTIMENT = "sentiment";
    static final String EVENT_SUMMARY_TOKEN = "summary-token";

    // A number is only complete once the comma after it has arrived.
    private static final Pattern OVERALL = Pattern.compile("\"overall_sentiment_score\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*,");
    private static final Pattern CURRENT = Pattern.compile("\"current_sentiment_score\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*,");
    private static final Pattern LABEL = Pattern.compile("\"current_sentiment_label\"\\s*:\\s*\"([A-Za-z]+)\"");
    private static final Pattern SUMMARY_OPEN = Pattern.compile("\"summary\"\\s*:\\s*\"");

    private final PipelineListener listener;
    private final StringBuilder raw = new StringBuilder();

    private boolean sentimentSent;
    /** Index in {@link #raw} of the first character of the summary value; -1 until seen. */
    private int summaryStart = -1;
    /** Index in {@link #raw} up to which the summary value has been consumed. */
    private int cursor;
    private boolean summaryClosed;

    AnalysisStreamTap(PipelineListener listener) {
        this.listener = listener;
    }

    @Override
    public void accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        raw.append(chunk);
        if (!sentimentSent) {
            trySentiment();
        }
        if (!summaryClosed) {
            trySummary();
        }
    }

    private void trySentiment() {
        Matcher overall = OVERALL.matcher(raw);
        Matcher current = CURRENT.matcher(raw);
        Matcher label = LABEL.matcher(raw);
        if (!overall.find() || !current.find() || !label.find()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overallSentiment", Double.parseDouble(overall.group(1)));
        payload.put("currentSentiment", Double.parseDouble(current.group(1)));
        payload.put("label", label.group(1).toLowerCase());
        sentimentSent = true;
        listener.on(EVENT_SENTIMENT, payload);
    }

    private void trySummary() {
        if (summaryStart < 0) {
            Matcher open = SUMMARY_OPEN.matcher(raw);
            if (!open.find()) {
                return;
            }
            summaryStart = open.end();
            cursor = summaryStart;
        }

        StringBuilder out = new StringBuilder();
        int i = cursor;
        int len = raw.length();

        while (i < len) {
            char c = raw.charAt(i);
            if (c == '"') {
                summaryClosed = true;
                i++;
                break;
            }
            if (c != '\\') {
                out.append(c);
                i++;
                continue;
            }
            // Escape sequence: need at least one more char to know what it is.
            if (i + 1 >= len) {
                break;
            }
            char e = raw.charAt(i + 1);
            if (e == 'u') {
                // \\uXXXX needs four hex digits; wait for all of them.
                if (i + 6 > len) {
                    break;
                }
                try {
                    out.append((char) Integer.parseInt(raw.substring(i + 2, i + 6), 16));
                } catch (NumberFormatException ignored) {
                    // Malformed escape: drop it rather than corrupt the text.
                }
                i += 6;
                continue;
            }
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                default -> { /* \r \b \f and anything unknown: not worth showing */ }
            }
            i += 2;
        }

        cursor = i;
        if (out.length() > 0) {
            listener.on(EVENT_SUMMARY_TOKEN, out.toString());
        }
    }
}
