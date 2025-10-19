package com.inqwise.async.compliance;

import static org.junit.jupiter.api.Assertions.*;

import com.inqwise.async.stream.AsyncInputStream;
import com.inqwise.async.stream.AsyncOutputStream;
import com.inqwise.async.stream.AsyncReadStream;
import com.inqwise.async.stream.AsyncWriteStream;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.junit5.RunTestOnContext;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Stream compatibility tests for bidirectional conversion between
 * Java I/O streams and Vert.x reactive streams.
 */
@ExtendWith(VertxExtension.class)
@Timeout(10000)
public class StreamCompatibilityTest {

    private static final Logger logger = LogManager.getLogger(
        StreamCompatibilityTest.class
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

    /**
     * Test bidirectional conversion: InputStream -> ReadStream -> InputStream
     */
    @Test
    public void testInputStreamBidirectionalCompatibility(
        VertxTestContext testContext
    ) throws Exception {
        String originalData = "Test data";
        byte[] originalBytes = originalData.getBytes(StandardCharsets.UTF_8);

        // Create AsyncReadStream from ByteArrayInputStream
        ByteArrayInputStream originalInputStream = new ByteArrayInputStream(
            originalBytes
        );
        AsyncReadStream asyncReadStream = new AsyncReadStream(
            vertx,
            originalInputStream
        );

        // Test that data can be read through the stream
        StringBuilder result = new StringBuilder();

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                assertEquals(originalData, result.toString());
                testContext.completeNow();
            })
            .handler(buffer -> {
                result.append(buffer.toString());
            });
    }

    /**
     * Test bidirectional conversion: OutputStream -> WriteStream -> OutputStream
     */
    @Test
    public void testOutputStreamBidirectionalCompatibility(
        VertxTestContext testContext
    ) throws Exception {
        String testData = "Bidirectional OutputStream test";

        ByteArrayOutputStream originalOutputStream =
            new ByteArrayOutputStream();
        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(
            vertx,
            originalOutputStream
        );

        Buffer buffer = Buffer.buffer(testData);
        asyncWriteStream
            .exceptionHandler(testContext::failNow)
            .write(buffer)
            .onComplete(writeResult -> {
                if (writeResult.succeeded()) {
                    asyncWriteStream.end(endResult -> {
                        if (endResult.succeeded()) {
                            String resultData = originalOutputStream.toString();
                            assertEquals(testData, resultData);
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
     * Test stream chaining and data integrity
     */
    @Test
    public void testStreamChainingCompatibility(VertxTestContext testContext)
        throws Exception {
        String testData = "Stream chaining test";

        ByteArrayInputStream source = new ByteArrayInputStream(
            testData.getBytes()
        );
        AsyncReadStream readStream = new AsyncReadStream(vertx, source);

        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        AsyncWriteStream writeStream = new AsyncWriteStream(vertx, destination);

        readStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                writeStream.end(ar -> {
                    if (ar.succeeded()) {
                        String result = destination.toString();
                        assertEquals(testData, result);
                        testContext.completeNow();
                    } else {
                        testContext.failNow(ar.cause());
                    }
                });
            })
            .handler(buffer -> {
                writeStream.write(buffer).onFailure(testContext::failNow);
            });
    }

    /**
     * Test concurrent stream operations compatibility
     */
    @Test
    public void testConcurrentStreamCompatibility(VertxTestContext testContext)
        throws Exception {
        String testData = "Concurrent test data";

        ByteArrayInputStream input = new ByteArrayInputStream(
            testData.getBytes()
        );
        AsyncReadStream readStream = new AsyncReadStream(vertx, input);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AsyncWriteStream writeStream = new AsyncWriteStream(vertx, output);

        readStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                writeStream
                    .end()
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            String result = output.toString();
                            assertEquals(testData, result);
                            testContext.completeNow();
                        } else {
                            testContext.failNow(ar.cause());
                        }
                    });
            })
            .handler(buffer -> {
                writeStream.write(buffer).onFailure(testContext::failNow);
            });
    }

    /**
     * Test large data compatibility and memory efficiency
     */
    @Test
    public void testLargeDataCompatibility(VertxTestContext testContext)
        throws Exception {
        // Create 1MB of test data
        int dataSize = 1024 * 1024;
        byte[] largeData = new byte[dataSize];
        for (int i = 0; i < dataSize; i++) {
            largeData[i] = (byte) (i % 256);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(largeData);
        AsyncReadStream readStream = new AsyncReadStream(vertx, input, 8192); // 8KB chunks

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AsyncWriteStream writeStream = new AsyncWriteStream(vertx, output);

        AtomicInteger chunksProcessed = new AtomicInteger(0);
        AtomicInteger bytesProcessed = new AtomicInteger(0);

        readStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                writeStream
                    .end()
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            assertEquals(dataSize, bytesProcessed.get());
                            assertTrue(
                                chunksProcessed.get() > 1,
                                "Should have processed multiple chunks"
                            );

                            byte[] result = output.toByteArray();
                            assertEquals(dataSize, result.length);
                            assertArrayEquals(largeData, result);

                            testContext.completeNow();
                        } else {
                            testContext.failNow(ar.cause());
                        }
                    });
            })
            .handler(buffer -> {
                chunksProcessed.incrementAndGet();
                bytesProcessed.addAndGet(buffer.length());
                writeStream.write(buffer).onFailure(testContext::failNow);
            });
    }

    /**
     * Test stream compatibility with different character encodings
     */
    @Test
    public void testEncodingCompatibility(VertxTestContext testContext)
        throws Exception {
        String testData = "Encoding test: Basic ASCII text";
        byte[] encodedData = testData.getBytes(StandardCharsets.UTF_8);

        ByteArrayInputStream input = new ByteArrayInputStream(encodedData);
        AsyncReadStream readStream = new AsyncReadStream(vertx, input);

        StringBuilder result = new StringBuilder();

        readStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                assertEquals(testData, result.toString());
                testContext.completeNow();
            })
            .handler(buffer -> {
                result.append(buffer.toString());
            });
    }
}
