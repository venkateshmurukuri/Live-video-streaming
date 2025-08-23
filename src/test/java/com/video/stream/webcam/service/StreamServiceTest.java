package com.video.stream.webcam.service;

import com.video.stream.webcam.dto.StreamStatus;
import com.video.stream.webcam.service.impl.StreamServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamServiceTest {
    
    @Mock
    private FFmpegService ffmpegService;
    
    private StreamService streamService;
    
    @BeforeEach
    void setUp() {
        streamService = new StreamServiceImpl(ffmpegService);
    }
    
    @Test
    void validateRtmpDestination_ValidFormats_ReturnsTrue() {
        // Arrange
        String[] validUrls = {
            "rtmp://localhost/live/stream",
            "rtmp://192.168.1.100:1935/live/stream",
            "rtmp://example.com/live",
            "rtmp://server.com:1935/app/stream",
            "rtmp://localhost:1935/hls/test3",
            "rtmp://example.com/app/stream",
            "rtmp://localhost/app/stream"
        };

        // Act & Assert
        for (String url : validUrls) {
            assertTrue(streamService.validateRtmpDestination(url), 
                "Should validate: " + url);
        }
    }
    
    @Test
    void testValidateRtmpDestination_InvalidFormats() {
        assertFalse(streamService.validateRtmpDestination("http://localhost/live/stream"));
        assertFalse(streamService.validateRtmpDestination("rtmp://"));
        assertFalse(streamService.validateRtmpDestination(""));
        assertFalse(streamService.validateRtmpDestination(null));
        assertFalse(streamService.validateRtmpDestination("ftp://localhost/live/stream"));
    }
    
    @Test
    void validateRtmpDestination_NullInput_ReturnsFalse() {
        // Act
        boolean result = streamService.validateRtmpDestination(null);

        // Assert
        assertFalse(result);
    }
    
    @Test
    void validateRtmpDestination_EmptyInput_ReturnsFalse() {
        // Act
        boolean result = streamService.validateRtmpDestination("");

        // Assert
        assertFalse(result);
    }
    
    @Test
    void validateRtmpDestination_WhitespaceInput_ReturnsFalse() {
        // Act
        boolean result = streamService.validateRtmpDestination("   ");

        // Assert
        assertFalse(result);
    }
    
    @Test
    void createAndStartStream_ValidInput_ReturnsStreamId() {
        // Arrange
        String inputStream = "HD Webcam";
        String destination = "rtmp://localhost:1935/hls/test3";
        UUID expectedStreamId = UUID.randomUUID();
        
        when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
            .thenReturn(StreamStatus.builder()
                .streamId(expectedStreamId.toString())
                .status("RUNNING")
                .retryCount(0)
                .isActive(true)
                .build());

        // Act
        UUID actualStreamId = streamService.createAndStartStream(inputStream, destination);

        // Assert
        assertNotNull(actualStreamId);
        verify(ffmpegService).startStreaming(actualStreamId, inputStream, destination);
    }
    
    @Test
    void createAndStartStream_WithSpecialCharacters_HandlesCorrectly() {
        // Arrange
        String inputStream = "HD Webcam (USB)";
        String destination = "rtmp://localhost:1935/hls/test-stream_123";
        UUID expectedStreamId = UUID.randomUUID();
        
        when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
            .thenReturn(StreamStatus.builder()
                .streamId(expectedStreamId.toString())
                .status("RUNNING")
                .retryCount(0)
                .isActive(true)
                .build());

        // Act
        UUID actualStreamId = streamService.createAndStartStream(inputStream, destination);

        // Assert
        assertNotNull(actualStreamId);
        verify(ffmpegService).startStreaming(actualStreamId, inputStream, destination);
    }
    
    @Test
    void createAndStartStream_FFmpegServiceThrowsException_PropagatesException() {
        // Arrange
        String inputStream = "HD Webcam";
        String destination = "rtmp://localhost:1935/hls/test3";
        
        when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
            .thenThrow(new RuntimeException("FFmpeg error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            streamService.createAndStartStream(inputStream, destination);
        });
    }
    
    @Test
    void stopStreaming_ValidStreamId_ReturnsTrue() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.stopStreaming(streamId)).thenReturn(true);

        // Act
        boolean result = streamService.stopStreaming(streamId);

        // Assert
        assertTrue(result);
        verify(ffmpegService).stopStreaming(streamId);
    }
    
    @Test
    void stopStreaming_InvalidStreamId_ReturnsFalse() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.stopStreaming(streamId)).thenReturn(false);

        // Act
        boolean result = streamService.stopStreaming(streamId);

        // Assert
        assertFalse(result);
        verify(ffmpegService).stopStreaming(streamId);
    }
    
    @Test
    void stopStreaming_FFmpegServiceThrowsException_PropagatesException() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.stopStreaming(streamId)).thenThrow(new RuntimeException("FFmpeg error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            streamService.stopStreaming(streamId);
        });
    }
    
    @Test
    void getStreamStatus_ValidStreamId_ReturnsStatus() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        StreamStatus expectedStatus = StreamStatus.builder()
            .streamId(streamId.toString())
            .status("RUNNING")
            .startTime(LocalDateTime.now())
            .retryCount(0)
            .isActive(true)
            .build();
        when(ffmpegService.getStreamStatus(streamId)).thenReturn(expectedStatus);

        // Act
        StreamStatus actualStatus = streamService.getStreamStatus(streamId);

        // Assert
        assertNotNull(actualStatus);
        assertEquals(expectedStatus, actualStatus);
        verify(ffmpegService).getStreamStatus(streamId);
    }
    
    @Test
    void getStreamStatus_InvalidStreamId_ReturnsNull() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.getStreamStatus(streamId)).thenReturn(null);

        // Act
        StreamStatus actualStatus = streamService.getStreamStatus(streamId);

        // Assert
        assertNull(actualStatus);
        verify(ffmpegService).getStreamStatus(streamId);
    }
    
    @Test
    void getStreamStatus_FFmpegServiceThrowsException_PropagatesException() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.getStreamStatus(streamId)).thenThrow(new RuntimeException("FFmpeg error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            streamService.getStreamStatus(streamId);
        });
    }
    
    @Test
    void isStreamActive_ActiveStream_ReturnsTrue() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.isStreamActive(streamId)).thenReturn(true);

        // Act
        boolean result = streamService.isStreamActive(streamId);

        // Assert
        assertTrue(result);
        verify(ffmpegService).isStreamActive(streamId);
    }
    
    @Test
    void isStreamActive_InactiveStream_ReturnsFalse() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.isStreamActive(streamId)).thenReturn(false);

        // Act
        boolean result = streamService.isStreamActive(streamId);

        // Assert
        assertFalse(result);
        verify(ffmpegService).isStreamActive(streamId);
    }
    
    @Test
    void isStreamActive_FFmpegServiceThrowsException_PropagatesException() {
        // Arrange
        UUID streamId = UUID.randomUUID();
        when(ffmpegService.isStreamActive(streamId)).thenThrow(new RuntimeException("FFmpeg error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            streamService.isStreamActive(streamId);
        });
    }
    
    @Test
    void ffmpegService_StartStreamingWithDifferentStatuses_HandlesCorrectly() {
        // Arrange
        String inputStream = "HD Webcam";
        String destination = "rtmp://localhost:1935/hls/test3";
        UUID streamId = UUID.randomUUID();
        
        // Test different status responses
        String[] statuses = {"STARTING", "RUNNING", "STOPPED", "ERROR", "RETRYING"};
        
        for (String status : statuses) {
            when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
                .thenReturn(StreamStatus.builder()
                    .streamId(streamId.toString())
                    .status(status)
                    .retryCount(0)
                    .isActive(!"STOPPED".equals(status) && !"ERROR".equals(status))
                    .build());

            // Act
            UUID actualStreamId = streamService.createAndStartStream(inputStream, destination);

            // Assert
            assertNotNull(actualStreamId);
            verify(ffmpegService).startStreaming(actualStreamId, inputStream, destination);
            
            // Reset mock for next iteration
            reset(ffmpegService);
        }
    }
    
    @Test
    void ffmpegService_StartStreamingWithRetryCount_HandlesCorrectly() {
        // Arrange
        String inputStream = "HD Webcam";
        String destination = "rtmp://localhost:1935/hls/test3";
        UUID streamId = UUID.randomUUID();
        
        when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
            .thenReturn(StreamStatus.builder()
                .streamId(streamId.toString())
                .status("RETRYING")
                .retryCount(3)
                .lastRetryTime(LocalDateTime.now())
                .lastError("Connection timeout")
                .isActive(false)
                .build());

        // Act
        UUID actualStreamId = streamService.createAndStartStream(inputStream, destination);

        // Assert
        assertNotNull(actualStreamId);
        verify(ffmpegService).startStreaming(actualStreamId, inputStream, destination);
    }
    
    @Test
    void ffmpegService_StartStreamingWithErrorDetails_HandlesCorrectly() {
        // Arrange
        String inputStream = "HD Webcam";
        String destination = "rtmp://localhost:1935/hls/test3";
        UUID streamId = UUID.randomUUID();
        
        when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
            .thenReturn(StreamStatus.builder()
                .streamId(streamId.toString())
                .status("ERROR")
                .retryCount(5)
                .lastRetryTime(LocalDateTime.now())
                .lastError("FFmpeg process failed: Invalid input format")
                .isActive(false)
                .build());

        // Act
        UUID actualStreamId = streamService.createAndStartStream(inputStream, destination);

        // Assert
        assertNotNull(actualStreamId);
        verify(ffmpegService).startStreaming(actualStreamId, inputStream, destination);
    }
    
    @Test
    void createAndStartStream_WithVeryLongInput_HandlesCorrectly() {
        // Arrange
        String inputStream = "Very Long Webcam Name With Special Characters @#$%^&*()_+-=[]{}|;':\",./<>?";
        String destination = "rtmp://localhost:1935/hls/very-long-stream-name-with-special-characters";
        UUID expectedStreamId = UUID.randomUUID();
        
        when(ffmpegService.startStreaming(any(UUID.class), eq(inputStream), eq(destination)))
            .thenReturn(StreamStatus.builder()
                .streamId(expectedStreamId.toString())
                .status("RUNNING")
                .retryCount(0)
                .isActive(true)
                .build());

        // Act
        UUID actualStreamId = streamService.createAndStartStream(inputStream, destination);

        // Assert
        assertNotNull(actualStreamId);
        verify(ffmpegService).startStreaming(actualStreamId, inputStream, destination);
    }
}
