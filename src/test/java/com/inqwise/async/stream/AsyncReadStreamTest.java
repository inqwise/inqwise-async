package com.inqwise.async.stream;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
@Timeout(5000)
public class AsyncReadStreamTest {

    @BeforeEach
    public void setUp(Vertx vertx) {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testReadStreamSuccessfullyReadsData(Vertx vertx, VertxTestContext testContext) {
        String data = "Hello, AsyncReadStream!";
        InputStream inputStream = new ByteArrayInputStream(data.getBytes());

        AsyncReadStream asyncReadStream = new AsyncReadStream(vertx, inputStream);

        StringBuilder result = new StringBuilder();

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                assertEquals(data, result.toString());
                testContext.completeNow();
            })
            .handler(buffer -> result.append(buffer.toString()));
    }

    @Test
    public void testReadStreamHandlesEndOfStream(Vertx vertx, VertxTestContext testContext) {
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        AsyncReadStream asyncReadStream = new AsyncReadStream(vertx, inputStream);

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> testContext.completeNow())
            .handler(buffer -> testContext.failNow(new Exception("Should not receive data")));
    }

    @Test
    public void testReadStreamHandlesExceptions(Vertx vertx, VertxTestContext testContext) {
        InputStream inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Test exception");
            }
        };

        AsyncReadStream asyncReadStream = new AsyncReadStream(vertx, inputStream);

        asyncReadStream
            .exceptionHandler(th -> {
                assertTrue(th instanceof IOException);
                assertEquals("Test exception", th.getMessage());
                testContext.completeNow();
            })
            .handler(buffer -> testContext.failNow(new Exception("Should not receive data")));
    }

    @Test
    public void testReadStreamPauseAndResume(Vertx vertx, VertxTestContext testContext) {
        String data = "Pause and Resume Test";
        InputStream inputStream = new ByteArrayInputStream(data.getBytes());

        AsyncReadStream asyncReadStream = new AsyncReadStream(vertx, inputStream);

        StringBuilder result = new StringBuilder();

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                assertEquals(data, result.toString());
                testContext.completeNow();
            })
            .handler(buffer -> {
                result.append(buffer.toString());
                asyncReadStream.pause();

                vertx.setTimer(100, id -> asyncReadStream.resume());
            });
    }

    @Test
    public void testReadStreamHandlesLargeData(Vertx vertx, VertxTestContext testContext) {
        byte[] largeData = new byte[1024 * 1024]; // 1 MB of data
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        InputStream inputStream = new ByteArrayInputStream(largeData);

        AsyncReadStream asyncReadStream = new AsyncReadStream(vertx, inputStream, 8192);

        byte[] result = new byte[largeData.length];
        int[] offset = {0};

        asyncReadStream
            .exceptionHandler(testContext::failNow)
            .endHandler(v -> {
                assertArrayEquals(largeData, result);
                testContext.completeNow();
            })
            .handler(buffer -> {
                byte[] bytes = buffer.getBytes();
                System.arraycopy(bytes, 0, result, offset[0], bytes.length);
                offset[0] += bytes.length;
            });
    }
}
