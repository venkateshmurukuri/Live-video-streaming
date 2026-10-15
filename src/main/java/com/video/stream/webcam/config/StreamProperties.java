package com.video.stream.webcam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything tunable about capture, encoding and retries. Bound from the {@code stream.*} properties. */
@Data
@ConfigurationProperties(prefix = "stream")
public class StreamProperties {

    /** FFmpeg binary; a bare name is resolved on PATH. */
    private String ffmpegPath = "ffmpeg";

    /** Capture backend: auto, dshow, avfoundation, v4l2 or test. */
    private String captureFormat = "auto";

    /** RTMP URL pre-filled in the UI. */
    private String defaultDestination = "rtmp://localhost:1935/hls/cam";

    private int fps = 30;
    private String videoSize = "1280x720";
    private int videoBitrateKbps = 2000;

    /** Relaunches allowed after consecutive FFmpeg failures before the stream is marked FAILED. */
    private int maxRetries = 5;
    private long initialBackoffMs = 1000;
    private long maxBackoffMs = 15000;

    /** A run longer than this is considered healthy, so a later failure gets a fresh retry budget. */
    private long stableRunMs = 30000;
}
