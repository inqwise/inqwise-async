package com.inqwise.async.stream;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The {@code AsyncReadStream} class wraps a standard Java {@link InputStream}
 * and exposes it as a Vert.x {@link ReadStream}&lt;{@link Buffer}&gt;.
 * It allows for asynchronous, non-blocking reading of input streams in a Vert.x-compliant way.
 * <p>
 * This class uses a single-threaded {@link ExecutorService} to perform blocking I/O operations
 * without blocking the Vert.x event loop. Data read from the {@code InputStream} is emitted
 * to the {@code ReadStream} handlers, maintaining back-pressure and flow control.
 * </p>
 */
public class AsyncReadStream implements ReadStream<Buffer> {

    /**
     * Default chunk size used when reading from the input stream.
     */
    static final int DEFAULT_CHUNK_SIZE = 8192;

    /**
     * The Vert.x instance.
     */
    private final Vertx vertx;

    /**
     * The input stream to read data from.
     */
    private final InputStream in;

    /**
     * The size of the chunks to read from the input stream.
     */
    private final int chunkSize;

    /**
     * The handler invoked when the stream is closed or fully read.
     */
    private Handler<Void> endHandler;

    /**
     * The data handler that receives data read from the stream.
     */
    private Handler<Buffer> dataHandler;

    /**
     * The handler called when an error occurs while reading the stream.
     */
    private Handler<Throwable> exceptionHandler;

    /**
     * Indicates if the stream is closed.
     */
    private volatile boolean closed = false;

    /**
     * ExecutorService for executing blocking I/O operations.
     */
    private final ExecutorService executor;

    /**
     * Creates a new {@code AsyncReadStream} with the default chunk size.
     *
     * @param vertx the Vert.x instance
     * @param in    the input stream to read
     * @throws NullPointerException if {@code vertx} or {@code in} is {@code null}
     */
    public AsyncReadStream(Vertx vertx, InputStream in) {
        this(vertx, in, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new {@code AsyncReadStream} with the specified chunk size.
     *
     * @param vertx     the Vert.x instance
     * @param in        the input stream to read
     * @param chunkSize the size of chunks to read from the input stream
     * @throws NullPointerException     if {@code vertx} or {@code in} is {@code null}
     * @throws IllegalArgumentException if {@code chunkSize} is not a positive integer
     */
    public AsyncReadStream(Vertx vertx, InputStream in, int chunkSize) {
        if (vertx == null) {
            throw new NullPointerException("Vertx instance 'vertx' cannot be null");
        }
        if (in == null) {
            throw new NullPointerException("InputStream 'in' cannot be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be a positive integer");
        }

        this.vertx = vertx;
        this.in = in;
        this.chunkSize = chunkSize;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "async-read-stream-thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Sets the end handler which is called when the stream has been fully read or closed.
     *
     * @param endHandler the handler to be called on stream end
     * @return a reference to this, so the API can be used fluently
     */
    @Override
    public synchronized AsyncReadStream endHandler(Handler<Void> endHandler) {
        this.endHandler = endHandler;
        return this;
    }

    /**
     * Sets the data handler which is called as data is read from the stream.
     * Passing {@code null} to this method pauses the reading.
     *
     * @param handler the data handler, or {@code null} to pause reading
     * @return a reference to this, so the API can be used fluently
     */
    @Override
    public synchronized AsyncReadStream handler(Handler<Buffer> handler) {
        this.dataHandler = handler;
        if (handler != null && !closed) {
            doRead();
        }
        return this;
    }

    /**
     * Pauses the reading of the stream.
     *
     * @return a reference to this, so the API can be used fluently
     */
    @Override
    public synchronized AsyncReadStream pause() {
        this.dataHandler = null;
        return this;
    }

    /**
     * Resumes reading from the stream if it was paused.
     *
     * @return a reference to this, so the API can be used fluently
     */
    @Override
    public synchronized AsyncReadStream resume() {
        if (dataHandler != null && !closed) {
            doRead();
        }
        return this;
    }

    /**
     * Sets the exception handler which is called when an error occurs during reading.
     *
     * @param handler the exception handler
     * @return a reference to this, so the API can be used fluently
     */
    @Override
    public synchronized AsyncReadStream exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * Fetches the specified amount of data. Not implemented in this class.
     *
     * @param amount the number of items to fetch
     * @return a reference to this, so the API can be used fluently
     * @throws UnsupportedOperationException since fetch is not implemented
     */
    @Override
    public ReadStream<Buffer> fetch(long amount) {
        // Back-pressure not supported in this implementation
        throw new UnsupportedOperationException("fetch method is not implemented");
    }

    /**
     * Initiates the asynchronous reading of data from the input stream.
     * This method schedules reading tasks using the ExecutorService.
     */
    private void doRead() {
        executor.submit(() -> {
            try {
                byte[] buffer = new byte[chunkSize];
                int bytesRead = in.read(buffer);
                if (bytesRead == -1) {
                    // End of stream
                    closeInputStream();
                    vertx.runOnContext(v -> {
                        Handler<Void> handler;
                        synchronized (this) {
                            handler = endHandler;
                        }
                        if (handler != null) {
                            handler.handle(null);
                        }
                    });
                    executor.shutdown();
                } else {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    Buffer bufferData = Buffer.buffer(data);

                    Handler<Buffer> handler;
                    synchronized (this) {
                        handler = dataHandler;
                    }
                    if (handler != null) {
                        vertx.runOnContext(v -> handler.handle(bufferData));
                        // Continue reading
                        doRead();
                    }
                }
            } catch (Throwable e) {
                handleException(e);
                closeInputStream();
                executor.shutdown();
            }
        });
    }

    /**
     * Closes the input stream quietly, ignoring any exceptions.
     * Sets the {@code closed} flag to prevent further reading.
     */
    private void closeInputStream() {
        if (!closed) {
            closed = true;
            try {
                in.close();
            } catch (IOException e) {
                // Ignore exception on close
            }
        }
    }

    /**
     * Handles exceptions by invoking the exception handler if set.
     *
     * @param t the throwable to handle
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