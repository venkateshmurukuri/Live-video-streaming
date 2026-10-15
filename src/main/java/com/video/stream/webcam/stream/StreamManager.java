package com.video.stream.webcam.stream;

import com.video.stream.webcam.capture.FFmpegCommandBuilder;
import com.video.stream.webcam.capture.ProcessLauncher;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Owns every stream: launches FFmpeg, relaunches it within the retry policy, and tears it down on stop. */
@Slf4j
@Service
public class StreamManager {

    private static final int ERROR_CONTEXT_LINES = 5;
    private static final Duration STOP_GRACE = Duration.ofSeconds(5);
    private static final long STOP_POLL_MS = 100;

    private final FFmpegCommandBuilder commands;
    private final ProcessLauncher launcher;
    private final RetryPolicy retryPolicy;
    private final ExecutorService executor;
    private final Map<UUID, StreamSession> sessions = new ConcurrentHashMap<>();

    public StreamManager(FFmpegCommandBuilder commands, ProcessLauncher launcher,
                         RetryPolicy retryPolicy, ExecutorService streamExecutor) {
        this.commands = commands;
        this.launcher = launcher;
        this.retryPolicy = retryPolicy;
        this.executor = streamExecutor;
    }

    public StreamSession start(String device, String destination) {
        StreamSession session = new StreamSession(UUID.randomUUID(), device, destination);
        sessions.put(session.id(), session);
        session.runner(executor.submit(() -> supervise(session)));
        log.info("Stream {} created: device='{}' -> {}", session.id(), device, destination);
        return session;
    }

    public StreamSession get(UUID id) {
        StreamSession session = sessions.get(id);
        if (session == null) {
            throw new StreamNotFoundException(id);
        }
        return session;
    }

    public List<StreamSession> list() {
        return sessions.values().stream().sorted(Comparator.comparing(StreamSession::startedAt)).toList();
    }

    /** Stops the stream and blocks until FFmpeg has exited (or the grace period passes). Idempotent. */
    public StreamSession stop(UUID id) {
        StreamSession session = get(id);
        if (!session.requestStop()) {
            return session;
        }
        session.setState(StreamState.STOPPING);
        terminate(session.process());
        awaitRunner(session);
        log.info("Stream {} stopped", id);
        return session;
    }

    @PreDestroy
    void stopAll() {
        sessions.values().stream().filter(StreamSession::isLive).forEach(session -> stop(session.id()));
    }

    /** Runs FFmpeg for the session's lifetime, relaunching on failure within the retry policy. */
    private void supervise(StreamSession session) {
        int consecutiveFailures = 0;
        while (!session.stopRequested()) {
            long launchedAt = System.nanoTime();
            Process process;
            try {
                process = launcher.launch(commands.build(session.device(), session.destination()));
            } catch (IOException e) {
                session.fail("could not launch ffmpeg: " + e.getMessage());
                log.error("Stream {}: {}", session.id(), session.lastError());
                return;
            }
            session.attach(process);
            // stop() may have raced us between launch and attach; it would have found no process to kill
            if (session.stopRequested()) {
                terminate(process);
                break;
            }
            session.setState(StreamState.RUNNING);

            String tail = drain(session.id(), process);
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                terminate(process);
                break;
            } finally {
                session.detach();
            }
            if (session.stopRequested()) {
                break;
            }

            long ranMs = (System.nanoTime() - launchedAt) / 1_000_000;
            if (retryPolicy.wasStable(ranMs)) {
                consecutiveFailures = 0;
            }
            consecutiveFailures++;
            String error = "ffmpeg exited with code " + exitCode + (tail.isBlank() ? "" : " — " + tail);
            if (!retryPolicy.canRetry(consecutiveFailures)) {
                session.fail(error);
                log.error("Stream {} failed after {} consecutive failures: {}",
                        session.id(), consecutiveFailures, error);
                return;
            }
            long delayMs = retryPolicy.backoffMs(consecutiveFailures);
            session.markRetry(error);
            log.warn("Stream {}: {}; relaunch {}/{} in {} ms",
                    session.id(), error, consecutiveFailures, retryPolicy.maxRetries(), delayMs);
            if (!sleepUnlessStopped(session, delayMs)) {
                break;
            }
        }
        session.setState(StreamState.STOPPED);
    }

    /** Pumps FFmpeg's output until it exits; logs at debug and keeps the last few lines as error context. */
    private String drain(UUID id, Process process) {
        Deque<String> tail = new ArrayDeque<>(ERROR_CONTEXT_LINES);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                log.debug("ffmpeg[{}] {}", id, line);
                if (tail.size() == ERROR_CONTEXT_LINES) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            }
        } catch (IOException e) {
            log.debug("ffmpeg[{}] output closed: {}", id, e.getMessage());
        }
        return String.join(" | ", tail);
    }

    /** SIGTERM lets FFmpeg flush and close the RTMP connection; SIGKILL only if it ignores that. */
    private static void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void awaitRunner(StreamSession session) {
        Future<?> runner = session.runner();
        if (runner == null) {
            return;
        }
        try {
            runner.get(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Stream {} supervisor did not finish within {}", session.id(), STOP_GRACE);
        } catch (ExecutionException e) {
            log.error("Stream {} supervisor threw", session.id(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Sleeps in short slices so a stop request cuts a long backoff short. Returns false if stopped. */
    private static boolean sleepUnlessStopped(StreamSession session, long delayMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        while (System.nanoTime() < deadline) {
            if (session.stopRequested()) {
                return false;
            }
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            try {
                Thread.sleep(Math.max(1, Math.min(STOP_POLL_MS, remainingMs)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !session.stopRequested();
    }
}
