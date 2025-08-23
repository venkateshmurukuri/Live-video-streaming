package com.video.stream.webcam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamStatusResponse {
    
    private String streamId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime lastRetryTime;
    private int retryCount;
    private String lastError;
    private boolean isActive;
    
    public static StreamStatusResponse from(StreamStatus status) {
        return StreamStatusResponse.builder()
                .streamId(status.getStreamId())
                .status(status.getStatus())
                .startTime(status.getStartTime())
                .lastRetryTime(status.getLastRetryTime())
                .retryCount(status.getRetryCount())
                .lastError(status.getLastError())
                .isActive(status.isActive())
                .build();
    }
}
