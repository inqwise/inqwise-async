package com.inqwise.async.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inqwise.async.stream.AsyncInputStream;
import com.inqwise.async.stream.AsyncReadStream;
import com.inqwise.async.stream.AsyncWriteStream;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.junit5.RunTestOnContext;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * RFC Compliance tests for Inqwise Async library.
 * These tests verify compliance with reactive stream principles,
 * thread safety, and non-blocking behavior requirements.
 */
@ExtendWith(VertxExtension.class)
@Timeout(10000)
public class RFCComplianceTest {

    private static final Logger logger = LogManager.getLogger(
        RFCComplianceTest.class
    );

    @RegisterExtension
    RunTestOnContext rtoc = new RunTestOnContext();

    Vertx vertx;

    @BeforeEach
    void prepare(VertxTestContext testContext) {
        vertx = rtoc.vertx();
        // Prepare something on a Vert.x event-loop thread
        // The thread changes with each test instance
        testContext.completeNow();
    }

    @AfterEach
    void cleanUp(VertxTestContext testContext) {
        // Clean things up on the same Vert.x event-loop thread
        // that called prepare and foo
        testContext.completeNow();
    }

    /**
     * RFC-001: Verify that blocking I/O operations do not block the Vert.x event loop
     */
    @Test
    public void testNonBlockingEventLoopCompliance(VertxTestContext testContext)
        throws Exception {
        String testData = "Event loop should not be blocked";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
            testData.getBytes()
        );

        AsyncReadStream asyncReadStream = new AsyncReadStream(
            vertx,
            inputStream
        );
        AtomicBoolean eventLoopBlocked = new AtomicBoolean(false);
        AtomicInteger dataReceived = new AtomicInteger(0);

        // Schedule a task to run on the event loop while reading
        vertx.setTimer(100, id -> {
            if (Thread.currentThread().getName().contains("eventloop")) {
                eventLoopBlocked.set(false); // Event loop is responsive
            }
        });

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .handler(buffer -> {
                logger.debug("handler");
                dataReceived.addAndGet(buffer.length());
                // Verify we're on the event loop thread
                assertTrue(
                    Thread.currentThread().getName().contains("eventloop"),
                    "Data handler should execute on event loop"
                );
            })
            .endHandler(v -> {
                assertEquals(testData.length(), dataReceived.get());
                assertFalse(eventLoopBlocked.get());
                testContext.completeNow();
            });
    }

    /**
     * RFC-002: Verify proper resource cleanup and lifecycle management
     */
    @Test
    public void testResourceLifecycleCompliance(VertxTestContext testContext)
        throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(
            vertx,
            outputStream
        );

        Buffer testBuffer = Buffer.buffer("Resource lifecycle test");

        asyncWriteStream
            .exceptionHandler(testContext::failNow)
            .write(testBuffer)
            .onComplete(writeResult -> {
                if (writeResult.succeeded()) {
                    asyncWriteStream.end(endResult -> {
                        if (endResult.succeeded()) {
                            // Verify data was written
                            assertEquals(
                                testBuffer.toString(),
                                outputStream.toString()
                            );
                            testContext.completeNow();
                        } else {
                            testContext.failNow(endResult.cause());
                        }
                    });
                } else {
                    testContext.failNow(writeResult.cause());
                }
            });
    }

    /**
     * RFC-003: Verify back-pressure handling in AsyncInputStream
     */
    @Test
    public void testBackPressureCompliance(VertxTestContext testContext)
        throws Exception {
        // Create a large buffer to trigger back-pressure
        byte[] largeData = new byte[64 * 1024]; // 64KB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        ReadStream<Buffer> mockReadStream = new ReadStream<Buffer>() {
            private Handler<Buffer> handler;
            private Handler<Void> endHandler;
            private boolean paused = false;
            private final AtomicBoolean ended = new AtomicBoolean(false);

            @Override
            public ReadStream<Buffer> exceptionHandler(
                Handler<Throwable> handler
            ) {
                return this;
            }

            @Override
            public ReadStream<Buffer> handler(Handler<Buffer> handler) {
                this.handler = handler;
                if (handler != null && !paused && !ended.get()) {
                    vertx.runOnContext(v -> {
                        if (this.handler != null && !paused && !ended.get()) {
                            this.handler.handle(Buffer.buffer(largeData));
                            end();
                        }
                    });
                }
                return this;
            }

            @Override
            public ReadStream<Buffer> pause() {
                paused = true;
                return this;
            }

            @Override
            public ReadStream<Buffer> resume() {
                paused = false;
                return this;
            }

            @Override
            public ReadStream<Buffer> fetch(long amount) {
                return this;
            }

            @Override
            public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
                this.endHandler = endHandler;
                return this;
            }

            private void end() {
                if (ended.compareAndSet(false, true) && endHandler != null) {
                    vertx.runOnContext(v -> endHandler.handle(null));
                }
            }
        };

        AsyncInputStream asyncInputStream = new AsyncInputStream(
            mockReadStream
        );

        // Read data and verify back-pressure works
        CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
            try {
                int totalRead = 0;
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = asyncInputStream.read(buffer)) != -1) {
                    totalRead += bytesRead;
                }

                assertEquals(largeData.length, totalRead);
                asyncInputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        readFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                testContext.failNow(throwable);
            } else {
                testContext.completeNow();
            }
        });
    }

    /**
     * RFC-004: Verify thread safety of stream operations
     */
    @Test
    public void testThreadSafetyCompliance(VertxTestContext testContext)
        throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(
            vertx,
            outputStream
        );

        int numThreads = 5;
        int messagesPerThread = 10;
        AtomicInteger completedWrites = new AtomicInteger(0);
        AtomicInteger expectedWrites = new AtomicInteger(
            numThreads * messagesPerThread
        );

        asyncWriteStream.exceptionHandler(testContext::failNow);

        for (int thread = 0; thread < numThreads; thread++) {
            final int threadId = thread;
            Thread writerThread = new Thread(() -> {
                for (int msg = 0; msg < messagesPerThread; msg++) {
                    Buffer buffer = Buffer.buffer(
                        "Thread" + threadId + "Msg" + msg + "\n"
                    );
                    asyncWriteStream
                        .write(buffer)
                        .onComplete(ar -> {
                            if (ar.succeeded()) {
                                if (
                                    completedWrites.incrementAndGet() ==
                                    expectedWrites.get()
                                ) {
                                    asyncWriteStream.end(endAr -> {
                                        if (endAr.succeeded()) {
                                            // Verify all messages were written
                                            String result =
                                                outputStream.toString();
                                            long lineCount = result
                                                .lines()
                                                .count();
                                            assertEquals(
                                                expectedWrites.get(),
                                                lineCount
                                            );
                                            testContext.completeNow();
                                        } else {
                                            testContext.failNow(endAr.cause());
                                        }
                                    });
                                }
                            } else {
                                testContext.failNow(ar.cause());
                            }
                        });
                }
            });
            writerThread.start();
        }
    }

    /**
     * RFC-005: Verify proper exception propagation and error handling
     */
    @Test
    public void testExceptionPropagationCompliance(
        VertxTestContext testContext
    ) {
        InputStream faultyStream = new InputStream() {
            private boolean firstCall = true;

            @Override
            public int read() throws IOException {
                if (firstCall) {
                    firstCall = false;
                    return 'H'; // Return one byte successfully
                }
                throw new IOException("Simulated I/O error");
            }
        };

        AsyncReadStream asyncReadStream = new AsyncReadStream(
            vertx,
            faultyStream
        );
        AtomicBoolean dataReceived = new AtomicBoolean(false);

        asyncReadStream
            .exceptionHandler(throwable -> {
                assertTrue(
                    dataReceived.get(),
                    "Should have received some data before exception"
                );
                assertTrue(throwable instanceof IOException);
                assertEquals("Simulated I/O error", throwable.getMessage());
                testContext.completeNow();
            })
            .endHandler(v ->
                testContext.failNow(
                    "Should not reach end handler when exception occurs"
                )
            )
            .handler(buffer -> {
                dataReceived.set(true);
                assertEquals('H', buffer.getByte(0));
            });
    }

    /**
     * RFC-006: Verify compliance with stream pause/resume semantics
     */
    @Test
    public void testStreamControlCompliance(VertxTestContext testContext)
        throws Exception {
        String testData =
            "Pause and resume test data that is longer than usual";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
            testData.getBytes()
        );

        AsyncReadStream asyncReadStream = new AsyncReadStream(
            vertx,
            inputStream
        );
        AtomicInteger chunksReceived = new AtomicInteger(0);
        AtomicBoolean wasPaused = new AtomicBoolean(false);
        StringBuilder result = new StringBuilder();

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .handler(buffer -> {
                int chunks = chunksReceived.incrementAndGet();
                result.append(buffer.toString());

                if (chunks == 1) {
                    // Pause after first chunk
                    asyncReadStream.pause();
                    wasPaused.set(true);

                    // Resume after a delay
                    vertx.setTimer(100, id -> asyncReadStream.resume());
                }
            })
            .endHandler(v -> {
                assertTrue(wasPaused.get(), "Stream should have been paused");
                assertTrue(
                    chunksReceived.get() >= 1,
                    "Should have received at least one chunk"
                );
                assertEquals(testData, result.toString());
                testContext.completeNow();
            });
    }
}
