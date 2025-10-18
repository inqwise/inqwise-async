# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

Inqwise Async is a Java library that bridges standard Java blocking I/O structures with Vert.x's asynchronous, non-blocking APIs. It provides wrappers for traditional Java I/O classes (InputStream, OutputStream, Reader, Writer) enabling integration of existing Java codebases into Vert.x-based reactive applications without disrupting the event loop.

## Core Architecture

### Stream Bridge Pattern
The library implements a bidirectional bridge pattern with four key classes:

1. **AsyncReadStream** - Wraps Java `InputStream` → Vert.x `ReadStream<Buffer>`
2. **AsyncWriteStream** - Wraps Java `OutputStream` → Vert.x `WriteStream<Buffer>`  
3. **AsyncInputStream** - Wraps Vert.x `ReadStream<Buffer>` → Java `InputStream`
4. **AsyncOutputStream** - Wraps Vert.x `WriteStream<Buffer>` → Java `OutputStream`

### Threading Model
- Uses single-threaded `ExecutorService` instances with daemon threads
- Named threads for debugging: `async-read-stream-thread`, `async-write-stream-thread`, etc.
- Non-blocking event loop preservation through `vertx.runOnContext()`
- Back-pressure management using `BlockingQueue` (AsyncInputStream) and pause/resume mechanics

### Key Design Patterns
- **Event Loop Protection**: All blocking I/O operations execute on separate threads
- **Resource Management**: Automatic ExecutorService shutdown on stream closure
- **Exception Handling**: Asynchronous exception propagation to Vert.x handlers
- **Flow Control**: Back-pressure implementation prevents memory overflow

## Development Commands

### Build and Test
```bash
# Clean build and run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=AsyncReadStreamTest

# Run specific test method
mvn test -Dtest=AsyncReadStreamTest#testReadStreamSuccessfullyReadsData

# Compile and package without tests
mvn clean compile package -DskipTests

# Generate Javadoc
mvn javadoc:javadoc

# Run tests with coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Quality Checks
```bash
# Dependency analysis
mvn dependency:analyze

# Security vulnerability check
mvn dependency:tree

# Validate Maven project structure
mvn validate

# Full verification with integration tests
mvn clean verify
```

### Release Preparation
```bash
# Generate release artifacts (source + javadoc)
mvn clean package source:jar javadoc:jar

# Test release profile
mvn clean package -Psonatype-oss-release -DskipTests
```

## Testing Framework

- **JUnit 5** with **Vert.x JUnit 5 Extension**
- **VertxTestContext** for async test coordination
- **JaCoCo** for code coverage analysis
- Default timeout: 5000ms (`@Timeout(5000)`)
- Test data patterns: ByteArrayInputStream for controlled input, large data tests (1MB), exception simulation

### Coverage Requirements
- **Line Coverage**: Minimum 80%
- **Branch Coverage**: Minimum 70%
- Current coverage: ~77% lines, ~54% branches

### Test Categories
- **Functional Tests**: Basic read/write operations
- **Flow Control Tests**: Pause/resume, back-pressure
- **Error Handling Tests**: IOException simulation, stream closure
- **Performance Tests**: Large data handling, chunked processing
- **RFC Compliance Tests**: Thread safety, non-blocking behavior, resource management
- **Stream Compatibility Tests**: Bidirectional conversion, encoding support

## Architecture Considerations

### Thread Safety
All stream classes use synchronized blocks and atomic variables for thread-safe operations. The `closed` flag pattern prevents operations after stream closure.

### Resource Lifecycle
- Streams automatically close underlying resources
- ExecutorService instances are shut down on stream closure
- Exception handlers prevent resource leaks during error conditions

### Back-Pressure Strategy
- **AsyncInputStream**: Uses bounded `BlockingQueue` (32KB max) with pause/resume at 50% capacity
- **AsyncReadStream/WriteStream**: Limited back-pressure support (fetch() not implemented)
- Flow control prioritizes preventing memory overflow over strict Reactive Streams compliance

### Integration Points
- Requires Vert.x 4.5.10+ (provided dependency)
- Compatible with Java 21+ (enforced by Maven)
- Uses `inqwise-errors` library for enhanced error handling

## Maven Profile Usage

### Development
Default profile includes all development dependencies and test execution.

### Release (sonatype-oss-release)
- GPG signing enabled
- Central Publishing Maven Plugin
- Source and Javadoc JAR generation
- Auto-publishing to Maven Central

## CI/CD Pipeline

The project uses GitHub Actions with:
- **Multi-JDK Testing**: Java 21 and 22
- **Security Scanning**: Snyk (separate workflow)  
- **Code Quality**: Dependency analysis and validation
- **Integration Tests**: Full verification suite
- **Release Readiness**: Artifact generation validation

## Working with Tests

When writing new tests, follow the established patterns:
- Use `@ExtendWith(VertxExtension.class)` for Vert.x integration
- Inject `Vertx` and `VertxTestContext` parameters
- Use `testContext.completeNow()` for async test completion
- Use `testContext.failNow()` for test failures
- Apply `@Timeout(5000)` for reasonable test timeouts

## Dependencies to Consider

When adding dependencies, be mindful of:
- **Vert.x Core**: Currently provided scope (4.5.10)
- **Log4j2**: Provided scope for logging delegation
- **Commons IO**: Available for utility functions
- **JUnit 5**: Test-scoped with Vert.x integration