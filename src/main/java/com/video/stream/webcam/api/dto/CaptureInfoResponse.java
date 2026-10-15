package com.video.stream.webcam.api.dto;

/** What the UI needs to pre-fill the form for the machine the app is running on. */
public record CaptureInfoResponse(
        String backend,
        String defaultDevice,
        String deviceHint,
        String defaultDestination) {
}
