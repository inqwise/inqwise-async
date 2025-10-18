package com.inqwise.async.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
@Timeout(5000)
public class AsyncOutputStreamTest {

    @BeforeEach
    public void setUp(Vertx vertx) {
        
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testWriteDataSuccessfully(Vertx vertx, VertxTestContext testContext) throws IOException {
        String data = "Hello, AsyncOutputStream!";
        Buffer expectedBuffer = Buffer.buffer(data);

        MockWriteStream mockWriteStream = new MockWriteStream();
        AsyncOutputStream asyncOutputStream = new AsyncOutputStream(vertx, mockWriteStream);

        asyncOutputStream.write(data.getBytes());
        asyncOutputStream.close();

        Buffer result = mockWriteStream.getReceivedBuffer();
        assertEquals(expectedBuffer, result);

        testContext.completeNow();
    }

    @Test
    public void testHandleExceptionDuringWrite(Vertx vertx, VertxTestContext testContext) throws IOException {
        ExceptionThrowingWriteStream writeStream = new ExceptionThrowingWriteStream();
        try(AsyncOutputStream asyncOutputStream = new AsyncOutputStream(vertx, writeStream)){

	        assertThrows(IOException.class, () -> asyncOutputStream.write("Test Data".getBytes()));
	
	        testContext.completeNow();
        }
    }

    @Test
    public void testHandleBackPressure(Vertx vertx, VertxTestContext testContext) throws IOException {
        String data = "Back-pressure Test Data";
        Buffer expectedBuffer = Buffer.buffer(data);

        BackPressureWriteStream mockWriteStream = new BackPressureWriteStream(vertx);
        AsyncOutputStream asyncOutputStream = new AsyncOutputStream(vertx, mockWriteStream);

        // Start a thread to simulate drain after some delay
        new Thread(() -> {
            try {
                Thread.sleep(100); // Simulate some delay
                mockWriteStream.simulateDrain();
            } catch (InterruptedException e) {
                // Handle interruption
            }
        }).start();

        asyncOutputStream.write(data.getBytes());
        asyncOutputStream.close();

        Buffer result = mockWriteStream.getReceivedBuffer();
        assertEquals(expectedBuffer, result);

        testContext.completeNow();
    }

    @Test
    public void testWriteAfterClose(Vertx vertx, VertxTestContext testContext) throws IOException {
        MockWriteStream mockWriteStream = new MockWriteStream();
        AsyncOutputStream asyncOutputStream = new AsyncOutputStream(vertx, mockWriteStream);
        asyncOutputStream.close();

        assertThrows(IOException.class, () -> asyncOutputStream.write("Should Fail".getBytes()));

        testContext.completeNow();
    }

    // Mock WriteStream implementations

    private static class MockWriteStream implements WriteStream<Buffer> {
        private final Buffer receivedBuffer = Buffer.buffer();

        @Override
        public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            receivedBuffer.appendBuffer(data);
            return Future.succeededFuture();
        }

        public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            receivedBuffer.appendBuffer(data);
            handler.handle(Future.succeededFuture());
        }

        @Override
        public Future<Void> end() {
            return Future.succeededFuture();
        }

        public void end(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }

        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
            return this;
        }

        public Buffer getReceivedBuffer() {
            return receivedBuffer;
        }
    }

    private static class ExceptionThrowingWriteStream implements WriteStream<Buffer> {
        private Handler<Throwable> exceptionHandler;

        @Override
        public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            this.exceptionHandler = handler;
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            Future<Void> failedFuture = Future.failedFuture(new RuntimeException("Test exception"));
            if (exceptionHandler != null) {
                exceptionHandler.handle(failedFuture.cause());
            }
            return failedFuture;
        }

        public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            Future<Void> failedFuture = Future.failedFuture(new RuntimeException("Test exception"));
            if (exceptionHandler != null) {
                exceptionHandler.handle(failedFuture.cause());
            }
            handler.handle(failedFuture);
        }

        @Override
        public Future<Void> end() {
            return Future.succeededFuture();
        }

        public void end(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }

        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
            return this;
        }
    }

    private static class BackPressureWriteStream implements WriteStream<Buffer> {
        private final Buffer receivedBuffer = Buffer.buffer();
        private Handler<Void> drainHandler;
        private boolean writeQueueFull = true;
        private final Vertx vertx;

        public BackPressureWriteStream(Vertx vertx) {
            this.vertx = vertx;
        }

        @Override
        public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            receivedBuffer.appendBuffer(data);
            writeQueueFull = true;

            // Simulate delay before calling drainHandler
            vertx.setTimer(50, id -> {
                writeQueueFull = false;
                if (drainHandler != null) {
                    drainHandler.handle(null);
                }
            });

            return Future.succeededFuture();
        }

        public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            receivedBuffer.appendBuffer(data);
            writeQueueFull = true;

            // Simulate delay before calling drainHandler
            vertx.setTimer(50, id -> {
                writeQueueFull = false;
                if (drainHandler != null) {
                    drainHandler.handle(null);
                }
            });

            handler.handle(Future.succeededFuture());
        }

        @Override
        public Future<Void> end() {
            return Future.succeededFuture();
        }

        public void end(Handler<AsyncResult<Void>> handler) {
            handler.handle(Future.succeededFuture());
        }

        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return writeQueueFull;
        }

        @Override
        public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
            this.drainHandler = handler;
            return this;
        }

        public void simulateDrain() {
            writeQueueFull = false;
            if (drainHandler != null) {
                drainHandler.handle(null);
            }
        }

        public Buffer getReceivedBuffer() {
            return receivedBuffer;
        }
    }
}