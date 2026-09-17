# Agent Assist — Widget API

How a widget shows results **as they arrive** instead of waiting for everything.

Base URLs
- Production: `https://agent-assist-portal-api.demosaiportal.com`
- Local dev:  `http://localhost:9099` (or through the portal proxy)

All endpoints are under `/api/v1/agent-assistant`. CORS allows any origin. Every
response carries an `X-Request-Id` header — quote it when reporting a problem.

---

## The flow

```
1. POST /process/async                       → 202 { processId }
2. open three streams (or poll three GETs):
     /results/{processId}/sentiment/sse
     /results/{processId}/convo-summary/sse
     /results/{processId}/suggested-response/sse      ← token by token
3. render each piece the moment it lands; close each stream on "done"
```

Measured on an English knowledge-base question: **sentiment at ~1.9 s**, the
summary streaming from ~2.3 s, the reply streaming from ~3.4 s. Questions that
need the customer's Salesforce data (a mobile number was sent and a matching
intent was detected) add the Salesforce lookup first, so expect ~6 s for the
first piece on those. Nothing waits for anything else.

---

## 1. Submit a message

`POST /api/v1/agent-assistant/process/async`

```json
{
  "interactionId": "avaya-12345",
  "from": "customer",
  "message": "can you tell me validity of my plan",
  "mobileNumber": "+6596542183",
  "projectName": "TELCO"
}
```

| Field | Required | Notes |
|---|---|---|
| `interactionId` | yes | Conversation id. Same value for every message of one conversation. |
| `from` | yes | `customer` or `user` (`user` = the agent). Agent messages are stored and summarised but get **no suggestions**. |
| `message` | yes | ≤ 10 000 chars, any language. |
| `mobileNumber` | no | Enables Salesforce customer data (plan, cards, policies) when a matching intent is detected. |
| `projectName` | no | Scopes the knowledge base and intents (`TELCO`, `METRO`, …). Empty → `HOSPITALITY`. |

Response — immediately:

```json
{ "processId": "a8b135dd6bd2432fb6339035092cbaee", "interactionId": "avaya-12345", "status": "PROCESSING" }
```

---

## 2a. Streaming (recommended)

Three `GET` endpoints, `Content-Type: text/event-stream`. A browser `EventSource`
works directly. **Close the stream when you receive `done`** — otherwise the
browser reconnects and you get the piece again.

### Sentiment — `GET /results/{processId}/sentiment/sse`

```
event: sentiment
data: {"overallSentiment":-0.4,"currentSentiment":-0.6,"label":"negative"}

event: done
data: {"piece":"sentiment"}
```

Scores run −1 … +1. `overallSentiment` is the whole conversation, `currentSentiment` this message.

### Conversation summary — `GET /results/{processId}/convo-summary/sse`

Streams word by word, same pattern as the reply: append `token` text as it
arrives, then replace it with the `summary` event, which is the final text.

```
event: token
data: {"text":"Customer"}

event: token
data: {"text":" reported"}
        …

event: summary
data: {"summary":"Customer reported that their internet has been very slow since yesterday ..."}

event: done
data: {"piece":"convo-summary"}
```

### Suggested reply — `GET /results/{processId}/suggested-response/sse`

```
event: token
data: {"text":"Hi"}

event: token
data: {"text":" Vyankatesh,"}
        …many…

event: suggestions
data: {
  "suggestedResponses": [
    { "englishReply": "Hi Vyankatesh, your current plan, Ultra 30, expired ...",
      "userLanguageReply": null }
  ],
  "knowledgeSources": [
    { "fileName": "4iG_KB_Mobile_SIM_Activation.pdf", "relevanceScore": 0.62,
      "category": "mobile sim activation", "contentPreview": "…", "downloadUrl": "…" }
  ],
  "documentsFound": 3,
  "usedKnowledgeBase": true
}

event: done
data: {"piece":"suggested-response"}
```

Rules for the reply piece:

- **Append `token` text as it arrives**, then **replace the whole thing with
  `suggestions`** when that event lands — it is the authoritative, cleaned text.
- The streamed text is the raw model output. It usually starts with `1. ` — strip
  `^\s*\d+[.)]\s*` for display.
- `userLanguageReply` is filled only for non-English customers; show it when
  present, `englishReply` otherwise.
- Tokens are only streamed when the knowledge base found something. When it
  found nothing you get no tokens, just a `suggestions` event with the fallback
  reply ("I don't have that information…") or a customer-data answer.
- `suggestedResponses` is empty for agent (`from: user`) messages.

### Errors on any stream

```
event: error
data: {"error":"…"}
```

The stream closes after it. Show the message; the other two pieces may still succeed.

---

## 2b. Polling (no SSE available)

Same `POST`, then poll each piece every ~500 ms:

| Endpoint | 200 body |
|---|---|
| `GET /results/{processId}/sentiment` | `{ processId, status, overallSentiment, currentSentiment, label }` |
| `GET /results/{processId}/convo-summary` | `{ processId, status, summary }` |
| `GET /results/{processId}/suggested-response` | `{ processId, status, suggestedResponses, knowledgeSources, documentsFound, usedKnowledgeBase }` |
| `GET /results/{processId}/result` | `{ processId, status, response }` — the full one-shot response, once everything is done |

While a piece is not yet ready: `202 { "status": "pending" }`.
If processing failed: `500 { "status": "FAILED", "error": "…" }`.
Unknown `processId`: `404`.

---

## Newest message of a conversation

Every endpoint above also exists as
`GET /api/v1/agent-assistant/{interactionId}/latest/…` — same paths, same bodies,
resolving to the most recent message of that conversation. Useful when the
widget only knows the interaction id.

---

## One-shot (unchanged)

`POST /api/v1/agent-assistant/process` — same request body, one response with
everything after ~6–8 s. Still supported; nothing about it changed.

---

## Minimal browser example

```js
const base = 'https://agent-assist-portal-api.demosaiportal.com/api/v1/agent-assistant';

const { processId } = await (await fetch(`${base}/process/async`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ interactionId, from: 'customer', message, mobileNumber, projectName }),
})).json();

function stream(piece, handlers) {
  const es = new EventSource(`${base}/results/${processId}/${piece}/sse`);
  for (const [name, fn] of Object.entries(handlers)) {
    es.addEventListener(name, e => fn(JSON.parse(e.data)));
  }
  es.addEventListener('done', () => es.close());
  es.addEventListener('error', e => { if (e.data) { showError(JSON.parse(e.data).error); es.close(); } });
  return es;
}

stream('sentiment',          { sentiment:   s => renderSentiment(s) });
stream('convo-summary',      {
  token:       t => appendSummaryText(t.text),         // live typing
  summary:     s => renderSummary(s.summary),          // final
});
stream('suggested-response', {
  token:       t => appendReplyText(t.text),          // live typing
  suggestions: s => renderReply(s.suggestedResponses, s.knowledgeSources),  // final
});
```

---

## Timeouts and proxies

- Streams complete within ~10 s in normal operation; the server closes them itself.
- Every response includes `X-Accel-Buffering: no`, so nginx passes events through
  as they are written. Cloudflare sits in front of production and streams
  `text/event-stream` fine.
- If a stream shows nothing until the very end, something in between is
  buffering — check for a proxy the widget goes through.
