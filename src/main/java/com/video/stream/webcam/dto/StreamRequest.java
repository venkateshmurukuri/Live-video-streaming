package com.video.stream.webcam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamRequest {
    
    @NotBlank(message = "inputStream is required")
    private String inputStream;
    
    @NotBlank(message = "destination is required")
    private String destination;
}
