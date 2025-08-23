package com.video.stream.webcam.controller;

import com.video.stream.webcam.dto.StreamRequest;
import com.video.stream.webcam.dto.StreamResponse;
import com.video.stream.webcam.dto.StreamStatus;
import com.video.stream.webcam.dto.StreamStatusResponse;
import com.video.stream.webcam.service.StreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {
    
    private final StreamService streamService;
    
    @GetMapping("/")
    public String getWebInterface() {
        return "redirect:/index.html";
    }
    
    @PostMapping("/create")
    public ResponseEntity<StreamResponse> createStream(@Valid @RequestBody StreamRequest request) {
        log.info("Received stream creation request - inputStream: {}, destination: {}", 
                request.getInputStream(), request.getDestination());
        
        try {
            // Validate RTMP destination format
            if (!streamService.validateRtmpDestination(request.getDestination())) {
                log.warn("Invalid RTMP destination format: {}", request.getDestination());
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new StreamResponse("Invalid RTMP destination format. Expected format: rtmp://host[:port]/path", false));
            }
            
            // Create stream and start FFmpeg streaming
            UUID streamId = streamService.createAndStartStream(request.getInputStream(), request.getDestination());
            
            log.info("Stream created and started successfully with ID: {}", streamId);
            
            // Return success response with UUID
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new StreamResponse(streamId.toString()));
                
        } catch (Exception e) {
            log.error("Error creating stream", e);
            // Return error response for any unexpected errors
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StreamResponse("An error occurred while processing the request: " + e.getMessage(), false));
        }
    }
    
    @GetMapping("/{streamId}/status")
    public ResponseEntity<StreamStatusResponse> getStreamStatus(@PathVariable String streamId) {
        try {
            UUID uuid = UUID.fromString(streamId);
            StreamStatus status = streamService.getStreamStatus(uuid);
            
            if (status == null) {
                return ResponseEntity.notFound().build();
            }
            
            StreamStatusResponse response = StreamStatusResponse.from(status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: {}", streamId);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting stream status for streamId: {}", streamId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{streamId}")
    public ResponseEntity<StreamResponse> stopStream(@PathVariable String streamId) {
        try {
            UUID uuid = UUID.fromString(streamId);
            boolean stopped = streamService.stopStreaming(uuid);
            
            if (stopped) {
                return ResponseEntity.ok(new StreamResponse(streamId, "Stream stopped successfully", true));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: {}", streamId);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error stopping stream for streamId: {}", streamId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StreamResponse(streamId, "Error stopping stream: " + e.getMessage(), false));
        }
    }
}
