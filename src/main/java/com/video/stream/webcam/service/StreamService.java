package com.video.stream.webcam.service;

import com.video.stream.webcam.dto.StreamStatus;

import java.util.UUID;

public interface StreamService {
    
    /**
     * Create stream and start FFmpeg streaming
     * @param inputStream the input stream string
     * @param destination the RTMP destination
     * @return UUID of the created stream
     */
    UUID createAndStartStream(String inputStream, String destination);
    
    /**
     * Validate if the destination follows RTMP standard format
     * @param destination the destination to validate
     * @return true if valid RTMP format, false otherwise
     */
    boolean validateRtmpDestination(String destination);
    
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
