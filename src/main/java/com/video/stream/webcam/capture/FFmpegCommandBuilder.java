package com.video.stream.webcam.capture;

import com.video.stream.webcam.config.StreamProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Builds the FFmpeg argument list as a list, so device names with spaces never go through a shell. */
@Component
public class FFmpegCommandBuilder {

    private final StreamProperties props;
    private final CaptureBackend backend;

    @Autowired
    public FFmpegCommandBuilder(StreamProperties props) {
        this(props, CaptureBackend.fromProperty(props.getCaptureFormat(), System.getProperty("os.name")));
    }

    public FFmpegCommandBuilder(StreamProperties props, CaptureBackend backend) {
        this.props = props;
        this.backend = backend;
    }

    public CaptureBackend backend() {
        return backend;
    }

    public List<String> versionCommand() {
        return List.of(props.getFfmpegPath(), "-version");
    }

    /**
     * Camera (or test pattern) in, H.264/AAC in FLV out over RTMP.
     * <p>
     * A silent AAC track is muxed in because some RTMP servers and players stall on video-only streams.
     * The GOP is pinned to two seconds with scene-cut detection off so the HLS packager can cut
     * segments on keyframes at a predictable cadence.
     */
    public List<String> build(String device, String rtmpDestination) {
        CaptureBackend input = "test".equalsIgnoreCase(device) ? CaptureBackend.TEST : backend;
        int fps = props.getFps();
        String gop = Integer.toString(fps * 2);
        String videoBitrate = props.getVideoBitrateKbps() + "k";
        String videoBuffer = (props.getVideoBitrateKbps() * 2) + "k";

        List<String> cmd = new ArrayList<>();
        cmd.add(props.getFfmpegPath());
        cmd.addAll(List.of("-hide_banner", "-loglevel", "warning", "-nostats"));
        cmd.addAll(input.inputArgs(device, fps, props.getVideoSize()));
        cmd.addAll(List.of("-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100"));
        cmd.addAll(List.of("-map", "0:v:0", "-map", "1:a:0"));
        cmd.addAll(List.of(
                "-c:v", "libx264", "-preset", "veryfast", "-tune", "zerolatency", "-pix_fmt", "yuv420p",
                "-r", Integer.toString(fps), "-g", gop, "-keyint_min", gop, "-sc_threshold", "0",
                "-b:v", videoBitrate, "-maxrate", videoBitrate, "-bufsize", videoBuffer));
        cmd.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ar", "44100"));
        cmd.addAll(List.of("-f", "flv", rtmpDestination));
        return List.copyOf(cmd);
    }
}
