package com.video.stream.webcam.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(5, 1000, 15000, 30000);

    @Test
    void backoffDoublesThenCaps() {
        assertThat(policy.backoffMs(1)).isEqualTo(1000);
        assertThat(policy.backoffMs(2)).isEqualTo(2000);
        assertThat(policy.backoffMs(3)).isEqualTo(4000);
        assertThat(policy.backoffMs(4)).isEqualTo(8000);
        assertThat(policy.backoffMs(5)).isEqualTo(15000);
        assertThat(policy.backoffMs(6)).isEqualTo(15000);
    }

    @Test
    void backoffDoesNotOverflowForAbsurdRetryNumbers() {
        assertThat(policy.backoffMs(70)).isEqualTo(15000);
        assertThat(policy.backoffMs(Integer.MAX_VALUE)).isEqualTo(15000);
    }

    @Test
    void allowsExactlyMaxRetriesRelaunches() {
        assertThat(policy.canRetry(1)).isTrue();
        assertThat(policy.canRetry(5)).isTrue();
        assertThat(policy.canRetry(6)).isFalse();
        assertThat(new RetryPolicy(0, 1, 1, 1).canRetry(1)).isFalse();
    }

    @Test
    void stableRunIsJudgedAgainstThreshold() {
        assertThat(policy.wasStable(29_999)).isFalse();
        assertThat(policy.wasStable(30_000)).isTrue();
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new RetryPolicy(-1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, 500, 100, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.backoffMs(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
