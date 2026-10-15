package com.video.stream.webcam.stream;

import java.util.UUID;

public class StreamNotFoundException extends RuntimeException {

    public StreamNotFoundException(UUID id) {
        super("No stream with id " + id);
    }
}
