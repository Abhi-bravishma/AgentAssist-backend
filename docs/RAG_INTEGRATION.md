# RAG Integration - Suggested Responses & Knowledge Articles

> **Date:** 2026-02-27
> **Status:** Implemented

---

## Overview

Agent Assist now integrates with an external RAG (Retrieval Augmented Generation) application to provide AI-powered reply suggestions based on:
1. **Conversation history** - The full context of customer-agent interaction
2. **Knowledge base documents** - Uploaded documents stored in Qdrant vector database

The integration also shows which knowledge base documents were used to generate suggestions (**Knowledge Sources**).

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AGENT ASSIST APP                                │
│                           (agentassist - Port 8080)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌──────────────────────┐    ┌─────────────────────┐    │
│  │  Frontend   │───▶│ ConversationProcessing│───▶│   AnalysisService   │    │
│  │  (Agent UI) │    │      Service          │    │                     │    │
│  └─────────────┘    └──────────────────────┘    └──────────┬──────────┘    │
│                                                             │               │
│                              ┌───────────────────────────────┤               │
│                              │                               │               │
│                              ▼                               ▼               │
│                     ┌─────────────────┐           ┌─────────────────┐       │
│                     │  OpenAI/Ollama  │           │  RagClient      │       │
│                     │  (Translation,  │           │  (Suggestions)  │       │
│                     │   Sentiment)    │           │                 │       │
│                     └─────────────────┘           └────────┬────────┘       │
│                                                            │                │
└────────────────────────────────────────────────────────────┼────────────────┘
                                                             │
                                                             │ HTTP/REST
                                                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               RAG APP                                        │
│                        (bravishma-rag - Port 8080)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐    ┌─────────────────────┐    ┌────────────────┐  │
│  │ AgentAssistController│───▶│ AgentAssistService  │───▶│ FilteredVector │  │
│  │ /api/v1/agent-assist │    │                     │    │ SearchService  │  │
│  │ /suggestions         │    └──────────┬──────────┘    └───────┬────────┘  │
│  └─────────────────────┘               │                        │           │
│                                        │                        │           │
│                                        ▼                        ▼           │
│                              ┌─────────────────┐      ┌─────────────────┐   │
│                              │  Ollama LLM     │      │   Qdrant        │   │
│                              │  (Suggestions)  │      │  Vector DB      │   │
│                              └─────────────────┘      └─────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow

```
1. CUSTOMER MESSAGE ARRIVES
   │
   ▼
2. AGENT ASSIST - ConversationProcessingService.processMessage()
   ├── Detect language (OpenAI)
   ├── Translate to English (OpenAI)
   ├── Save message to MySQL
   ├── Analyze sentiment (OpenAI)
   │
   ▼
3. CALL RAG APP for Suggestions
   │
   │  POST http://135.222.41.146:8080/api/v1/agent-assist/suggestions
   │  {
   │    "conversationHistory": ["Customer: ...", "Agent: ..."],
   │    "latestMessage": "Customer: ...",
   │    "suggestionCount": 3
   │  }
   │
   ▼
4. RAG APP - AgentAssistService
   ├── Query Qdrant (vector search with company filter)
   ├── Retrieve relevant document chunks
   ├── Generate suggestions using Ollama + context
   │
   ▼
5. RAG APP RETURNS
   │
   │  {
   │    "suggestions": ["...", "...", "..."],
   │    "sourceDocuments": [
   │      {"fileName": "policy.pdf", "score": 0.85, ...}
   │    ],
   │    "documentsFound": 2,
   │    "usedContext": true
   │  }
   │
   ▼
6. AGENT ASSIST - Process Response
   ├── Translate suggestions to user's language (if needed)
   ├── Map sourceDocuments → KnowledgeSources
   │
   ▼
7. RETURN TO FRONTEND (OneShotResponse)
   {
     "overallSentiment": 0.5,
     "currentSentiment": -0.2,
     "summary": "Customer inquiry about...",
     "suggestedResponses": [
       {"englishReply": "...", "userLanguageReply": "..."}
     ],
     "knowledgeSources": [
       {"fileName": "policy.pdf", "relevanceScore": 0.85, "contentPreview": "..."}
     ],
     "documentsFound": 2,
     "usedKnowledgeBase": true
   }
```

---

## Files Created/Modified in Agent Assist

### New Files

| File | Purpose |
|------|---------|
| `config/RagClientConfig.java` | WebClient configuration for RAG API |
| `dto/rag/RagSuggestionRequest.java` | Request DTO for RAG API |
| `dto/rag/RagSuggestionResponse.java` | Response DTO from RAG API |
| `dto/rag/RagSourceDocument.java` | Source document metadata |
| `dto/rag/RagSearchFilter.java` | Search filter parameters |
| `dto/responseDTO/KnowledgeSource.java` | Knowledge source for frontend |
| `service/rag/RagClient.java` | HTTP client for RAG API calls |

### Modified Files

| File | Changes |
|------|---------|
| `application.yaml` | Added `rag` configuration section |
| `dto/responseDTO/OneShotResponse.java` | Added `knowledgeSources`, `documentsFound`, `usedKnowledgeBase` |
| `service/analysis/AnalysisService.java` | Added `buildReplySuggestionsWithRag()` method |
| `service/processing/ConversationProcessingService.java` | Use RAG for suggestions when enabled |

---

## Files Created in RAG App

| File | Purpose |
|------|---------|
| `dto/request/AgentAssistRequest.java` | Request DTO |
| `dto/response/AgentAssistResponse.java` | Response DTO |
| `dto/response/SourceDocument.java` | Document metadata |
| `service/AgentAssistService.java` | Service interface |
| `service/impl/AgentAssistServiceImpl.java` | Service implementation |
| `controller/AgentAssistController.java` | REST endpoint |

---

## Configuration

### Agent Assist (`application.yaml`)

```yaml
# RAG Integration
rag:
  base-url: ${RAG_BASE_URL:http://135.222.41.146:8080}
  timeout: ${RAG_TIMEOUT:30000}
  enabled: ${RAG_ENABLED:true}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `RAG_BASE_URL` | `http://135.222.41.146:8080` | RAG application URL |
| `RAG_TIMEOUT` | `30000` | Request timeout (ms) |
| `RAG_ENABLED` | `true` | Enable/disable RAG integration |

---

## API Contracts

### RAG Request

```json
POST /api/v1/agent-assist/suggestions

{
  "conversationHistory": [
    "Customer: Hi, I need help with my order",
    "Agent: Hello! I'd be happy to help. What's your order number?",
    "Customer: It's #12345, the delivery is late"
  ],
  "latestMessage": "Customer: It's #12345, the delivery is late",
  "agentId": 1,
  "filters": {
    "categories": ["support", "delivery"],
    "minScore": 0.5,
    "topK": 5
  },
  "suggestionCount": 3
}
```

### RAG Response

```json
{
  "suggestions": [
    "I apologize for the delay with order #12345. Let me check the current status for you right away.",
    "I understand your concern about the late delivery. I'll investigate this immediately and provide you with an update.",
    "Thank you for your patience. Let me look into order #12345 and find out what's causing the delay."
  ],
  "sourceDocuments": [
    {
      "fileName": "delivery-policy.pdf",
      "score": 0.85,
      "sourceType": "pdf",
      "category": "support",
      "chunkIndex": 3,
      "contentPreview": "Our standard delivery timeframe is 3-5 business days. If your order is delayed..."
    },
    {
      "fileName": "customer-service-guide.pdf",
      "score": 0.72,
      "sourceType": "pdf",
      "category": "training",
      "chunkIndex": 12,
      "contentPreview": "When handling delivery complaints, always acknowledge the customer's frustration..."
    }
  ],
  "documentsFound": 2,
  "usedContext": true,
  "agentPrompt": "Default",
  "conversationSummary": "Conversation with 3 messages. Latest: Customer: It's #12345, the delivery is late",
  "blocked": false,
  "blockedReason": null
}
```

### OneShotResponse (Agent Assist)

```json
{
  "overallSentiment": 0.3,
  "currentSentiment": -0.4,
  "summary": "Customer is inquiring about a delayed delivery for order #12345.",
  "suggestedResponses": [
    {
      "englishReply": "I apologize for the delay with order #12345...",
      "userLanguageReply": "Me disculpo por el retraso con el pedido #12345..."
    }
  ],
  "knowledgeSources": [
    {
      "fileName": "delivery-policy.pdf",
      "relevanceScore": 0.85,
      "documentType": "pdf",
      "category": "support",
      "contentPreview": "Our standard delivery timeframe is 3-5 business days..."
    }
  ],
  "documentsFound": 2,
  "usedKnowledgeBase": true
}
```

---

## Fallback Behavior

When RAG is disabled or unavailable:
1. Suggestions are generated using local AI (OpenAI/Ollama)
2. `knowledgeSources` will be empty
3. `usedKnowledgeBase` will be `false`
4. `documentsFound` will be `0`

---

## Testing

### Enable RAG
```yaml
rag:
  enabled: true
```

### Disable RAG (fallback to local AI)
```yaml
rag:
  enabled: false
```

### Test Endpoint
```bash
curl -X POST http://localhost:8080/api/v1/agent-assistant/process \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "interactionId": "test-123",
    "from": "user",
    "messageText": "I need help with my delivery"
  }'
```
