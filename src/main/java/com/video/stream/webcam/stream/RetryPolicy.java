package com.video.stream.webcam.stream;

import com.video.stream.webcam.config.StreamProperties;

/**
 * Bounded exponential backoff for relaunching FFmpeg.
 *
 * @param maxRetries       relaunches allowed after consecutive failures
 * @param initialBackoffMs delay before the first relaunch; doubles on each subsequent one
 * @param maxBackoffMs     ceiling for the delay
 * @param stableRunMs      a process that lived at least this long resets the failure count
 */
public record RetryPolicy(int maxRetries, long initialBackoffMs, long maxBackoffMs, long stableRunMs) {

    public RetryPolicy {
        if (maxRetries < 0 || initialBackoffMs < 0 || stableRunMs < 0) {
            throw new IllegalArgumentException("retry settings must not be negative");
        }
        if (maxBackoffMs < initialBackoffMs) {
            throw new IllegalArgumentException("maxBackoffMs must be >= initialBackoffMs");
        }
    }

    public static RetryPolicy from(StreamProperties props) {
        return new RetryPolicy(props.getMaxRetries(), props.getInitialBackoffMs(),
                props.getMaxBackoffMs(), props.getStableRunMs());
    }

    /** Whether a relaunch is allowed after {@code consecutiveFailures} failures in a row. */
    public boolean canRetry(int consecutiveFailures) {
        return consecutiveFailures <= maxRetries;
    }

    /** Delay before retry number {@code retry} (1-based): {@code initial × 2^(retry−1)}, capped. */
    public long backoffMs(int retry) {
        if (retry < 1) {
            throw new IllegalArgumentException("retry is 1-based");
        }
        int shift = retry - 1;
        if (shift >= Long.SIZE - 1 || initialBackoffMs > (Long.MAX_VALUE >> shift)) {
            return maxBackoffMs;
        }
        return Math.min(initialBackoffMs << shift, maxBackoffMs);
    }

    public boolean wasStable(long runMs) {
        return runMs >= stableRunMs;
    }
}
