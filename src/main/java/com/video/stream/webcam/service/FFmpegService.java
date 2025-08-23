package com.video.stream.webcam.service;

import com.video.stream.webcam.dto.StreamStatus;

import java.util.UUID;

public interface FFmpegService {
    
    /**
     * Start FFmpeg streaming process
     * @param streamId the stream ID
     * @param webcamName the webcam device name
     * @param rtmpDestination the RTMP destination URL
     * @return StreamStatus with process information
     */
    StreamStatus startStreaming(UUID streamId, String webcamName, String rtmpDestination);
    
    /**
     * Stop FFmpeg streaming process
     * @param streamId the stream ID
     * @return true if stopped successfully, false otherwise
     */
    boolean stopStreaming(UUID streamId);
    
    /**
     * Get stream status
     * @param streamId the stream ID
     * @return StreamStatus or null if not found
     */
    StreamStatus getStreamStatus(UUID streamId);
    
    /**
     * Check if stream is active
     * @param streamId the stream ID
     * @return true if stream is active, false otherwise
     */
    boolean isStreamActive(UUID streamId);
}
