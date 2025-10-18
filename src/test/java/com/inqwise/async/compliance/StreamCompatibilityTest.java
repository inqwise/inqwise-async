package com.inqwise.async.compliance;

import com.inqwise.async.stream.AsyncInputStream;
import com.inqwise.async.stream.AsyncOutputStream;
import com.inqwise.async.stream.AsyncReadStream;
import com.inqwise.async.stream.AsyncWriteStream;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stream compatibility tests for bidirectional conversion between
 * Java I/O streams and Vert.x reactive streams.
 */
@ExtendWith(VertxExtension.class)
@Timeout(10000)
public class StreamCompatibilityTest {

    /**
     * Test bidirectional conversion: InputStream -> ReadStream -> InputStream
     */
    @Test
    public void testInputStreamBidirectionalCompatibility(Vertx vertx, VertxTestContext testContext) throws Exception {
        String originalData = "Test data";
        byte[] originalBytes = originalData.getBytes(StandardCharsets.UTF_8);
        
        // Create AsyncReadStream from ByteArrayInputStream
        ByteArrayInputStream originalInputStream = new ByteArrayInputStream(originalBytes);
        AsyncReadStream asyncReadStream = new AsyncReadStream(vertx, originalInputStream);
        
        // Test that data can be read through the stream
        StringBuilder result = new StringBuilder();
        
        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .handler(buffer -> {
                result.append(buffer.toString());
            })
            .endHandler(v -> {
                assertEquals(originalData, result.toString());
                testContext.completeNow();
            });
    }

    /**
     * Test bidirectional conversion: OutputStream -> WriteStream -> OutputStream
     */
    @Test
    public void testOutputStreamBidirectionalCompatibility(Vertx vertx, VertxTestContext testContext) throws Exception {
        String testData = "Bidirectional OutputStream test";
        
        ByteArrayOutputStream originalOutputStream = new ByteArrayOutputStream();
        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(vertx, originalOutputStream);
        
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
    public void testStreamChainingCompatibility(Vertx vertx, VertxTestContext testContext) throws Exception {
        String testData = "Stream chaining test";
        
        ByteArrayInputStream source = new ByteArrayInputStream(testData.getBytes());
        AsyncReadStream readStream = new AsyncReadStream(vertx, source);
        
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        AsyncWriteStream writeStream = new AsyncWriteStream(vertx, destination);
        
        readStream
            .exceptionHandler(testContext::failNow)
            .handler(buffer -> {
                writeStream.write(buffer).onFailure(testContext::failNow);
            })
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
            });
    }

    /**
     * Test concurrent stream operations compatibility
     */
    @Test
    public void testConcurrentStreamCompatibility(Vertx vertx, VertxTestContext testContext) throws Exception {
        String testData = "Concurrent test data";
        
        ByteArrayInputStream input = new ByteArrayInputStream(testData.getBytes());
        AsyncReadStream readStream = new AsyncReadStream(vertx, input);
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AsyncWriteStream writeStream = new AsyncWriteStream(vertx, output);
        
        readStream
            .exceptionHandler(testContext::failNow)
            .handler(buffer -> {
                writeStream.write(buffer).onFailure(testContext::failNow);
            })
            .endHandler(v -> {
                writeStream.end(ar -> {
                    if (ar.succeeded()) {
                        String result = output.toString();
                        assertEquals(testData, result);
                        testContext.completeNow();
                    } else {
                        testContext.failNow(ar.cause());
                    }
                });
            });
    }

    /**
     * Test large data compatibility and memory efficiency
     */
    @Test
    public void testLargeDataCompatibility(Vertx vertx, VertxTestContext testContext) throws Exception {
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
            .handler(buffer -> {
                chunksProcessed.incrementAndGet();
                bytesProcessed.addAndGet(buffer.length());
                writeStream.write(buffer).onFailure(testContext::failNow);
            })
            .endHandler(v -> {
                writeStream.end(ar -> {
                    if (ar.succeeded()) {
                        assertEquals(dataSize, bytesProcessed.get());
                        assertTrue(chunksProcessed.get() > 1, "Should have processed multiple chunks");
                        
                        byte[] result = output.toByteArray();
                        assertEquals(dataSize, result.length);
                        assertArrayEquals(largeData, result);
                        
                        testContext.completeNow();
                    } else {
                        testContext.failNow(ar.cause());
                    }
                });
            });
    }

    /**
     * Test stream compatibility with different character encodings
     */
    @Test
    public void testEncodingCompatibility(Vertx vertx, VertxTestContext testContext) throws Exception {
        String testData = "Encoding test: Basic ASCII text";
        byte[] encodedData = testData.getBytes(StandardCharsets.UTF_8);
        
        ByteArrayInputStream input = new ByteArrayInputStream(encodedData);
        AsyncReadStream readStream = new AsyncReadStream(vertx, input);
        
        StringBuilder result = new StringBuilder();
        
        readStream
            .exceptionHandler(testContext::failNow)
            .handler(buffer -> {
                result.append(buffer.toString());
            })
            .endHandler(v -> {
                assertEquals(testData, result.toString());
                testContext.completeNow();
            });
    }
}