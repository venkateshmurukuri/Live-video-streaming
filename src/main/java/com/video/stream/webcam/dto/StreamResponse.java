package com.video.stream.webcam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamResponse {
    
    private String streamId;
    private String message;
    private boolean success;
    
    // Success constructor
    public StreamResponse(String streamId) {
        this.streamId = streamId;
        this.success = true;
        this.message = "Stream created successfully";
    }
    
    // Error constructor
    public StreamResponse(String message, boolean success) {
        this.message = message;
        this.success = success;
    }
}
