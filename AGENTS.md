# Spring AI Practice - Agent Instructions

Demo project: multi-module Maven. Spring Boot 4.1.0 + Java 25 + Spring AI 2.0.1, three AI providers via Spring AI.
Comments and git commit messages are in Chinese — match this style.

## Build & Run
- **Set JAVA_HOME to a JDK 25 first.** System default is JDK 1.8 and builds fail without it. Windows PowerShell:
  `$env:JAVA_HOME="D:\softwarework\JDK\ms-25.0.3"; & ".\mvnw.cmd" clean test`
- **Multi-module**: root aggregator pom has two modules. Build one module with `-pl`:
  `& ".\mvnw.cmd" compile -pl spring-ai-application` (add `-am` if it depends on the mcp module — it does not).
- Build: `./mvnw clean compile` · Test: `./mvnw test` · Single test: `./mvnw test -Dtest=SpringAiPracticeApplicationTests` or `-pl spring-ai-application test -Dtest=...`
- Run web app: `./mvnw spring-boot:run -pl spring-ai-application` · Profile: `-Dspring-boot.run.arguments=--spring.profiles.active=sit` (or `prod`). `spring-ai-mcp-server-webflux` is a separate long-running MCP server, not a dependency of the app module.
- `--enable-native-access=ALL-UNNAMED` is configured **only for `mvn spring-boot:run`** (Netty native libs on Java 25). For IDEA, add it to VM options; for Docker, set it separately. It also forces run working-directory to multi-module root (`spring-boot-maven-plugin` `workingDirectory`).

## Environment & Config
- `.env` at project root is git-ignored and loaded at boot by `config/DotenvEnvironmentPostProcessor` (registered via `META-INF/spring.factories`). Needed keys: `DEEPSEEK_API_KEY`, `DEEPSEEK_CHAT_MODEL`, `OPENROUTER_API_KEY`, `OPENROUTER_CHAT_MODEL`, `OPENROUTER_DEFAULT_MODEL`, `OPENROUTER_IMAGE_MODEL`, `OPENROUTER_VIDEO_MODEL`, `OPENROUTER_BASE_URL`, `OPENROUTER_SITE_URL`, `OPENROUTER_TTS_MODEL`, `OPENROUTER_STT_MODEL`, `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`.
- **`contextLoads()` and app startup require BOTH `.env` (API keys) and the Docker stack (Milvus on localhost:8081, MySQL) running** — the context bootstraps MilvusVectorStore/MCP and fails fast with `Connection refused: 8081` when the container is down. Pure-function tests (`src/test/.../utils/`) do NOT need this.
- `spring-ai-mcp-server-webflux` 也通过 `cn.javastack.mcp.config.DotenvEnvironmentPostProcessor`（各自模块内 `META-INF/spring.factories` 注册）+ 多模块回退查找，加载项目根的 `.env`；`PexelsMcpTools` 使用 `PEXELS_API_KEY`。
- Profiles dev (default, set in `application.yaml`) / sit / prod are near-identical; every value is an env-var placeholder with a yaml default.
- `spring.ai.chat.client.enabled: false` — auto-configured `ChatClient`/`Builder` are disabled; the only `ChatClient` beans come from `config/ChatClientConfig`.

## Architecture
Three deliberately different integration patterns:
1. **Spring AI `ChatClient` beans** — DeepSeek (Primary), OpenAI, Ollama, all built by `ChatClientConfig.buildChatClient()` with identical default system/user context.
2. **OpenRouter via raw `RestClient`** (`client/OpenRouterClient`, NOT Spring AI): chat, image, video submit/status, video-understand. Paths (`chat-completions-path`, `image-path`, `video-path`) come from `OpenRouterProperties`, never hardcoded in the client. DTOs are records in `dto/openrouter/`; response parsing in `utils/OpenRouterResponseUtils` (two `extractContent` overloads).
3. **Structured output** — `service/impl/UserServiceImpl` via `ParameterizedTypeReference`.

Workflow-pattern agents live in `agent/` and are the project's showcase of Orchestrator-Workers (`MerchantPreAuditAgent`), Parallelization (`CodeReviewAgent`), Routing (`OnlineCustomerAgent`), plus `ResumeEvaluationAgent`. Workers run in parallel via the shared `taskExecutor` bean (`config/AsyncThreadPoolConfig`) with per-task timeout/degradation — reuse this pattern for new agents.

Package map (`cn.javastack.ai`): `config/` (beans + properties + .env loader), `controller/`, `agent/`, `handler/` (GlobalExceptionHandler), `client/`, `dto/`, `utils/`, `service/` (+ `impl/`), `domain/` (+ `enums/`), `mapper/`, `mcptools/`, `common/` (in-memory caches), `advisors/`.

## Key wiring facts
- The OpenAI ChatModel routes through the OpenRouter gateway (`spring.ai.openai.base-url = https://openrouter.ai/api/v1`, `OPENROUTER_API_KEY`), while `OpenRouterClient` uses its own `base-url: https://openrouter.ai/api` + `/v1/...` paths. These are two independent HTTP stacks.
- `OpenRouterClient.chat()` **swallows all exceptions and returns HTTP 200 with a "繁忙" message** — deliberate legacy behavior; do not change it to throw.
- Model fallbacks, all from yaml, never hardcoded: `chat()` → `default-model` (`xiaomi/mimo-v2.5` since late 2026), `generateImage()` → `image-model`, `generateVideo()` → `video-model`.
- **Default `ChatClient` system prompt is `...以 {voice} 的口吻回答问题` with an unrendered `{voice}` placeholder.** Any agent/controller that calls `.prompt().system(...)` must override it (like `CodeReviewAgent`); calling only `.user(...)` leaves the placeholder text in the system message.
- DeepSeek reasoner endpoints cast `ChatResponse` output to `DeepSeekAssistantMessage` to read `getReasoningContent()`.
- `GlobalExceptionHandler` lives in `handler/`; `springdoc.packages-to-scan: cn.javastack.ai.controller,cn.javastack.ai.handler` — keep in sync or Swagger goes blank.

## DI & code conventions
- `@Resource(name="beanName")` for field injection to disambiguate the 3 `ChatClient` beans; `@RequiredArgsConstructor` for constructor injection. **No `@Autowired`, no `@Qualifier`.**
- **Never import `org.springframework.core.io.Resource` by simple name** — it clashes with `jakarta.annotation.Resource`, which aborts javac annotation processing and breaks Lombok project-wide. Use the FQN at the usage site (see `AudioController`).
- Lombok uses explicit `annotationProcessorPaths` in pom.xml (declared for both compile and test-compile). Files use Lombok (`@Slf4j`), so any import that breaks annotation processing breaks the whole build.
- **Swagger multi-file upload** (`List<MultipartFile>`) needs `@Parameter(content = @Content(mediaType = MULTIPART_FORM_DATA_VALUE, array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))`. Writing `type = "string"` alone renders a single-file control. Single `MultipartFile` keeps plain `schema = @Schema(...)` (see `AgentController`).

## Gotchas
- Do NOT write/edit Java files with PowerShell `Set-Content`/`Out-File` — UTF-8 Chinese comments get corrupted. Use the edit/write tools.
- Test coverage: `contextLoads()` (needs full env, see above) + pure-function unit tests in `spring-ai-application/src/test/.../utils/` and `spring-ai-mcp-server-webflux/src/test/.../utils/`. Add pure-function unit tests; avoid test doubles for the external AI providers.
- Swagger UI: `/swagger-ui.html`.
- `target/`, `.env`, `.env.local` are git-ignored.