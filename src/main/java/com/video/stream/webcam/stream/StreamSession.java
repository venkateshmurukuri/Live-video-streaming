package com.video.stream.webcam.stream;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mutable state of one stream; written by the supervisor thread, read by API threads. */
public final class StreamSession {

    private final UUID id;
    private final String device;
    private final String destination;
    private final Instant startedAt = Instant.now();
    private final AtomicBoolean stopRequested = new AtomicBoolean();

    private volatile StreamState state = StreamState.STARTING;
    private volatile int retryCount;
    private volatile Instant lastRetryAt;
    private volatile String lastError;
    private volatile Process process;
    private volatile Future<?> runner;

    public StreamSession(UUID id, String device, String destination) {
        this.id = id;
        this.device = device;
        this.destination = destination;
    }

    public UUID id() {
        return id;
    }

    public String device() {
        return device;
    }

    public String destination() {
        return destination;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public StreamState state() {
        return state;
    }

    public int retryCount() {
        return retryCount;
    }

    public Instant lastRetryAt() {
        return lastRetryAt;
    }

    public String lastError() {
        return lastError;
    }

    public boolean isLive() {
        return state == StreamState.STARTING || state == StreamState.RUNNING || state == StreamState.RETRYING;
    }

    public boolean stopRequested() {
        return stopRequested.get();
    }

    /** Returns true only for the first caller, so stop is idempotent. */
    boolean requestStop() {
        return stopRequested.compareAndSet(false, true);
    }

    void setState(StreamState state) {
        this.state = state;
    }

    void markRetry(String error) {
        retryCount++;
        lastRetryAt = Instant.now();
        lastError = error;
        state = StreamState.RETRYING;
    }

    void fail(String error) {
        lastError = error;
        state = StreamState.FAILED;
    }

    void attach(Process process) {
        this.process = process;
    }

    void detach() {
        this.process = null;
    }

    Process process() {
        return process;
    }

    void runner(Future<?> runner) {
        this.runner = runner;
    }

    Future<?> runner() {
        return runner;
    }
}
