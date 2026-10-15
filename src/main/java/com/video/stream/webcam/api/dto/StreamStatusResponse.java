package com.video.stream.webcam.api.dto;

import com.video.stream.webcam.stream.StreamSession;
import com.video.stream.webcam.stream.StreamState;
import java.time.Instant;
import java.util.UUID;

public record StreamStatusResponse(
        UUID id,
        String device,
        String destination,
        StreamState state,
        Instant startedAt,
        int retryCount,
        Instant lastRetryAt,
        String lastError) {

    public static StreamStatusResponse from(StreamSession session) {
        return new StreamStatusResponse(session.id(), session.device(), session.destination(),
                session.state(), session.startedAt(), session.retryCount(),
                session.lastRetryAt(), session.lastError());
    }
}
