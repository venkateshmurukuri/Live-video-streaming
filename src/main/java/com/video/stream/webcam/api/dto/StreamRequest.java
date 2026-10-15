package com.video.stream.webcam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StreamRequest(

        @NotBlank(message = "device is required")
        String device,

        @NotBlank(message = "destination is required")
        @Pattern(regexp = "^rtmps?://[A-Za-z0-9.\\-]+(:\\d{1,5})?/[A-Za-z0-9._\\-]+(/[A-Za-z0-9._\\-]+)*$",
                message = "destination must look like rtmp://host[:port]/app/stream")
        String destination) {
}
