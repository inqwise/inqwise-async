package com.inqwise.async.stream;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
@Timeout(5000)
public class AsyncWriteStreamTest {
	private static final Logger logger = LogManager.getLogger(AsyncWriteStreamTest.class);
	
    @BeforeEach
    public void setUp(Vertx vert) {
    }

    @AfterEach
    public void tearDown() {
       
    }

    @Test
    public void testWriteStreamSuccessfullyWritesData(Vertx vertx, VertxTestContext testContext) {
    	logger.debug("testWriteStreamSuccessfullyWritesData");
    	String data = "Hello, AsyncWriteStream!";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(vertx, outputStream);

        asyncWriteStream
            .exceptionHandler(testContext::failNow)
            .write(Buffer.buffer(data))
            .onComplete(ar -> {
                assertTrue(ar.succeeded());
                assertEquals(data, outputStream.toString());
                testContext.completeNow();
            });
    }

    @Test
    public void testWriteStreamHandlesExceptions(Vertx vertx, VertxTestContext testContext) {
        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Test exception");
            }
        };

        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(vertx, outputStream);

        asyncWriteStream
            .exceptionHandler(th -> {
                assertTrue(th instanceof IOException);
                assertEquals("Test exception", th.getMessage());
                testContext.completeNow();
            })
            .write(Buffer.buffer("Test Data"))
            .onComplete(ar -> {
                assertTrue(ar.failed());
                assertTrue(ar.cause() instanceof IOException);
            });
    }

    @Test
    public void testWriteStreamEndsProperly(Vertx vertx, VertxTestContext testContext) throws IOException {
        String data = "End Stream Test";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(vertx, outputStream);

        asyncWriteStream
            .exceptionHandler(testContext::failNow)
            .write(Buffer.buffer(data))
            .compose(v -> asyncWriteStream.end())
            .onComplete(ar -> {
                assertTrue(ar.succeeded());
                assertEquals(data, outputStream.toString());
                testContext.completeNow();
            });
    }

    @Test
    public void testWriteStreamHandlesLargeData(Vertx vertx, VertxTestContext testContext) {
        byte[] largeData = new byte[1024 * 1024]; // 1 MB of data
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(vertx, outputStream);

        asyncWriteStream
            .exceptionHandler(testContext::failNow)
            .write(Buffer.buffer(largeData))
            .onComplete(ar -> {
                assertTrue(ar.succeeded());
                assertArrayEquals(largeData, outputStream.toByteArray());
                testContext.completeNow();
            });
    }

    @Test
    public void testWriteStreamAfterEnd(Vertx vertx, VertxTestContext testContext) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        AsyncWriteStream asyncWriteStream = new AsyncWriteStream(vertx, outputStream);

        asyncWriteStream
            .exceptionHandler(testContext::failNow)
            .end(ar -> {
                assertTrue(ar.succeeded());

                // Try to write after ending the stream
                asyncWriteStream.write(Buffer.buffer("Should Fail"))
                    .onComplete(writeAr -> {
                        assertTrue(writeAr.failed());
                        assertTrue(writeAr.cause() instanceof IllegalStateException);
                        testContext.completeNow();
                    });
            });
    }
}