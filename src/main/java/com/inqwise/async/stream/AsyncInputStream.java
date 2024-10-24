package com.inqwise.async.stream;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

/**
 * The {@code AsyncInputStream} class wraps a Vert.x {@link ReadStream}
 * into a standard Java {@link InputStream}. It allows reading data from
 * the ReadStream in a blocking manner, handling back-pressure and flow control.
 * <p>
 * This implementation uses a {@link BlockingQueue} to buffer incoming data
 * and provides methods to read bytes synchronously, while managing the asynchronous
 * nature of the underlying ReadStream.
 * </p>
 */
public class AsyncInputStream extends InputStream {

    private final AtomicBoolean readStreamFinished;
    private final AtomicBoolean readStreamPaused;
    private final AtomicBoolean closed;

    private static final int MAX_QUEUE_SIZE = 32768;

    private final BlockingQueue<Byte> buffer;

    private final ReadStream<Buffer> inputStream;

    private final AtomicReference<Throwable> exception = new AtomicReference<>(null);

    /**
     * Creates a new {@code AsyncInputStream} wrapping the given Vert.x {@link ReadStream}.
     *
     * @param inputStream the Vert.x ReadStream to wrap
     * @throws NullPointerException if {@code inputStream} is {@code null}
     */
    public AsyncInputStream(ReadStream<Buffer> inputStream) {
        if (inputStream == null) {
            throw new NullPointerException("inputStream cannot be null");
        }

        this.readStreamPaused = new AtomicBoolean(false);
        this.readStreamFinished = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);

        this.buffer = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.inputStream = inputStream;

        this.inputStream.handler(this::handleBuffer);
        this.inputStream.endHandler(v -> readStreamFinished.set(true));
        this.inputStream.exceptionHandler(this::handleException);

        // Start the stream
        this.inputStream.resume();
    }

    /**
     * Handles incoming buffers from the ReadStream by adding their bytes to the buffer queue.
     * If the buffer queue reaches its maximum capacity, the ReadStream is paused to apply back-pressure.
     *
     * @param handleBuffer the buffer received from the ReadStream
     */
    private void handleBuffer(Buffer handleBuffer) {
        int index = 0;
        while (index < handleBuffer.length()) {
            try {
                buffer.put(handleBuffer.getByte(index));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Handle interruption
                break;
            }
            index++;
        }
        // Pause the stream if buffer is full
        if (buffer.remainingCapacity() == 0) {
            stop();
        }
    }

    /**
     * Handles exceptions from the ReadStream by storing the exception and marking the stream as finished.
     *
     * @param throwable the exception thrown by the ReadStream
     */
    private void handleException(Throwable throwable) {
        exception.set(throwable);
        readStreamFinished.set(true);
    }

    /**
     * Resumes reading from the underlying ReadStream if it was previously paused.
     * This is used to manage back-pressure when the buffer queue has available capacity.
     *
     * @return this {@code AsyncInputStream} instance
     */
    public AsyncInputStream start() {
        if (readStreamPaused.compareAndSet(true, false)) {
            this.inputStream.resume();
        }
        return this;
    }

    /**
     * Pauses reading from the underlying ReadStream.
     * This is used to apply back-pressure when the buffer queue is full.
     *
     * @return this {@code AsyncInputStream} instance
     */
    public AsyncInputStream stop() {
        if (readStreamPaused.compareAndSet(false, true)) {
            this.inputStream.pause();
        }
        return this;
    }

    /**
     * Reads the next byte of data from the input stream.
     * This method blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * @return the next byte of data, or {@code -1} if the end of the stream has been reached
     * @throws IOException if an I/O error occurs or if the stream is closed
     */
    @Override
    public int read() throws IOException {
        return readInternal();
    }

    /**
     * Reads the next byte of data from the buffer queue.
     * Manages back-pressure and handles stream closure and exceptions.
     *
     * @return the next byte of data, or {@code -1} if the end of the stream has been reached
     * @throws IOException if an I/O error occurs or if the stream is closed
     */
    private int readInternal() throws IOException {
        checkClosed();

        if (exception.get() != null) {
            throw new IOException("Exception in underlying ReadStream", exception.get());
        }

        try {
            Byte b = buffer.poll(5000, TimeUnit.MILLISECONDS);
            if (b == null) {
                if (readStreamFinished.get()) {
                    if (exception.get() != null) {
                        throw new IOException("Exception in underlying ReadStream", exception.get());
                    }
                    return -1; // End of stream
                } else {
                    // No data yet, continue waiting
                    return readInternal();
                }
            } else {
                // After consuming a byte, check if we need to resume the stream
                if (readStreamPaused.get() && buffer.remainingCapacity() > MAX_QUEUE_SIZE / 2) {
                    start();
                }
                return Byte.toUnsignedInt(b);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading from stream", e);
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

    /**
     * Returns an estimate of the number of bytes that can be read (or skipped over)
     * from this input stream without blocking by the next invocation of a method
     * for this input stream.
     *
     * @return an estimate of the number of bytes that can be read without blocking
     * @throws IOException if an I/O error occurs or if the stream is closed
     */
    @Override
    public int available() throws IOException {
        checkClosed();
        return buffer.size();
    }

    /**
     * Closes this input stream and releases any system resources associated with the stream.
     * Once the stream has been closed, further read(), available(), or close() invocations will throw an {@link IOException}.
     * Closing a previously closed stream has no effect.
     *
     * @throws IOException if an I/O error occurs during closing the stream
     */
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            stop();
            buffer.clear();
            inputStream.handler(null);
            inputStream.endHandler(null);
            inputStream.exceptionHandler(null);
            super.close();
        }
    }
}