package com.inqwise.async.stream;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@code AsyncOutputStream} class wraps a Vert.x {@link WriteStream}
 * into a standard Java {@link OutputStream}. It allows writing data to
 * the WriteStream asynchronously, handling back-pressure and flow control.
 * <p>
 * This implementation uses an {@link ExecutorService} to manage asynchronous
 * write operations without blocking the Vert.x event loop. It ensures thread safety
 * and proper synchronization between the blocking {@code OutputStream} methods
 * and the asynchronous nature of the {@code WriteStream}.
 * </p>
 */
public class AsyncOutputStream extends OutputStream {

    private final Vertx vertx;
    private final WriteStream<Buffer> writeStream;
    private final ExecutorService executor;
    private final AtomicBoolean closed;

    /**
     * Creates a new {@code AsyncOutputStream} wrapping the given Vert.x {@link WriteStream}.
     *
     * @param vertx       the Vert.x instance
     * @param writeStream the Vert.x WriteStream to wrap
     * @throws NullPointerException if {@code vertx} or {@code writeStream} is {@code null}
     */
    public AsyncOutputStream(Vertx vertx, WriteStream<Buffer> writeStream) {
        if (vertx == null) {
            throw new NullPointerException("Vertx instance cannot be null");
        }
        if (writeStream == null) {
            throw new NullPointerException("WriteStream cannot be null");
        }
        this.vertx = vertx;
        this.writeStream = writeStream;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "async-output-stream-thread");
            thread.setDaemon(true);
            return thread;
        });
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Writes a single byte to the output stream.
     * This method blocks until the byte is written or an exception occurs.
     *
     * @param b the {@code byte} to write
     * @throws IOException if an I/O error occurs or if the stream is closed
     */
    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    /**
     * Writes {@code len} bytes from the specified byte array starting at offset {@code off}
     * to this output stream. This method blocks until all the bytes are written or an exception occurs.
     *
     * @param b   the data to write
     * @param off the start offset in the data
     * @param len the number of bytes to write
     * @throws IOException               if an I/O error occurs or if the stream is closed
     * @throws NullPointerException      if {@code b} is {@code null}
     * @throws IndexOutOfBoundsException if {@code off} or {@code len} is invalid
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();

        if (b == null) {
            throw new NullPointerException("Buffer is null");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("Invalid offset/length");
        }

        // Submit the write task to the executor
        Future<Void> future = executor.submit(() -> {
            CompletableFuture<Void> writeFuture = new CompletableFuture<>();
            Buffer buffer = Buffer.buffer().appendBytes(b, off, len);

            vertx.runOnContext(v -> {
                writeStream.write(buffer).onComplete(ar -> {
                    if (ar.succeeded()) {
                        writeFuture.complete(null);
                    } else {
                        writeFuture.completeExceptionally(ar.cause());
                    }
                });
            });

            try {
                writeFuture.get();
            } catch (ExecutionException e) {
                throw new IOException("An error occurred during writing to the stream", e.getCause());
            }
            return null;
        });

        // Wait for the write operation to complete
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing to stream", e);
        } catch (ExecutionException e) {
            throw new IOException("An error occurred during writing to the stream", e.getCause());
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes to be written out.
     * In this implementation, flush is a no-op since writes are immediate.
     *
     * @throws IOException if an I/O error occurs or if the stream is closed
     */
    @Override
    public void flush() throws IOException {
        checkClosed();
        // No-op, as writes are immediate in Vert.x
    }

    /**
     * Closes this output stream and releases any system resources associated with this stream.
     * Once the stream is closed, further write(), flush(), or close() invocations will throw an {@link IOException}.
     * Closing a previously closed stream has no effect.
     *
     * @throws IOException if an I/O error occurs during closing the stream
     */
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            Future<Void> future = executor.submit(() -> {
                CompletableFuture<Void> closeFuture = new CompletableFuture<>();

                vertx.runOnContext(v -> {
                    writeStream.end().onComplete(ar -> {
                        if (ar.succeeded()) {
                            closeFuture.complete(null);
                        } else {
                            closeFuture.completeExceptionally(ar.cause());
                        }
                    });
                });

                try {
                    closeFuture.get();
                } catch (ExecutionException e) {
                    throw new IOException("An error occurred during closing the stream", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while closing the stream", e);
                }
                return null;
            });

            // Wait for the close operation to complete
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while closing the stream", e);
            } catch (ExecutionException e) {
                throw new IOException("An error occurred during closing the stream", e.getCause());
            } finally {
                executor.shutdown();
            }
        }
    }

    /**
     * Checks if the stream has been closed and throws an {@link IOException} if it has.
     *
     * @throws IOException if the stream is closed
     */
    private void checkClosed() throws IOException {
        if (closed.get()) {
            throw new IOException("Stream is closed");
        }
    }
}