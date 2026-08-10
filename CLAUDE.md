# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

```bash
mvn spring-boot:run                    # Run dev server on port 8080
mvn clean install                      # Build WAR package
mvn test                               # Run all tests
mvn test -Dtest=ClassName              # Run single test class
mvn test -Dtest=ClassName#methodName   # Run single test method
```

Swagger UI available at: `http://localhost:8080/swagger-ui.html`

## Architecture Overview

Spring Boot 3.3.3 backend (Java 21) providing AI-powered conversation analysis for agent assistance. Uses OpenAI API (via Spring AI) for LLM capabilities.

### Request Flow

```
Controller → Service → AiProvider → OpenAI API
                ↓
           Repository → MySQL
```

Main processing flow in `ConversationProcessingService.processMessage()`:
1. Detect language (OpenAI)
2. Translate to English (OpenAI)
3. Save message to DB
4. Analyze conversation (OpenAI) → sentiment, summary, suggestions
5. Translate suggestions back to user's language
6. Return `OneShotResponse`

### Package Structure

| Package | Responsibility |
|---------|----------------|
| `controller` | REST endpoints under `/api/v1/` |
| `service/analysis` | AI analysis orchestration |
| `service/conversation` | Message and conversation persistence |
| `service/translation` | Language detection and translation |
| `service/processing` | Main message processing pipeline |
| `ai` | AI providers (OpenAI, Ollama) with prompt engineering |
| `repository` | JPA repositories |
| `model` | JPA entities |
| `dto/requestDTO` | Request payloads |
| `dto/responseDTO` | Response payloads |
| `mapper` | MapStruct entity-DTO mappers |
| `security` | JWT filter, Spring Security config |
| `config` | WebClient, OpenAPI configs |

### API Endpoints

**Public (no auth):**
- `POST /api/v1/auth/register` - Register user
- `POST /api/v1/auth/login` - Get JWT token

**Protected (JWT required):**
- `POST /api/v1/agent-assistant/process` - Process message (main endpoint)
- `GET /api/v1/{interactionId}/sentiment/overall` - Overall sentiment
- `GET /api/v1/{interactionId}/sentiment/current` - Current message sentiment
- `GET /api/v1/{interactionId}/suggestions` - Reply suggestions
- `GET /api/v1/{interactionId}/summary` - Conversation summary
- `POST /api/v1/translate` - Translate text
- `POST /api/v1/language/detect` - Detect language

### OpenAI Integration

`AiProvider` interface (implemented by `OpenAiProvider` and `OllamaProvider`) handles all LLM calls:
- `analyzeConversation()` - Returns sentiment scores, summary, suggestions
- `analyzeText()` - Single text analysis
- `translateToEnglish()` / `translateFromEnglish()` - Translation
- `detectLanguage()` - Returns ISO 639-1 code
- `computeOverallSentiment()` - Aggregate sentiment from message list
- `regenerateSuggestions()` - Generate new suggestions excluding previous ones

All methods return fallback values on error (no exceptions thrown to caller).

### Configuration

`application.yaml` requires:
```yaml
spring.datasource.url/username/password  # MySQL connection
jwt.secret                                # JWT signing key
spring.ai.openai.api-key                  # OpenAI API key
ai.provider                               # "openai" (default) or "ollama"
ai.openai.model                           # Model name (default: gpt-4o-mini)
```

Database migrations in `src/main/resources/db/changelog/`.

### Key Libraries

- **MapStruct** - Entity ↔ DTO mapping (generated at compile time)
- **Lombok** - Boilerplate reduction (@Data, @RequiredArgsConstructor, @Builder)
- **Spring AI** - OpenAI and Ollama integration for LLM calls
- **Liquibase** - Database migrations
- **springdoc-openapi** - Swagger/OpenAPI documentation
