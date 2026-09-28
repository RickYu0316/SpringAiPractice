# Spring AI Practice - Agent Instructions

AI Project: 2-module Maven. Spring Boot 4.1.0 + Java 25 + Spring AI 2.0.1, four AI-provider code paths.
Comments and git commit messages are in Chinese — match this style.
Line endings are pinned per-extension in `.gitattributes` (`*.java`/`.xml`/`.yaml`/`.md` = LF, `*.cmd` = CRLF) — don't rewrite them wholesale.

## Modules
- `spring-ai-application` — the demo app, port 8080, entry `SpringAiApplication`, 16 controllers under `controller/`.
- `spring-ai-mcp-server-webflux` — a **separate long-running** MCP server, port 8081, STREAMABLE at `/mcp`. Not a Maven dependency of the app module; it just has to be *running* (see below).

## Build & Run
- **Set JAVA_HOME to a JDK 25 first.** System default is JDK 1.8 and builds fail without it:
  `$env:JAVA_HOME="D:\softwarework\JDK\ms-25.0.3"; & ".\mvnw.cmd" clean test`
- **`spring-boot:run` needs `-pl`, and the MCP server must start first.** Root pom is `packaging=pom` (aggregator), so omitting `-pl` makes the plugin run against both modules and the second one fails:
  ```
  & ".\mvnw.cmd" spring-boot:run -pl spring-ai-mcp-server-webflux   # first → 8081
  & ".\mvnw.cmd" spring-boot:run -pl spring-ai-application          # then  → 8080
  ```
  Profile: `-Dspring-boot.run.arguments=--spring.profiles.active=sit` (or `prod`).
- Compile one module: `& ".\mvnw.cmd" compile -pl spring-ai-application`. No `-am` needed — neither module depends on the other.
- Single test: `& ".\mvnw.cmd" -pl spring-ai-application test -Dtest=OpenRouterResponseUtilsTest`
- `--enable-native-access=ALL-UNNAMED` is applied **only** by `mvn spring-boot:run` (Netty native libs on Java 25); IDEA runs need it in VM options. That same plugin config pins `workingDirectory` to the multi-module root — which is where `.env` is found.
- Swagger UI `/swagger-ui.html`.

## Startup prerequisites — `mvn test` and app startup fail fast without these
- **MCP server on 8081 is a hard prerequisite (most common failure).** With it down the context dies during bean creation: `mcpSyncClients` → `Client failed to initialize by explicit API call` ← `Connection refused: 8081`, cascading `deepSeekChatModel` → `codeReviewAgent` → `Failed to load ApplicationContext`. MCP clients initialize eagerly at startup, not lazily.
- **Milvus on 19530** (easy to mistake for 8081) — `MilvusVectorStore` creates/validates collection `vector_store` during startup.
- **MySQL on 3306**, db `sap_db`, user `sap` — DataSource + `JdbcChatMemoryRepository`.
- **`.env` must define the 4 `MYSQL_*` keys** (`MYSQL_HOST/USER/PASSWORD/DATABASE`): `ChatClientConfig` injects them via `@Value("${MYSQL_HOST}")` etc. to build the MySQL-MCP system prompt, and `datasource.password` uses `${MYSQL_PASSWORD}`. Only `DEEPSEEK_API_KEY`, `OPENROUTER_API_KEY`, `MYSQL_PASSWORD` are the yaml placeholders lacking defaults; every other `${...}` in `application-*.yaml` has a default. `PEXELS_API_KEY` defaults to empty (the MCP tool degrades).
- **Business tables are not auto-migrated** (no Flyway/Liquibase): import `spring-ai-application/src/main/resources/sql/sys_user.sql` and `user_address.sql` by hand. `SPRING_AI_CHAT_MEMORY` *is* auto-created (`initialize-schema: always`).
- stdio MCP servers (`mcp/mcp-servers.json`) run `cmd.exe /c npx -y ...` → the **Windows host** needs Node/npx and npm reachability; the first boot downloads the packages.
- Pure-function tests in `src/test/.../utils/` need none of the above.

## Environment & config
- `.env` at the project root is git-ignored and loaded by `config/DotenvEnvironmentPostProcessor` (registered in each module's `META-INF/spring.factories`). It resolves `.env` in the CWD, else falls back to `../.env` — that fallback is what makes multi-module runs work. Values are `addFirst`, so **`.env` overrides real environment variables and yaml defaults**; a stale `.env` silently wins.
- Profiles dev (default, set in `application.yaml`) / sit / prod are near-identical; nearly every value is an env-var placeholder with a yaml default.
- `spring.ai.chat.client.enabled: false` — auto-configured `ChatClient`/`Builder` are off; every `ChatClient` bean comes from `config/ChatClientConfig` (DeepSeek is `@Primary`).
- `.env`, `.env.local`, `target/`, `logs/` are git-ignored.

## Infra / local stack
Container + WSL2 reality (Ollama 0.32.5 `deepseek-r1:8b` on **CPU ~10 token/s** — no `/dev/kfd`, `CONFIG_HSA_AMD` off; Open WebUI 3000; Portainer 9443; Milvus 2.6.22 19530/9091; MySQL 8.0 3306; Attu 8000) and the `networkingMode=mirrored` requirement behind every `localhost:*` in the yamls → **`spring-ai-application/src/main/resources/static/knowledgeBase/WSL2+Docker+Ollama+DeepSeek 本地部署详细流程.md`**. Read it before touching infra or yaml endpoints.
- `spring.ai.ollama.init.pull-model-strategy: never` → the app never auto-pulls a model; a missing model is a hard error, not a download.
- `spring.ai.model.embedding: openai` → **RAG embeddings go through the OpenRouter gateway (`voyageai/voyage-4`, 1024 dims), not Ollama.** `spring.ai.vectorstore.milvus.embedding-dimension: 1024` must match; changing the embedding model without changing it breaks collection creation.

## Architecture
Deliberately different integration patterns:
1. **Spring AI `ChatClient` beans** — DeepSeek (Primary), OpenAI, Ollama + MCP/RAG variants, all from `ChatClientConfig.buildChatClient()`.
2. **OpenRouter via raw `RestClient`** (`client/OpenRouterClient`, NOT Spring AI): chat, image, video submit/status, moderation. Paths (`chat-completions-path`, `image-path`, `video-path`) come from `OpenRouterProperties`, never hardcoded. DTOs are records in `dto/openrouter/`; parsing in `utils/OpenRouterResponseUtils` (two `extractContent` overloads).
3. **Structured output** — `controller/StructuredController` via `.entity(Class)` / `ParameterizedTypeReference` / `MapOutputConverter` / `ListOutputConverter`, plus `DeepSeekChatOptions` + `ResponseFormat.Type.JSON_OBJECT` for the raw variant.
4. **MCP** — app as client (streamable-http to 8081 + 2 stdio servers); `spring-ai-mcp-server-webflux` as server (Http context, Pexels, payment-failure tools).

Workflow-pattern agents in `agent/`: Orchestrator-Workers (`MerchantPreAuditAgent`), Parallelization (`CodeReviewAgent`), Routing (`OnlineCustomerAgent`), Evaluator-Optimizer (`SqlOptimizeAgent`), serial (`ResumeEvaluationAgent`). Parallel workers use the shared `taskExecutor` bean (`config/AsyncThreadPoolConfig`, also `videoTaskExecutor`) with per-task timeout/degradation — reuse that pattern for new agents.

Package map (`cn.javastack.ai`): `config/` (beans + properties + .env loader), `controller/`, `agent/`, `handler/` (GlobalExceptionHandler), `client/`, `dto/` (+ `openrouter/`, `custom/`, `deepseek/`), `utils/`, `service/` (+ `impl/`), `domain/` (+ `enums/`), `mapper/`, `mcptools/`, `common/` (Caffeine caches: `GeneratedMediaCache`, `VideoTaskCache` — in-memory, so generated media/task ids are lost on restart and unusable from a second instance), `advisors/`.

## Key wiring facts
- The OpenAI ChatModel routes through the OpenRouter gateway (`spring.ai.openai.base-url = https://openrouter.ai/api/v1`), while `OpenRouterClient` uses its own `base-url: https://openrouter.ai/api` + `/v1/...` paths. Two independent HTTP stacks.
- `OpenRouterClient.chat()` **swallows all exceptions and returns HTTP 200 with a "繁忙" message** — deliberate legacy behavior; do not change it to throw.
- Model fallbacks, all from yaml, never hardcoded: `chat()` → `default-model`, `generateImage()` → `image-model`, `generateVideo()` → `video-model`, `moderate()` → `moderation-model`.
- **Default `ChatClient` system prompt is `...以 {voice} 的口吻回答问题` with an unrendered `{voice}` placeholder.** Any agent/controller calling `.prompt().system(...)` must override it (like `CodeReviewAgent`); `.user(...)` alone leaves the literal placeholder in the system message.
- DeepSeek reasoner endpoints cast `ChatResponse` output to `DeepSeekAssistantMessage` to read `getReasoningContent()`.
- `MySQL_*` values are inlined into an MCP system prompt (see Startup prerequisites) — they are prompt text, so a password with newlines/quotes will corrupt it.
- `GlobalExceptionHandler` lives in `handler/`; `springdoc.packages-to-scan: cn.javastack.ai.controller,cn.javastack.ai.handler` — keep in sync or Swagger goes blank.
- WebFlux/SSE async requests use `webMvcAsyncTaskExecutor` with a 180s default timeout (`config/WebMvcConfig`).

## DI & code conventions
- `@Resource(name="beanName")` for field injection to disambiguate the `ChatClient` beans; `@RequiredArgsConstructor` for constructor injection. **No `@Autowired`, no `@Qualifier`.**
- **Never import `org.springframework.core.io.Resource` by simple name** — it clashes with `jakarta.annotation.Resource`, which aborts javac annotation processing and breaks Lombok project-wide. Use the FQN at the usage site (see `AudioController`).
- Lombok uses explicit `annotationProcessorPaths` in both poms (compile *and* test-compile). Files use Lombok (`@Slf4j`), so any import that breaks annotation processing breaks the whole build.
- **Swagger multi-file upload** (`List<MultipartFile>`) needs `@Parameter(content = @Content(mediaType = MULTIPART_FORM_DATA_VALUE, array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))`; `type = "string"` alone renders a single-file control. A single `MultipartFile` keeps plain `schema = @Schema(...)` (see `AgentController`).

## Gotchas
- Do NOT write/edit Java files with PowerShell `Set-Content`/`Out-File` — UTF-8 Chinese comments get corrupted. Use the edit/write tools.
- Add pure-function unit tests under `src/test/.../utils/`; avoid test doubles for the external AI providers (they'd only assert the mock).
- From PowerShell, don't pass percent-encoded Chinese URLs to `Invoke-RestMethod` — it double-decodes and Tomcat returns 500 (`MalformedInputException`). Use `curl.exe` + `[uri]::EscapeDataString()`, or just a browser.
