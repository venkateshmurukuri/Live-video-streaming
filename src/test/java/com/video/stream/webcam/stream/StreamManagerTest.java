package com.video.stream.webcam.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.video.stream.webcam.capture.CaptureBackend;
import com.video.stream.webcam.capture.FFmpegCommandBuilder;
import com.video.stream.webcam.capture.ProcessLauncher;
import com.video.stream.webcam.config.StreamProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Drives the supervisor with tiny shell scripts in place of FFmpeg, so the retry and stop paths run for real. */
@EnabledOnOs({OS.LINUX, OS.MAC})
class StreamManagerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final FFmpegCommandBuilder commands =
            new FFmpegCommandBuilder(new StreamProperties(), CaptureBackend.TEST);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static ProcessLauncher shell(String script) {
        return command -> new ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start();
    }

    @Test
    void relaunchesUpToMaxRetriesThenFails() {
        StreamManager manager = new StreamManager(commands, shell("echo boom; exit 3"),
                new RetryPolicy(2, 10, 20, 60_000), executor);

        StreamSession session = manager.start("test", "rtmp://localhost/hls/x");
        awaitUntil(() -> session.state() == StreamState.FAILED);

        assertThat(session.retryCount()).isEqualTo(2);
        assertThat(session.lastError()).contains("code 3").contains("boom");
        assertThat(session.isLive()).isFalse();
    }

    @Test
    void stopTerminatesTheRunningProcess() throws IOException {
        AtomicReference<Process> launched = new AtomicReference<>();
        ProcessLauncher capturing = command -> {
            Process process = shell("sleep 30").launch(command);
            launched.set(process);
            return process;
        };
        StreamManager manager = new StreamManager(commands, capturing,
                new RetryPolicy(5, 10, 20, 60_000), executor);

        StreamSession session = manager.start("test", "rtmp://localhost/hls/x");
        awaitUntil(() -> session.state() == StreamState.RUNNING);

        long startedAt = System.nanoTime();
        StreamSession stopped = manager.stop(session.id());
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(stopped.state()).isEqualTo(StreamState.STOPPED);
        assertThat(launched.get().isAlive()).isFalse();
        assertThat(tookMs).isLessThan(5_000);
        assertThat(session.retryCount()).isZero();
        // a second stop is a no-op, not an error
        assertThat(manager.stop(session.id()).state()).isEqualTo(StreamState.STOPPED);
    }

    @Test
    void stopDoesNotWaitForOrphanedChildrenHoldingTheOutputPipe() {
        // the shell dies on SIGTERM but its background child keeps our stdout pipe open for 30 s
        StreamManager manager = new StreamManager(commands, shell("sleep 30 & wait"),
                new RetryPolicy(5, 10, 20, 60_000), executor);

        StreamSession session = manager.start("test", "rtmp://localhost/hls/x");
        awaitUntil(() -> session.state() == StreamState.RUNNING);

        long startedAt = System.nanoTime();
        StreamSession stopped = manager.stop(session.id());

        assertThat(stopped.state()).isEqualTo(StreamState.STOPPED);
        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(3_000);
    }

    @Test
    void stopDuringBackoffDoesNotWaitForTheFullDelay() {
        StreamManager manager = new StreamManager(commands, shell("exit 1"),
                new RetryPolicy(5, 60_000, 60_000, 60_000), executor);

        StreamSession session = manager.start("test", "rtmp://localhost/hls/x");
        awaitUntil(() -> session.state() == StreamState.RETRYING);

        long startedAt = System.nanoTime();
        manager.stop(session.id());

        assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(2_000);
        assertThat(session.state()).isEqualTo(StreamState.STOPPED);
    }

    @Test
    void stableRunResetsTheRetryBudget() {
        // every run lives longer than stableRunMs, so one allowed retry never runs out
        StreamManager manager = new StreamManager(commands, shell("sleep 0.3; exit 1"),
                new RetryPolicy(1, 10, 20, 200), executor);

        StreamSession session = manager.start("test", "rtmp://localhost/hls/x");
        awaitUntil(() -> session.retryCount() >= 3);

        assertThat(session.state()).isNotEqualTo(StreamState.FAILED);
        manager.stop(session.id());
    }

    @Test
    void launchFailureMarksTheStreamFailedWithTheCause() {
        ProcessLauncher broken = command -> {
            throw new IOException("ffmpeg: not found");
        };
        StreamManager manager = new StreamManager(commands, broken,
                new RetryPolicy(5, 10, 20, 60_000), executor);

        StreamSession session = manager.start("test", "rtmp://localhost/hls/x");
        awaitUntil(() -> session.state() == StreamState.FAILED);

        assertThat(session.lastError()).contains("ffmpeg: not found");
        assertThat(session.retryCount()).isZero();
    }

    @Test
    void unknownIdIsReported() {
        StreamManager manager = new StreamManager(commands, shell("exit 0"),
                new RetryPolicy(0, 10, 20, 60_000), executor);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.get(java.util.UUID.randomUUID()))
                .isInstanceOf(StreamNotFoundException.class);
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + TIMEOUT);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
    }
}
