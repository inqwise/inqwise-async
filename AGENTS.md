# Repository Guidelines

## Project Structure & Module Organization
- Root `pom.xml` defines the single Maven module built against Java 21 and Vert.x 5.0.x.
- Gateway entry points live in `src/main/java/com/inqwise/opinion/gateway` (`ApiGatewayLauncher`, `ApiGatewayVerticle`, `GatewayConfig`).
- Shared async stream adapters for bridging blocking I/O reside under `src/main/java/com/inqwise/async/stream`.
- Tests mirror the main packages inside `src/test/java`, using the same package names for coverage and clarity.
- Optional runtime configuration is read from `config/application.json` or environment variables during startup.

## Build, Test, and Development Commands
- `mvn clean package` — compiles sources, runs the full test suite, and produces the shaded JAR in `target/`.
- `mvn test` — executes unit tests with JUnit 5 and Vert.x JUnit extensions without creating artifacts.
- `java -cp target/inqwise-async-1.1.2-SNAPSHOT.jar com.inqwise.opinion.gateway.ApiGatewayLauncher` — launches the gateway locally after packaging; override ports with `HTTP_PORT=9090` or supply `config/application.json`.

## Coding Style & Naming Conventions
- Use four-space indentation, braces on the same line as declarations, and favor `final` for dependencies injected via constructors.
- Follow standard Java naming (`PascalCase` classes, `camelCase` members, `UPPER_SNAKE_CASE` constants) and align imports per IntelliJ/Spotless defaults.
- Maintain the existing pattern of small, focused verticles and prefer Vert.x `Future`/`Promise` APIs for async flows.

## Testing Guidelines
- Write tests with JUnit 5 (`@ExtendWith(VertxExtension.class)`) and Vert.x test utilities; name files with the `*Test` suffix.
- Use `VertxTestContext` and `Async` helpers to avoid blocking the event loop.
- The project tracks coverage via JaCoCo/Codecov; strive to keep or raise coverage when modifying gateway logic.

## Commit & Pull Request Guidelines
- Follow the existing Conventional Commit style (`fix:`, `chore:`, `deps(deps):`, `ci(deps):`, etc.) for clear history and automated release notes.
- Reference issues in the body when applicable, describe functional changes, and list validation steps (e.g., commands run, configs used).
- For gateway changes, include samples of affected endpoints or configuration knobs so reviewers can reproduce locally.

## Security & Configuration Tips
- Do not commit secrets; prefer environment variables for credentials and mark optional values in `config/application.json`.
- Tighten upstream calls by keeping hop-by-hop headers filtered and reusing the shared `WebClient` options as shown in the verticle.
- When adding new endpoints, ensure health probes (`/health/live`, `/health/ready`) remain lightweight and under existing timeout budgets.
