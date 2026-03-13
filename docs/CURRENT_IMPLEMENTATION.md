# Current Implementation - Suggested Responses & Knowledge Articles

> **Note:** This document captures the existing implementation before new functionality is added.

---

## 1. Suggested Responses

### Overview
AI-generated reply suggestions for agents based on conversation context. Returns 3 empathetic, professional responses in both English and user's language.

### Architecture

```
AnalysisController
       ↓
AnalysisService.buildReplySuggestions()
       ↓
BaseAiProvider.analyzeConversation()
       ↓
OpenAI API (gpt-4o-mini)
       ↓
TranslationService (if non-English)
       ↓
List<SuggestedResponse>
```

### Key Files

| File | Purpose |
|------|---------|
| `ai/AiProvider.java` | Interface defining `analyzeConversation()` and `regenerateSuggestions()` |
| `ai/BaseAiProvider.java` | Core implementation with prompts and JSON parsing |
| `ai/openai/OpenAiProvider.java` | OpenAI-specific provider (extends BaseAiProvider) |
| `service/analysis/AnalysisService.java` | Orchestrates suggestion generation |
| `controller/AnalysisController.java` | REST endpoints |
| `dto/responseDTO/SuggestedResponse.java` | Response DTO with bilingual replies |
| `dto/responseDTO/AiAnalysisBundle.java` | Contains suggestions list from AI |

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/{interactionId}/suggestions` | Get 3 suggested responses |
| POST | `/regenerate` | Regenerate different suggestions |

### Flow Details

#### `AnalysisService.buildReplySuggestions()` (lines 51-86)
```java
1. Fetch conversation by interactionId
2. Extract all messages as strings
3. Get latest user message
4. Call aiProvider.analyzeConversation(messages, latestMsg)
5. If user language != English:
   - Translate each suggestion to user's language
6. Return List<SuggestedResponse> with englishReply + userLanguageReply
```

#### `BaseAiProvider.analyzeConversation()` (lines 79-140)
```java
1. Build prompt requesting JSON with:
   - currentSentiment (-1 to 1)
   - overallSentiment (-1 to 1)
   - summary (2-3 sentences)
   - suggestions (array of 3 strings)
2. Send to OpenAI
3. Parse JSON response
4. Return AiAnalysisBundle
```

#### Prompt Structure (BaseAiProvider lines 90-114)
```
You are a sentiment and conversation analyst.
Analyze this customer service conversation and return JSON:
{
  "currentSentiment": <-1 to 1>,
  "overallSentiment": <-1 to 1>,
  "summary": "<2-3 sentence summary>",
  "suggestions": ["<reply1>", "<reply2>", "<reply3>"]
}

Rules for suggestions:
- Professional and empathetic
- Address customer's concern directly
- Offer solutions or next steps
```

#### `BaseAiProvider.regenerateSuggestions()` (lines 261-322)
- Same as analyzeConversation but adds instruction to avoid previous suggestions
- Prompt includes: "Do NOT include these previous suggestions: [...]"

### Response DTOs

**SuggestedResponse.java:**
```java
public class SuggestedResponse {
    private String englishReply;
    private String userLanguageReply;
}
```

**AiAnalysisBundle.java:**
```java
public class AiAnalysisBundle {
    private double currentSentiment;
    private double overallSentiment;
    private String summary;
    private List<String> suggestions;
}
```

### Error Handling
- All AI methods return fallback values on error (no exceptions thrown)
- Fallback suggestions: empty list `[]`
- Logged at WARN level

---

## 2. Knowledge Articles

### Overview
Static reference documents stored in MySQL. Supports CRUD operations, full-text search, and file uploads (PDF, Word, Excel, text).

### Architecture

```
KnowledgeArticleController
           ↓
KnowledgeArticleService
           ↓
KnowledgeArticleRepository (JPA)
           ↓
MySQL (knowledge_article table)
```

### Key Files

| File | Purpose |
|------|---------|
| `model/KnowledgeArticleEntity.java` | JPA entity |
| `repository/KnowledgeArticleRepository.java` | Data access |
| `service/knowledge/KnowledgeArticleService.java` | Business logic |
| `controller/KnowledgeArticleController.java` | REST endpoints |
| `dto/responseDTO/KnowledgeArticleDTO.java` | Response DTO |
| `dto/requestDTO/KnowledgeArticleRequest.java` | Request DTO |
| `mapper/KnowledgeArticleMapper.java` | Entity-DTO mapping |
| `util/FileParser.java` | File content extraction |

### Database Schema

**Table: `knowledge_article`**
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| name | VARCHAR | Article title |
| type | VARCHAR | Category/type |
| content | TEXT | Article content |

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/knowledge-articles` | List articles (paginated) |
| GET | `/api/v1/knowledge-articles/{id}` | Get article by ID |
| GET | `/api/v1/knowledge-articles/search?query=` | Full-text search |
| POST | `/api/v1/knowledge-articles` | Create article |
| PUT | `/api/v1/knowledge-articles/{id}` | Update article |
| DELETE | `/api/v1/knowledge-articles/{id}` | Delete article |
| POST | `/api/v1/knowledge-articles/upload` | Upload single file |
| POST | `/api/v1/knowledge-articles/upload/bulk` | Bulk file upload |

### Service Methods

#### `KnowledgeArticleService.java`

```java
// Paginated list
getArticles(int page, int size) → Page<KnowledgeArticleDTO>

// Single article
getArticleById(Long id) → KnowledgeArticleDTO

// Search by name or content
searchArticles(String query) → List<KnowledgeArticleDTO>

// CRUD
createArticle(KnowledgeArticleRequest) → KnowledgeArticleDTO
updateArticle(Long id, KnowledgeArticleRequest) → KnowledgeArticleDTO
deleteArticle(Long id) → void

// File upload
uploadFile(MultipartFile file, String type) → KnowledgeArticleDTO
uploadFiles(List<MultipartFile> files, String type) → List<KnowledgeArticleDTO>
```

### Repository Queries

```java
// Full-text search on name and content
findByNameContainingIgnoreCaseOrContentContainingIgnoreCase(String name, String content)

// Filter by type
findByType(String type)
```

### File Upload

**Supported formats:**
- Text: `.txt`, `.md`, `.json`, `.html`, `.xml`, `.csv`
- Documents: `.pdf`, `.doc`, `.docx`
- Spreadsheets: `.xls`, `.xlsx`

**Validation:**
- Max file size: 10MB
- File parsing via `FileParser` utility

**FileParser extracts:**
- PDF → Text content using Apache PDFBox
- Word (.docx) → Text using Apache POI
- Excel → Cell values as text
- Plain text → Direct content

---

## 3. Integration Points

### Suggestions in Message Processing

`ConversationProcessingService.processMessage()` (lines 88-101):
```java
1. Detect language
2. Translate to English
3. Save message
4. Call analysisService.buildReplySuggestions()  // <-- Suggestions generated here
5. Build OneShotResponse with suggestions
6. Return response
```

### Current Limitations

**Suggested Responses:**
- No context from knowledge articles
- Suggestions are purely conversation-based
- No learning from agent selections

**Knowledge Articles:**
- Static storage only
- No AI-powered search/matching
- Not integrated with suggestion generation

---

## 4. Configuration

```yaml
# AI Provider
ai:
  provider: openai  # or "ollama"
  openai:
    model: gpt-4o-mini
    temperature: 0.2
  ollama:
    model: mistral:7b
    temperature: 0.2

# Spring AI
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

---

*Document created: 2026-02-27*
*Purpose: Reference for existing implementation before new features*
