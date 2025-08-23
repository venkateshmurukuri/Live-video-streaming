package com.video.stream.webcam.service.impl;

import com.video.stream.webcam.dto.StreamStatus;
import com.video.stream.webcam.service.FFmpegService;
import com.video.stream.webcam.service.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamServiceImpl implements StreamService {
    
    private final FFmpegService ffmpegService;
    
    // In-memory storage for stream data
    private final Map<UUID, StreamData> streamDataMap = new HashMap<>();
    
    // RTMP URL pattern validation
    private static final Pattern RTMP_PATTERN = Pattern.compile(
        "^rtmp://[a-zA-Z0-9.-]+(:[0-9]+)?(/[a-zA-Z0-9._/-]*)?$"
    );
    
    @Override
    public UUID createAndStartStream(String inputStream, String destination) {
        UUID streamId = UUID.randomUUID();
        StreamData streamData = new StreamData(inputStream, destination);
        streamDataMap.put(streamId, streamData);
        log.info("Stream data saved with ID: {}", streamId);
        
        // Start FFmpeg streaming
        ffmpegService.startStreaming(streamId, inputStream, destination);
        
        return streamId;
    }
    
    @Override
    public boolean validateRtmpDestination(String destination) {
        if (destination == null || destination.trim().isEmpty()) {
            log.warn("RTMP destination is null or empty");
            return false;
        }
        boolean isValid = RTMP_PATTERN.matcher(destination.trim()).matches();
        log.debug("RTMP destination validation result: {} for destination: {}", isValid, destination);
        return isValid;
    }
    
    @Override
    public boolean stopStreaming(UUID streamId) {
        return ffmpegService.stopStreaming(streamId);
    }
    
    @Override
    public StreamStatus getStreamStatus(UUID streamId) {
        return ffmpegService.getStreamStatus(streamId);
    }
    
    @Override
    public boolean isStreamActive(UUID streamId) {
        return ffmpegService.isStreamActive(streamId);
    }
    
    // Inner class to hold stream data
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class StreamData {
        private final String inputStream;
        private final String destination;
    }
}
