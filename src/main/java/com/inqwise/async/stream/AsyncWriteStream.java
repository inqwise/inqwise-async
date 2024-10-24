package com.inqwise.async.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

/**
 * The {@code AsyncWriteStream} class wraps a standard Java {@link OutputStream}
 * and exposes it as a Vert.x {@link WriteStream}&lt;{@link Buffer}&gt;.
 * It enables asynchronous, non-blocking writing to output streams in a Vert.x-compliant manner.
 * <p>
 * This implementation utilizes a single-threaded {@link ExecutorService} to perform blocking I/O
 * operations without blocking the Vert.x event loop. Data written to this stream is handled
 * asynchronously, allowing seamless integration with Vert.x's asynchronous APIs.
 * </p>
 * <p>
 * Note that back-pressure mechanisms are not supported in this implementation. Methods related
 * to back-pressure, such as {@code setWriteQueueMaxSize}, {@code writeQueueFull}, and {@code drainHandler},
 * are not implemented and will not function as expected.
 * </p>
 *
 * @see WriteStream
 * @see OutputStream
 */
public class AsyncWriteStream implements WriteStream<Buffer> {

    /**
     * The Vert.x instance used to schedule write operations on the event loop.
     */
    private final Vertx vertx;

    /**
     * The underlying {@link OutputStream} to which data is written.
     */
    private final OutputStream out;

    /**
     * The handler invoked when an error occurs during writing.
     */
    private Handler<Throwable> exceptionHandler;

    /**
     * Indicates whether the stream has been closed.
     */
    private volatile boolean closed = false;

    /**
     * ExecutorService responsible for executing blocking I/O operations.
     */
    private final ExecutorService executor;

    /**
     * Creates a new {@code AsyncWriteStream} wrapping the given Vert.x {@link OutputStream}.
     *
     * @param vertx the Vert.x instance
     * @param out   the output stream to write to
     * @throws NullPointerException if {@code vertx} or {@code out} is {@code null}
     */
    public AsyncWriteStream(Vertx vertx, OutputStream out) {
        if (out == null) {
            throw new NullPointerException("OutputStream 'out' cannot be null");
        }
        if (vertx == null) {
            throw new NullPointerException("Vertx instance 'vertx' cannot be null");
        }
        this.vertx = vertx;
        this.out = out;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "async-write-stream-thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Sets the exception handler that is called when an error occurs during writing.
     *
     * @param handler the exception handler
     * @return a reference to this {@code AsyncWriteStream}, allowing method chaining
     */
    @Override
    public synchronized WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * Asynchronously writes data to the output stream.
     * <p>
     * This method submits the write operation to an {@link ExecutorService} to avoid blocking
     * the Vert.x event loop. The write operation is performed in a separate thread, and the
     * {@link WriteStream} is used to handle the actual writing.
     * </p>
     *
     * @param data the {@link Buffer} containing data to write
     * @return a {@link Future} that completes when the write operation finishes successfully
     *         or fails with an exception
     */
    @Override
    public Future<Void> write(Buffer data) {
        if (closed) {
            return Future.failedFuture(new IllegalStateException("Stream is closed"));
        }

        Future<Void> writeFuture = Future.future(promise -> {
            executor.submit(() -> {
                try {
                    byte[] bytes = data.getBytes();
                    out.write(bytes);
                    out.flush();
                    // Notify Vert.x that the write has succeeded
                    vertx.runOnContext(v -> promise.complete());
                } catch (IOException e) {
                    handleException(e);
                    // Notify Vert.x that the write has failed
                    vertx.runOnContext(v -> promise.fail(e));
                    // Shutdown the executor to prevent further writes
                    executor.shutdown();
                }
            });
        });

        return writeFuture;
    }

    /**
     * Asynchronously writes data to the output stream and notifies the provided handler upon completion.
     *
     * @param data    the {@link Buffer} containing data to write
     * @param handler the handler to be notified upon completion or failure of the write operation
     */
    @Override
    public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
        write(data).onComplete(handler);
    }

    /**
     * Ends the stream, optionally notifying the provided handler upon completion.
     * <p>
     * This method flushes and closes the underlying {@link OutputStream}. After invoking
     * this method, the stream is marked as closed, and further write operations are prohibited.
     * </p>
     *
     * @param handler the handler to be notified when the stream is successfully closed or if an error occurs
     */
    @Override
    public void end(Handler<AsyncResult<Void>> handler) {
        if (closed) {
            if (handler != null) {
                handler.handle(Future.succeededFuture());
            }
            return;
        }
        closed = true;

        Future<Void> endFuture = Future.future(promise -> {
            executor.submit(() -> {
                try {
                    out.flush();
                    out.close();
                    // Notify Vert.x that the stream has ended successfully
                    vertx.runOnContext(v -> promise.complete());
                } catch (IOException e) {
                    handleException(e);
                    // Notify Vert.x that closing the stream has failed
                    vertx.runOnContext(v -> promise.fail(e));
                } finally {
                    // Shutdown the executor as no further writes are allowed
                    executor.shutdown();
                }
            });
        });

        if (handler != null) {
            endFuture.onComplete(handler);
        }
    }

    /**
     * Sets the maximum size of the write queue.
     * <p>
     * This implementation does not support back-pressure, so this method has no effect.
     * </p>
     *
     * @param maxSize the maximum size of the write queue
     * @return a reference to this {@code AsyncWriteStream}, allowing method chaining
     */
    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
        // Back-pressure is not supported in this implementation
        return this;
    }

    /**
     * Checks if the write queue is full.
     * <p>
     * This implementation does not support back-pressure, so this method always returns {@code false}.
     * </p>
     *
     * @return {@code false}, indicating that the write queue is never full
     */
    @Override
    public boolean writeQueueFull() {
        // Back-pressure is not supported in this implementation
        return false;
    }

    /**
     * Sets the drain handler which is called when the write queue is ready to accept more data.
     * <p>
     * This implementation does not support back-pressure, so this method has no effect.
     * </p>
     *
     * @param handler the drain handler
     * @return a reference to this {@code AsyncWriteStream}, allowing method chaining
     */
    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
        // Back-pressure is not supported in this implementation
        return this;
    }

    /**
     * Handles exceptions by invoking the exception handler if one is set.
     * If no exception handler is set, the exception is printed to the standard error stream.
     *
     * @param t the {@link Throwable} to handle
     */
    private void handleException(Throwable t) {
        Handler<Throwable> handler;
        synchronized (this) {
            handler = exceptionHandler;
        }
        if (handler != null) {
            vertx.runOnContext(v -> handler.handle(t));
        } else {
            // Log the exception if no handler is set
            t.printStackTrace();
        }
    }
}