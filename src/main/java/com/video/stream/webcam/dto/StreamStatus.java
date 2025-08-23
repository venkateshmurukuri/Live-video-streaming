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
public class StreamStatus {
    
    private String streamId;
    private String status; // RUNNING, STOPPED, ERROR, RETRYING
    private LocalDateTime startTime;
    private LocalDateTime lastRetryTime;
    private int retryCount;
    private String lastError;
    private boolean isActive;
    private Process process;
    private Thread thread;
}
