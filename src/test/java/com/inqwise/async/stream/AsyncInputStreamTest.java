package com.inqwise.async.stream;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class AsyncInputStreamTest {

    @Test
    public void testReadDataSuccessfully(Vertx vertx, VertxTestContext testContext) {
        String data = "Hello, AsyncInputStream!";
        Buffer buffer = Buffer.buffer(data);

        ReadStream<Buffer> readStream = new MockReadStream(buffer);

        AsyncInputStream asyncInputStream = new AsyncInputStream(readStream);

        CompletableFuture.runAsync(() -> {
            try {
                byte[] readBuffer = new byte[1024];
                int bytesRead = asyncInputStream.read(readBuffer);

                String result = new String(readBuffer, 0, bytesRead);

                testContext.verify(() -> {
                    assertEquals(data, result);
                });

                asyncInputStream.close();
                testContext.completeNow();
            } catch (IOException e) {
                testContext.failNow(e);
            }
        });
    }

    @Test
    public void testHandleEndOfStream(Vertx vertx, VertxTestContext testContext) {
        ReadStream<Buffer> readStream = new MockReadStream(null);

        AsyncInputStream asyncInputStream = new AsyncInputStream(readStream);

        CompletableFuture.runAsync(() -> {
            try {
                int result = asyncInputStream.read();

                testContext.verify(() -> {
                    assertEquals(-1, result);
                });

                asyncInputStream.close();
                testContext.completeNow();
            } catch (IOException e) {
                testContext.failNow(e);
            }
        });
    }

    @Test
    public void testHandleException(Vertx vertx, VertxTestContext testContext) {
        ReadStream<Buffer> readStream = new ExceptionThrowingReadStream();

        AsyncInputStream asyncInputStream = new AsyncInputStream(readStream);

        CompletableFuture.runAsync(() -> {
            testContext.verify(() -> {
                assertThrows(IOException.class, asyncInputStream::read);
            });

            testContext.completeNow();
        });
    }

    @Test
    public void testPauseAndResume(Vertx vertx, VertxTestContext testContext) {
        String data = "Pause and Resume Test";
        Buffer buffer = Buffer.buffer(data);

        ReadStream<Buffer> readStream = new MockReadStream(buffer);

        AsyncInputStream asyncInputStream = new AsyncInputStream(readStream);

        CompletableFuture.runAsync(() -> {
            try {
                byte[] readBuffer = new byte[1024];
                int bytesRead = asyncInputStream.read(readBuffer);

                String result = new String(readBuffer, 0, bytesRead);

                testContext.verify(() -> {
                    assertEquals(data, result);
                });

                asyncInputStream.close();
                testContext.completeNow();
            } catch (IOException e) {
                testContext.failNow(e);
            }
        });
    }

    @Test
    public void testReadAfterClose(Vertx vertx, VertxTestContext testContext) throws IOException {
        String data = "Test Data";
        Buffer buffer = Buffer.buffer(data);

        ReadStream<Buffer> readStream = new MockReadStream(buffer);

        AsyncInputStream asyncInputStream = new AsyncInputStream(readStream);
        asyncInputStream.close();

        CompletableFuture.runAsync(() -> {
            testContext.verify(() -> {
                assertThrows(IOException.class, asyncInputStream::read);
            });
            testContext.completeNow();
        });
    }

    // Mock ReadStream implementations
    private static class MockReadStream implements ReadStream<Buffer> {

        private final Buffer buffer;
        private Handler<Buffer> dataHandler;
        private Handler<Void> endHandler;
        private boolean paused = false;
        private final AtomicBoolean emitted = new AtomicBoolean(false);

        MockReadStream(Buffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public ReadStream<Buffer> handler(Handler<Buffer> handler) {
            this.dataHandler = handler;
            return this;
        }

        @Override
        public ReadStream<Buffer> pause() {
            this.paused = true;
            return this;
        }

        @Override
        public ReadStream<Buffer> resume() {
            this.paused = false;
            emitData();
            return this;
        }

        @Override
        public ReadStream<Buffer> fetch(long amount) {
            return this;
        }

        @Override
        public ReadStream<Buffer> endHandler(Handler<Void> handler) {
            this.endHandler = handler;
            return this;
        }

        private void emitData() {
            if (!paused && emitted.compareAndSet(false, true)) {
                if (buffer != null && dataHandler != null) {
                    dataHandler.handle(buffer);
                }
                if (endHandler != null) {
                    endHandler.handle(null);
                }
            }
        }
    }

    private static class ExceptionThrowingReadStream implements ReadStream<Buffer> {

        private Handler<Throwable> exceptionHandler;

        @Override
        public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            this.exceptionHandler = handler;
            return this;
        }

        @Override
        public ReadStream<Buffer> handler(Handler<Buffer> handler) {
            return this;
        }

        @Override
        public ReadStream<Buffer> pause() {
            return this;
        }

        @Override
        public ReadStream<Buffer> resume() {
            if (exceptionHandler != null) {
                exceptionHandler.handle(new RuntimeException("Test exception"));
            }
            return this;
        }

        @Override
        public ReadStream<Buffer> fetch(long amount) {
            return this;
        }

        @Override
        public ReadStream<Buffer> endHandler(Handler<Void> handler) {
            return this;
        }
    }
}